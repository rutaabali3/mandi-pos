package com.example.print

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.model.MandiBill
import com.example.model.MandiMath
import com.example.model.ReceiptPaperWidth
import java.io.File

object ThermalReceiptEngine {

    fun buildThermalReceiptHtml(
        bill: MandiBill,
        paperWidth: ReceiptPaperWidth
    ): String {
        val (mann, remKg) = MandiMath.calculateMannBreakdown(bill.totalWeightKg)
        val widthMm = paperWidth.cssWidthMm
        val cols = if (paperWidth == ReceiptPaperWidth.WIDTH_58MM) 2 else 3
        val escapedCustomer = TextUtils.htmlEncode(bill.customerName)
        val escapedItem = TextUtils.htmlEncode(bill.itemName)
        val escapedBillNo = TextUtils.htmlEncode(bill.billNumber)
        val formattedDate = TextUtils.htmlEncode(MandiMath.formatDateTime(bill.effectiveTimeMillis()))

        val bagCells = bill.weights.mapIndexed { idx, w ->
            "<div class=\"bag-cell\">#${idx + 1}: ${MandiMath.formatWeight(w)}kg</div>"
        }.joinToString("\n")

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <style>
                @page {
                  size: ${widthMm + 8}mm auto;
                  margin: 2mm;
                }
                * { box-sizing: border-box; }
                body {
                  font-family: 'Courier New', Courier, monospace;
                  color: #000000;
                  background: #ffffff;
                  width: ${widthMm}mm;
                  margin: 0 auto;
                  padding: 3mm 2mm;
                  font-size: 11px;
                  line-height: 1.35;
                }
                .center { text-align: center; }
                .shop-title {
                  font-size: 14px;
                  font-weight: 800;
                  letter-spacing: 0.5px;
                  margin-bottom: 2px;
                }
                .sub-note { font-size: 10px; margin-bottom: 6px; }
                .dashed {
                  border-top: 1px dashed #000;
                  margin: 6px 0;
                }
                .meta-row {
                  display: flex;
                  justify-content: space-between;
                  margin-bottom: 2px;
                }
                .bags-title {
                  font-weight: 700;
                  margin-bottom: 4px;
                  text-transform: uppercase;
                  font-size: 10px;
                }
                .bags-grid {
                  display: grid;
                  grid-template-columns: repeat($cols, 1fr);
                  gap: 2px 4px;
                  font-size: 10px;
                }
                .bag-cell {
                  white-space: nowrap;
                }
                .total-row {
                  display: flex;
                  justify-content: space-between;
                  margin: 3px 0;
                }
                .net-payable {
                  font-size: 15px;
                  font-weight: 900;
                  display: flex;
                  justify-content: space-between;
                  margin-top: 6px;
                  padding-top: 4px;
                  border-top: 2px dashed #000;
                }
                .exact-sub {
                  text-align: right;
                  font-size: 9px;
                  margin-top: 2px;
                }
                .footer {
                  text-align: center;
                  font-size: 10px;
                  margin-top: 8px;
                }
              </style>
            </head>
            <body>
              <div class="center">
                <div class="shop-title">MANDI COMMISSION SHOP</div>
                <div class="sub-note">Wholesale Agricultural Weighment Slip</div>
              </div>
              <div class="dashed"></div>
              <div class="meta-row"><span>Bill #:</span><span><strong>$escapedBillNo</strong></span></div>
              <div class="meta-row"><span>Date:</span><span>$formattedDate</span></div>
              <div class="meta-row"><span>Customer:</span><span>$escapedCustomer</span></div>
              <div class="meta-row"><span>Commodity:</span><span>$escapedItem</span></div>
              <div class="dashed"></div>
              <div class="bags-title">Itemized Bag Weights (${bill.totalBags})</div>
              <div class="bags-grid">
                $bagCells
              </div>
              <div class="dashed"></div>
              <div class="total-row"><span>Total Bags:</span><span><strong>${bill.totalBags}</strong></span></div>
              <div class="total-row"><span>Gross Weight:</span><span><strong>${MandiMath.formatWeight(bill.totalWeightKg)} kg</strong></span></div>
              <div class="total-row"><span>Mann Breakdown:</span><span>$mann Mann, ${MandiMath.formatWeight(remKg)} kg</span></div>
              <div class="total-row"><span>Rate / 40kg Mann:</span><span>${MandiMath.formatRupeesWhole(bill.ratePerMann)}</span></div>
              <div class="total-row"><span>Formula:</span><span>(${MandiMath.formatWeight(bill.totalWeightKg)} x ${MandiMath.formatWeight(bill.ratePerMann)}) / 40</span></div>
              <div class="net-payable">
                <span>NET PAYABLE:</span>
                <span>${MandiMath.formatRupeesWhole(bill.finalCost)}</span>
              </div>
              <div class="exact-sub">Exact: ${MandiMath.formatRupeesExact(bill.finalCost)}</div>
              <div class="dashed"></div>
              <div class="footer">Computerized Weighment Slip<br/>Thank You!</div>
            </body>
            </html>
        """.trimIndent()
    }

    fun printThermalReceipt(
        context: Context,
        bill: MandiBill,
        paperWidth: ReceiptPaperWidth,
        onError: (String) -> Unit
    ) {
        try {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager == null) {
                        onError("Print service is unavailable on this device.")
                        return
                    }
                    val jobName = "MandiSlip_${bill.billNumber}"
                    val adapter = webView.createPrintDocumentAdapter(jobName)
                    val attributes = PrintAttributes.Builder()
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                        .build()
                    printManager.print(jobName, adapter, attributes)
                }
            }
            val html = buildThermalReceiptHtml(bill, paperWidth)
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to launch print dialog.")
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Receipt Slip"))
    }

    fun shareCsvFile(context: Context, csvContent: String, fileName: String, onError: (String) -> Unit) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            outFile.writeText(csvContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mandi POS Bills Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export / Share CSV"))
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to export CSV file.")
        }
    }
}
