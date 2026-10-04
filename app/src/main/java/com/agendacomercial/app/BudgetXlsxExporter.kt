package com.agendacomercial.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class BudgetExportResult(
    val success: Boolean,
    val imagesIncluded: Int,
    val productCount: Int,
    val errorMessage: String? = null
)

object BudgetXlsxExporter {

    suspend fun write(
        context: Context,
        uri: Uri,
        client: Client,
        lines: List<BudgetLine>
    ): BudgetExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val logo = createPaddedLogo(context)

            val productImages = coroutineScope {
                lines.map { line ->
                    async {
                        fetchProductImage(
                            pageUrl = line.product.webUrl,
                            productName = line.product.name
                        )
                    }
                }.awaitAll()
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeWorkbook(
                    output = output,
                    client = client,
                    lines = lines,
                    logoBytes = logo,
                    productImages = productImages
                )
            } ?: error("No se pudo abrir el archivo de destino")

            BudgetExportResult(
                success = true,
                imagesIncluded = productImages.count { it != null },
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

    private fun writeWorkbook(
        output: OutputStream,
        client: Client,
        lines: List<BudgetLine>,
        logoBytes: ByteArray,
        productImages: List<ByteArray?>
    ) {
        ZipOutputStream(output).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook())
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            put(zip, "xl/styles.xml", styles())
            put(zip, "xl/theme/theme1.xml", theme())
            put(zip, "xl/worksheets/sheet1.xml", sheet(client, lines))
            put(zip, "xl/worksheets/_rels/sheet1.xml.rels", sheetRels())
            put(
                zip,
                "xl/drawings/drawing1.xml",
                drawing(lines, productImages)
            )
            put(
                zip,
                "xl/drawings/_rels/drawing1.xml.rels",
                drawingRels(productImages)
            )
            putBytes(zip, "xl/media/logo.jpg", logoBytes)

            productImages.forEachIndexed { index, bytes ->
                if (bytes != null) {
                    putBytes(
                        zip,
                        "xl/media/product_${index + 1}.png",
                        bytes
                    )
                }
            }
        }
    }

    private fun put(
        zip: ZipOutputStream,
        path: String,
        content: String
    ) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putBytes(
        zip: ZipOutputStream,
        path: String,
        content: ByteArray
    ) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content)
        zip.closeEntry()
    }

    private fun contentTypes() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>
</Types>"""

    private fun rootRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship
      Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
      Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook
    xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Presupuesto" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private fun workbookRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship
      Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
      Target="worksheets/sheet1.xml"/>
  <Relationship
      Id="rId2"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
      Target="styles.xml"/>
  <Relationship
      Id="rId3"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme"
      Target="theme/theme1.xml"/>
</Relationships>"""

    private fun sheetRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship
      Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing"
      Target="../drawings/drawing1.xml"/>
