package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.MandiBill
import com.example.model.MandiMath
import com.example.model.ReceiptPaperWidth
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ThermalInkBlack
import com.example.ui.theme.ThermalPaperWhite

@Composable
fun ThermalReceiptPreviewDialog(
    bill: MandiBill,
    paperWidth: ReceiptPaperWidth,
    onPaperWidthChange: (ReceiptPaperWidth) -> Unit,
    onPrintClick: () -> Unit,
    onCopyWhatsAppClick: () -> Unit,
    onShareTextClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val (mann, remKg) = MandiMath.calculateMannBreakdown(bill.totalWeightKg)
    val cols = if (paperWidth == ReceiptPaperWidth.WIDTH_58MM) 2 else 3

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thermal Slip Preview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_receipt_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close receipt preview"
                        )
                    }
                }

                // 58mm / 80mm Paper Width Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "Thermal Roll:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReceiptPaperWidth.entries.forEach { widthOption ->
                        FilterChip(
                            selected = paperWidth == widthOption,
                            onClick = { onPaperWidthChange(widthOption) },
                            label = { Text("${widthOption.label} Slip") },
                            modifier = Modifier.testTag("paper_width_${widthOption.label}")
                        )
                    }
                }

                // Authentic Monospace B&W Thermal Paper Slip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(if (paperWidth == ReceiptPaperWidth.WIDTH_58MM) 0.82f else 1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThermalPaperWhite)
                            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shop_header_name),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ThermalInkBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Wholesale Agricultural Weighment",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = ThermalInkBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        DashedThermalDivider()

                        ThermalLine("Bill #:", bill.billNumber, boldValue = true)
                        ThermalLine("Date:", MandiMath.formatDateTime(bill.effectiveTimeMillis()))
                        ThermalLine("Customer:", bill.customerName)
                        ThermalLine("Item:", bill.itemName)

                        DashedThermalDivider()

                        Text(
                            text = "ITEMIZED BAGS (${bill.totalBags})",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = ThermalInkBlack,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        val rows = bill.weights.mapIndexed { idx, w ->
                            "#${idx + 1}: ${MandiMath.formatWeight(w)}kg"
                        }.chunked(cols)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            rows.forEach { rowCells ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    rowCells.forEach { cell ->
                                        Text(
                                            text = cell,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 10.sp,
                                            color = ThermalInkBlack,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(cols - rowCells.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        DashedThermalDivider()

                        ThermalLine("Total Bags:", "${bill.totalBags}", boldValue = true)
                        ThermalLine(
                            "Gross Weight:",
                            "${MandiMath.formatWeight(bill.totalWeightKg)} kg",
                            boldValue = true
                        )
                        ThermalLine(
                            "Mann Split:",
                            "$mann Mann, ${MandiMath.formatWeight(remKg)} kg"
                        )
                        ThermalLine(
                            "Rate / 40kg:",
                            MandiMath.formatRupeesWhole(bill.ratePerMann)
                        )
                        ThermalLine(
                            "Formula:",
                            "(${MandiMath.formatWeight(bill.totalWeightKg)}x${MandiMath.formatWeight(bill.ratePerMann)})/40"
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(thickness = 1.5.dp, color = ThermalInkBlack)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NET PAYABLE:",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ThermalInkBlack
                            )
                            Text(
                                text = MandiMath.formatRupeesWhole(bill.finalCost),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = ThermalInkBlack
                            )
                        }
                        Text(
                            text = "Exact: ${MandiMath.formatRupeesExact(bill.finalCost)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = ThermalInkBlack,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )

                        DashedThermalDivider()

                        Text(
                            text = stringResource(R.string.receipt_footer_note),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = ThermalInkBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onPrintClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dialog_print_slip_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print ${paperWidth.label} Thermal Slip", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyWhatsAppClick,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_copy_whatsapp_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Slip")
                    }
                    OutlinedButton(
                        onClick = onShareTextClick,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_share_whatsapp_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThermalLine(
    label: String,
    value: String,
    boldValue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = ThermalInkBlack
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            color = ThermalInkBlack,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DashedThermalDivider() {
    Text(
        text = "- - - - - - - - - - - - - - - - - - -",
        fontFamily = JetBrainsMonoFamily,
        fontSize = 10.sp,
        color = ThermalInkBlack,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        textAlign = TextAlign.Center
    )
}
