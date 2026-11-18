package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BillRepository
import com.example.data.DraftDataStore
import com.example.model.BillDateFilter
import com.example.model.DailyKpiSummary
import com.example.model.MandiBill
import com.example.model.MandiDraft
import com.example.model.MandiMath
import com.example.model.PosTab
import com.example.model.ReceiptPaperWidth
import com.example.model.UiState
import com.example.model.WeighmentTotals
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.math.roundToLong

data class PosUiState(
    val activeTab: PosTab = PosTab.POS,
    val mojodaRateInput: String = "",
    val customerName: String = "Cash Buyer",
    val itemName: String = "Potato / Aloo",
    val bagWeightInput: String = "",
    val weights: List<Double> = emptyList(),
    val showKeypad: Boolean = true,
    val isSaving: Boolean = false,
    val paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_80MM,
    val previewBill: MandiBill? = null,
    val triggerSystemPrintOnOpen: Boolean = false,
    val searchQuery: String = "",
    val dateFilter: BillDateFilter = BillDateFilter.TODAY,
    val customDateMillis: Long? = null,
    val billSequenceToday: Int = 0,
    val sequenceDateKey: String = ""
) {
    val ratePerMann: Double
        get() = mojodaRateInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0

    val totals: WeighmentTotals
        get() = MandiMath.calculate(weights, ratePerMann)
}

sealed interface PosUiEvent {
    data class ShowMessage(val message: String) : PosUiEvent
    data class LaunchPrint(val bill: MandiBill, val paperWidth: ReceiptPaperWidth) : PosUiEvent
}

