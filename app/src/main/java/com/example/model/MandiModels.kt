package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

const val MANN_WEIGHT_KG = 40.0

val COMMODITY_PRESETS = listOf(
    "Potato / Aloo",
    "Onion / Pyaaz",
    "Tomato / Tamatar",
    "Garlic / Lehsun",
    "Ginger / Adrak",
    "Wheat / Gandum",
    "Rice / Chawal"
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PosTab {
    POS,
    HISTORY
}

enum class BillDateFilter(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    ALL("All Bills"),
    CUSTOM_DATE("Pick Date")
}

enum class ReceiptPaperWidth(val label: String, val cssWidthMm: Int) {
    WIDTH_58MM("58mm", 54),
    WIDTH_80MM("80mm", 72)
}

@Keep
@IgnoreExtraProperties
data class MandiBill(
    @DocumentId val id: String = "",
    val userId: String = "",
    val billNumber: String = "",
    val customerName: String = "Cash Buyer",
    val itemName: String = "Potato / Aloo",
    val weights: List<Double> = emptyList(),
    val totalBags: Long = 0L,
    val totalWeightKg: Double = 0.0,
    val ratePerMann: Double = 0.0,
    val finalCost: Double = 0.0,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    // Client-side timestamp fallback used solely for offline local mirror & display; excluded from Firestore writes
    @get:com.google.firebase.firestore.Exclude val localTimestampMillis: Long = 0L
) {
    fun effectiveTimeMillis(): Long {
        return createdAt?.toDate()?.time
            ?: if (localTimestampMillis > 0L) localTimestampMillis else System.currentTimeMillis()
    }
}

data class MandiDraft(
    val weights: List<Double> = emptyList(),
    val mojodaRateInput: String = "",
    val customerName: String = "Cash Buyer",
    val itemName: String = "Potato / Aloo",
    val billSequenceToday: Int = 0,
    val sequenceDateKey: String = ""
)

data class WeighmentTotals(
    val totalBags: Int,
    val totalWeightKg: Double,
    val mannCount: Int,
    val remainingKg: Double,
    val ratePerMann: Double,
    val finalCostExact: Double,
    val finalCostRounded: Long
)

data class DailyKpiSummary(
    val totalBills: Int,
    val totalBags: Int,
    val totalWeightKg: Double,
    val mannCount: Int,
    val remainingKg: Double,
    val totalCashRounded: Long
)

object MandiMath {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val wholeRupeeFormat = DecimalFormat("#,##0", symbols)
    private val exactDecimalFormat = DecimalFormat("#,##0.00", symbols)
    private val weightTrimFormat = DecimalFormat("#,##0.##", symbols)

    fun calculate(weights: List<Double>, ratePerMann: Double): WeighmentTotals {
        val totalBags = weights.size
        val totalWeightKg = weights.sum()
        val mannCount = (totalWeightKg / MANN_WEIGHT_KG).toInt()
        val remainingKg = (totalWeightKg - (mannCount * MANN_WEIGHT_KG)).coerceAtLeast(0.0)
        val safeRate = ratePerMann.coerceAtLeast(0.0)
        val finalCostExact = (totalWeightKg * safeRate) / MANN_WEIGHT_KG
        val finalCostRounded = finalCostExact.roundToLong()
        return WeighmentTotals(
            totalBags = totalBags,
            totalWeightKg = totalWeightKg,
            mannCount = mannCount,
            remainingKg = remainingKg,
            ratePerMann = safeRate,
            finalCostExact = finalCostExact,
            finalCostRounded = finalCostRounded
        )
    }

    fun calculateMannBreakdown(totalWeightKg: Double): Pair<Int, Double> {
        val mann = (totalWeightKg / MANN_WEIGHT_KG).toInt()
        val rem = (totalWeightKg - (mann * MANN_WEIGHT_KG)).coerceAtLeast(0.0)
        return mann to rem
    }

    fun formatWeight(kg: Double): String = weightTrimFormat.format(kg)

    fun formatMannSubtitle(totalWeightKg: Double): String {
        val (mann, rem) = calculateMannBreakdown(totalWeightKg)
        return "$mann Mann, ${formatWeight(rem)} kg"
    }

    fun formatRupeesWhole(amount: Long): String = "Rs. ${wholeRupeeFormat.format(amount)}"

    fun formatRupeesWhole(amount: Double): String = "Rs. ${wholeRupeeFormat.format(amount.roundToLong())}"

    fun formatRupeesExact(amount: Double): String = "Rs. ${exactDecimalFormat.format(amount)}"

    fun formatDateTime(millis: Long): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        return fmt.format(Date(millis))
    }

    fun formatTimeOnly(millis: Long): String {
        val fmt = SimpleDateFormat("hh:mm a", Locale.US)
        return fmt.format(Date(millis))
    }

    fun dateKey(millis: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        return fmt.format(Date(millis))
    }

    fun formatWhatsAppSlip(bill: MandiBill): String {
        val (mann, rem) = calculateMannBreakdown(bill.totalWeightKg)
        val bagsLines = bill.weights.mapIndexed { idx, w ->
            "#${idx + 1}: ${formatWeight(w)} kg"
        }.chunked(3).joinToString("\n") { it.joinToString(" | ") }

        return buildString {
            appendLine("*MANDI COMMISSION SHOP*")
            appendLine("Bill #: ${bill.billNumber}")
            appendLine("Date: ${formatDateTime(bill.effectiveTimeMillis())}")
            appendLine("Customer: ${bill.customerName}")
            appendLine("Item: ${bill.itemName}")
            appendLine("------------------------")
            appendLine("*Bag Weights:*")
            appendLine(bagsLines)
            appendLine("------------------------")
            appendLine("Total Bags: ${bill.totalBags}")
            appendLine("Total Weight: ${formatWeight(bill.totalWeightKg)} kg ($mann Mann, ${formatWeight(rem)} kg)")
            appendLine("Rate / 40kg Mann: ${formatRupeesWhole(bill.ratePerMann)}")
            appendLine("*NET PAYABLE: ${formatRupeesWhole(bill.finalCost)}*")
            append("_(Exact: ${formatRupeesExact(bill.finalCost)})_")
        }
    }

    fun buildCsv(bills: List<MandiBill>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        fun esc(value: String): String {
            val clean = value.replace("\"", "\"\"")
            return "\"$clean\""
        }
        val sb = StringBuilder()
        sb.appendLine(
            "Bill Number,Date,Time,Customer Name,Commodity,Total Bags,Total Weight (kg),Mann Breakdown,Rate per 40kg (Rs),Final Cost Exact (Rs),Final Cost Rounded (Rs),Bag Weights (kg)"
        )
        bills.forEach { bill ->
            val dt = Date(bill.effectiveTimeMillis())
            val mannText = formatMannSubtitle(bill.totalWeightKg)
            val weightsJoined = bill.weights.joinToString(" | ") { formatWeight(it) }
            sb.appendLine(
                listOf(
                    esc(bill.billNumber),
                    esc(dateFmt.format(dt)),
                    esc(timeFmt.format(dt)),
                    esc(bill.customerName),
                    esc(bill.itemName),
                    bill.totalBags.toString(),
                    formatWeight(bill.totalWeightKg).replace(",", ""),
                    esc(mannText),
                    String.format(Locale.US, "%.2f", bill.ratePerMann),
                    String.format(Locale.US, "%.2f", bill.finalCost),
                    bill.finalCost.roundToLong().toString(),
                    esc(weightsJoined)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }
}
