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

    var prospects by mutableStateOf<List<Client>>(emptyList())
        private set

    var agenda by mutableStateOf<List<Visit>>(emptyList())
        private set

    init {
        refresh()
    }

    fun refresh() {
        clients = db.clients()
        prospects = db.prospects()

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val from = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 395)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        agenda = db.agenda(from, cal.timeInMillis)
    }

    fun client(id: Long) = db.client(id)
    fun visit(id: Long) = db.visit(id)
    fun visits(id: Long) = db.visits(id)
    fun products() = db.products()
    fun orders(id: Long) = db.orders(id)
    fun consumption(id: Long) = db.consumption(id)
    fun dailyVisits(from: Long, to: Long) = db.dailyVisits(from, to)
    fun dailyOrders(from: Long, to: Long) = db.dailyOrders(from, to)

    fun addClient(
        name: String,
        business: String,
        nif: String,
        bankAccount: String,
        contact: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        postal: String,
        notes: String,
        isProspect: Boolean = false
    ): Long {
        val id = db.addClient(
            name,
            business,
            nif,
            bankAccount,
            contact,
            phone,
            email,
            address,
            city,
            postal,
            notes,
            isProspect
        )

        refresh()
        return id
    }

    fun addCompletedProspect(
        name: String,
        business: String,
        nif: String,
        bankAccount: String,
        contact: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        postal: String,
        observations: String,
        conversationSummary: String,
        needs: String,
        commitments: String,
        visitNotes: String
    ): Long {
        val id = db.addClient(
            name,
            business,
            nif,
            bankAccount,
            contact,
            phone,
            email,
            address,
            city,
            postal,
            observations,
            true
        )

        db.saveVisitReport(
            visitId = null,
            clientId = id,
            conversationSummary = conversationSummary,
            needs = needs,
            commitments = commitments,
            notes = visitNotes
        )

        refresh()
        return id
    }

    fun updateClient(
        id: Long,
        name: String,
        business: String,
        nif: String,
        bankAccount: String,
        contact: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        postal: String,
        notes: String
    ) {
        val current = db.client(id) ?: return

        db.updateClient(
            id,
            name,
            business,
            nif,
            bankAccount,
            contact,
            phone,
            email,
            address,
            city,
            postal,
            notes,
            current.isProspect
        )
        refresh()
    }

    fun updatePhoto(id: Long, path: String) {
        db.updatePhotoPath(id, path)
        refresh()
    }

    fun convertProspectToClient(id: Long) {
        db.convertToClient(id)
        refresh()
    }

    fun addVisit(clientId: Long, scheduledAt: Long, purpose: String, notes: String) {
        db.addVisit(
            clientId = clientId,
            scheduledAt = scheduledAt,
            purpose = purpose.ifBlank {
                if (db.client(clientId)?.isProspect == true) "Prospección" else "Visita comercial"
            },
            notes = notes
        )
        refresh()
    }

    fun completeVisit(id: Long) {
        db.completeVisit(id)
        refresh()
    }

    fun saveVisitReport(
        visitId: Long?,
        clientId: Long,
        conversationSummary: String,
        needs: String,
        commitments: String,
        notes: String,
        convertProspect: Boolean = false
    ) {
        db.saveVisitReport(
            visitId = visitId,
            clientId = clientId,
            conversationSummary = conversationSummary,
            needs = needs,
            commitments = commitments,
            notes = notes
        )

        if (convertProspect) {
            db.convertToClient(clientId)
        }

        refresh()
    }

    fun addOrder(clientId: Long, quantities: Map<Long, Int>) {
        db.addOrder(clientId, quantities)
        refresh()
    }
}