class MandiPosViewModel(
    private val repository: BillRepository,
    private val draftDataStore: DraftDataStore,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PosUiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val localMirrorBills = MutableStateFlow<List<MandiBill>>(emptyList())

    private val cloudBillsState: StateFlow<UiState<List<MandiBill>>> =
        repository.observeBills(currentUserId)
            .map<List<MandiBill>, UiState<List<MandiBill>>> { bills ->
                UiState.Success(bills)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing bills", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to sync cloud bills"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val billsState: StateFlow<UiState<List<MandiBill>>> =
        combine(cloudBillsState, localMirrorBills) { cloud, local ->
            when (cloud) {
                is UiState.Success -> {
                    val cloudBillNums = cloud.data.map { it.billNumber }.toSet()
                    val pendingLocal = local.filter { it.billNumber !in cloudBillNums }
                    val merged = (cloud.data + pendingLocal)
                        .sortedByDescending { it.effectiveTimeMillis() }
                    viewModelScope.launch {
                        draftDataStore.saveMirroredBills(currentUserId, merged)
                    }
                    UiState.Success(merged)
                }
                is UiState.Loading -> {
                    if (local.isNotEmpty()) UiState.Success(local) else UiState.Loading
                }
                is UiState.Error -> {
                    if (local.isNotEmpty()) UiState.Success(local) else cloud
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val filteredBills: StateFlow<List<MandiBill>> =
        combine(billsState, _uiState) { state, ui ->
            val all = (state as? UiState.Success)?.data ?: emptyList()
            filterBills(all, ui.searchQuery, ui.dateFilter, ui.customDateMillis)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val todayKpis: StateFlow<DailyKpiSummary> =
        billsState.map { state ->
            val all = (state as? UiState.Success)?.data ?: emptyList()
            val todayBills = filterBills(all, "", BillDateFilter.TODAY, null)
            val totalWeight = todayBills.sumOf { it.totalWeightKg }
            val (mann, rem) = MandiMath.calculateMannBreakdown(totalWeight)
            DailyKpiSummary(
                totalBills = todayBills.size,
                totalBags = todayBills.sumOf { it.totalBags.toInt() },
                totalWeightKg = totalWeight,
                mannCount = mann,
                remainingKg = rem,
                totalCashRounded = todayBills.sumOf { it.finalCost.roundToLong() }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DailyKpiSummary(0, 0, 0.0, 0, 0.0, 0L)
        )

    init {
        viewModelScope.launch {
            val draft = draftDataStore.loadDraft()
            val mirrored = draftDataStore.loadMirroredBills(currentUserId)
            localMirrorBills.value = mirrored
            _uiState.update {
                it.copy(
                    mojodaRateInput = draft.mojodaRateInput,
                    customerName = draft.customerName,
                    itemName = draft.itemName,
                    weights = draft.weights,
                    billSequenceToday = draft.billSequenceToday,
                    sequenceDateKey = draft.sequenceDateKey
                )
            }
        }
    }

    private fun persistDraft() {
        val s = _uiState.value
        viewModelScope.launch {
            draftDataStore.saveDraft(
                MandiDraft(
                    weights = s.weights,
                    mojodaRateInput = s.mojodaRateInput,
                    customerName = s.customerName,
                    itemName = s.itemName,
                    billSequenceToday = s.billSequenceToday,
                    sequenceDateKey = s.sequenceDateKey
                )
            )
        }
    }

    fun selectTab(tab: PosTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateMojodaRate(raw: String) {
        val cleaned = sanitizeDecimalInput(raw, maxDecimals = 2, maxLength = 9)
        _uiState.update { it.copy(mojodaRateInput = cleaned) }
        persistDraft()
    }

    fun updateCustomerName(name: String) {
        _uiState.update { it.copy(customerName = name.take(80)) }
        persistDraft()
    }

    fun updateItemName(item: String) {
        _uiState.update { it.copy(itemName = item.take(80)) }
        persistDraft()
    }

    fun updateBagWeightInput(raw: String) {
        val cleaned = sanitizeDecimalInput(raw, maxDecimals = 2, maxLength = 6)
        _uiState.update { it.copy(bagWeightInput = cleaned) }
    }

    fun toggleKeypad() {
        _uiState.update { it.copy(showKeypad = !it.showKeypad) }
    }

    fun appendKeypadChar(ch: Char) {
        val current = _uiState.value.bagWeightInput
        if (ch == '.' && current.contains('.')) return
        if (ch == '.' && current.isEmpty()) {
            _uiState.update { it.copy(bagWeightInput = "0.") }
            return
        }
        val next = sanitizeDecimalInput(current + ch, maxDecimals = 2, maxLength = 6)
        _uiState.update { it.copy(bagWeightInput = next) }
    }

    fun backspaceKeypad() {
        val current = _uiState.value.bagWeightInput
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(bagWeightInput = current.dropLast(1)) }
        }
    }

    fun clearKeypadInput() {
        _uiState.update { it.copy(bagWeightInput = "") }
    }

    fun addBagFromInput() {
        val raw = _uiState.value.bagWeightInput.trim()
        val weight = raw.toDoubleOrNull()
        if (weight == null || weight <= 0.0 || weight > 1000.0) {
            _events.tryEmit(PosUiEvent.ShowMessage("Enter a valid bag weight (0.1 to 1000 kg)"))
            return
        }
        if (_uiState.value.weights.size >= 500) {
            _events.tryEmit(PosUiEvent.ShowMessage("Maximum 500 bags per bill reached"))
            return
        }
        _uiState.update {
            it.copy(
                weights = it.weights + weight,
                bagWeightInput = ""
            )
        }
        persistDraft()
    }

    fun removeBagAt(index: Int) {
        val list = _uiState.value.weights
        if (index !in list.indices) return
        val updated = list.toMutableList().apply { removeAt(index) }
        _uiState.update { it.copy(weights = updated) }
        persistDraft()
    }

    fun updateBagAt(index: Int, newWeight: Double) {
        val list = _uiState.value.weights
        if (index !in list.indices) return
        if (newWeight <= 0.0 || newWeight > 1000.0) {
            _events.tryEmit(PosUiEvent.ShowMessage("Bag weight must be between 0.1 and 1000 kg"))
            return
        }
        val updated = list.toMutableList().apply { set(index, newWeight) }
        _uiState.update { it.copy(weights = updated) }
        persistDraft()
    }

    fun undoLastBag() {
        val list = _uiState.value.weights
        if (list.isEmpty()) return
        _uiState.update { it.copy(weights = list.dropLast(1)) }
        persistDraft()
    }

    fun resetCurrentBillKeepRate() {
        _uiState.update {
            it.copy(
                weights = emptyList(),
                bagWeightInput = "",
                customerName = "Cash Buyer"
            )
        }
        persistDraft()
        _events.tryEmit(PosUiEvent.ShowMessage("New bill ready (Mojoda rate kept)"))
    }

    fun saveAndPrintBill() {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.weights.isEmpty()) {
            _events.tryEmit(PosUiEvent.ShowMessage("Add at least one bag weight before saving."))
            return
        }
        if (state.ratePerMann <= 0.0) {
            _events.tryEmit(PosUiEvent.ShowMessage("Enter a valid Mojoda rate per 40 kg Mann."))
            return
        }

        val now = System.currentTimeMillis()
        val todayKey = MandiMath.dateKey(now)
        val nextSeq = if (state.sequenceDateKey == todayKey) state.billSequenceToday + 1 else 1
        val billNumber = "MND-${todayKey.takeLast(6)}-${nextSeq.toString().padStart(3, '0')}"

        val totals = state.totals
        val cleanCustomer = state.customerName.trim().ifBlank { "Cash Buyer" }
        val cleanItem = state.itemName.trim().ifBlank { "Potato / Aloo" }

        val draftBill = MandiBill(
            id = "local_${now}",
            userId = currentUserId,
            billNumber = billNumber,
            customerName = cleanCustomer,
            itemName = cleanItem,
            weights = state.weights,
            totalBags = totals.totalBags.toLong(),
            totalWeightKg = totals.totalWeightKg,
            ratePerMann = totals.ratePerMann,
            finalCost = totals.finalCostExact,
            createdAt = null,
            localTimestampMillis = now
        )

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val result = withTimeoutOrNull(3500L) {
                repository.saveBill(currentUserId, draftBill)
            }

            if (result != null && result.isFailure) {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Failed to save bill."
                _uiState.update { it.copy(isSaving = false) }
                _events.tryEmit(PosUiEvent.ShowMessage(msg))
                return@launch
            }

            val savedBill = result?.getOrNull() ?: draftBill
            val updatedMirror = (listOf(savedBill) + localMirrorBills.value)
                .distinctBy { it.billNumber }
                .sortedByDescending { it.effectiveTimeMillis() }
            localMirrorBills.value = updatedMirror
            draftDataStore.saveMirroredBills(currentUserId, updatedMirror)

            _uiState.update {
                it.copy(
                    isSaving = false,
                    weights = emptyList(),
                    bagWeightInput = "",
                    customerName = "Cash Buyer",
                    billSequenceToday = nextSeq,
                    sequenceDateKey = todayKey,
                    previewBill = savedBill,
                    triggerSystemPrintOnOpen = true
                )
            }
            persistDraft()

            val syncNotice = if (result == null) {
                "Bill $billNumber saved offline — will sync automatically"
            } else {
                "Bill $billNumber saved to Cloud Firestore"
            }
            _events.tryEmit(PosUiEvent.ShowMessage(syncNotice))
            _events.tryEmit(PosUiEvent.LaunchPrint(savedBill, _uiState.value.paperWidth))
        }
    }

    fun openReceiptPreview(bill: MandiBill, autoPrint: Boolean = false) {
        _uiState.update {
            it.copy(
                previewBill = bill,
                triggerSystemPrintOnOpen = autoPrint
            )
        }
        if (autoPrint) {
            _events.tryEmit(PosUiEvent.LaunchPrint(bill, _uiState.value.paperWidth))
        }
    }

    fun closeReceiptPreview() {
        _uiState.update {
            it.copy(previewBill = null, triggerSystemPrintOnOpen = false)
        }
    }

    fun setPaperWidth(width: ReceiptPaperWidth) {
        _uiState.update { it.copy(paperWidth = width) }
    }

    fun buildCurrentDraftSlipText(): String? {
        val state = _uiState.value
        if (state.weights.isEmpty()) return null
        val totals = state.totals
        val tempBill = MandiBill(
            billNumber = "DRAFT",
            customerName = state.customerName.trim().ifBlank { "Cash Buyer" },
            itemName = state.itemName.trim().ifBlank { "Potato / Aloo" },
            weights = state.weights,
            totalBags = totals.totalBags.toLong(),
            totalWeightKg = totals.totalWeightKg,
            ratePerMann = totals.ratePerMann,
            finalCost = totals.finalCostExact,
            localTimestampMillis = System.currentTimeMillis()
        )
        return MandiMath.formatWhatsAppSlip(tempBill)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateDateFilter(filter: BillDateFilter, customMillis: Long? = null) {
        _uiState.update {
            it.copy(
                dateFilter = filter,
                customDateMillis = if (filter == BillDateFilter.CUSTOM_DATE) customMillis else it.customDateMillis
            )
        }
    }

    fun deleteBill(bill: MandiBill) {
        viewModelScope.launch {
            val updatedLocal = localMirrorBills.value.filterNot {
                it.billNumber == bill.billNumber || (bill.id.isNotBlank() && it.id == bill.id)
            }
            localMirrorBills.value = updatedLocal
            draftDataStore.saveMirroredBills(currentUserId, updatedLocal)

            if (bill.id.isNotBlank() && !bill.id.startsWith("local_")) {
                val res = repository.deleteBill(currentUserId, bill.id)
                if (res.isFailure) {
                    _events.tryEmit(
                        PosUiEvent.ShowMessage(
                            res.exceptionOrNull()?.localizedMessage ?: "Failed to delete cloud bill"
                        )
                    )
                    return@launch
                }
            }
            _events.tryEmit(PosUiEvent.ShowMessage("Bill ${bill.billNumber} deleted"))
        }
    }

    private fun filterBills(
        bills: List<MandiBill>,
        query: String,
        filter: BillDateFilter,
        customDateMillis: Long?
    ): List<MandiBill> {
        val q = query.trim().lowercase()
        val now = System.currentTimeMillis()
        val todayKey = MandiMath.dateKey(now)
        val sevenDaysAgo = startOfDayMillis(now) - (6L * 24L * 60L * 60L * 1000L)
        val customKey = customDateMillis?.let { MandiMath.dateKey(it) }

        return bills.filter { bill ->
            val time = bill.effectiveTimeMillis()
            val dateMatch = when (filter) {
                BillDateFilter.TODAY -> MandiMath.dateKey(time) == todayKey
                BillDateFilter.LAST_7_DAYS -> time >= sevenDaysAgo
                BillDateFilter.ALL -> true
                BillDateFilter.CUSTOM_DATE -> customKey == null || MandiMath.dateKey(time) == customKey
            }
            val textMatch = q.isEmpty() ||
                bill.customerName.lowercase().contains(q) ||
                bill.itemName.lowercase().contains(q) ||
                bill.billNumber.lowercase().contains(q)
            dateMatch && textMatch
        }
    }

    private fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun sanitizeDecimalInput(raw: String, maxDecimals: Int, maxLength: Int): String {
        val sb = StringBuilder()
        var dotSeen = false
        var decimalsAfterDot = 0
        for (ch in raw) {
            if (sb.length >= maxLength) break
            when {
                ch.isDigit() -> {
                    if (dotSeen) {
                        if (decimalsAfterDot < maxDecimals) {
                            sb.append(ch)
                            decimalsAfterDot++
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                ch == '.' && !dotSeen -> {
                    dotSeen = true
                    if (sb.isEmpty()) sb.append('0')
                    sb.append('.')
                }
            }
        }
        return sb.toString()
    }

    private companion object {
        const val TAG = "MandiPosVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
