package com.agendacomercial.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class Screen {
    data object Agenda : Screen()
    data object Clients : Screen()
    data object Prospects : Screen()
    data class NewParty(val prospect: Boolean) : Screen()
    data class Detail(val id: Long) : Screen()
    data class EditParty(val id: Long) : Screen()
    data class NewVisit(val id: Long) : Screen()
    data class NewOrder(val id: Long) : Screen()
    data class BeforeVisit(val clientId: Long, val visitId: Long?) : Screen()
    data class InVisit(val clientId: Long, val visitId: Long?) : Screen()
}

@Composable
fun CrmApp(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Agenda) }

    MaterialTheme {
        val root = screen is Screen.Agenda || screen is Screen.Clients || screen is Screen.Prospects

        Scaffold(
            bottomBar = {
                if (root) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = screen is Screen.Agenda,
                            onClick = {
                                vm.refresh()
                                screen = Screen.Agenda
                            },
                            icon = { Text("A") },
                            label = { Text("Agenda") }
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Clients,
                            onClick = {
                                vm.refresh()
                                screen = Screen.Clients
                            },
                            icon = { Text("C") },
                            label = { Text("Clientes") }
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Prospects,
                            onClick = {
                                vm.refresh()
                                screen = Screen.Prospects
                            },
                            icon = { Text("P") },
                            label = { Text("Prospecciones") }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (val s = screen) {
                    Screen.Agenda -> WeeklyAgendaScreen(vm) { clientId, visitId ->
                        screen = Screen.BeforeVisit(clientId, visitId)
                    }

                    Screen.Clients -> PartyListScreen(
                        title = "Clientes",
                        emptyText = "Todavía no hay clientes.",
                        parties = vm.clients,
                        addLabel = "+ Nuevo",
                        onAdd = { screen = Screen.NewParty(false) },
                        onOpen = { screen = Screen.Detail(it) }
                    )

                    Screen.Prospects -> PartyListScreen(
                        title = "Prospecciones",
                        emptyText = "Todavía no hay prospecciones.",
                        parties = vm.prospects,
                        addLabel = "+ Nueva",
                        onAdd = { screen = Screen.NewParty(true) },
                        onOpen = { screen = Screen.Detail(it) }
                    )

                    is Screen.NewParty -> PartyFormScreen(
                        vm = vm,
                        party = null,
                        newIsProspect = s.prospect,
                        back = {
                            screen = if (s.prospect) Screen.Prospects else Screen.Clients
                        },
                        saved = { screen = Screen.Detail(it) }
                    )

                    is Screen.Detail -> PartyDetailScreen(
                        vm = vm,
                        id = s.id,
                        back = {
                            screen = if (vm.client(s.id)?.isProspect == true) Screen.Prospects else Screen.Clients
                        },
                        edit = { screen = Screen.EditParty(s.id) },
                        before = { screen = Screen.BeforeVisit(s.id, null) },
                        inVisit = { screen = Screen.InVisit(s.id, null) },
                        visit = { screen = Screen.NewVisit(s.id) },
                        order = { screen = Screen.NewOrder(s.id) },
                        convert = {
                            vm.convertProspectToClient(s.id)
                            screen = Screen.Detail(s.id)
                        }
                    )

                    is Screen.EditParty -> PartyFormScreen(
                        vm = vm,
                        party = vm.client(s.id),
                        newIsProspect = false,
                        back = { screen = Screen.Detail(s.id) },
                        saved = { screen = Screen.Detail(it) }
                    )

                    is Screen.NewVisit -> VisitScheduleScreen(vm, s.id) {
                        screen = Screen.Detail(s.id)
                    }

                    is Screen.NewOrder -> OrderScreen(vm, s.id) {
                        screen = Screen.Detail(s.id)
                    }

                    is Screen.BeforeVisit -> BeforeVisitScreen(
                        vm = vm,
                        clientId = s.clientId,
                        visitId = s.visitId,
                        back = { screen = Screen.Agenda },
                        openParty = { screen = Screen.Detail(s.clientId) },
                        startVisit = { screen = Screen.InVisit(s.clientId, s.visitId) }
                    )

                    is Screen.InVisit -> InVisitScreen(
                        vm = vm,
                        clientId = s.clientId,
                        visitId = s.visitId,
                        back = { screen = Screen.BeforeVisit(s.clientId, s.visitId) },
                        saved = { screen = Screen.Detail(s.clientId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyAgendaScreen(vm: AppViewModel, open: (Long, Long) -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val start = remember(weekOffset) { weekStart(weekOffset) }
    val end = remember(start) {
        Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis
    }
    val visits = vm.agenda.filter { it.scheduledAt in start until end }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Agenda semanal") })

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { weekOffset-- }) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }

            Text(
                weekRangeLabel(start),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )

            IconButton(onClick = { weekOffset++ }) {
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((0..6).toList()) { dayIndex ->
                val dayStart = dayStart(start, dayIndex)
                val dayEnd = dayStart(start, dayIndex + 1)
                val dayVisits = visits
                    .filter { it.scheduledAt in dayStart until dayEnd }
                    .sortedBy { it.scheduledAt }

                Card(Modifier.width(205.dp)) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            dayLabel(dayStart),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (dayVisits.isEmpty()) {
                            Text("Sin visitas", style = MaterialTheme.typography.bodySmall)
                        }

                        dayVisits.forEach { visit ->
                            Surface(
                                tonalElevation = 2.dp,
                                shape = MaterialTheme.shapes.small,
                                color = partyContainerColor(visit.isProspect),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { open(visit.clientId, visit.id) }
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        timeOnly(visit.scheduledAt),
                                        fontWeight = FontWeight.Bold,
                                        color = partyAccentColor(visit.isProspect)
                                    )
                                    Text(
                                        visit.clientName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (visit.isProspect) "Prospección" else "Cliente",
                                        color = partyAccentColor(visit.isProspect),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        visit.purpose,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (visit.status == "REALIZADA") {
                                        Text(
                                            "Realizada",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (visits.isEmpty()) {
            Text(
                "No hay visitas programadas esta semana.",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartyListScreen(
    title: String,
    emptyText: String,
    parties: List<Client>,
    addLabel: String,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val list = parties.filter {
        query.isBlank() || listOf(it.name, it.businessName, it.contactPerson, it.city, it.phone).any { value ->
            value.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            actions = {
                TextButton(onClick = onAdd) { Text(addLabel) }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar") },
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isEmpty()) {
                item { Text(emptyText) }
            }

            items(list, key = { it.id }) { party ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(party.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = partyContainerColor(party.isProspect)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            party.businessName.ifBlank { party.name },
                            fontWeight = FontWeight.Bold,
                            color = partyAccentColor(party.isProspect)
                        )
                        Text(
                            "Contacto: " + party.contactPerson.ifBlank { party.name }
                        )
                        Text(
                            "Teléfono: " + party.phone.ifBlank { "Sin teléfono" }
                        )
                        Text(
                            if (party.isProspect) "Prospección" else "Cliente",
                            color = partyAccentColor(party.isProspect),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartyDetailScreen(
    vm: AppViewModel,
    id: Long,
    back: () -> Unit,
    edit: () -> Unit,
    before: () -> Unit,
    inVisit: () -> Unit,
    visit: () -> Unit,
    order: () -> Unit,
    convert: () -> Unit
) {
    var reload by remember { mutableIntStateOf(0) }
    val party = remember(id, reload, vm.clients, vm.prospects) { vm.client(id) } ?: return
    val visits = remember(id, reload) { vm.visits(id) }
    val orders = remember(id, reload) { vm.orders(id) }
    val consumption = remember(id, reload) { vm.consumption(id) }
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(
                title = {
                    Text(
                        if (party.isProspect) "Prospección" else party.businessName.ifBlank { party.name }
                    )
                },
                navigationIcon = { TextButton(onClick = back) { Text("<") } },
                actions = { TextButton(onClick = edit) { Text("Editar") } }
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    party.businessName.ifBlank { party.name },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (party.businessName.isNotBlank()) Text(party.name)

                Text(
                    listOf(party.address, party.postalCode, party.city)
                        .filter(String::isNotBlank)
                        .joinToString(", ")
                )

                if (party.contactPerson.isNotBlank()) Text("Contacto: " + party.contactPerson)
                if (party.phone.isNotBlank()) Text("Tel: " + party.phone)
                if (party.email.isNotBlank()) Text(party.email)

                if (party.observations.isNotBlank()) {
                    Text("Observaciones", fontWeight = FontWeight.Bold)
                    Text(party.observations)
                }

                Spacer(Modifier.height(6.dp))

                Button(onClick = before, modifier = Modifier.fillMaxWidth()) {
                    Text(if (party.isProspect) "Antes de prospectar" else "Antes de entrar")
                }

                Button(onClick = inVisit, modifier = Modifier.fillMaxWidth()) {
                    Text(if (party.isProspect) "Estoy prospectando" else "Estoy con el cliente")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = order, modifier = Modifier.weight(1f)) {
                        Text("Nuevo pedido")
                    }
                    Button(onClick = visit, modifier = Modifier.weight(1f)) {
                        Text("Nueva cita")
                    }
                }

                if (party.isProspect) {
                    OutlinedButton(onClick = convert, modifier = Modifier.fillMaxWidth()) {
                        Text("Prospección conseguida · pasar a Clientes")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (party.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + party.phone))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Llamar") }
                    }

                    if (party.address.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val place = Uri.encode(party.address + " " + party.city)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + place))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Mapa") }
                    }
                }
            }
        }

        item { Heading(if (party.isProspect) "Actividad de prospección" else "Productos que consume") }

        if (!party.isProspect) {
            if (consumption.isEmpty()) {
                item { Text("Todavía no hay consumo registrado.", Modifier.padding(horizontal = 16.dp)) }
            }
            items(consumption) { item ->
                InfoCard {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text("Unidades históricas: " + item.units)
                    Text("Último pedido: " + date(item.lastOrderedAt) + " · " + money(item.lastPrice))
                }
            }

            item { Heading("Últimos pedidos") }

            if (orders.isEmpty()) {
                item { Text("Sin pedidos todavía.", Modifier.padding(horizontal = 16.dp)) }
            }

            items(orders, key = { it.id }) { item ->
                InfoCard {
                    Text(date(item.createdAt) + " · " + money(item.total), fontWeight = FontWeight.Bold)
                    Text(item.status)
                }
            }
        } else {
            item {
                InfoCard {
                    Text("Si haces un pedido desde esta ficha, pasará automáticamente a Clientes.")
                    Text("También puedes usar el botón “Prospección conseguida” cuando ya sea cliente.")
                }
            }
        }

        item { Heading(if (party.isProspect) "Historial de prospecciones" else "Historial de visitas") }

        if (visits.isEmpty()) {
            item { Text("Sin actividad registrada.", Modifier.padding(horizontal = 16.dp)) }
        }

        items(visits, key = { it.id }) { item ->
            InfoCard {
                Text(dateTime(item.scheduledAt), fontWeight = FontWeight.Bold)
                Text(item.status + " · " + item.purpose)
                if (item.conversationSummary.isNotBlank()) Text("Resumen: " + item.conversationSummary)
                if (item.needs.isNotBlank()) Text("Necesidades: " + item.needs)
                if (item.commitments.isNotBlank()) Text("Próximos pasos: " + item.commitments)
                if (item.notes.isNotBlank()) Text("Notas: " + item.notes)

                if (item.status != "REALIZADA") {
                    TextButton(
                        onClick = {
                            vm.completeVisit(item.id)
                            reload++
                        }
                    ) { Text("Marcar realizada") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeforeVisitScreen(
    vm: AppViewModel,
    clientId: Long,
    visitId: Long?,
    back: () -> Unit,
    openParty: () -> Unit,
    startVisit: () -> Unit
) {
    val party = remember(clientId, vm.clients, vm.prospects) { vm.client(clientId) } ?: return
    val appointment = remember(visitId) { visitId?.let { vm.visit(it) } }
    val visits = remember(clientId) { vm.visits(clientId) }
    val orders = remember(clientId) { vm.orders(clientId) }
    val consumption = remember(clientId) { vm.consumption(clientId) }
    val previousVisit = visits.firstOrNull { it.status == "REALIZADA" && it.id != visitId }
    val lastOrder = orders.firstOrNull()
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(
                title = { Text(if (party.isProspect) "Antes de prospectar" else "Antes de entrar") },
                navigationIcon = { TextButton(onClick = back) { Text("<") } },
                actions = { TextButton(onClick = openParty) { Text("Ficha") } }
            )
        }

        item {
            InfoCard {
                Text(
                    party.businessName.ifBlank { party.name },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (party.businessName.isNotBlank()) Text(party.name)
                if (appointment != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Cita: " + dateTime(appointment.scheduledAt), fontWeight = FontWeight.Bold)
                    Text("Motivo: " + appointment.purpose)
                    if (appointment.notes.isNotBlank()) Text("Preparación: " + appointment.notes)
                }
            }
        }

        item {
            Heading("Datos rápidos")
            InfoCard {
                if (party.contactPerson.isNotBlank()) Text("Contacto: " + party.contactPerson)
                if (party.phone.isNotBlank()) Text("Teléfono: " + party.phone)
                Text(
                    "Dirección: " + listOf(party.address, party.postalCode, party.city)
                        .filter(String::isNotBlank)
                        .joinToString(", ")
                )
                if (party.observations.isNotBlank()) {
                    Text("Observaciones", fontWeight = FontWeight.Bold)
                    Text(party.observations)
                }
            }
        }

        if (!party.isProspect) {
            item { Heading("Lo que consume") }

            if (consumption.isEmpty()) {
                item { InfoCard { Text("Todavía no hay productos consumidos registrados.") } }
            } else {
                items(consumption.take(5)) { item ->
                    InfoCard {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text("Unidades acumuladas: " + item.units)
                        Text("Última compra: " + date(item.lastOrderedAt))
                    }
                }
            }

            item { Heading("Último pedido") }
            item {
                InfoCard {
                    if (lastOrder == null) {
                        Text("Todavía no hay pedidos.")
                    } else {
                        Text(date(lastOrder.createdAt) + " · " + money(lastOrder.total), fontWeight = FontWeight.Bold)
                        Text(lastOrder.status)
                    }
                }
            }
        }

        item { Heading(if (party.isProspect) "Última prospección" else "Última visita realizada") }

        item {
            InfoCard {
                if (previousVisit == null) {
                    Text("No hay actividad anterior registrada.")
                } else {
                    Text(dateTime(previousVisit.scheduledAt), fontWeight = FontWeight.Bold)
                    if (previousVisit.conversationSummary.isNotBlank()) Text("Resumen: " + previousVisit.conversationSummary)
                    if (previousVisit.needs.isNotBlank()) Text("Necesidades: " + previousVisit.needs)
                    if (previousVisit.commitments.isNotBlank()) Text("Próximos pasos: " + previousVisit.commitments)
                    if (previousVisit.notes.isNotBlank()) Text("Notas: " + previousVisit.notes)
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = startVisit, modifier = Modifier.fillMaxWidth()) {
                    Text(if (party.isProspect) "Empezar prospección" else "Estoy con el cliente")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (party.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + party.phone))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Llamar") }
                    }

                    if (party.address.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val place = Uri.encode(party.address + " " + party.city)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + place))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Mapa") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InVisitScreen(
    vm: AppViewModel,
    clientId: Long,
    visitId: Long?,
    back: () -> Unit,
    saved: () -> Unit
) {
    val party = remember(clientId, vm.clients, vm.prospects) { vm.client(clientId) } ?: return
    val existing = remember(visitId) { visitId?.let { vm.visit(it) } }

    var conversation by remember { mutableStateOf(existing?.conversationSummary ?: "") }
    var needs by remember { mutableStateOf(existing?.needs ?: "") }
    var commitments by remember { mutableStateOf(existing?.commitments ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (party.isProspect) "Estoy prospectando" else "Estoy con el cliente") },
            navigationIcon = { TextButton(onClick = back) { Text("<") } }
        )

        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    party.businessName.ifBlank { party.name },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Puedes escribir o pulsar Voz para dictar cada campo.")
            }

            item {
                VoiceField(
                    label = "Resumen de la conversación",
                    value = conversation,
                    onValueChange = { conversation = it },
                    appendVoice = true,
                    minLines = 3
                )
            }

            item {
                VoiceField(
                    label = "Necesidades / oportunidades",
                    value = needs,
                    onValueChange = { needs = it },
                    appendVoice = true,
                    minLines = 3
                )
            }

            item {
                VoiceField(
                    label = "Compromisos / próximos pasos",
                    value = commitments,
                    onValueChange = { commitments = it },
                    appendVoice = true,
                    minLines = 3
                )
            }

            item {
                VoiceField(
                    label = "Otras observaciones",
                    value = notes,
                    onValueChange = { notes = it },
                    appendVoice = true,
                    minLines = 3
                )
            }

            item {
                Button(
                    onClick = {
                        vm.saveVisitReport(
                            visitId = visitId,
                            clientId = clientId,
                            conversationSummary = conversation.trim(),
                            needs = needs.trim(),
                            commitments = commitments.trim(),
                            notes = notes.trim(),
                            convertProspect = false
                        )
                        saved()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (party.isProspect) "Guardar prospección" else "Guardar visita y marcar realizada")
                }
            }

            if (party.isProspect) {
                item {
                    OutlinedButton(
                        onClick = {
                            vm.saveVisitReport(
                                visitId = visitId,
                                clientId = clientId,
                                conversationSummary = conversation.trim(),
                                needs = needs.trim(),
                                commitments = commitments.trim(),
                                notes = notes.trim(),
                                convertProspect = true
                            )
                            saved()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar prospección conseguida y pasar a Clientes")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartyFormScreen(
    vm: AppViewModel,
    party: Client?,
    newIsProspect: Boolean,
    back: () -> Unit,
    saved: (Long) -> Unit
) {
    var name by remember(party?.id) { mutableStateOf(party?.name ?: "") }
    var business by remember(party?.id) { mutableStateOf(party?.businessName ?: "") }
    var contact by remember(party?.id) { mutableStateOf(party?.contactPerson ?: "") }
    var phone by remember(party?.id) { mutableStateOf(party?.phone ?: "") }
    var email by remember(party?.id) { mutableStateOf(party?.email ?: "") }
    var address by remember(party?.id) { mutableStateOf(party?.address ?: "") }
    var city by remember(party?.id) { mutableStateOf(party?.city ?: "") }
    var postal by remember(party?.id) { mutableStateOf(party?.postalCode ?: "") }
    var notes by remember(party?.id) { mutableStateOf(party?.observations ?: "") }

    val editing = party != null
    val isProspect = party?.isProspect ?: newIsProspect
    val noun = if (isProspect) "prospección" else "cliente"

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (editing) "Editar $noun"
                    else if (isProspect) "Nueva prospección" else "Nuevo cliente"
                )
            },
            navigationIcon = { TextButton(onClick = back) { Text("<") } }
        )

        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { VoiceField("Nombre *", name, { name = it }) }
            item { VoiceField("Empresa / establecimiento", business, { business = it }) }
            item { VoiceField("Persona de contacto", contact, { contact = it }) }
            item { VoiceField("Teléfono", phone, { phone = it }, keyboardType = KeyboardType.Phone) }
            item { VoiceField("Email", email, { email = it }, keyboardType = KeyboardType.Email) }
            item { VoiceField("Dirección", address, { address = it }) }
            item { VoiceField("Ciudad", city, { city = it }) }
            item { VoiceField("Código postal", postal, { postal = it }, keyboardType = KeyboardType.Number) }
            item {
                VoiceField(
                    label = "Observaciones",
                    value = notes,
                    onValueChange = { notes = it },
                    appendVoice = true,
                    minLines = 3
                )
            }

            item {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (editing) {
                            vm.updateClient(
                                id = party.id,
                                name = name.trim(),
                                business = business.trim(),
                                contact = contact.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                city = city.trim(),
                                postal = postal.trim(),
                                notes = notes.trim()
                            )
                            saved(party.id)
                        } else {
                            saved(
                                vm.addClient(
                                    name = name.trim(),
                                    business = business.trim(),
                                    contact = contact.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    address = address.trim(),
                                    city = city.trim(),
                                    postal = postal.trim(),
                                    notes = notes.trim(),
                                    isProspect = isProspect
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editing) "Guardar cambios" else "Guardar $noun")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitScheduleScreen(vm: AppViewModel, clientId: Long, done: () -> Unit) {
    val context = LocalContext.current
    val party = remember(clientId, vm.clients, vm.prospects) { vm.client(clientId) }
    val initial = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    var scheduledAt by remember { mutableStateOf(initial.timeInMillis) }
    var purpose by remember {
        mutableStateOf(if (party?.isProspect == true) "Prospección" else "Visita comercial")
    }
    var notes by remember { mutableStateOf("") }

    fun chooseDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAt }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = scheduledAt
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                scheduledAt = updated.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun chooseTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAt }

        TimePickerDialog(
            context,
            { _, hour, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = scheduledAt
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                scheduledAt = updated.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (party?.isProspect == true) "Programar prospección" else "Programar visita") },
            navigationIcon = { TextButton(onClick = done) { Text("<") } }
        )

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Fecha y hora elegidas", fontWeight = FontWeight.Bold)
            Text(dateTime(scheduledAt), style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { chooseDate() }, modifier = Modifier.weight(1f)) {
                    Text("Elegir fecha")
                }
                OutlinedButton(onClick = { chooseTime() }, modifier = Modifier.weight(1f)) {
                    Text("Elegir hora")
                }
            }

            VoiceField("Motivo", purpose, { purpose = it })

            VoiceField(
                label = "Notas para preparar",
                value = notes,
                onValueChange = { notes = it },
                appendVoice = true,
                minLines = 3
            )

            Button(
                onClick = {
                    vm.addVisit(clientId, scheduledAt, purpose, notes)
                    done()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar cita")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderScreen(vm: AppViewModel, id: Long, done: () -> Unit) {
    val products = remember { vm.products() }
    val party = remember(id, vm.clients, vm.prospects) { vm.client(id) }
    val qty = remember { mutableStateMapOf<Long, Int>() }
    val total = products.sumOf { it.price * (qty[it.id] ?: 0) }

    Column {
        TopAppBar(
            title = { Text("Nuevo pedido") },
            navigationIcon = { TextButton(onClick = done) { Text("<") } }
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (party?.isProspect == true) {
                item {
                    InfoCard {
                        Text(
                            "Al guardar este pedido, la prospección pasará automáticamente a Clientes.",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(products, key = { it.id }) { product ->
                InfoCard {
                    Text(product.name, fontWeight = FontWeight.Bold)
                    Text(product.sku + " · " + money(product.price))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                qty[product.id] = ((qty[product.id] ?: 0) - 1).coerceAtLeast(0)
                            }
                        ) { Text("-") }

                        Text((qty[product.id] ?: 0).toString(), Modifier.padding(top = 12.dp))

                        OutlinedButton(
                            onClick = {
                                qty[product.id] = (qty[product.id] ?: 0) + 1
                            }
                        ) { Text("+") }
                    }
                }
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Total: " + money(total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        enabled = total > 0,
                        onClick = {
                            vm.addOrder(id, qty.toMap())
                            done()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar pedido")
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    appendVoice: Boolean = false,
    minLines: Int = 1
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()

            if (spoken.isNotBlank()) {
                onValueChange(
                    if (appendVoice && value.isNotBlank()) value.trimEnd() + " " + spoken
                    else spoken
                )
            }
        }
    }

    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora")
        }

        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No hay un servicio de reconocimiento de voz disponible.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = startVoice) { Text("Voz") }
        }
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private fun weekStart(offset: Int): Long {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        add(Calendar.WEEK_OF_YEAR, offset)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun dayStart(weekStart: Long, dayIndex: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = weekStart
        add(Calendar.DAY_OF_YEAR, dayIndex)
    }.timeInMillis

private fun dayLabel(value: Long): String =
    SimpleDateFormat("EEE d MMM", Locale("es", "ES")).format(Date(value))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() }

private fun weekRangeLabel(start: Long): String {
    val end = dayStart(start, 6)
    return SimpleDateFormat("d MMM", Locale("es", "ES")).format(Date(start)) +
        " – " +
        SimpleDateFormat("d MMM yyyy", Locale("es", "ES")).format(Date(end))
}

private fun timeOnly(value: Long) =
    SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date(value))

private fun dateTime(value: Long) =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

private fun date(value: Long) =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(value))

private val ProspectBlue = Color(0xFF1565C0)
private val ProspectBlueSoft = Color(0xFFE3F2FD)
private val ClientGreen = Color(0xFF2E7D32)
private val ClientGreenSoft = Color(0xFFE8F5E9)

private fun partyAccentColor(isProspect: Boolean) =
    if (isProspect) ProspectBlue else ClientGreen

private fun partyContainerColor(isProspect: Boolean) =
    if (isProspect) ProspectBlueSoft else ClientGreenSoft

private fun money(value: Double) =
    NumberFormat.getCurrencyInstance(Locale("es", "ES")).format(value)
