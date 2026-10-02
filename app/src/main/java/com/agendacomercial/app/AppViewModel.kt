package com.agendacomercial.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.Calendar

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = CrmDb(app)

    var clients by mutableStateOf<List<Client>>(emptyList())
        private set
    var agenda by mutableStateOf<List<Visit>>(emptyList())
        private set

    init { refresh() }

    fun refresh() {
        clients = db.clients()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        agenda = db.agenda(from, cal.timeInMillis)
    }

    fun client(id: Long) = db.client(id)
    fun visits(id: Long) = db.visits(id)
    fun products() = db.products()
    fun orders(id: Long) = db.orders(id)
    fun consumption(id: Long) = db.consumption(id)

    fun addClient(name: String, business: String, contact: String, phone: String, email: String, address: String, city: String, postal: String, notes: String): Long {
        val id = db.addClient(name, business, contact, phone, email, address, city, postal, notes)
        refresh()
        return id
    }

    fun addVisit(clientId: Long, daysAhead: Int, purpose: String, notes: String) {
        val whenMillis = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        db.addVisit(clientId, whenMillis, purpose.ifBlank { "Visita comercial" }, notes)
        refresh()
    }

    fun completeVisit(id: Long) { db.completeVisit(id); refresh() }
    fun addOrder(clientId: Long, quantities: Map<Long, Int>) { db.addOrder(clientId, quantities); refresh() }
}
