package com.agendacomercial.app

import android.content.Context
import android.net.Uri
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BudgetXlsxExporter {

    fun write(
        context: Context,
        uri: Uri,
        client: Client,
        lines: List<BudgetLine>
    ): Boolean {
        return runCatching {
            val logo = context.resources
                .openRawResource(R.drawable.app_logo)
                .use { it.readBytes() }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeWorkbook(
                    output = output,
                    client = client,
                    lines = lines,
                    logoBytes = logo
                )
            } ?: error("No se pudo abrir el archivo de destino")

            true
        }.getOrDefault(false)
    }

    private fun writeWorkbook(
        output: OutputStream,
        client: Client,
        lines: List<BudgetLine>,
        logoBytes: ByteArray
    ) {
        ZipOutputStream(output).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook())
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            put(zip, "xl/styles.xml", styles())
            put(zip, "xl/worksheets/sheet1.xml", sheet(client, lines))
            put(zip, "xl/worksheets/_rels/sheet1.xml.rels", sheetRels())
            put(zip, "xl/drawings/drawing1.xml", drawing())
            put(zip, "xl/drawings/_rels/drawing1.xml.rels", drawingRels())
            putBytes(zip, "xl/media/logo.png", logoBytes)
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
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>
</Types>"""

    private fun rootRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
      Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Presupuesto" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private fun workbookRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
      Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
      Target="styles.xml"/>
</Relationships>"""

    private fun sheetRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing"
      Target="../drawings/drawing1.xml"/>
</Relationships>"""

    private fun drawingRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
      Target="../media/logo.png"/>
</Relationships>"""

    private fun drawing() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr
    xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <xdr:twoCellAnchor editAs="oneCell">
    <xdr:from>
      <xdr:col>0</xdr:col>
      <xdr:colOff>0</xdr:colOff>
      <xdr:row>0</xdr:row>
      <xdr:rowOff>0</xdr:rowOff>
    </xdr:from>
    <xdr:to>
      <xdr:col>2</xdr:col>
      <xdr:colOff>0</xdr:colOff>
      <xdr:row>4</xdr:row>
      <xdr:rowOff>0</xdr:rowOff>
    </xdr:to>
    <xdr:pic>
      <xdr:nvPicPr>
        <xdr:cNvPr id="1" name="Logo Pa Sola"/>
        <xdr:cNvPicPr/>
      </xdr:nvPicPr>
      <xdr:blipFill>
        <a:blip r:embed="rId1"/>
        <a:stretch><a:fillRect/></a:stretch>
      </xdr:blipFill>
      <xdr:spPr>
        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
      </xdr:spPr>
    </xdr:pic>
    <xdr:clientData/>
  </xdr:twoCellAnchor>
</xdr:wsDr>"""

    private fun styles() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0.00 [$€-es-ES]"/>
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
        val total = lines.sumOf {
            it.boxes.toDouble() * it.pricePerBox
        }

        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        )
        sb.append(
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">"""
        )

        sb.append("""
<sheetViews>
  <sheetView workbookViewId="0">
    <pane ySplit="10" topLeftCell="A11" activePane="bottomLeft" state="frozen"/>
  </sheetView>
</sheetViews>
<cols>
  <col min="1" max="1" width="17" customWidth="1"/>
  <col min="2" max="2" width="30" customWidth="1"/>
  <col min="3" max="3" width="12" customWidth="1"/>
  <col min="4" max="4" width="10" customWidth="1"/>
  <col min="5" max="6" width="14" customWidth="1"/>
  <col min="7" max="7" width="42" customWidth="1"/>
  <col min="8" max="8" width="38" customWidth="1"/>
</cols>
<sheetData>
""".trimIndent())

        sb.append("<row r=\"1\" ht=\"30\" customHeight=\"1\">")
        sb.append(cell("C1", "PRESUPUESTO PÀ SOLÀ", 1))
        sb.append("</row>")

        sb.append("<row r=\"2\" ht=\"35\" customHeight=\"1\">")
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
            "Código",
            "Producto",
            "U./caja",
            "Cajas",
            "Precio/caja",
            "Importe",
            "Descripción",
            "Ficha web"
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
            val amount = line.boxes.toDouble() * line.pricePerBox

            sb.append("<row r=\"$row\">")
            sb.append(cell("A$row", line.code, 4))
            sb.append(cell("B$row", line.product.name, 4))
            sb.append(numberCell("C$row", line.product.unitsPerBox.toDouble(), 4))
            sb.append(numberCell("D$row", line.boxes.toDouble(), 4))
            sb.append(numberCell("E$row", line.pricePerBox, 5))
            sb.append(numberCell("F$row", amount, 5))
            sb.append(cell("G$row", line.product.description, 4))
            sb.append(cell("H$row", line.product.webUrl, 4))
            sb.append("</row>")
        }

        sb.append("<row r=\"$totalRow\" ht=\"24\" customHeight=\"1\">")
        sb.append(cell("E$totalRow", "TOTAL", 3))
        sb.append(numberCell("F$totalRow", total, 6))
        sb.append("</row>")

        sb.append("</sheetData>")

        sb.append(
            """<mergeCells count="5">
              <mergeCell ref="C1:H1"/>
              <mergeCell ref="C2:H2"/>
              <mergeCell ref="C3:H3"/>
              <mergeCell ref="B6:D6"/>
              <mergeCell ref="B8:D8"/>
            </mergeCells>"""
        )

        if (lines.isNotEmpty()) {
            sb.append(
                """<autoFilter ref="A10:H${totalRow - 1}"/>"""
            )
        }

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
}
