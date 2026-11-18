package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.COMMODITY_PRESETS
import com.example.model.MandiMath
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun PosScreen(
    uiState: PosUiState,
    onRateChange: (String) -> Unit,
    onCustomerChange: (String) -> Unit,
    onItemChange: (String) -> Unit,
    onBagWeightInputChange: (String) -> Unit,
    onToggleKeypad: () -> Unit,
    onKeypadChar: (Char) -> Unit,
    onKeypadBackspace: () -> Unit,
    onKeypadClear: () -> Unit,
    onAddBag: () -> Unit,
    onRemoveBagAt: (Int) -> Unit,
    onUpdateBagAt: (Int, Double) -> Unit,
    onUndoLastBag: () -> Unit,
    onResetBillConfirm: () -> Unit,
    onSaveAndPrint: () -> Unit,
    onCopyWhatsApp: () -> Unit,
    onShareWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

    val bagFocusRequester = remember { FocusRequester() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val isWideLayout = maxWidth >= 780.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RateAndBillInfoCard(
                        uiState = uiState,
                        onRateChange = onRateChange,
                        onCustomerChange = onCustomerChange,
                        onItemChange = onItemChange
                    )
                    RapidBagEntryCard(
                        uiState = uiState,
                        focusRequester = bagFocusRequester,
                        onBagWeightInputChange = onBagWeightInputChange,
                        onToggleKeypad = onToggleKeypad,
                        onKeypadChar = onKeypadChar,
                        onKeypadBackspace = onKeypadBackspace,
                        onKeypadClear = onKeypadClear,
                        onAddBag = {
                            onAddBag()
                            bagFocusRequester.requestFocus()
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnteredBagsGridCard(
                        weights = uiState.weights,
                        onEditBag = { idx, weight ->
                            editingIndex = idx
                            editingValue = MandiMath.formatWeight(weight).replace(",", "")
                        },
                        onRemoveBag = onRemoveBagAt,
                        onUndoLast = onUndoLastBag
                    )
                    LiveSummaryAndActionsCard(
                        uiState = uiState,
                        onSaveAndPrint = onSaveAndPrint,
                        onCopyWhatsApp = onCopyWhatsApp,
                        onShareWhatsApp = onShareWhatsApp,
                        onRequestReset = { showResetConfirm = true }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RateAndBillInfoCard(
                    uiState = uiState,
                    onRateChange = onRateChange,
                    onCustomerChange = onCustomerChange,
                    onItemChange = onItemChange
                )
                RapidBagEntryCard(
                    uiState = uiState,
                    focusRequester = bagFocusRequester,
                    onBagWeightInputChange = onBagWeightInputChange,
                    onToggleKeypad = onToggleKeypad,
                    onKeypadChar = onKeypadChar,
                    onKeypadBackspace = onKeypadBackspace,
                    onKeypadClear = onKeypadClear,
                    onAddBag = {
                        onAddBag()
                        bagFocusRequester.requestFocus()
                    }
                )
                EnteredBagsGridCard(
                    weights = uiState.weights,
                    onEditBag = { idx, weight ->
                        editingIndex = idx
                        editingValue = MandiMath.formatWeight(weight).replace(",", "")
                    },
                    onRemoveBag = onRemoveBagAt,
                    onUndoLast = onUndoLastBag
                )
                LiveSummaryAndActionsCard(
                    uiState = uiState,
                    onSaveAndPrint = onSaveAndPrint,
                    onCopyWhatsApp = onCopyWhatsApp,
                    onShareWhatsApp = onShareWhatsApp,
                    onRequestReset = { showResetConfirm = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (editingIndex != null) {
        val idx = editingIndex!!
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Edit Bag #${idx + 1}") },
            text = {
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_bag_weight_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = editingValue.trim().toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            onUpdateBagAt(idx, parsed)
                        }
                        editingIndex = null
                    },
                    modifier = Modifier.testTag("confirm_edit_bag_button")
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Start New Bill?") },
            text = {
                Text("This clears all ${uiState.weights.size} entered bags and resets customer to Cash Buyer. Your daily Mojoda Rate will be preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetBillConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_reset_bill_button")
                ) {
                    Text("Clear Bags")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

@Composable
private fun RateAndBillInfoCard(
    uiState: PosUiState,
    onRateChange: (String) -> Unit,
    onCustomerChange: (String) -> Unit,
    onItemChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Prominent Mojoda Rate Input
            OutlinedTextField(
                value = uiState.mojodaRateInput,
                onValueChange = onRateChange,
                label = { Text(stringResource(R.string.label_mojoda_rate)) },
                placeholder = { Text("e.g. 2400") },
                prefix = {
                    Text(
                        text = "Rs. ",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                suffix = {
                    Text(
                        text = "/ 40kg",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mojoda_rate_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.customerName,
                    onValueChange = onCustomerChange,
                    label = { Text("Customer / Beopari") },
                    placeholder = { Text(stringResource(R.string.default_customer_name)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.customerName.isNotEmpty() && uiState.customerName != "Cash Buyer") {
                            IconButton(onClick = { onCustomerChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear customer")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("customer_name_input")
                )

                OutlinedTextField(
                    value = uiState.itemName,
                    onValueChange = onItemChange,
                    label = { Text("Commodity") },
                    placeholder = { Text(stringResource(R.string.default_item_name)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("commodity_name_input")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                COMMODITY_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = uiState.itemName.equals(preset, ignoreCase = true),
                        onClick = { onItemChange(preset) },
                        label = { Text(preset, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("commodity_chip_${preset.take(6).lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun RapidBagEntryCard(
    uiState: PosUiState,
    focusRequester: FocusRequester,
    onBagWeightInputChange: (String) -> Unit,
    onToggleKeypad: () -> Unit,
    onKeypadChar: (Char) -> Unit,
    onKeypadBackspace: () -> Unit,
    onKeypadClear: () -> Unit,
    onAddBag: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Scale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Rapid Bag Weight Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilterChip(
                    selected = uiState.showKeypad,
                    onClick = onToggleKeypad,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Dialpad,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (uiState.showKeypad) "Hide Pad" else "Show Pad") },
                    modifier = Modifier.testTag("toggle_keypad_chip")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.bagWeightInput,
                    onValueChange = onBagWeightInputChange,
                    placeholder = { Text("Weight kg (e.g. 55)") },
                    suffix = {
                        Text(
                            text = "kg",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onAddBag() }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("bag_weight_input")
                )

                Button(
                    onClick = onAddBag,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .height(58.dp)
                        .testTag("add_bag_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Bag", fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = uiState.showKeypad) {
                PosNumericKeypad(
                    onChar = onKeypadChar,
                    onBackspace = onKeypadBackspace,
                    onClear = onKeypadClear,
                    onAddBag = onAddBag
                )
            }
        }
    }
}

@Composable
private fun PosNumericKeypad(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onAddBag: () -> Unit
) {
    val rows = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "BACK")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    if (key == "BACK") {
                        FilledTonalButton(
                            onClick = onBackspace,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("keypad_backspace")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace"
                            )
                        }
                    } else {
                        Surface(
                            onClick = { onChar(key.first()) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("keypad_key_$key")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = key,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.38f)
                    .height(52.dp)
                    .testTag("keypad_clear")
            ) {
                Text("Clear", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onAddBag,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.62f)
                    .height(52.dp)
                    .testTag("keypad_add_bag")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ ADD BAG", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnteredBagsGridCard(
    weights: List<Double>,
    onEditBag: (Int, Double) -> Unit,
    onRemoveBag: (Int) -> Unit,
    onUndoLast: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Entered Bags (${weights.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (weights.isNotEmpty()) {
                    TextButton(
                        onClick = onUndoLast,
                        modifier = Modifier.testTag("undo_last_bag_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo Last")
                    }
                }
            }

            if (weights.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No bags weighed yet. Type a bag weight above (e.g. 55) or tap the keypad to tally sacks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    weights.forEachIndexed { index, weight ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onEditBag(index, weight) }
                                .testTag("bag_chip_${index + 1}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "#${index + 1}: ${MandiMath.formatWeight(weight)} kg",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit bag ${index + 1}",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier.size(13.dp)
                                )
                                IconButton(
                                    onClick = { onRemoveBag(index) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("remove_bag_${index + 1}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove bag ${index + 1}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSummaryAndActionsCard(
    uiState: PosUiState,
    onSaveAndPrint: () -> Unit,
    onCopyWhatsApp: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onRequestReset: () -> Unit
) {
    val totals = uiState.totals

    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStatTile(
                    label = "TOTAL BAGS",
                    value = "${totals.totalBags}",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatTile(
                    label = "TOTAL WEIGHT",
                    value = "${MandiMath.formatWeight(totals.totalWeightKg)} kg",
                    subtitle = "${totals.mannCount} Mann, ${MandiMath.formatWeight(totals.remainingKg)} kg",
                    modifier = Modifier.weight(1.3f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MOJODA RATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${MandiMath.formatRupeesWhole(totals.ratePerMann)} / 40kg",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "FINAL PAYABLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = MandiMath.formatRupeesWhole(totals.finalCostRounded),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("final_payable_amount")
                    )
                    Text(
                        text = "Exact: ${MandiMath.formatRupeesExact(totals.finalCostExact)}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onSaveAndPrint,
                enabled = !uiState.isSaving && uiState.weights.isNotEmpty() && totals.ratePerMann > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_and_print_button")
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving Bill...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_save_print),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCopyWhatsApp,
                    enabled = uiState.weights.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_whatsapp_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Slip", maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onShareWhatsApp,
                    enabled = uiState.weights.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_whatsapp_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onRequestReset,
                    enabled = uiState.weights.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("reset_new_bill_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            )
        }
    }
}
