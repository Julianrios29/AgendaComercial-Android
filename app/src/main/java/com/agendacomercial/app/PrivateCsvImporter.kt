package com.agendacomercial.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Datos confidenciales: solo se importan desde archivos seleccionados por el usuario.
 * No se empaquetan clientes ni tarifas en el APK, y nunca se sincronizan con servidores.
 */
internal object PrivateCsvImporter {
    private const val MAX_CSV_BYTES = 500_000
    private val codePattern = Regex("[0-9]{5}")
    private val numericPattern = Regex("[0-9]+(\\.[0-9]+)?")
    private val timePattern = Regex("([0-9]{1,3}(-[0-9]{1,3})?)?")
    private val tempPattern = Regex("([0-9]{2,3}(-[0-9]{2,3})?)?")

    private fun parse(context: Context, uri: Uri): List<List<String>> {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                count += read
                require(count <= MAX_CSV_BYTES) { "El CSV supera el tamaño permitido" }
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        } ?: error("No se puede abrir el archivo seleccionado")
        val rows = parseSemicolonCsv(content)
        require(rows.size > 1) { "El CSV no contiene registros" }
        return rows
    }

    /**
     * Analiza CSV con ; como separador y admite comillas, ; dentro de textos,
     * comillas duplicadas y saltos de línea en campos entrecomillados.
     */
    private fun parseSemicolonCsv(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        fun finishField() {
            currentRow.add(field.toString().trim())
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow)
            currentRow = mutableListOf()
        }

        while (i < content.length) {
            when (val ch = content[i]) {
                '"' -> {
                    if (quoted && i + 1 < content.length && content[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = !quoted
                    }
                }
                ';' -> if (quoted) field.append(ch) else finishField()
                '\r', '\n' -> if (quoted) {
                    field.append(ch)
                } else {
                    if (ch == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    finishRow()
                }
                else -> field.append(ch)
            }
            i++
        }
        require(!quoted) { "CSV incorrecto: comillas sin cerrar" }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) finishRow()
        return rows
    }

    private fun column(headers: List<String>, name: String): Int {
        val pos = headers.indexOf(name)
        require(pos >= 0) { "Falta columna: $name" }
        return pos
    }

    fun importClients(context: Context, uri: Uri): Int {
        val rows = parse(context, uri)
        val h = rows.first().map(String::lowercase)
        val code = column(h, "codigo_cliente")
        val name = column(h, "nombre")
        val prospect = column(h, "es_prospeccion")
        val contact = column(h, "contacto")
        val phone = column(h, "telefono")
        val email = column(h, "email")
        val address = column(h, "direccion")
        val city = column(h, "poblacion")
        val postal = column(h, "codigo_postal")
        val revenue = listOf("importe_abril_2026", "importe_mayo_2026", "importe_junio_2026")
            .map { column(h, it) }
        val seen = HashSet<String>()
        val parsed = rows.drop(1).map { fields ->
            require(fields.size == h.size) { "El CSV de clientes tiene columnas incorrectas" }
            val c = fields[code]
            require(c.matches(Regex("[0-9]{1,10}")) && seen.add(c)) {
                "Código de cliente duplicado o incorrecto"
            }
            require(fields[name].isNotBlank()) { "Hay un cliente sin nombre" }
            ClientRow(
                code = c, name = fields[name], prospect = fields[prospect].equals("true", true),
                contact = fields[contact], phone = fields[phone], email = fields[email],
                address = fields[address], city = fields[city], postal = fields[postal],
                revenue = revenue.joinToString(" / ") { fields[it] }
            )
        }
        val appDb = CrmDb(context)
        val privateDb = Store(context)
        val db = privateDb.writableDatabase
        db.beginTransaction()
        try {
            parsed.forEach { item ->
                // Buscamos el registro importado previamente junto con los valores
                // de la última importación. Esto permite respetar retoques
                // manuales y actualizar los campos que aún proceden del CSV.
                val previous = db.rawQuery(
                    """SELECT client_id,source_name,source_contact,source_phone,
                              source_email,source_address,source_city,source_postal
                       FROM imported_clients WHERE code=?""",
                    arrayOf(item.code)
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else {
                        fun prior(index: Int): String? =
                            if (cursor.isNull(index)) null else cursor.getString(index)
                        ImportedClientState(
                            id = cursor.getLong(0),
                            name = prior(1), contact = prior(2), phone = prior(3),
                            email = prior(4), address = prior(5),
                            city = prior(6), postal = prior(7)
                        )
                    }
                }
                val old = previous?.let { appDb.client(it.id) }
                val clientId = if (old == null) {
                    appDb.addClient(
                        name = item.name, business = "", nif = "", bankAccount = "",
                        contact = item.contact, phone = item.phone, email = item.email,
                        address = item.address, city = item.city, postalCode = item.postal,
                        observations = "Código cliente: " + item.code +
                            "\nFacturación abril/mayo/junio 2026: " + item.revenue,
                        isProspect = item.prospect
                    )
                } else {
                    appDb.updateClient(
                        id = old.id,
                        name = preferCsv(old.name, item.name, previous.name),
                        business = old.businessName,
                        nif = old.nif,
                        bankAccount = old.bankAccount,
                        contact = preferCsv(old.contactPerson, item.contact, previous.contact),
                        phone = preferCsv(old.phone, item.phone, previous.phone),
                        email = preferCsv(old.email, item.email, previous.email),
                        address = preferCsv(old.address, item.address, previous.address),
                        city = preferCsv(old.city, item.city, previous.city),
                        postalCode = preferCsv(old.postalCode, item.postal, previous.postal),
                        observations = old.observations,
                        isProspect = old.isProspect
                    )
                    old.id
                }
                check(clientId > 0) { "No se ha podido guardar un cliente" }
                db.insertWithOnConflict("imported_clients", null, ContentValues().apply {
                    put("code", item.code)
                    put("client_id", clientId)
                    put("source_name", item.name)
                    put("source_contact", item.contact)
                    put("source_phone", item.phone)
                    put("source_email", item.email)
                    put("source_address", item.address)
                    put("source_city", item.city)
                    put("source_postal", item.postal)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            privateDb.close()
            appDb.close()
        }
        return parsed.size
    }

    fun importProducts(context: Context, uri: Uri): Int {
        val rows = parse(context, uri)
        val h = rows.first().map(String::lowercase)
        val code = column(h, "code")
        val name = column(h, "name")
        val category = column(h, "category")
        val size = column(h, "size")
        val price = column(h, "price_unit_eur")
        val units = column(h, "units_per_box")
        val cooking = column(h, "cooking_time_min")
        val temperature = column(h, "cooking_temperature_c")
        val note = column(h, "cooking_note")
        val seen = HashSet<String>()
        val parsed = rows.drop(1).mapIndexed { index, fields ->
            val lineNumber = index + 2
            require(fields.size == h.size) { "Fila $lineNumber: el CSV tiene columnas incorrectas" }
            val c = fields[code]
            val priceText = fields[price].replace(',', '.')
            require(codePattern.matches(c) && seen.add(c)) { "Fila $lineNumber: código repetido o inválido" }
            require(fields[name].isNotBlank() && numericPattern.matches(priceText) &&
                (priceText.toDoubleOrNull() ?: 0.0) > 0.0) { "Fila $lineNumber: precio inválido" }
            val boxUnits = fields[units].toIntOrNull()
            require(boxUnits != null && boxUnits > 0) { "Fila $lineNumber: unidades por caja incorrectas" }
            require(timePattern.matches(fields[cooking]) && tempPattern.matches(fields[temperature])) {
                "Fila $lineNumber: cocción incorrecta"
            }
            ProductRow(
                code = c, name = fields[name], category = fields[category],
                size = fields[size], price = priceText.toDouble(), units = boxUnits,
                cookingTime = fields[cooking], cookingTemp = fields[temperature],
                note = fields[note]
            )
        }
        val store = Store(context)
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            parsed.forEach { p ->
                db.insertWithOnConflict("private_products", null, ContentValues().apply {
                    put("code", p.code); put("name", p.name)
                    put("category", p.category); put("size", p.size)
                    put("price", p.price); put("units", p.units)
                    put("cooking_time", p.cookingTime); put("cooking_temp", p.cookingTemp)
                    put("cooking_note", p.note)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            store.close()
        }
        // La pestaña de pedidos existente comparte estas referencias.
        val mainDb = CrmDb(context)
        val productsDb = mainDb.writableDatabase
        productsDb.beginTransaction()
        try {
            parsed.forEach { p ->
                val existing = productsDb.rawQuery(
                    "SELECT id FROM products WHERE sku=? LIMIT 1",
                    arrayOf(p.code)
                ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                val values = ContentValues().apply {
                    put("sku", p.code)
                    put("name", p.name)
                    put("price", p.price)
                }
                if (existing == null) {
                    check(productsDb.insert("products", null, values) > 0) {
                        "No se ha podido guardar un producto"
                    }
                } else {
                    productsDb.update("products", values, "id=?", arrayOf(existing.toString()))
                }
            }
            // Solo retirar productos ficticios si no tienen pedidos vinculados.
            productsDb.execSQL(
                """DELETE FROM products WHERE sku IN ('A001','B001','C001')
                   AND id NOT IN (SELECT product_id FROM order_items)"""
            )
            productsDb.setTransactionSuccessful()
        } finally {
            productsDb.endTransaction()
            mainDb.close()
        }
        return parsed.size
    }

    fun catalog(context: Context): List<BudgetCatalogProduct> {
        val store = Store(context)
        return try {
            store.readableDatabase.rawQuery(
                """SELECT code,name,category,size,price,units,cooking_time,cooking_temp
                   FROM private_products ORDER BY category,name""", null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(BudgetCatalogProduct(
                            id = cursor.getString(0), name = cursor.getString(1),
                            category = cursor.getString(2), description = cursor.getString(3),
                            unitPrice = cursor.getDouble(4), unitsPerBox = cursor.getInt(5),
                            code = cursor.getString(0), cookingTime = cursor.getString(6),
                            cookingTemperature = cursor.getString(7), webUrl = ""
                        ))
                    }
                }
            }
        } finally { store.close() }
    }

    /** Si el usuario modificó un campo, prevalece su edición sobre el CSV. */
    private fun preferCsv(current: String, incoming: String, oldImported: String?): String {
        if (incoming.isBlank()) return current
        return when {
            oldImported == null -> if (current.isBlank()) incoming else current
            current == oldImported || current.isBlank() -> incoming
            else -> current
        }
    }

    private data class ImportedClientState(
        val id: Long,
        val name: String?,
        val contact: String?,
        val phone: String?,
        val email: String?,
        val address: String?,
        val city: String?,
        val postal: String?
    )

    private data class ClientRow(
        val code: String, val name: String, val prospect: Boolean,
        val contact: String, val phone: String, val email: String,
        val address: String, val city: String, val postal: String,
        val revenue: String
    )

    private data class ProductRow(
        val code: String, val name: String, val category: String,
        val size: String, val price: Double, val units: Int,
        val cookingTime: String, val cookingTemp: String, val note: String
    )

    private class Store(context: Context) :
        SQLiteOpenHelper(context, "agenda_private_data.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE private_products (
                    code TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL,
                    category TEXT NOT NULL, size TEXT NOT NULL,
                    price REAL NOT NULL, units INTEGER NOT NULL,
                    cooking_time TEXT NOT NULL, cooking_temp TEXT NOT NULL,
                    cooking_note TEXT NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE imported_clients (
                    code TEXT PRIMARY KEY NOT NULL, client_id INTEGER NOT NULL,
                    source_name TEXT, source_contact TEXT, source_phone TEXT,
                    source_email TEXT, source_address TEXT, source_city TEXT,
                    source_postal TEXT
                )"""
            )
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                listOf("source_name", "source_contact", "source_phone", "source_email",
                    "source_address", "source_city", "source_postal").forEach { field ->
                    db.execSQL("ALTER TABLE imported_clients ADD COLUMN $field TEXT")
                }
            }
        }
    }
}
