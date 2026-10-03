package com.agendacomercial.app

import android.content.Context
import android.net.Uri
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DailyVisitsXlsxExporter {

    fun write(
        context: Context,
        uri: Uri,
        visits: List<DailyVisitActivity>,
        dayStart: Long
    ): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeWorkbook(output, visits, dayStart)
            } ?: error("No se pudo abrir el archivo de destino")
            true
        }.getOrDefault(false)
    }

    private fun writeWorkbook(
        output: OutputStream,
        visits: List<DailyVisitActivity>,
        dayStart: Long
    ) {
        ZipOutputStream(output).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook())
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            put(zip, "xl/styles.xml", styles())
            put(zip, "xl/worksheets/sheet1.xml", sheet(visits, dayStart))
        }
    }

    private fun put(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
      Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Visitas" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
      Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2"
      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
      Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF000000"/><name val="Calibri"/></font>
    <font><b/><sz val="14"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFD34E"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFDCEEFF"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFDDF4DF"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF050505"/><bgColor indexed="64"/></patternFill></fill>
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
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0">
      <alignment vertical="top" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1">
      <alignment vertical="top" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1">
      <alignment vertical="top" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="5" borderId="0" xfId="0" applyFill="1" applyFont="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun sheet(
        visits: List<DailyVisitActivity>,
        dayStart: Long
    ): String {
        val date = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
            .format(Date(dayStart))
        val time = SimpleDateFormat("HH:mm", Locale("es", "ES"))

        val headers = listOf(
            "Tipo",
            "Hora",
            "Local",
            "Persona de contacto",
            "Teléfono",
            "Motivo",
            "Resultado / resumen",
            "Necesidades / oportunidades",
            "Próximos pasos",
            "Notas"
        )

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""
<sheetViews>
  <sheetView workbookViewId="0">
    <pane ySplit="3" topLeftCell="A4" activePane="bottomLeft" state="frozen"/>
  </sheetView>
</sheetViews>
<cols>
  <col min="1" max="1" width="14" customWidth="1"/>
  <col min="2" max="2" width="10" customWidth="1"/>
  <col min="3" max="3" width="28" customWidth="1"/>
  <col min="4" max="4" width="24" customWidth="1"/>
  <col min="5" max="5" width="16" customWidth="1"/>
  <col min="6" max="6" width="28" customWidth="1"/>
  <col min="7" max="10" width="34" customWidth="1"/>
</cols>
<sheetData>
""".trimIndent())

        sb.append("<row r=\"1\" ht=\"26\" customHeight=\"1\">")
        sb.append(cell("A1", "Pà Solà Comercial · Visitas del $date", 4))
        sb.append("</row>")

        sb.append("<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t></t></is></c></row>")

        sb.append("<row r=\"3\" ht=\"28\" customHeight=\"1\">")
        headers.forEachIndexed { index, value ->
            sb.append(cell(columnName(index + 1) + "3", value, 1))
        }
        sb.append("</row>")

        visits.forEachIndexed { index, visit ->
            val row = index + 4
            val style = if (visit.isProspection) 2 else 3
            val values = listOf(
                if (visit.isProspection) "Prospección" else "Cliente",
                time.format(Date(visit.completedAt)),
                visit.localName,
                visit.contactPerson,
                visit.phone,
                visit.purpose,
                visit.conversationSummary,
                visit.needs,
                visit.commitments,
                visit.notes
            )

            sb.append("<row r=\"$row\">")
            values.forEachIndexed { column, value ->
                sb.append(cell(columnName(column + 1) + row, value, style))
            }
            sb.append("</row>")
        }

        sb.append("</sheetData>")

        if (visits.isNotEmpty()) {
            sb.append("""<autoFilter ref="A3:J${visits.size + 3}"/>""")
        }

        sb.append("""<mergeCells count="1"><mergeCell ref="A1:J1"/></mergeCells>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun cell(ref: String, value: String, style: Int): String {
        return "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            xml(value) +
            "</t></is></c>"
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
