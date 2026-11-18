package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.BillRepository
import com.example.data.DraftDataStore
import com.example.model.MandiMath
import com.example.model.PosTab
import com.example.print.ThermalReceiptEngine
import com.example.ui.HistoryScreen
import com.example.ui.MandiPosViewModel
import com.example.ui.PosScreen
import com.example.ui.PosUiEvent
import com.example.ui.ThermalReceiptPreviewDialog
import com.example.ui.theme.MandiPosTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MandiPosTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MandiPosAppShell(user = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandiPosAppShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: MandiPosViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MandiPosViewModel(
                    repository = BillRepository(db),
                    draftDataStore = DraftDataStore(app.applicationContext),
                    currentUserId = user.uid
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val billsState by viewModel.billsState.collectAsStateWithLifecycle()
    val filteredBills by viewModel.filteredBills.collectAsStateWithLifecycle()
    val todayKpis by viewModel.todayKpis.collectAsStateWithLifecycle()

    var showSignOutConfirm by remember { mutableStateOf(false) }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = MandiMath.buildCsv(filteredBills)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csv.toByteArray(Charsets.UTF_8))
                }
                scope.launch {
                    snackbarHostState.showSnackbar("CSV exported for Google Sheets (${filteredBills.size} bills)")
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to save CSV")
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PosUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is PosUiEvent.LaunchPrint -> {
                    ThermalReceiptEngine.printThermalReceipt(
                        context = context,
                        bill = event.bill,
                        paperWidth = event.paperWidth,
                        onError = { err ->
                            scope.launch { snackbarHostState.showSnackbar(err) }
                        }
                    )
                }
            }
        }
    }

    if (uiState.activeTab == PosTab.HISTORY) {
        BackHandler {
            viewModel.selectTab(PosTab.POS)
        }
    }

    val operatorLabel = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@")
        ?: "Operator"
    val operatorInitial = operatorLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "M"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 780.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = operatorInitial,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Text(
                                    text = operatorLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showSignOutConfirm = true },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.sign_out)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = uiState.activeTab == PosTab.POS,
                            onClick = { viewModel.selectTab(PosTab.POS) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.activeTab == PosTab.POS) {
                                        Icons.Filled.PointOfSale
                                    } else {
                                        Icons.Outlined.PointOfSale
                                    },
                                    contentDescription = stringResource(R.string.tab_pos)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_pos)) },
                            modifier = Modifier.testTag("nav_tab_pos")
                        )
                        NavigationBarItem(
                            selected = uiState.activeTab == PosTab.HISTORY,
                            onClick = { viewModel.selectTab(PosTab.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.activeTab == PosTab.HISTORY) {
                                        Icons.Filled.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = stringResource(R.string.tab_history)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_history)) },
                            modifier = Modifier.testTag("nav_tab_history")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavigationRailItem(
                            selected = uiState.activeTab == PosTab.POS,
                            onClick = { viewModel.selectTab(PosTab.POS) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.activeTab == PosTab.POS) {
                                        Icons.Filled.PointOfSale
                                    } else {
                                        Icons.Outlined.PointOfSale
                                    },
                                    contentDescription = stringResource(R.string.tab_pos)
                                )
                            },
                            label = { Text("POS") },
                            modifier = Modifier.testTag("rail_tab_pos")
                        )
                        NavigationRailItem(
                            selected = uiState.activeTab == PosTab.HISTORY,
                            onClick = { viewModel.selectTab(PosTab.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.activeTab == PosTab.HISTORY) {
                                        Icons.Filled.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = stringResource(R.string.tab_history)
                                )
                            },
                            label = { Text("History") },
                            modifier = Modifier.testTag("rail_tab_history")
                        )
                    }
                }

                when (uiState.activeTab) {
                    PosTab.POS -> {
                        PosScreen(
                            uiState = uiState,
                            onRateChange = viewModel::updateMojodaRate,
                            onCustomerChange = viewModel::updateCustomerName,
                            onItemChange = viewModel::updateItemName,
                            onBagWeightInputChange = viewModel::updateBagWeightInput,
                            onToggleKeypad = viewModel::toggleKeypad,
                            onKeypadChar = viewModel::appendKeypadChar,
                            onKeypadBackspace = viewModel::backspaceKeypad,
                            onKeypadClear = viewModel::clearKeypadInput,
                            onAddBag = viewModel::addBagFromInput,
                            onRemoveBagAt = viewModel::removeBagAt,
                            onUpdateBagAt = viewModel::updateBagAt,
                            onUndoLastBag = viewModel::undoLastBag,
                            onResetBillConfirm = viewModel::resetCurrentBillKeepRate,
                            onSaveAndPrint = viewModel::saveAndPrintBill,
                            onCopyWhatsApp = {
                                val text = viewModel.buildCurrentDraftSlipText()
                                if (text != null) {
                                    ThermalReceiptEngine.copyToClipboard(context, "Mandi Slip", text)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Receipt copied for WhatsApp!")
                                    }
                                }
                            },
                            onShareWhatsApp = {
                                val text = viewModel.buildCurrentDraftSlipText()
                                if (text != null) {
                                    ThermalReceiptEngine.shareText(context, text, "Mandi Weighment Slip")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PosTab.HISTORY -> {
                        HistoryScreen(
                            billsState = billsState,
                            filteredBills = filteredBills,
                            todayKpis = todayKpis,
                            searchQuery = uiState.searchQuery,
                            dateFilter = uiState.dateFilter,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onDateFilterChange = viewModel::updateDateFilter,
                            onReprintBill = { bill ->
                                viewModel.openReceiptPreview(bill, autoPrint = true)
                            },
                            onCopyBillWhatsApp = { bill ->
                                ThermalReceiptEngine.copyToClipboard(
                                    context,
                                    "Mandi Slip ${bill.billNumber}",
                                    MandiMath.formatWhatsAppSlip(bill)
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar("Bill ${bill.billNumber} copied for WhatsApp!")
                                }
                            },
                            onDeleteBill = viewModel::deleteBill,
                            onSaveCsvClick = {
                                val fileName = "mandi_bills_${MandiMath.dateKey(System.currentTimeMillis())}.csv"
                                createCsvLauncher.launch(fileName)
                            },
                            onShareCsvClick = {
                                val fileName = "mandi_bills_${MandiMath.dateKey(System.currentTimeMillis())}.csv"
                                val csv = MandiMath.buildCsv(filteredBills)
                                ThermalReceiptEngine.shareCsvFile(context, csv, fileName) { err ->
                                    scope.launch { snackbarHostState.showSnackbar(err) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    val previewBill = uiState.previewBill
    if (previewBill != null) {
        ThermalReceiptPreviewDialog(
            bill = previewBill,
            paperWidth = uiState.paperWidth,
            onPaperWidthChange = viewModel::setPaperWidth,
            onPrintClick = {
                ThermalReceiptEngine.printThermalReceipt(
                    context = context,
                    bill = previewBill,
                    paperWidth = uiState.paperWidth,
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCopyWhatsAppClick = {
                ThermalReceiptEngine.copyToClipboard(
                    context,
                    "Mandi Slip ${previewBill.billNumber}",
                    MandiMath.formatWhatsAppSlip(previewBill)
                )
                scope.launch {
                    snackbarHostState.showSnackbar("Receipt copied for WhatsApp!")
                }
            },
            onShareTextClick = {
                ThermalReceiptEngine.shareText(
                    context,
                    MandiMath.formatWhatsAppSlip(previewBill),
                    "Mandi Bill ${previewBill.billNumber}"
                )
            },
            onDismiss = viewModel::closeReceiptPreview
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out?") },
            text = {
                Text("Signed in as ${user.email ?: operatorLabel}. Your active draft and local bill backup remain safely stored.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        signOut(credentialManager, scope)
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
