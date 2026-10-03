package com.agendacomercial.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed class Screen {
    data object Agenda : Screen()
    data object Clients : Screen()
    data object Prospects : Screen()
    data object Summary : Screen()
    data class NewParty(val prospect: Boolean) : Screen()
    data class Detail(val id: Long) : Screen()
    data class EditParty(val id: Long) : Screen()
    data class NewVisit(val id: Long) : Screen()
    data class NewOrder(val id: Long) : Screen()
    data class BeforeVisit(val clientId: Long, val visitId: Long?) : Screen()
    data class InVisit(val clientId: Long, val visitId: Long?) : Screen()
    data class AfterVisit(val clientId: Long) : Screen()
    data class Suggestions(val clientId: Long) : Screen()
}

@Composable
fun CrmApp(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Agenda) }

    MaterialTheme(colorScheme = CorporateDarkColors) {
        val root =
            screen is Screen.Agenda ||
            screen is Screen.Clients ||
            screen is Screen.Prospects ||
            screen is Screen.Summary

        Scaffold(
            containerColor = Color.Black,
            bottomBar = {
                if (root) {
                    NavigationBar(containerColor = Color.Black) {
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
                            icon = { Text("C", color = ClientGreen) },
                            label = { Text("Clientes", color = ClientGreen) }
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Prospects,
                            onClick = {
                                vm.refresh()
                                screen = Screen.Prospects
                            },
                            icon = { Text("P", color = ProspectBlue) },
                            label = { Text("Prospecciones", color = ProspectBlue) }
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Summary,
                            onClick = {
                                vm.refresh()
                                screen = Screen.Summary
                            },
                            icon = { Text("R") },
                            label = { Text("Resumen") }
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

                    Screen.Summary -> DailySummaryScreen(vm)

                    is Screen.NewParty -> PartyFormScreen(
                        vm = vm,
                        party = null,
                        newIsProspect = s.prospect,
                        back = {
                            screen = if (s.prospect) Screen.Prospects else Screen.Clients
                        },
                        saved = { id ->
                            screen =
                                if (s.prospect) Screen.AfterVisit(id)
                                else Screen.Detail(id)
                        }
                    )

                    is Screen.Detail -> PartyDetailScreen(
                        vm = vm,
                        id = s.id,
                        back = {
                            screen =
                                if (vm.client(s.id)?.isProspect == true) Screen.Prospects
                                else Screen.Clients
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
                        saved = { screen = Screen.AfterVisit(s.clientId) }
                    )

                    is Screen.AfterVisit -> AfterVisitScreen(
                        vm = vm,
                        clientId = s.clientId,
                        suggestions = { screen = Screen.Suggestions(s.clientId) },
                        openParty = { screen = Screen.Detail(s.clientId) },
                        agenda = { screen = Screen.Agenda }
                    )

                    is Screen.Suggestions -> SuggestionsScreen(
                        vm = vm,
                        clientId = s.clientId,
                        back = { screen = Screen.AfterVisit(s.clientId) },
                        openClient = { screen = Screen.Detail(it) },
                        agenda = { screen = Screen.Agenda }
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
    val end = remember(start) { dayStart(start, 7) }
    val visits = vm.agenda.filter { it.scheduledAt in start until end }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Agenda semanal",
                    color = CorporateGold,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                val context = LocalContext.current
                val agendaLogo = remember {
                    runCatching {
                        BitmapFactory.decodeResource(
                            context.resources,
                            R.drawable.app_logo
                        )?.asImageBitmap()
                    }.getOrNull()
                }

                if (agendaLogo != null) {
                    Image(
                        bitmap = agendaLogo,
                        contentDescription = "Logo Pà Solà",
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(46.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "PA SOLÀ",
                        color = CorporateGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { weekOffset-- }) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CorporateGold
                )
            }

            Text(
                weekRangeLabel(start),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CorporateGold,
                modifier = Modifier.padding(top = 12.dp)
            )

            IconButton(onClick = { weekOffset++ }) {
                Text(
                    "›",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CorporateGold
                )
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
                                contentColor = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { open(visit.clientId, visit.id) }
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        timeOnly(visit.scheduledAt),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(visit.clientName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (visit.isProspect) "Prospección" else "Cliente",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(visit.purpose, style = MaterialTheme.typography.bodySmall)

                                    if (visit.status == "REALIZADA") {
                                        Text("Realizada", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (visits.isEmpty()) {
            Text(
                "No hay visitas programadas esta semana.",
                modifier = Modifier.padding(16.dp)
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
        query.isBlank() ||
            listOf(it.name, it.businessName, it.contactPerson, it.phone, it.nif).any { value ->
                value.contains(query, ignoreCase = true)
            }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("PA SOLÀ", color = CorporateGold, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.labelLarge)
                }
            },
            actions = { TextButton(onClick = onAdd) { Text(addLabel) } }
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
                        containerColor = partyContainerColor(party.isProspect),
                        contentColor = Color.White
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            party.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Contacto: " + party.contactPerson.ifBlank { "Sin contacto" }
                        )
                        Text(
                            "Teléfono: " + party.phone.ifBlank { "Sin teléfono" }
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
    var photoVersion by remember { mutableIntStateOf(0) }
    var pendingPhotoPath by remember(id) { mutableStateOf<String?>(null) }

    val party = remember(id, reload, vm.clients, vm.prospects) { vm.client(id) } ?: return
    val visits = remember(id, reload) { vm.visits(id) }
    val orders = remember(id, reload) { vm.orders(id) }
    val consumption = remember(id, reload) { vm.consumption(id) }
    val context = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingPhotoPath
        if (success && path != null) {
            vm.updatePhoto(id, path)
            photoVersion++
            reload++
        } else if (path != null) {
            File(path).delete()
        }
        pendingPhotoPath = null
    }

    fun takePhoto() {
        val file = createClientPhotoFile(context, id)
        pendingPhotoPath = file.absolutePath
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        photoLauncher.launch(uri)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(
                title = {
                    Text(
                        party.name,
                        color = partyAccentColor(party.isProspect)
                    )
                },
                navigationIcon = { TextButton(onClick = back) { Text("<") } },
                actions = { TextButton(onClick = edit) { Text("Editar") } }
            )
        }

        item {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                LocalPhotoBox(
                    photoPath = party.photoPath,
                    version = photoVersion,
                    isProspect = party.isProspect,
                    onClick = { takePhoto() }
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (party.isProspect) "Prospección" else "Cliente",
                        color = partyAccentColor(party.isProspect),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        party.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (party.businessName.isNotBlank()) {
                        Text("Empresa: " + party.businessName)
                    }
                    if (party.nif.isNotBlank()) {
                        Text("NIF: " + party.nif)
                    }
                    if (party.bankAccount.isNotBlank()) {
                        Text("Cuenta: " + party.bankAccount)
                    }
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
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

        if (!party.isProspect) {
            item { Heading("Productos que consume") }

            if (consumption.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay consumo registrado.",
                        Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            items(consumption) { item ->
                InfoCard {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text("Unidades históricas: " + item.units)
                    Text(
                        "Último pedido: " +
                            date(item.lastOrderedAt) +
                            " · " +
                            money(item.lastPrice)
                    )
                }
            }

            item { Heading("Últimos pedidos") }

            if (orders.isEmpty()) {
                item {
                    Text(
                        "Sin pedidos todavía.",
                        Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            items(orders, key = { it.id }) { item ->
                InfoCard {
                    Text(
                        date(item.createdAt) + " · " + money(item.total),
                        fontWeight = FontWeight.Bold
                    )
                    Text(item.status)
                }
            }
        }

        item {
            Heading(
                if (party.isProspect) "Historial de prospecciones"
                else "Historial de visitas"
            )
        }

        if (visits.isEmpty()) {
            item {
                Text(
                    "Sin actividad registrada.",
                    Modifier.padding(horizontal = 16.dp)
                )
            }
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
                    Text("Próximos pasos: " + item.commitments)
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
                    ) { Text("Marcar realizada") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailySummaryScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val range = remember { todayRange() }
    val visits = remember(vm.clients, vm.prospects, vm.agenda) {
        vm.dailyVisits(range.first, range.second)
    }
    val orders = remember(vm.clients, vm.prospects, vm.agenda) {
        vm.dailyOrders(range.first, range.second)
    }

    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            val ok = DailyVisitsXlsxExporter.write(
                context = context,
                uri = uri,
                visits = visits,
                dayStart = range.first
            )

            Toast.makeText(
                context,
                if (ok) "Excel guardado correctamente" else "No se ha podido crear el Excel",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val prospections = visits.filter { it.isProspection }
    val clientVisits = visits.filterNot { it.isProspection }
    val orderTotal = orders.sumOf { it.total }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("PA SOLÀ", color = CorporateGold, fontWeight = FontWeight.Bold)
                    Text("Resumen del día", style = MaterialTheme.typography.labelLarge)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    fullDate(range.first),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetric(
                        title = "Prospecciones",
                        value = prospections.size.toString(),
                        color = ProspectBlueSoft,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        title = "Visitas",
                        value = clientVisits.size.toString(),
                        color = ClientGreenSoft,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        title = "Pedidos",
                        value = orders.size.toString(),
                        color = ClientGreenSoft,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text("Venta del día", fontWeight = FontWeight.Bold)
                        Text(
                            money(orderTotal),
                            style = MaterialTheme.typography.headlineSmall,
                            color = ClientGreen
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val fileDate = SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale("es", "ES")
                        ).format(Date(range.first))

                        excelLauncher.launch("Visitas_$fileDate.xlsx")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exportar Excel de visitas")
                }
            }

            item { HeadingNoPadding("Prospecciones realizadas") }

            if (prospections.isEmpty()) {
                item { Text("No hay prospecciones realizadas hoy.") }
            }

            items(prospections, key = { "p" + it.id }) { activity ->
                ActivityCard(
                    activity = activity,
                    isProspection = true
                )
            }

            item { HeadingNoPadding("Visitas a clientes") }

            if (clientVisits.isEmpty()) {
                item { Text("No hay visitas a clientes realizadas hoy.") }
            }

            items(clientVisits, key = { "v" + it.id }) { activity ->
                ActivityCard(
                    activity = activity,
                    isProspection = false
                )
            }

            item { HeadingNoPadding("Pedidos del día") }

            if (orders.isEmpty()) {
                item { Text("No hay pedidos creados hoy.") }
            }

            items(orders, key = { "o" + it.id }) { order ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ClientGreenSoft,
                        contentColor = Color.White
                    )
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            timeOnly(order.createdAt) + " · " + order.localName,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (order.contactPerson.isNotBlank()) {
                            Text("Contacto: " + order.contactPerson)
                        }
                        if (order.phone.isNotBlank()) {
                            Text("Teléfono: " + order.phone)
                        }
                        Text(
                            "Pedido: " + money(order.total),
                            fontWeight = FontWeight.Bold
                        )
                        Text(order.status)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SummaryMetric(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color,
            contentColor = Color.White
        )
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActivityCard(
    activity: DailyVisitActivity,
    isProspection: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = partyContainerColor(isProspection),
            contentColor = Color.White
        )
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                timeOnly(activity.completedAt) + " · " + activity.localName,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (activity.contactPerson.isNotBlank()) {
                Text("Contacto: " + activity.contactPerson)
            }
            if (activity.phone.isNotBlank()) {
                Text("Teléfono: " + activity.phone)
            }
            Text("Motivo: " + activity.purpose)
            if (activity.conversationSummary.isNotBlank()) {
                Text("Resumen: " + activity.conversationSummary)
            }
            if (activity.needs.isNotBlank()) {
                Text("Necesidades: " + activity.needs)
            }
            if (activity.commitments.isNotBlank()) {
                Text("Próximos pasos: " + activity.commitments)
            }
            if (activity.notes.isNotBlank()) {
                Text("Notas: " + activity.notes)
            }
        }
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
    val party = remember(clientId, vm.clients, vm.prospects) {
        vm.client(clientId)
    } ?: return

    val appointment = remember(visitId) { visitId?.let { vm.visit(it) } }
    val visits = remember(clientId) { vm.visits(clientId) }
    val orders = remember(clientId) { vm.orders(clientId) }
    val consumption = remember(clientId) { vm.consumption(clientId) }
    val previousVisit = visits.firstOrNull {
        it.status == "REALIZADA" && it.id != visitId
    }
    val lastOrder = orders.firstOrNull()
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(
                title = {
                    Text(
                        if (party.isProspect) "Antes de prospectar" else "Antes de entrar",
                        color = partyAccentColor(party.isProspect)
                    )
                },
                navigationIcon = { TextButton(onClick = back) { Text("<") } },
                actions = { TextButton(onClick = openParty) { Text("Ficha") } }
            )
        }

        item {
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = partyContainerColor(party.isProspect),
                    contentColor = Color.White
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        party.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (party.businessName.isNotBlank()) {
                        Text("Empresa: " + party.businessName)
                    }
                    if (party.nif.isNotBlank()) {
                        Text("NIF: " + party.nif)
                    }
                    if (appointment != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cita: " + dateTime(appointment.scheduledAt),
                            fontWeight = FontWeight.Bold
                        )
                        Text("Motivo: " + appointment.purpose)
                        if (appointment.notes.isNotBlank()) {
                            Text("Preparación: " + appointment.notes)
                        }
                    }
                }
            }
        }

        item {
            Heading("Datos rápidos")
            InfoCard {
                if (party.contactPerson.isNotBlank()) {
                    Text("Contacto: " + party.contactPerson)
                }
                if (party.phone.isNotBlank()) {
                    Text("Teléfono: " + party.phone)
                }
                Text(
                    "Dirección: " +
                        listOf(party.address, party.postalCode, party.city)
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
                item {
                    InfoCard {
                        Text("Todavía no hay productos consumidos registrados.")
                    }
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
                        Text(
                            date(lastOrder.createdAt) + " · " + money(lastOrder.total),
                            fontWeight = FontWeight.Bold
                        )
                        Text(lastOrder.status)
                    }
                }
            }
        }

        item {
            Heading(
                if (party.isProspect) "Última prospección"
                else "Última visita realizada"
            )
        }

        item {
            InfoCard {
                if (previousVisit == null) {
                    Text("No hay actividad anterior registrada.")
                } else {
                    Text(
                        dateTime(previousVisit.scheduledAt),
                        fontWeight = FontWeight.Bold
                    )
                    if (previousVisit.conversationSummary.isNotBlank()) {
                        Text("Resumen: " + previousVisit.conversationSummary)
                    }
                    if (previousVisit.needs.isNotBlank()) {
                        Text("Necesidades: " + previousVisit.needs)
                    }
                    if (previousVisit.commitments.isNotBlank()) {
                        Text("Próximos pasos: " + previousVisit.commitments)
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
                    Text(
                        if (party.isProspect) "Empezar prospección"
                        else "Estoy con el cliente"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (party.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:" + party.phone)
                                    )
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
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("geo:0,0?q=" + place)
                                    )
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

private data class NearbyClientSuggestion(
    val client: Client,
    val distanceKm: Float?
)

private data class ProspectSearch(
    val title: String,
    val query: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AfterVisitScreen(
    vm: AppViewModel,
    clientId: Long,
    suggestions: () -> Unit,
    openParty: () -> Unit,
    agenda: () -> Unit
) {
    val party = remember(clientId, vm.clients, vm.prospects) {
        vm.client(clientId)
    } ?: return

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Actividad guardada",
                    color = CorporateGold,
                    fontWeight = FontWeight.Bold
                )
            }
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = partyContainerColor(party.isProspect),
                    contentColor = Color.White
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        party.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (party.isProspect) "Prospección registrada"
                        else "Visita registrada"
                    )
                    val address = partyAddress(party)
                    if (address.isNotBlank()) {
                        Text(address)
                    }
                }
            }

            Text(
                "¿Quieres aprovechar que estás en esta zona?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = suggestions,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
            ) {
                Text("Sugerencias cerca")
            }

            Text(
                "Busca clientes que ya tienes próximos y posibles nuevas prospecciones de hostelería.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = openParty,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver a la ficha")
            }

            OutlinedButton(
                onClick = agenda,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver a Agenda")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionsScreen(
    vm: AppViewModel,
    clientId: Long,
    back: () -> Unit,
    openClient: (Long) -> Unit,
    agenda: () -> Unit
) {
    val context = LocalContext.current
    val source = remember(clientId, vm.clients, vm.prospects) {
        vm.client(clientId)
    } ?: return

    var nearbyClients by remember(clientId, vm.clients) {
        mutableStateOf<List<NearbyClientSuggestion>>(emptyList())
    }
    var loadingNearby by remember(clientId, vm.clients) {
        mutableStateOf(true)
    }

    LaunchedEffect(clientId, vm.clients) {
        loadingNearby = true
        nearbyClients = findNearbyClients(
            context = context,
            origin = source,
            clients = vm.clients
        )
        loadingNearby = false
    }

    val anchor = partyAddress(source).ifBlank {
        source.city.ifBlank { source.name }
    }

    val prospectSearches = remember(anchor) {
        listOf(
            ProspectSearch(
                title = "Restaurantes · menú ~20 €",
                query = "restaurantes menú 20 euros cocina de calidad",
                description = "Restaurantes de ticket medio y cocina cuidada."
            ),
            ProspectSearch(
                title = "Restauración media-alta",
                query = "restaurantes bistró gastronómico cocina de autor",
                description = "Bistrós y restaurantes con producto y presentación de nivel medio-alto."
            ),
            ProspectSearch(
                title = "Hamburgueserías gourmet",
                query = "hamburguesería gourmet",
                description = "Locales de hamburguesa premium donde encaje un pan de alta calidad."
            ),
            ProspectSearch(
                title = "Frankfurt gourmet",
                query = "frankfurt gourmet hot dog gourmet",
                description = "Frankfurts y hot dogs de concepto cuidado o premium."
            ),
            ProspectSearch(
                title = "Brunch y cafetería premium",
                query = "brunch cafetería premium restaurante",
                description = "Locales con bocadillos, tostadas y oferta gastronómica cuidada."
            ),
            ProspectSearch(
                title = "Pan premium / cocina de autor",
                query = "restaurante cocina de autor pan artesano premium",
                description = "Candidatos con potencial para consumir pan de muy alta calidad."
            )
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Sugerencias",
                        color = CorporateGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        source.name,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            navigationIcon = {
                TextButton(onClick = back) { Text("<") }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Zona de referencia",
                    color = CorporateGold,
                    fontWeight = FontWeight.Bold
                )
                Text(anchor)
            }

            item {
                HeadingNoPadding("Clientes tuyos cerca")
            }

            if (loadingNearby) {
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Calculando cercanía por las direcciones guardadas…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (nearbyClients.isEmpty()) {
                item {
                    Text(
                        "No he podido localizar otros clientes cercanos con las direcciones guardadas."
                    )
                }
            } else {
                items(nearbyClients, key = { "near_" + it.client.id }) { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openClient(suggestion.client.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = ClientGreenSoft,
                            contentColor = Color.White
                        )
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                suggestion.client.name,
                                fontWeight = FontWeight.Bold
                            )
                            if (suggestion.client.contactPerson.isNotBlank()) {
                                Text("Contacto: " + suggestion.client.contactPerson)
                            }
                            if (suggestion.client.phone.isNotBlank()) {
                                Text("Tel: " + suggestion.client.phone)
                            }
                            val distance = suggestion.distanceKm
                            Text(
                                if (distance != null) {
                                    String.format(
                                        Locale("es", "ES"),
                                        "%.1f km aprox.",
                                        distance
                                    )
                                } else {
                                    "Misma zona/ciudad"
                                },
                                color = ClientGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                HeadingNoPadding("Nuevas prospecciones")
                Text(
                    "Estas búsquedas se abren en Maps usando como referencia el local que acabas de visitar. Son candidatos comerciales; conviene comprobar carta, precio y encaje antes de visitarlos.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            items(prospectSearches, key = { it.title }) { search ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ProspectBlueSoft,
                        contentColor = Color.White
                    )
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            search.title,
                            fontWeight = FontWeight.Bold
                        )
                        Text(search.description)
                        Button(
                            onClick = {
                                openMapSearch(
                                    context = context,
                                    query = search.query,
                                    anchor = anchor
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Buscar cerca")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = agenda,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver a Agenda")
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private fun partyAddress(client: Client): String {
    return listOf(
        client.address,
        client.postalCode,
        client.city
    ).filter(String::isNotBlank).joinToString(", ")
}

@Suppress("DEPRECATION")
private suspend fun findNearbyClients(
    context: Context,
    origin: Client,
    clients: List<Client>
): List<NearbyClientSuggestion> = withContext(Dispatchers.IO) {
    val others = clients.filter { it.id != origin.id && !it.isProspect }

    if (others.isEmpty()) {
        return@withContext emptyList()
    }

    val fallback = others
        .filter {
            origin.city.isNotBlank() &&
                it.city.equals(origin.city, ignoreCase = true)
        }
        .take(6)
        .map { NearbyClientSuggestion(it, null) }

    val originQuery = partyAddress(origin)
    if (
        originQuery.isBlank() ||
        !Geocoder.isPresent()
    ) {
        return@withContext fallback
    }

    runCatching {
        val geocoder = Geocoder(context, Locale("es", "ES"))
        val originAddress = geocoder
            .getFromLocationName(originQuery, 1)
            ?.firstOrNull()
            ?: return@runCatching fallback

        val originLocation = Location("origin").apply {
            latitude = originAddress.latitude
            longitude = originAddress.longitude
        }

        others.mapNotNull { client ->
            val query = partyAddress(client)
            if (query.isBlank()) {
                return@mapNotNull null
            }

            val address = runCatching {
                geocoder.getFromLocationName(query, 1)?.firstOrNull()
            }.getOrNull() ?: return@mapNotNull null

            val target = Location("client").apply {
                latitude = address.latitude
                longitude = address.longitude
            }

            NearbyClientSuggestion(
                client = client,
                distanceKm = originLocation.distanceTo(target) / 1000f
            )
        }
            .sortedBy { it.distanceKm ?: Float.MAX_VALUE }
            .take(6)
            .ifEmpty { fallback }
    }.getOrElse {
        fallback
    }
}

private fun openMapSearch(
    context: Context,
    query: String,
    anchor: String
) {
    val fullQuery = "$query cerca de $anchor"
    val geoUri = Uri.parse(
        "geo:0,0?q=" + Uri.encode(fullQuery)
    )

    val mapIntent = Intent(
        Intent.ACTION_VIEW,
        geoUri
    )

    if (mapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapIntent)
        return
    }

    val webUri = Uri.parse(
        "https://www.google.com/search?q=" +
            Uri.encode(fullQuery)
    )
    context.startActivity(
        Intent(Intent.ACTION_VIEW, webUri)
    )
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
    val party = remember(clientId, vm.clients, vm.prospects) {
        vm.client(clientId)
    } ?: return

    val existing = remember(visitId) { visitId?.let { vm.visit(it) } }

    var conversation by remember {
        mutableStateOf(existing?.conversationSummary ?: "")
    }
    var needs by remember { mutableStateOf(existing?.needs ?: "") }
    var commitments by remember {
        mutableStateOf(existing?.commitments ?: "")
    }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (party.isProspect) "Estoy prospectando"
                    else "Estoy con el cliente",
                    color = partyAccentColor(party.isProspect)
                )
            },
            navigationIcon = { TextButton(onClick = back) { Text("<") } }
        )

        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    party.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = partyAccentColor(party.isProspect)
                )
                Text(
                    if (party.isProspect) "Prospección" else "Cliente",
                    color = partyAccentColor(party.isProspect),
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
                    Text(
                        if (party.isProspect) "Guardar prospección"
                        else "Guardar visita y marcar realizada"
                    )
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
    var business by remember(party?.id) {
        mutableStateOf(party?.businessName ?: "")
    }
    var nif by remember(party?.id) { mutableStateOf(party?.nif ?: "") }
    var bankAccount by remember(party?.id) {
        mutableStateOf(party?.bankAccount ?: "")
    }
    var contact by remember(party?.id) {
        mutableStateOf(party?.contactPerson ?: "")
    }
    var phone by remember(party?.id) { mutableStateOf(party?.phone ?: "") }
    var email by remember(party?.id) { mutableStateOf(party?.email ?: "") }
    var address by remember(party?.id) { mutableStateOf(party?.address ?: "") }
    var city by remember(party?.id) { mutableStateOf(party?.city ?: "") }
    var postal by remember(party?.id) { mutableStateOf(party?.postalCode ?: "") }
    var notes by remember(party?.id) {
        mutableStateOf(party?.observations ?: "")
    }

    val editing = party != null
    val isProspect = party?.isProspect ?: newIsProspect
    val noun = if (isProspect) "prospección" else "cliente"

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (editing) "Editar $noun"
                    else if (isProspect) "Nueva prospección"
                    else "Nuevo cliente",
                    color = partyAccentColor(isProspect)
                )
            },
            navigationIcon = { TextButton(onClick = back) { Text("<") } }
        )

        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                VoiceField(
                    "Nombre del local *",
                    name,
                    { name = it }
                )
            }
            item {
                VoiceField(
                    "Nombre de la empresa",
                    business,
                    { business = it }
                )
            }
            item { VoiceField("NIF", nif, { nif = it }) }
            item {
                VoiceField(
                    "Número de cuenta / IBAN",
                    bankAccount,
                    { bankAccount = it }
                )
            }
            item {
                VoiceField(
                    "Persona de contacto",
                    contact,
                    { contact = it }
                )
            }
            item {
                VoiceField(
                    "Teléfono",
                    phone,
                    { phone = it },
                    keyboardType = KeyboardType.Phone,
                    digitsOnlyVoice = true
                )
            }
            item {
                VoiceField(
                    "Email",
                    email,
                    { email = it },
                    keyboardType = KeyboardType.Email
                )
            }
            item {
                VoiceField(
                    "Dirección",
                    address,
                    { address = it }
                )
            }
            item {
                VoiceField(
                    "Ciudad",
                    city,
                    { city = it }
                )
            }
            item {
                VoiceField(
                    "Código postal",
                    postal,
                    { postal = it },
                    keyboardType = KeyboardType.Number,
                    digitsOnlyVoice = true
                )
            }
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
                                nif = nif.trim(),
                                bankAccount = bankAccount.trim(),
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
                                    nif = nif.trim(),
                                    bankAccount = bankAccount.trim(),
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
                    Text(
                        if (editing) "Guardar cambios"
                        else "Guardar $noun"
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitScheduleScreen(
    vm: AppViewModel,
    clientId: Long,
    done: () -> Unit
) {
    val context = LocalContext.current
    val party = remember(clientId, vm.clients, vm.prospects) {
        vm.client(clientId)
    }

    val initial = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    var scheduledAt by remember { mutableStateOf(initial.timeInMillis) }
    var purpose by remember {
        mutableStateOf(
            if (party?.isProspect == true) "Prospección"
            else "Visita comercial"
        )
    }
    var notes by remember { mutableStateOf("") }

    fun chooseDate() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = scheduledAt
        }

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
        val cal = Calendar.getInstance().apply {
            timeInMillis = scheduledAt
        }

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
            title = {
                Text(
                    if (party?.isProspect == true) "Programar prospección"
                    else "Programar visita",
                    color = partyAccentColor(party?.isProspect == true)
                )
            },
            navigationIcon = { TextButton(onClick = done) { Text("<") } }
        )

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Fecha y hora elegidas", fontWeight = FontWeight.Bold)
            Text(
                dateTime(scheduledAt),
                style = MaterialTheme.typography.titleLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { chooseDate() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Elegir fecha")
                }
                OutlinedButton(
                    onClick = { chooseTime() },
                    modifier = Modifier.weight(1f)
                ) {
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
private fun OrderScreen(
    vm: AppViewModel,
    id: Long,
    done: () -> Unit
) {
    val products = remember { vm.products() }
    val party = remember(id, vm.clients, vm.prospects) {
        vm.client(id)
    }
    val qty = remember { mutableStateMapOf<Long, Int>() }
    val total = products.sumOf { it.price * (qty[it.id] ?: 0) }

    Column {
        TopAppBar(
            title = {
                Text(
                    "Nuevo pedido",
                    color = partyAccentColor(party?.isProspect == true)
                )
            },
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
                                qty[product.id] =
                                    ((qty[product.id] ?: 0) - 1).coerceAtLeast(0)
                            }
                        ) { Text("-") }

                        Text(
                            (qty[product.id] ?: 0).toString(),
                            Modifier.padding(top = 12.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                qty[product.id] =
                                    (qty[product.id] ?: 0) + 1
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
    minLines: Int = 1,
    digitsOnlyVoice: Boolean = false
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
                val normalized = normalizeSpokenNumbers(spoken)
                val voiceValue = if (digitsOnlyVoice) {
                    normalized.filter { it.isDigit() }
                } else {
                    normalized
                }

                if (voiceValue.isNotBlank()) {
                    onValueChange(
                        if (appendVoice && value.isNotBlank()) {
                            value.trimEnd() + " " + voiceValue
                        } else {
                            voiceValue
                        }
                    )
                }
            }
        }
    }

    val startVoice = {
        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Habla ahora"
            )
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

private val SpanishDigits = mapOf(
    "cero" to "0",
    "un" to "1",
    "uno" to "1",
    "una" to "1",
    "dos" to "2",
    "tres" to "3",
    "cuatro" to "4",
    "cinco" to "5",
    "seis" to "6",
    "siete" to "7",
    "ocho" to "8",
    "nueve" to "9"
)

private val SpanishSmallNumbers = mapOf(
    "cero" to 0L,
    "un" to 1L,
    "uno" to 1L,
    "una" to 1L,
    "dos" to 2L,
    "tres" to 3L,
    "cuatro" to 4L,
    "cinco" to 5L,
    "seis" to 6L,
    "siete" to 7L,
    "ocho" to 8L,
    "nueve" to 9L,
    "diez" to 10L,
    "once" to 11L,
    "doce" to 12L,
    "trece" to 13L,
    "catorce" to 14L,
    "quince" to 15L,
    "dieciseis" to 16L,
    "diecisiete" to 17L,
    "dieciocho" to 18L,
    "diecinueve" to 19L,
    "veinte" to 20L,
    "veintiuno" to 21L,
    "veintiun" to 21L,
    "veintiuna" to 21L,
    "veintidos" to 22L,
    "veintitres" to 23L,
    "veinticuatro" to 24L,
    "veinticinco" to 25L,
    "veintiseis" to 26L,
    "veintisiete" to 27L,
    "veintiocho" to 28L,
    "veintinueve" to 29L
)

private val SpanishTens = mapOf(
    "treinta" to 30L,
    "cuarenta" to 40L,
    "cincuenta" to 50L,
    "sesenta" to 60L,
    "setenta" to 70L,
    "ochenta" to 80L,
    "noventa" to 90L
)

private val SpanishHundreds = mapOf(
    "cien" to 100L,
    "ciento" to 100L,
    "doscientos" to 200L,
    "doscientas" to 200L,
    "trescientos" to 300L,
    "trescientas" to 300L,
    "cuatrocientos" to 400L,
    "cuatrocientas" to 400L,
    "quinientos" to 500L,
    "quinientas" to 500L,
    "seiscientos" to 600L,
    "seiscientas" to 600L,
    "setecientos" to 700L,
    "setecientas" to 700L,
    "ochocientos" to 800L,
    "ochocientas" to 800L,
    "novecientos" to 900L,
    "novecientas" to 900L
)

private fun normalizedSpanishWord(value: String): String {
    return java.text.Normalizer.normalize(
        value.lowercase(Locale("es", "ES")),
        java.text.Normalizer.Form.NFD
    )
        .replace(Regex("\\p{M}+"), "")
        .trim(',', '.', ';', ':', '!', '?', '¿', '¡')
}

private fun isSpanishNumberWord(word: String): Boolean {
    return SpanishSmallNumbers.containsKey(word) ||
        SpanishTens.containsKey(word) ||
        SpanishHundreds.containsKey(word) ||
        word == "mil" ||
        word == "millon" ||
        word == "millones"
}

private fun spanishNumberGroupToDigits(words: List<String>): String? {
    val filtered = words.filter { it != "y" }
    if (filtered.isEmpty()) return null

    if (filtered.all { SpanishDigits.containsKey(it) }) {
        return filtered.joinToString("") { SpanishDigits.getValue(it) }
    }

    var total = 0L
    var current = 0L
    var found = false

    for (word in filtered) {
        when {
            SpanishSmallNumbers.containsKey(word) -> {
                current += SpanishSmallNumbers.getValue(word)
                found = true
            }

            SpanishTens.containsKey(word) -> {
                current += SpanishTens.getValue(word)
                found = true
            }

            SpanishHundreds.containsKey(word) -> {
                current += SpanishHundreds.getValue(word)
                found = true
            }

            word == "mil" -> {
                if (current == 0L) current = 1L
                total += current * 1000L
                current = 0L
                found = true
            }

            word == "millon" || word == "millones" -> {
                if (current == 0L && total == 0L) current = 1L
                total = (total + current) * 1_000_000L
                current = 0L
                found = true
            }
        }
    }

    return if (found) (total + current).toString() else null
}

private fun normalizeSpokenNumbers(text: String): String {
    val tokens = text.trim().split(Regex("\\s+"))
    if (tokens.isEmpty()) return text

    val output = mutableListOf<String>()
    var index = 0

    while (index < tokens.size) {
        val normalized = normalizedSpanishWord(tokens[index])

        if (!isSpanishNumberWord(normalized)) {
            output += tokens[index]
            index++
            continue
        }

        val numberWords = mutableListOf<String>()
        var cursor = index

        while (cursor < tokens.size) {
            val word = normalizedSpanishWord(tokens[cursor])

            if (isSpanishNumberWord(word)) {
                numberWords += word
                cursor++
                continue
            }

            if (
                word == "y" &&
                numberWords.isNotEmpty() &&
                cursor + 1 < tokens.size &&
                isSpanishNumberWord(normalizedSpanishWord(tokens[cursor + 1]))
            ) {
                numberWords += word
                cursor++
                continue
            }

            break
        }

        output += spanishNumberGroupToDigits(numberWords)
            ?: tokens.subList(index, cursor).joinToString(" ")
        index = cursor
    }

    return output.joinToString(" ")
}

@Composable
private fun LocalPhotoBox(
    photoPath: String,
    version: Int,
    isProspect: Boolean,
    onClick: () -> Unit
) {
    val bitmap = remember(photoPath, version) {
        if (photoPath.isNotBlank()) {
            BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .width(105.dp)
            .height(150.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = partyContainerColor(isProspect),
            contentColor = Color.White
        )
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Foto del local",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📷", style = MaterialTheme.typography.headlineMedium)
                    Text("Foto", fontWeight = FontWeight.Bold)
                    Text(
                        "Tocar para cámara",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CorporateSurfaceAlt
        )
    ) {
        Column(
            Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = CorporateGold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun HeadingNoPadding(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = CorporateGold
    )
}

private fun createClientPhotoFile(
    context: Context,
    clientId: Long
): File {
    val dir = File(context.filesDir, "client_photos")
    if (!dir.exists()) dir.mkdirs()
    return File(
        dir,
        "local_" + clientId + "_" + System.currentTimeMillis() + ".jpg"
    )
}

private fun todayRange(): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val end = Calendar.getInstance().apply {
        timeInMillis = start.timeInMillis
        add(Calendar.DAY_OF_YEAR, 1)
        add(Calendar.MILLISECOND, -1)
    }

    return start.timeInMillis to end.timeInMillis
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
    SimpleDateFormat(
        "EEE d MMM",
        Locale("es", "ES")
    ).format(Date(value)).replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale("es", "ES"))
        } else {
            it.toString()
        }
    }

private fun weekRangeLabel(start: Long): String {
    val end = dayStart(start, 6)
    return SimpleDateFormat(
        "d MMM",
        Locale("es", "ES")
    ).format(Date(start)) +
        " – " +
        SimpleDateFormat(
            "d MMM yyyy",
            Locale("es", "ES")
        ).format(Date(end))
}

private fun fullDate(value: Long) =
    SimpleDateFormat(
        "EEEE d 'de' MMMM 'de' yyyy",
        Locale("es", "ES")
    ).format(Date(value)).replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale("es", "ES"))
        } else {
            it.toString()
        }
    }

private fun timeOnly(value: Long) =
    SimpleDateFormat(
        "HH:mm",
        Locale("es", "ES")
    ).format(Date(value))

private fun dateTime(value: Long) =
    DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT
    ).format(Date(value))

private fun date(value: Long) =
    DateFormat.getDateInstance(
        DateFormat.SHORT
    ).format(Date(value))

private val CorporateGold = Color(0xFFFFD34E)
private val CorporateGreen = Color(0xFF0A6A42)
private val CorporateOrange = Color(0xFFF5A11A)
private val CorporateBlack = Color(0xFF050505)
private val CorporateSurface = Color(0xFF101010)
private val CorporateSurfaceAlt = Color(0xFF191919)

private val ProspectBlue = Color(0xFF90CAF9)
private val ProspectBlueSoft = Color(0xFF173A52)
private val ClientGreen = Color(0xFFA5D6A7)
private val ClientGreenSoft = Color(0xFF1D3D25)

private val CorporateDarkColors = darkColorScheme(
    primary = CorporateGold,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5A4710),
    onPrimaryContainer = Color.White,
    secondary = CorporateGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF15372A),
    onSecondaryContainer = Color.White,
    tertiary = CorporateOrange,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5A3508),
    onTertiaryContainer = Color.White,
    background = CorporateBlack,
    onBackground = Color.White,
    surface = CorporateSurface,
    onSurface = Color.White,
    surfaceVariant = CorporateSurfaceAlt,
    onSurfaceVariant = Color(0xFFEAEAEA),
    outline = Color(0xFF8B8B8B)
)

private fun partyAccentColor(isProspect: Boolean) =
    if (isProspect) ProspectBlue else ClientGreen

private fun partyContainerColor(isProspect: Boolean) =
    if (isProspect) ProspectBlueSoft else ClientGreenSoft

private fun money(value: Double) =
    NumberFormat.getCurrencyInstance(
        Locale("es", "ES")
    ).format(value)
