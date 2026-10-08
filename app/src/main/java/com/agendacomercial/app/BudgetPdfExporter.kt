package com.agendacomercial.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Genera un presupuesto PDF A4 apaisado a partir de datos almacenados
 * localmente. No utiliza servicios de conversion ni envía presupuestos a Internet.
 */
object BudgetPdfExporter {
    private const val PAGE_W = 842
    private const val PAGE_H = 595
    private const val PAGE_MARGIN = 28f
    private const val TABLE_Y = 156f
    private const val TABLE_HEADER_H = 25f
    private const val ROW_START_Y = TABLE_Y + TABLE_HEADER_H
    private const val MAX_ROWS_PER_PAGE = 9

    // Anchuras de columnas en puntos. Suman exactamente 786 (=842 - 2*28).
    private val columnWidths = floatArrayOf(
        70f, 55f, 184f, 46f, 38f, 62f, 66f, 110f, 73f, 82f
    )
    private val columnHeadings = arrayOf(
        "Foto", "Código", "Producto", "U./caja", "Cajas",
        "€/ud.", "Importe", "Gramos/det.", "Min.", "°C"
    )

    private val typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val gold = Color.rgb(255, 211, 78)
    private val ink = Color.rgb(30, 32, 36)
    private val lineColor = Color.rgb(214, 217, 220)
    private val lightGray = Color.rgb(246, 247, 249)
    private val dark = Color.rgb(11, 11, 11)