</Relationships>"""

    private fun drawingRels(
        productImages: List<ByteArray?>
    ): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        )
        sb.append(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""
        )

        sb.append(
            """<Relationship Id="rId1"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                Target="../media/logo.jpg"/>"""
        )

        var relationId = 2
        productImages.forEachIndexed { index, bytes ->
            if (bytes != null) {
                sb.append(
                    """<Relationship Id="rId$relationId"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                        Target="../media/product_${index + 1}.png"/>"""
                )
                relationId++
            }
        }

        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun drawing(
        lines: List<BudgetLine>,
        productImages: List<ByteArray?>
    ): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        )
        sb.append(
            """<xdr:wsDr
                xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">"""
        )

        sb.append(
            twoCellImage(
                fromCol = 0,
                fromRow = 0,
                toCol = 2,
                toRow = 4,
                id = 1,
                name = "Logo Pà Solà",
                relationId = 1
            )
        )

        var relationId = 2
        var imageId = 2
        productImages.forEachIndexed { index, bytes ->
            if (bytes != null && index < lines.size) {
                sb.append(
                    oneCellImage(
                        col = 0,
                        row = 10 + index,
                        widthPx = 115,
                        heightPx = 78,
                        id = imageId,
                        name = lines[index].product.name,
                        relationId = relationId
                    )
                )
                relationId++
                imageId++
            }
        }

        sb.append("</xdr:wsDr>")
        return sb.toString()
    }

    private fun twoCellImage(
        fromCol: Int,
        fromRow: Int,
        toCol: Int,
        toRow: Int,
        id: Int,
        name: String,
        relationId: Int
    ): String {
        return """
<xdr:twoCellAnchor editAs="oneCell">
  <xdr:from>
    <xdr:col>$fromCol</xdr:col>
    <xdr:colOff>0</xdr:colOff>
    <xdr:row>$fromRow</xdr:row>
    <xdr:rowOff>0</xdr:rowOff>
  </xdr:from>
  <xdr:to>
    <xdr:col>$toCol</xdr:col>
    <xdr:colOff>0</xdr:colOff>
    <xdr:row>$toRow</xdr:row>
    <xdr:rowOff>0</xdr:rowOff>
  </xdr:to>
  <xdr:pic>
    <xdr:nvPicPr>
      <xdr:cNvPr id="$id" name="${xml(name)}"/>
      <xdr:cNvPicPr/>
    </xdr:nvPicPr>
    <xdr:blipFill>
      <a:blip r:embed="rId$relationId"/>
      <a:srcRect l="0" t="0" r="0" b="0"/>
      <a:stretch><a:fillRect/></a:stretch>
    </xdr:blipFill>
    <xdr:spPr>
      <a:xfrm/>
      <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
    </xdr:spPr>
  </xdr:pic>
  <xdr:clientData/>
</xdr:twoCellAnchor>
""".trimIndent()
    }

    private fun oneCellImage(
        col: Int,
        row: Int,
        widthPx: Int,
        heightPx: Int,
        id: Int,
        name: String,
        relationId: Int
    ): String {
        val cx = widthPx * 9525L
        val cy = heightPx * 9525L

        return """
<xdr:oneCellAnchor>
  <xdr:from>
    <xdr:col>$col</xdr:col>
    <xdr:colOff>0</xdr:colOff>
    <xdr:row>$row</xdr:row>
    <xdr:rowOff>0</xdr:rowOff>
  </xdr:from>
  <xdr:ext cx="$cx" cy="$cy"/>
  <xdr:pic>
    <xdr:nvPicPr>
      <xdr:cNvPr id="$id" name="${xml(name)}"/>
      <xdr:cNvPicPr>
        <a:picLocks noChangeAspect="1"/>
      </xdr:cNvPicPr>
    </xdr:nvPicPr>
    <xdr:blipFill>
      <a:blip r:embed="rId$relationId"/>
      <a:stretch><a:fillRect/></a:stretch>
    </xdr:blipFill>
    <xdr:spPr>
      <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
    </xdr:spPr>
  </xdr:pic>
  <xdr:clientData/>
</xdr:oneCellAnchor>
""".trimIndent()
    }

    private fun styles() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0.00 &quot;€&quot;"/>
  </numFmts>

  <fonts count="4">
    <font><sz val="10"/><name val="Calibri"/></font>
    <font><b/><sz val="16"/><color rgb="FFFFD34E"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FF000000"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF000000"/><name val="Calibri"/></font>
  </fonts>

  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF050505"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFD34E"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE8F5E9"/><bgColor indexed="64"/></patternFill></fill>
  </fills>

  <borders count="2">
    <border/>
    <border>
      <left style="thin"><color rgb="FFBDBDBD"/></left>
      <right style="thin"><color rgb="FFBDBDBD"/></right>
      <top style="thin"><color rgb="FFBDBDBD"/></top>
      <bottom style="thin"><color rgb="FFBDBDBD"/></bottom>
      <diagonal/>
    </border>
  </borders>

  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>

  <cellXfs count="7">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0">
      <alignment vertical="top" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFill="1" applyFont="1">
      <alignment vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="2" borderId="0" xfId="0" applyFill="1" applyFont="1">
      <alignment vertical="top" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1">
      <alignment vertical="top" wrapText="1"/>
    </xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1">
      <alignment horizontal="right" vertical="top"/>
    </xf>
    <xf numFmtId="164" fontId="3" fillId="4" borderId="1" xfId="0" applyFill="1" applyFont="1" applyNumberFormat="1" applyBorder="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
  </cellXfs>

  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>"""

    private fun theme() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme name="Pà Solà" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
  <a:themeElements>
    <a:clrScheme name="Pà Solà">
      <a:dk1><a:srgbClr val="050505"/></a:dk1>
      <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="202020"/></a:dk2>
      <a:lt2><a:srgbClr val="F3F3F3"/></a:lt2>
      <a:accent1><a:srgbClr val="FFD34E"/></a:accent1>
      <a:accent2><a:srgbClr val="0A6A42"/></a:accent2>
      <a:accent3><a:srgbClr val="F5A11A"/></a:accent3>
      <a:accent4><a:srgbClr val="90CAF9"/></a:accent4>
      <a:accent5><a:srgbClr val="A5D6A7"/></a:accent5>
      <a:accent6><a:srgbClr val="8B8B8B"/></a:accent6>
      <a:hlink><a:srgbClr val="1565C0"/></a:hlink>
      <a:folHlink><a:srgbClr val="6A1B9A"/></a:folHlink>
    </a:clrScheme>

    <a:fontScheme name="Office">
      <a:majorFont>
        <a:latin typeface="Calibri"/>
        <a:ea typeface="Calibri"/>
        <a:cs typeface="Calibri"/>
      </a:majorFont>
      <a:minorFont>
        <a:latin typeface="Calibri"/>
        <a:ea typeface="Calibri"/>
        <a:cs typeface="Calibri"/>
      </a:minorFont>
    </a:fontScheme>

    <a:fmtScheme name="Pà Solà">
      <a:fillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:fillStyleLst>
      <a:lnStyleLst>
        <a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="19050"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="28575"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
      </a:lnStyleLst>
      <a:effectStyleLst>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
      </a:effectStyleLst>
      <a:bgFillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>"""

    private fun sheet(
        client: Client,
        lines: List<BudgetLine>
    ): String {
        val date = SimpleDateFormat(
            "dd/MM/yyyy",
            Locale("es", "ES")
        ).format(Date())

        val companySummary =
            "Panaderos desde 1615. Pan artesanal para hostelería y alta gastronomía, " +
            "elaborado con masa madre, harinas seleccionadas y sin aditivos."

        val contact = listOf(
            client.contactPerson,
            client.phone,
            client.email
        ).filter(String::isNotBlank).joinToString(" · ")

        val address = listOf(
            client.address,
            client.postalCode,
            client.city
        ).filter(String::isNotBlank).joinToString(", ")

        val startRow = 11
        val totalRow = startRow + lines.size
        val lastProductRow = (totalRow - 1).coerceAtLeast(10)
        val total = lines.sumOf {
            it.boxes.toDouble() *
                it.product.unitsPerBox.toDouble() *
                it.pricePerUnit
        }

        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        )
        sb.append(
            """<worksheet
                xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">"""
        )

        sb.append(
            """<dimension ref="A1:H$totalRow"/>"""
        )

        sb.append(
            """<sheetViews>
              <sheetView workbookViewId="0">
                <pane ySplit="10" topLeftCell="A11" activePane="bottomLeft" state="frozen"/>
              </sheetView>
            </sheetViews>"""
        )

        sb.append("""<sheetFormatPr defaultRowHeight="18"/>""")

        sb.append(
            """<cols>
              <col min="1" max="1" width="18" customWidth="1"/>
              <col min="2" max="2" width="17" customWidth="1"/>
              <col min="3" max="3" width="30" customWidth="1"/>
              <col min="4" max="5" width="10" customWidth="1"/>
              <col min="6" max="7" width="14" customWidth="1"/>
              <col min="8" max="8" width="46" customWidth="1"/>
            </cols>"""
        )

        sb.append("<sheetData>")

        sb.append("<row r=\"1\" ht=\"65\" customHeight=\"1\">")
        sb.append(cell("C1", "PRESUPUESTO PÀ SOLÀ", 1))
        sb.append("</row>")

        sb.append("<row r=\"2\" ht=\"36\" customHeight=\"1\">")
        sb.append(cell("C2", companySummary, 2))
        sb.append("</row>")

        sb.append("<row r=\"3\">")
        sb.append(cell("C3", "www.pasolasl.com", 2))
        sb.append("</row>")

        sb.append("<row r=\"5\">")
        sb.append(cell("A5", "Cliente", 3))
        sb.append(cell("B5", client.name, 0))
        sb.append(cell("E5", "Fecha", 3))
        sb.append(cell("F5", date, 0))
        sb.append("</row>")

        sb.append("<row r=\"6\">")
        sb.append(cell("A6", "Empresa", 3))
        sb.append(cell("B6", client.businessName, 0))
        sb.append("</row>")

        sb.append("<row r=\"7\">")
        sb.append(cell("A7", "Contacto", 3))
        sb.append(cell("B7", contact, 0))
        sb.append("</row>")

        sb.append("<row r=\"8\">")
        sb.append(cell("A8", "Dirección", 3))
        sb.append(cell("B8", address, 0))
        sb.append("</row>")

        val headers = listOf(
            "Foto",
            "Código",
            "Producto",
            "U./caja",
            "Cajas",
            "Precio/unidad",
            "Importe",
            "Descripción"
        )

        sb.append("<row r=\"10\" ht=\"28\" customHeight=\"1\">")
        headers.forEachIndexed { index, value ->
            sb.append(
                cell(
                    columnName(index + 1) + "10",
                    value,
                    3
                )
            )
        }
        sb.append("</row>")

        lines.forEachIndexed { index, line ->
            val row = startRow + index
            val amount =
                line.boxes.toDouble() *
                    line.product.unitsPerBox.toDouble() *
                    line.pricePerUnit

            sb.append(
                "<row r=\"$row\" ht=\"72\" customHeight=\"1\">"
            )
            sb.append(cell("B$row", line.code, 4))
            sb.append(cell("C$row", line.product.name, 4))
            sb.append(
                numberCell(
                    "D$row",
                    line.product.unitsPerBox.toDouble(),
                    4
                )
            )
            sb.append(numberCell("E$row", line.boxes.toDouble(), 4))
            sb.append(numberCell("F$row", line.pricePerUnit, 5))
            sb.append(numberCell("G$row", amount, 5))
            sb.append(cell("H$row", line.product.description, 4))
            sb.append("</row>")
        }

        sb.append("<row r=\"$totalRow\" ht=\"24\" customHeight=\"1\">")
        sb.append(cell("F$totalRow", "TOTAL", 3))
        sb.append(numberCell("G$totalRow", total, 6))
        sb.append("</row>")

        sb.append("</sheetData>")

        if (lines.isNotEmpty()) {
            sb.append(
                """<autoFilter ref="B10:H$lastProductRow"/>"""
            )
        }

        sb.append(
            """<mergeCells count="3">
              <mergeCell ref="C1:H1"/>
              <mergeCell ref="C2:H2"/>
              <mergeCell ref="C3:H3"/>
            </mergeCells>"""
        )

        sb.append(
            """<pageMargins
                left="0.4"
                right="0.4"
                top="0.5"
                bottom="0.5"
                header="0.2"
                footer="0.2"/>"""
        )

        sb.append("""<drawing r:id="rId1"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun cell(
        ref: String,
        value: String,
        style: Int
    ): String {
        return "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            xml(value) +
            "</t></is></c>"
    }

    private fun numberCell(
        ref: String,
        value: Double,
        style: Int
    ): String {
        return "<c r=\"$ref\" s=\"$style\"><v>$value</v></c>"
    }

    private fun xml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun columnName(index: Int): String {
        var n = index
        val result = StringBuilder()
        while (n > 0) {
            val mod = (n - 1) % 26
            result.append(('A'.code + mod).toChar())
            n = (n - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun createPaddedLogo(context: Context): ByteArray {
        val originalBytes = context.resources
            .openRawResource(R.drawable.app_logo)
            .use { it.readBytes() }

        return runCatching {
            val source = BitmapFactory.decodeByteArray(
                originalBytes,
                0,
                originalBytes.size
            ) ?: return@runCatching originalBytes

            val canvasWidth = 640
            val canvasHeight = 360
            val paddingX = 42f
            val paddingY = 28f

            val output = Bitmap.createBitmap(
                canvasWidth,
                canvasHeight,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(output)
            canvas.drawRGB(255, 255, 255)

            val availableWidth = canvasWidth - (paddingX * 2f)
            val availableHeight = canvasHeight - (paddingY * 2f)

            val scale = minOf(
                availableWidth / source.width.toFloat(),
                availableHeight / source.height.toFloat()
            )

            val drawWidth = source.width * scale
            val drawHeight = source.height * scale
            val left = (canvasWidth - drawWidth) / 2f
            val top = (canvasHeight - drawHeight) / 2f

            canvas.drawBitmap(
                source,
                null,
                RectF(
                    left,
                    top,
                    left + drawWidth,
                    top + drawHeight
                ),
                null
            )

            ByteArrayOutputStream().use { out ->
                val compressed = output.compress(
                    Bitmap.CompressFormat.JPEG,
                    96,
                    out
                )

                source.recycle()
                output.recycle()

                if (!compressed) {
                    error("No se pudo preparar el logo JPEG")
                }

                out.toByteArray()
            }
        }.getOrElse {
            val fallback = Bitmap.createBitmap(
                640,
                360,
                Bitmap.Config.ARGB_8888
            )

            Canvas(fallback).drawRGB(255, 255, 255)

            ByteArrayOutputStream().use { out ->
                fallback.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    out
                )
                fallback.recycle()
                out.toByteArray()
            }
        }
    }

    private fun fetchProductImage(
        pageUrl: String,
        productName: String
    ): ByteArray? {
        return runCatching {
            val html = downloadText(pageUrl)
            val imageUrl = findProductImageUrl(
                html = html,
                productName = productName
            ) ?: return@runCatching null

            val original = downloadBytes(imageUrl)
            val bitmap = BitmapFactory.decodeByteArray(
                original,
                0,
                original.size
            ) ?: return@runCatching null

            bitmapToPng(bitmap)
        }.getOrNull()
    }

    private fun downloadText(url: String): String {
        val connection = openConnection(url)
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = openConnection(url)
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 7000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) Pà-Solà-Comercial"
            )
            setRequestProperty(
                "Accept",
                "text/html,image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
            )
        }
    }

    private fun findProductImageUrl(
        html: String,
        productName: String
    ): String? {
        val imageTags = Regex(
            """<img\b[^>]*>""",
            setOf(
                RegexOption.IGNORE_CASE,
                RegexOption.DOT_MATCHES_ALL
            )
        ).findAll(html)

        val keywords = normalized(productName)
            .split(" ")
            .filter { it.length >= 4 }
            .take(3)

        imageTags.forEach { match ->
            val tag = match.value
            val alt = attribute(tag, "alt")
                ?: attribute(tag, "title")
                ?: ""

            val normalizedAlt = normalized(alt)
            val matchesProduct =
                keywords.isNotEmpty() &&
                    keywords.count { normalizedAlt.contains(it) } >=
                    minOf(2, keywords.size)

            if (matchesProduct) {
                val src =
                    attribute(tag, "data-src") ?:
                    attribute(tag, "data-lazy-src") ?:
                    attribute(tag, "src")

                if (!src.isNullOrBlank()) {
                    return cleanUrl(src)
                }
            }
        }

        val ogPatterns = listOf(
            Regex(
                """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["'][^>]*>""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["'][^>]*>""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<meta[^>]+name=["']twitter:image(?::src)?["'][^>]+content=["']([^"']+)["'][^>]*>""",
                RegexOption.IGNORE_CASE
            )
        )

        ogPatterns.forEach { pattern ->
            val found = pattern.find(html)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) {
                return cleanUrl(found)
            }
        }

        return null
    }

    private fun attribute(
        tag: String,
        name: String
    ): String? {
        val pattern = Regex(
            """\b$name\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(tag)?.groupValues?.getOrNull(1)
    }

    private fun cleanUrl(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&#38;", "&")
            .trim()
    }

    private fun normalized(value: String): String {
        return Normalizer
            .normalize(value.lowercase(Locale("es", "ES")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("×", "x")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val maxWidth = 420
        val maxHeight = 300
        val scale = minOf(
            1f,
            maxWidth.toFloat() / bitmap.width.toFloat(),
            maxHeight.toFloat() / bitmap.height.toFloat()
        )

        val scaled =
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

        return ByteArrayOutputStream().use { out ->
            scaled.compress(
                Bitmap.CompressFormat.PNG,
                90,
                out
            )
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
