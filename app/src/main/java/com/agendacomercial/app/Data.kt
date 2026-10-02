package com.agendacomercial.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Client(
    val id: Long,
    val name: String,
    val businessName: String,
    val contactPerson: String,
    val phone: String,
    val email: String,
    val address: String,
    val city: String,
    val postalCode: String,
    val observations: String,
    val isProspect: Boolean
)

data class Visit(
    val id: Long,
    val clientId: Long,
    val clientName: String,
    val scheduledAt: Long,
    val status: String,
    val purpose: String,
    val notes: String,
    val conversationSummary: String,
    val needs: String,
    val commitments: String,
    val completedAt: Long,
    val isProspect: Boolean
)

data class Product(val id: Long, val name: String, val sku: String, val price: Double)
data class Consumption(val name: String, val units: Int, val lastOrderedAt: Long, val lastPrice: Double)
data class OrderSummary(val id: Long, val createdAt: Long, val total: Double, val status: String)

class CrmDb(context: Context) : SQLiteOpenHelper(context, "agenda_comercial.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE clients(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                business_name TEXT NOT NULL DEFAULT '',
                contact_person TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                address TEXT NOT NULL DEFAULT '',
                city TEXT NOT NULL DEFAULT '',
                postal_code TEXT NOT NULL DEFAULT '',
                observations TEXT NOT NULL DEFAULT '',
                is_prospect INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE visits(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER NOT NULL,
                scheduled_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDIENTE',
                purpose TEXT NOT NULL DEFAULT 'Visita comercial',
                notes TEXT NOT NULL DEFAULT '',
                conversation_summary TEXT NOT NULL DEFAULT '',
                needs TEXT NOT NULL DEFAULT '',
                commitments TEXT NOT NULL DEFAULT '',
                completed_at INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sku TEXT NOT NULL DEFAULT '',
                price REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                total REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'CONFIRMADO',
                FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE order_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                unit_price REAL NOT NULL,
                FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """.trimIndent())

        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE visits ADD COLUMN conversation_summary TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE visits ADD COLUMN needs TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE visits ADD COLUMN commitments TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE visits ADD COLUMN completed_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE clients ADD COLUMN is_prospect INTEGER NOT NULL DEFAULT 0")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun seed(db: SQLiteDatabase) {
        listOf(
            Triple("Producto A", "A001", 10.0),
            Triple("Producto B", "B001", 15.5),
            Triple("Producto C", "C001", 8.75)
        ).forEach { (name, sku, price) ->
            db.insert("products", null, ContentValues().apply {
                put("name", name)
                put("sku", sku)
                put("price", price)
            })
        }

        val clientId = db.insert("clients", null, ContentValues().apply {
            put("name", "Cliente de ejemplo")
            put("business_name", "Comercio de ejemplo")
            put("phone", "600000000")
            put("address", "Dirección de ejemplo")
            put("city", "Barcelona")
            put("observations", "Aquí aparecerán tus observaciones comerciales.")
            put("is_prospect", 0)
        })

        db.insert("visits", null, ContentValues().apply {
            put("client_id", clientId)
            put("scheduled_at", System.currentTimeMillis() + 60 * 60 * 1000)
            put("purpose", "Presentación y seguimiento")
        })

        db.insert("clients", null, ContentValues().apply {
            put("name", "Prospecto de ejemplo")
            put("business_name", "Nuevo establecimiento")
            put("city", "Barcelona")
            put("observations", "Ejemplo de ficha que aparece en Prospecciones.")
            put("is_prospect", 1)
        })
    }

    fun clients(): List<Client> = parties(false)
    fun prospects(): List<Client> = parties(true)

    private fun parties(prospect: Boolean): List<Client> = readableDatabase.rawQuery(
        """
        SELECT id,name,business_name,contact_person,phone,email,address,city,postal_code,observations,is_prospect
        FROM clients
        WHERE is_prospect=?
        ORDER BY business_name COLLATE NOCASE, name COLLATE NOCASE
        """.trimIndent(),
        arrayOf(if (prospect) "1" else "0")
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(clientFromCursor(c))
        }
    }

    fun client(id: Long): Client? = readableDatabase.rawQuery(
        """
        SELECT id,name,business_name,contact_person,phone,email,address,city,postal_code,observations,is_prospect
        FROM clients
        WHERE id=?
        """.trimIndent(),
        arrayOf(id.toString())
    ).use { c ->
        if (!c.moveToFirst()) null else clientFromCursor(c)
    }

    private fun clientFromCursor(c: android.database.Cursor) = Client(
        id = c.getLong(0),
        name = c.getString(1),
        businessName = c.getString(2),
        contactPerson = c.getString(3),
        phone = c.getString(4),
        email = c.getString(5),
        address = c.getString(6),
        city = c.getString(7),
        postalCode = c.getString(8),
        observations = c.getString(9),
        isProspect = c.getInt(10) == 1
    )

    fun addClient(
        name: String,
        business: String,
        contact: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        postalCode: String,
        observations: String,
        isProspect: Boolean = false
    ): Long = writableDatabase.insert(
        "clients",
        null,
        clientValues(name, business, contact, phone, email, address, city, postalCode, observations, isProspect)
    )

    fun updateClient(
        id: Long,
        name: String,
        business: String,
        contact: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        postalCode: String,
        observations: String,
        isProspect: Boolean
    ) {
        writableDatabase.update(
            "clients",
            clientValues(name, business, contact, phone, email, address, city, postalCode, observations, isProspect),
            "id=?",
            arrayOf(id.toString())
        )
    }

    private fun clientValues(
        name: String,
        business: String,
        contact: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        postalCode: String,
        observations: String,
        isProspect: Boolean
    ) = ContentValues().apply {
        put("name", name)
        put("business_name", business)
        put("contact_person", contact)
        put("phone", phone)
        put("email", email)
        put("address", address)
        put("city", city)
        put("postal_code", postalCode)
        put("observations", observations)
        put("is_prospect", if (isProspect) 1 else 0)
    }

    fun convertToClient(id: Long) {
        writableDatabase.update(
            "clients",
            ContentValues().apply { put("is_prospect", 0) },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun agenda(from: Long, to: Long): List<Visit> = readableDatabase.rawQuery(
        """
        SELECT v.id,v.client_id,
               CASE WHEN c.business_name<>'' THEN c.business_name ELSE c.name END,
               v.scheduled_at,v.status,v.purpose,v.notes,
               v.conversation_summary,v.needs,v.commitments,v.completed_at,c.is_prospect
        FROM visits v
        JOIN clients c ON c.id=v.client_id
        WHERE v.scheduled_at BETWEEN ? AND ?
        ORDER BY v.scheduled_at
        """.trimIndent(),
        arrayOf(from.toString(), to.toString())
    ).use { c -> readVisits(c) }

    fun visits(clientId: Long): List<Visit> = readableDatabase.rawQuery(
        """
        SELECT v.id,v.client_id,
               CASE WHEN c.business_name<>'' THEN c.business_name ELSE c.name END,
               v.scheduled_at,v.status,v.purpose,v.notes,
               v.conversation_summary,v.needs,v.commitments,v.completed_at,c.is_prospect
        FROM visits v
        JOIN clients c ON c.id=v.client_id
        WHERE v.client_id=?
        ORDER BY v.scheduled_at DESC
        LIMIT 50
        """.trimIndent(),
        arrayOf(clientId.toString())
    ).use { c -> readVisits(c) }

    fun visit(id: Long): Visit? = readableDatabase.rawQuery(
        """
        SELECT v.id,v.client_id,
               CASE WHEN c.business_name<>'' THEN c.business_name ELSE c.name END,
               v.scheduled_at,v.status,v.purpose,v.notes,
               v.conversation_summary,v.needs,v.commitments,v.completed_at,c.is_prospect
        FROM visits v
        JOIN clients c ON c.id=v.client_id
        WHERE v.id=?
        """.trimIndent(),
        arrayOf(id.toString())
    ).use { c ->
        if (!c.moveToFirst()) null else visitFromCursor(c)
    }

    private fun readVisits(c: android.database.Cursor): List<Visit> = buildList {
        while (c.moveToNext()) add(visitFromCursor(c))
    }

    private fun visitFromCursor(c: android.database.Cursor) = Visit(
        id = c.getLong(0),
        clientId = c.getLong(1),
        clientName = c.getString(2),
        scheduledAt = c.getLong(3),
        status = c.getString(4),
        purpose = c.getString(5),
        notes = c.getString(6),
        conversationSummary = c.getString(7),
        needs = c.getString(8),
        commitments = c.getString(9),
        completedAt = c.getLong(10),
        isProspect = c.getInt(11) == 1
    )

    fun addVisit(clientId: Long, scheduledAt: Long, purpose: String, notes: String): Long =
        writableDatabase.insert("visits", null, ContentValues().apply {
            put("client_id", clientId)
            put("scheduled_at", scheduledAt)
            put("purpose", purpose)
            put("notes", notes)
        })

    fun completeVisit(id: Long) {
        writableDatabase.update(
            "visits",
            ContentValues().apply {
                put("status", "REALIZADA")
                put("completed_at", System.currentTimeMillis())
            },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun saveVisitReport(
        visitId: Long?,
        clientId: Long,
        conversationSummary: String,
        needs: String,
        commitments: String,
        notes: String
    ): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("status", "REALIZADA")
            put("completed_at", now)
            put("conversation_summary", conversationSummary)
            put("needs", needs)
            put("commitments", commitments)
            put("notes", notes)
        }

        if (visitId != null && visit(visitId) != null) {
            writableDatabase.update("visits", values, "id=?", arrayOf(visitId.toString()))
            return visitId
        }

        values.put("client_id", clientId)
        values.put("scheduled_at", now)
        values.put("purpose", if (client(clientId)?.isProspect == true) "Prospección" else "Visita comercial")
        return writableDatabase.insert("visits", null, values)
    }

    fun products(): List<Product> = readableDatabase.rawQuery(
        "SELECT id,name,sku,price FROM products ORDER BY name",
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Product(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3)))
        }
    }

    fun addOrder(clientId: Long, quantities: Map<Long, Int>) {
        val productMap = products().associateBy { it.id }
        val lines = quantities.filterValues { it > 0 }
        if (lines.isEmpty()) return

        val total = lines.entries.sumOf { (id, qty) -> (productMap[id]?.price ?: 0.0) * qty }
        val db = writableDatabase
        db.beginTransaction()

        try {
            val orderId = db.insert("orders", null, ContentValues().apply {
                put("client_id", clientId)
                put("created_at", System.currentTimeMillis())
                put("total", total)
                put("status", "CONFIRMADO")
            })

            lines.forEach { (productId, qty) ->
                val price = productMap[productId]?.price ?: 0.0
                db.insert("order_items", null, ContentValues().apply {
                    put("order_id", orderId)
                    put("product_id", productId)
                    put("quantity", qty)
                    put("unit_price", price)
                })
            }

            db.update(
                "clients",
                ContentValues().apply { put("is_prospect", 0) },
                "id=?",
                arrayOf(clientId.toString())
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun orders(clientId: Long): List<OrderSummary> = readableDatabase.rawQuery(
        "SELECT id,created_at,total,status FROM orders WHERE client_id=? ORDER BY created_at DESC LIMIT 50",
        arrayOf(clientId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(OrderSummary(c.getLong(0), c.getLong(1), c.getDouble(2), c.getString(3)))
        }
    }

    fun consumption(clientId: Long): List<Consumption> = readableDatabase.rawQuery(
        """
        SELECT p.name,CAST(SUM(oi.quantity) AS INTEGER),MAX(o.created_at),
               (SELECT oi2.unit_price
                FROM order_items oi2
                JOIN orders o2 ON o2.id=oi2.order_id
                WHERE o2.client_id=? AND oi2.product_id=p.id
                ORDER BY o2.created_at DESC
                LIMIT 1)
        FROM order_items oi
        JOIN orders o ON o.id=oi.order_id
        JOIN products p ON p.id=oi.product_id
        WHERE o.client_id=?
        GROUP BY p.id,p.name
        ORDER BY MAX(o.created_at) DESC
        """.trimIndent(),
        arrayOf(clientId.toString(), clientId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Consumption(c.getString(0), c.getInt(1), c.getLong(2), c.getDouble(3)))
        }
    }
}
