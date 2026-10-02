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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class Screen {
    data object Agenda : Screen()
    data object Clients : Screen()
    data object NewClient : Screen()
    data class ClientDetail(val id: Long) : Screen()
    data class EditClient(val id: Long) : Screen()
    data class NewVisit(val id: Long) : Screen()
    data class NewOrder(val id: Long) : Screen()
    data class BeforeVisit(val clientId: Long, val visitId: Long?) : Screen()
    data class InVisit(val clientId: Long, val visitId: Long?) : Screen()
}

@Composable
fun CrmApp(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Agenda) }

    MaterialTheme {
        val root = screen is Screen.Agenda || screen is Screen.Clients

        Scaffold(
            bottomBar = {
                if (root) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = screen is Screen.Agenda,
                            onClick = {
                                screen = Screen.Agenda
                                vm.refresh()
                            },
                            icon = { Text("A") },
                            label = { Text("Agenda") }
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Clients,
                            onClick = {
                                screen = Screen.Clients
                                vm.refresh()
                            },
                            icon = { Text("C") },
                            label = { Text("Clientes") }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (val s = screen) {
                    Screen.Agenda -> AgendaScreen(vm) { clientId, visitId ->
                        screen = Screen.BeforeVisit(clientId, visitId)
                    }

                    Screen.Clients -> ClientsScreen(
                        vm = vm,
                        open = { screen = Screen.ClientDetail(it) },
                        add = { screen = Screen.NewClient }
                    )

                    Screen.NewClient -> ClientFormScreen(
                        vm = vm,
                        client = null,
                        back = { screen = Screen.Clients },
                        saved = { screen = Screen.ClientDetail(it) }
                    )

                    is Screen.ClientDetail -> ClientDetailScreen(
                        vm = vm,
                        id = s.id,
                        back = { screen = Screen.Clients },
                        edit = { screen = Screen.EditClient(s.id) },
                        before = { screen = Screen.BeforeVisit(s.id, null) },
                        inVisit = { screen = Screen.InVisit(s.id, null) },
                        visit = { screen = Screen.NewVisit(s.id) },
                        order = { screen = Screen.NewOrder(s.id) }
                    )

                    is Screen.EditClient -> ClientFormScreen(
                        vm = vm,
                        client = vm.client(s.id),
                        back = { screen = Screen.ClientDetail(s.id) },
                        saved = { screen = Screen.ClientDetail(it) }
                    )

                    is Screen.NewVisit -> VisitScheduleScreen(vm, s.id) {
                        screen = Screen.ClientDetail(s.id)
                    }

                    is Screen.NewOrder -> OrderScreen(vm, s.id) {
                        screen = Screen.ClientDetail(s.id)
                    }

                    is Screen.BeforeVisit -> BeforeVisitScreen(
                        vm = vm,
                        clientId = s.clientId,
                        visitId = s.visitId,
                        back = { screen = Screen.Agenda },
                        openClient = { screen = Screen.ClientDetail(s.clientId) },
                        startVisit = { screen = Screen.InVisit(s.clientId, s.visitId) }
                    )

                    is Screen.InVisit -> InVisitScreen(
                        vm = vm,
                        clientId = s.clientId,
                        visitId = s.visitId,
                        back = { screen = Screen.BeforeVisit(s.clientId, s.visitId) },
                        saved = { screen = Screen.ClientDetail(s.clientId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaScreen(vm: AppViewModel, open: (Long, Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Agenda comercial") })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Próximas visitas", style = MaterialTheme.typography.titleMedium)
                Text("Toca una cita para ver el resumen antes de entrar.")
            }

            items(vm.agenda, key = { it.id }) { visit ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(visit.clientId, visit.id) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(dateTime(visit.scheduledAt), fontWeight = FontWeight.Bold)
                        Text(visit.clientName, style = MaterialTheme.typography.titleMedium)
                        Text(visit.status + " · " + visit.purpose)
                        if (visit.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(visit.notes)
                        }
                    }
                }
            }

            if (vm.agenda.isEmpty()) {
                item { Text("No tienes visitas programadas en los próximos 30 días.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientsScreen(vm: AppViewModel, open: (Long) -> Unit, add: () -> Unit) {
    var query by remember { mutableStateOf("") }

    val list = vm.clients.filter {
        query.isBlank() || listOf(it.name, it.businessName, it.city, it.phone).any { value ->
            value.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Clientes") },
            actions = {
                TextButton(onClick = add) { Text("+ Nuevo") }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar cliente") },
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.id }) { client ->
                Card(Modifier.fillMaxWidth().clickable { open(client.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(client.businessName.ifBlank { client.name }, fontWeight = FontWeight.Bold)
                        if (client.businessName.isNotBlank()) Text(client.name)
                        Text(listOf(client.address, client.city).filter(String::isNotBlank).joinToString(", "))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientDetailScreen(
    vm: AppViewModel,
    id: Long,
    back: () -> Unit,
    edit: () -> Unit,
    before: () -> Unit,
    inVisit: () -> Unit,
    visit: () -> Unit,
    order: () -> Unit
) {
    var reload by remember { mutableIntStateOf(0) }
    val client = remember(id, reload) { vm.client(id) } ?: return
    val visits = remember(id, reload) { vm.visits(id) }
    val orders = remember(id, reload) { vm.orders(id) }
    val consumption = remember(id, reload) { vm.consumption(id) }
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(
                title = { Text(client.businessName.ifBlank { client.name }) },
                navigationIcon = {
                    TextButton(onClick = back) { Text("<") }
                },
                actions = {
                    TextButton(onClick = edit) { Text("Editar") }
                }
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (client.businessName.isNotBlank()) {
                    Text(client.name, style = MaterialTheme.typography.titleMedium)
                }

                Text(listOf(client.address, client.postalCode, client.city).filter(String::isNotBlank).joinToString(", "))

                if (client.contactPerson.isNotBlank()) Text("Contacto: " + client.contactPerson)
                if (client.phone.isNotBlank()) Text("Tel: " + client.phone)
                if (client.email.isNotBlank()) Text(client.email)

                if (client.observations.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Observaciones del cliente", fontWeight = FontWeight.Bold)
                    Text(client.observations)
                }

                Spacer(Modifier.height(6.dp))

                Button(onClick = before, modifier = Modifier.fillMaxWidth()) {
                    Text("Antes de entrar")
                }

                Button(onClick = inVisit, modifier = Modifier.fillMaxWidth()) {
                    Text("Estoy con el cliente")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = order, modifier = Modifier.weight(1f)) {
                        Text("Nuevo pedido")
                    }
                    Button(onClick = visit, modifier = Modifier.weight(1f)) {
                        Text("Nueva cita")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + client.phone))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Llamar")
                        }
                    }

                    if (client.address.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val place = Uri.encode(client.address + " " + client.city)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + place))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mapa")
                        }
                    }
                }
            }
        }

        item { Heading("Productos que consume") }

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

        item { Heading("Historial de visitas") }

        if (visits.isEmpty()) {
            item { Text("Sin visitas registradas.", Modifier.padding(horizontal = 16.dp)) }
        }

        items(visits, key = { it.id }) { item ->
            InfoCard {
                Text(dateTime(item.scheduledAt), fontWeight = FontWeight.Bold)
                Text(item.status + " · " + item.purpose)

                if (item.conversationSummary.isNotBlank()) {
                    Text("Resumen: " + item.conversationSummary)
                }
                if (item.needs.isNotBlank()) {
                    Text("Necesidades: " + item.needs)
                }
                if (item.commitments.isNotBlank()) {
                    Text("Compromisos: " + item.commitments)
                }
                if (item.notes.isNotBlank()) {
                    Text("Notas: " + item.notes)
                }

                if (item.status != "REALIZADA") {
                    TextButton(
                        onClick = {
                            vm.completeVisit(item.id)
                            reload++
                        }
                    ) {
                        Text("Marcar realizada")
                    }
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
    openClient: () -> Unit,
    startVisit: () -> Unit
) {
    val client = remember(clientId) { vm.client(clientId) } ?: return
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
                title = { Text("Antes de entrar") },
                navigationIcon = { TextButton(onClick = back) { Text("<") } },
                actions = { TextButton(onClick = openClient) { Text("Ficha") } }
            )
        }

        item {
            InfoCard {
                Text(client.businessName.ifBlank { client.name }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (client.businessName.isNotBlank()) Text(client.name)
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
                if (client.contactPerson.isNotBlank()) Text("Contacto: " + client.contactPerson)
                if (client.phone.isNotBlank()) Text("Teléfono: " + client.phone)
                Text("Dirección: " + listOf(client.address, client.postalCode, client.city).filter(String::isNotBlank).joinToString(", "))
                if (client.observations.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Observaciones", fontWeight = FontWeight.Bold)
                    Text(client.observations)
                }
            }
        }

        item { Heading("Lo que consume") }

        if (consumption.isEmpty()) {
            item {
                InfoCard { Text("Todavía no hay productos consumidos registrados.") }
            }
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

        item { Heading("Última visita realizada") }

        item {
            InfoCard {
                if (previousVisit == null) {
                    Text("No hay una visita anterior registrada.")
                } else {
                    Text(dateTime(previousVisit.scheduledAt), fontWeight = FontWeight.Bold)
                    if (previousVisit.conversationSummary.isNotBlank()) {
                        Text("Resumen: " + previousVisit.conversationSummary)
                    }
                    if (previousVisit.needs.isNotBlank()) {
                        Text("Necesidades: " + previousVisit.needs)
                    }
                    if (previousVisit.commitments.isNotBlank()) {
                        Text("Compromisos: " + previousVisit.commitments)
                    }
                    if (previousVisit.notes.isNotBlank()) {
                        Text("Notas: " + previousVisit.notes)
                    }
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = startVisit, modifier = Modifier.fillMaxWidth()) {
                    Text("Estoy con el cliente")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + client.phone))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Llamar")
                        }
                    }

                    if (client.address.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val place = Uri.encode(client.address + " " + client.city)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + place))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mapa")
                        }
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
    val client = remember(clientId) { vm.client(clientId) } ?: return
    val existing = remember(visitId) { visitId?.let { vm.visit(it) } }

    var conversation by remember { mutableStateOf(existing?.conversationSummary ?: "") }
    var needs by remember { mutableStateOf(existing?.needs ?: "") }
    var commitments by remember { mutableStateOf(existing?.commitments ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Estoy con el cliente") },
            navigationIcon = { TextButton(onClick = back) { Text("<") } }
        )

        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(client.businessName.ifBlank { client.name }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                            notes = notes.trim()
                        )
                        saved()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar visita y marcar realizada")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientFormScreen(
    vm: AppViewModel,
    client: Client?,
    back: () -> Unit,
    saved: (Long) -> Unit
) {
    var name by remember(client?.id) { mutableStateOf(client?.name ?: "") }
    var business by remember(client?.id) { mutableStateOf(client?.businessName ?: "") }
    var contact by remember(client?.id) { mutableStateOf(client?.contactPerson ?: "") }
    var phone by remember(client?.id) { mutableStateOf(client?.phone ?: "") }
    var email by remember(client?.id) { mutableStateOf(client?.email ?: "") }
    var address by remember(client?.id) { mutableStateOf(client?.address ?: "") }
    var city by remember(client?.id) { mutableStateOf(client?.city ?: "") }
    var postal by remember(client?.id) { mutableStateOf(client?.postalCode ?: "") }
    var notes by remember(client?.id) { mutableStateOf(client?.observations ?: "") }

    val editing = client != null

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (editing) "Editar cliente" else "Nuevo cliente") },
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
                                id = client.id,
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
                            saved(client.id)
                        } else {
                            saved(
                                vm.addClient(
                                    name.trim(),
                                    business.trim(),
                                    contact.trim(),
                                    phone.trim(),
                                    email.trim(),
                                    address.trim(),
                                    city.trim(),
                                    postal.trim(),
                                    notes.trim()
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editing) "Guardar cambios" else "Guardar cliente")
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
    val initial = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    var scheduledAt by remember { mutableStateOf(initial.timeInMillis) }
    var purpose by remember { mutableStateOf("Visita comercial") }
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
            title = { Text("Programar visita") },
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

            VoiceField(
                label = "Motivo",
                value = purpose,
                onValueChange = { purpose = it }
            )

            VoiceField(
                label = "Notas para preparar la visita",
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
    val qty = remember { mutableStateMapOf<Long, Int>() }
    val total = products.sumOf { it.price * (qty[it.id] ?: 0) }

    Column {
        TopAppBar(
            title = { Text("Nuevo pedido") },
            navigationIcon = { TextButton(onClick = done) { Text("<") } }
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { product ->
                InfoCard {
                    Text(product.name, fontWeight = FontWeight.Bold)
                    Text(product.sku + " · " + money(product.price))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                qty[product.id] = ((qty[product.id] ?: 0) - 1).coerceAtLeast(0)
                            }
                        ) {
                            Text("-")
                        }

                        Text((qty[product.id] ?: 0).toString(), Modifier.padding(top = 12.dp))

                        OutlinedButton(
                            onClick = {
                                qty[product.id] = (qty[product.id] ?: 0) + 1
                            }
                        ) {
                            Text("+")
                        }
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
                val newValue = if (appendVoice && value.isNotBlank()) {
                    value.trimEnd() + " " + spoken
                } else {
                    spoken
                }
                onValueChange(newValue)
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
            TextButton(onClick = startVoice) {
                Text("Voz")
            }
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

private fun dateTime(value: Long) =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

private fun date(value: Long) =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(value))

private fun money(value: Double) =
    NumberFormat.getCurrencyInstance(Locale("es", "ES")).format(value)