    suspend fun write(
        context: Context,
        uri: Uri,
        client: Client,
        lines: List<BudgetLine>,
        sellerName: String,
        sellerPhone: String
    ): BudgetExportResult = withContext(Dispatchers.IO) {
        runCatching {
            require(lines.isNotEmpty()) { "Selecciona productos para el presupuesto" }

            // Búsqueda limitada a categorías web públicas; nunca se envían datos
            // privados de clientes, códigos, precios o facturación.
            val images = BudgetXlsxExporter.loadPublicProductImages(lines)
            val pdf = PdfDocument()
            try {
                val chunks = lines.indices.chunked(MAX_ROWS_PER_PAGE)
                chunks.forEachIndexed { pageIndex, indices ->
                    val info = PdfDocument.PageInfo.Builder(
                        PAGE_W, PAGE_H, pageIndex + 1
                    ).create()
                    val page = pdf.startPage(info)
                    val canvas = page.canvas
                    val isLastPage = pageIndex == chunks.lastIndex

                    drawHeader(
                        canvas = canvas,
                        name = sellerName,
                        phone = sellerPhone,
                        client = client
                    )

                    val selectedRowHeight = when {
                        indices.size <= 6 -> 46f
                        indices.size == 7 -> 42f
                        indices.size == 8 -> 40f
                        else -> 37f
                    }
                    drawTableHeader(canvas)

                    indices.forEachIndexed { localIndex, globalIndex ->
                        drawProductRow(
                            canvas = canvas,
                            y = ROW_START_Y + localIndex * selectedRowHeight,
                            height = selectedRowHeight,
                            line = lines[globalIndex],
                            photo = images.getOrNull(globalIndex),
                            alternate = localIndex % 2 == 1
                        )
                    }

                    if (isLastPage) {
                        val total = lines.sumOf {
                            it.boxes.toDouble() *
                                it.product.unitsPerBox.toDouble() *
                                it.pricePerUnit
                        }
                        drawTotal(canvas,
                            ROW_START_Y + indices.size * selectedRowHeight + 8f,
                            total)
                    }
                    drawFooter(canvas, pageIndex + 1, chunks.size)
                    pdf.finishPage(page)
                }

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    pdf.writeTo(out)
                } ?: error("No se puede guardar el PDF en la ubicación seleccionada")
            } finally {
                pdf.close()
            }

            BudgetExportResult(
                success = true,
                imagesIncluded = images.count { it != null },
                productCount = lines.size
            )
        }.getOrElse { error ->
            BudgetExportResult(
                success = false,
                imagesIncluded = 0,
                productCount = lines.size,
                errorMessage = error.message ?: error.javaClass.simpleName
            )
        }
    }

    private fun paint(
        color: Int = ink,
        size: Float = 9f,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) BudgetPdfExporter.typeface else regular
        textAlign = align
        isFilterBitmap = true
    }

    private fun drawHeader(
        canvas: Canvas,
        name: String,
        phone: String,
        client: Client
    ) {
        canvas.drawColor(Color.WHITE)
        canvas.drawRoundRect(
            RectF(PAGE_MARGIN, 20f, PAGE_W - PAGE_MARGIN, 100f),
            6f, 6f, paint(dark)
        )

        // Misma familia tipográfica y contraste alto para los tres elementos.
        canvas.drawText("PRESUPUESTO", 43f, 53f, paint(gold, 22f, bold = true))
        canvas.drawText(
            ellipsize(name.trim(), 370f, paint(gold, 13f, bold = true)),
            44f, 75f, paint(gold, 13f, bold = true)
        )
        canvas.drawText(
            ellipsize("Tel. " + phone.trim(), 335f, paint(gold, 12f, bold = true)),
            44f, 92f, paint(gold, 12f, bold = true)
        )

        val date = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date())
        canvas.drawText("Fecha: $date", PAGE_W - PAGE_MARGIN - 14f, 53f,
            paint(Color.WHITE, 10f, bold = true, align = Paint.Align.RIGHT))

        val metaPaint = paint(ink, 9f)
        val smallPaint = paint(ink, 8.3f)
        canvas.drawText(
            ellipsize("Cliente: " + client.name, 595f, metaPaint),
            PAGE_MARGIN + 3f, 119f, metaPaint
        )
        val business = client.businessName.takeIf { it.isNotBlank() } ?: client.name
        canvas.drawText(
            ellipsize("Empresa: " + business, 590f, smallPaint),
            PAGE_MARGIN + 3f, 132f, smallPaint
        )
        val details = listOf(
            client.contactPerson,
            client.phone,
            client.address,
            client.city
        ).filter(String::isNotBlank).joinToString("  ·  ")
        if (details.isNotBlank()) {
            canvas.drawText(ellipsize(details, 770f, smallPaint),
                PAGE_MARGIN + 3f, 145f, smallPaint)
        }
    }

    private fun drawTableHeader(canvas: Canvas) {
        canvas.drawRect(
            PAGE_MARGIN, TABLE_Y, PAGE_W - PAGE_MARGIN,
            TABLE_Y + TABLE_HEADER_H, paint(dark)
        )
        var x = PAGE_MARGIN
        val headingPaint = paint(Color.WHITE, 8f, bold = true)
        columnWidths.forEachIndexed { index, width ->
            canvas.drawText(
                ellipsize(columnHeadings[index], width - 6f, headingPaint),
                x + 3f,
                TABLE_Y + 16f,
                headingPaint
            )
            x += width
        }
    }

    private fun drawProductRow(
        canvas: Canvas,
        y: Float,
        height: Float,
        line: BudgetLine,
        photo: ByteArray?,
        alternate: Boolean
    ) {
        val bottom = y + height
        if (alternate) {
            canvas.drawRect(
                PAGE_MARGIN, y, PAGE_W - PAGE_MARGIN,
                bottom, paint(lightGray)
            )
        }
        canvas.drawLine(
            PAGE_MARGIN, bottom, PAGE_W - PAGE_MARGIN, bottom,
            paint(lineColor, 0.35f)
        )
        var x = PAGE_MARGIN
        columnWidths.forEach { width ->
            canvas.drawLine(x, y, x, bottom, paint(lineColor, 0.35f))
            x += width
        }
        canvas.drawLine(x, y, x, bottom, paint(lineColor, 0.35f))

        if (photo != null) {
            val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size)
            if (bitmap != null) {
                try {
                    val availableW = columnWidths[0] - 8f
                    val availableH = height - 5f
                    val scale = minOf(
                        availableW / bitmap.width.toFloat(),
                        availableH / bitmap.height.toFloat()
                    )
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val left = PAGE_MARGIN + (columnWidths[0] - w) / 2f
                    val top = y + (height - h) / 2f
                    canvas.drawBitmap(
                        bitmap, null,
                        RectF(left, top, left + w, top + h),
                        paint()
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        }

        val amount = line.boxes.toDouble() *
            line.product.unitsPerBox.toDouble() * line.pricePerUnit
        val cells = arrayOf(
            "",
            line.code,
            line.product.name,
            line.product.unitsPerBox.toString(),
            line.boxes.toString(),
            unitPrice(line.pricePerUnit),
            money(amount),
            line.product.description,
            line.cookingTime.trim(),
            line.cookingTemperature.trim()
        )
        val numericColumns = setOf(3, 4, 5, 6, 8, 9)
        x = PAGE_MARGIN
        cells.forEachIndexed { index, text ->
            if (index != 0) {
                val width = columnWidths[index]
                val isNumeric = index in numericColumns
                val p = paint(
                    ink,
                    if (index == 2) 9f else 8.2f,
                    bold = index == 2,
                    align = if (isNumeric) Paint.Align.RIGHT else Paint.Align.LEFT
                )
                if (isNumeric) {
                    canvas.drawText(
                        ellipsize(text, width - 8f, p),
                        x + width - 4f, y + 16f, p
                    )
                } else {
                    drawWrapped(
                        canvas,
                        text,
                        x + 4f,
                        y + 16f,
                        width - 8f,
                        height - 5f,
                        p
                    )
                }
            }
            x += columnWidths[index]
        }
    }

    private fun drawWrapped(
        canvas: Canvas, text: String, x: Float, baseY: Float,
        width: Float, maxHeight: Float, p: Paint
    ) {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return
        val allowedLines = if (maxHeight >= 28f) 3 else 2
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= width) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        val lineSpace = p.textSize + 2f
        lines.take(allowedLines).forEachIndexed { index, raw ->
            val printable = if (index == allowedLines - 1 && lines.size > allowedLines) {
                ellipsize(raw + "…", width, p)
            } else {
                ellipsize(raw, width, p)
            }
            canvas.drawText(printable, x, baseY + index * lineSpace, p)
        }
    }

    private fun drawTotal(canvas: Canvas, top: Float, amount: Double) {
        val y = top.coerceAtMost(554f)
        canvas.drawRoundRect(
            RectF(PAGE_W - 276f, y, PAGE_W - PAGE_MARGIN, y + 26f),
            4f, 4f, paint(dark)
        )
        canvas.drawText("TOTAL", PAGE_W - 265f, y + 18f,
            paint(gold, 11f, bold = true))
        canvas.drawText(money(amount), PAGE_W - PAGE_MARGIN - 12f, y + 18f,
            paint(Color.WHITE, 12f, bold = true, align = Paint.Align.RIGHT))
    }

    private fun drawFooter(canvas: Canvas, page: Int, totalPages: Int) {
        canvas.drawLine(
            PAGE_MARGIN, 569f, PAGE_W - PAGE_MARGIN, 569f,
            paint(lineColor, 0.5f)
        )
        canvas.drawText("Presupuesto generado por Agenda Comercial",
            PAGE_MARGIN, 583f, paint(Color.DKGRAY, 7.2f))
        canvas.drawText("$page / $totalPages",
            PAGE_W - PAGE_MARGIN, 583f,
            paint(Color.DKGRAY, 7.2f, align = Paint.Align.RIGHT))
    }

    private fun ellipsize(value: String, width: Float, p: Paint): String {
        if (p.measureText(value) <= width) return value
        val dots = "…"
        if (p.measureText(dots) > width) return ""
        var length = value.length
        while (length > 0 && p.measureText(value.substring(0, length) + dots) > width) {
            length--
        }
        return value.substring(0, length) + dots
    }

    private fun money(value: Double) =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("es", "ES")))
            .format(value) + " €"

    private fun unitPrice(value: Double) =
        DecimalFormat("#,##0.000", DecimalFormatSymbols(Locale("es", "ES")))
            .format(value)
}
