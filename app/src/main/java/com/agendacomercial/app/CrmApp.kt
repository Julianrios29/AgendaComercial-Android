package com.agendacomercial.app

import android.content.Intent
import android.net.Uri
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
import java.util.Date
import java.util.Locale

private sealed class Screen {
    data object Agenda : Screen()
    data object Clients : Screen()
    data object NewClient : Screen()
    data class ClientDetail(val id: Long) : Screen()
    data class NewVisit(val id: Long) : Screen()
    data class NewOrder(val id: Long) : Screen()
}

@Composable
fun CrmApp(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Agenda) }
    MaterialTheme {
        val root = screen is Screen.Agenda || screen is Screen.Clients
        Scaffold(bottomBar = {
            if (root) NavigationBar {
                NavigationBarItem(screen is Screen.Agenda, { screen = Screen.Agenda; vm.refresh() }, { Text("A") }, label = { Text("Agenda") })
                NavigationBarItem(screen is Screen.Clients, { screen = Screen.Clients; vm.refresh() }, { Text("C") }, label = { Text("Clientes") })
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (val s = screen) {
                    Screen.Agenda -> AgendaScreen(vm) { screen = Screen.ClientDetail(it) }
                    Screen.Clients -> ClientsScreen(vm, { screen = Screen.ClientDetail(it) }, { screen = Screen.NewClient })
                    Screen.NewClient -> NewClientScreen(vm, { screen = Screen.Clients }) { screen = Screen.ClientDetail(it) }
                    is Screen.ClientDetail -> DetailScreen(vm, s.id, { screen = Screen.Clients }, { screen = Screen.NewVisit(s.id) }, { screen = Screen.NewOrder(s.id) })
                    is Screen.NewVisit -> VisitScreen(vm, s.id) { screen = Screen.ClientDetail(s.id) }
                    is Screen.NewOrder -> OrderScreen(vm, s.id) { screen = Screen.ClientDetail(s.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaScreen(vm: AppViewModel, open: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Agenda comercial") })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Próximas visitas", style = MaterialTheme.typography.titleMedium) }
            items(vm.agenda, key = { it.id }) { v ->
                Card(Modifier.fillMaxWidth().clickable { open(v.clientId) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(dateTime(v.scheduledAt), fontWeight = FontWeight.Bold)
                        Text(v.clientName, style = MaterialTheme.typography.titleMedium)
                        Text(v.status + " · " + v.purpose)
                    }
                }
            }
            if (vm.agenda.isEmpty()) item { Text("No tienes visitas en los próximos 7 días.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientsScreen(vm: AppViewModel, open: (Long) -> Unit, add: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = vm.clients.filter { q.isBlank() || listOf(it.name, it.businessName, it.city, it.phone).any { x -> x.contains(q, true) } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Clientes") }, actions = { TextButton(onClick = add) { Text("+ Nuevo") } })
        OutlinedTextField(q, { q = it }, label = { Text("Buscar cliente") }, modifier = Modifier.padding(16.dp).fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { open(c.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.businessName.ifBlank { c.name }, fontWeight = FontWeight.Bold)
                        if (c.businessName.isNotBlank()) Text(c.name)
                        Text(listOf(c.address, c.city).filter(String::isNotBlank).joinToString(", "))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(vm: AppViewModel, id: Long, back: () -> Unit, visit: () -> Unit, order: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val client = remember(id, refresh) { vm.client(id) } ?: return
    val visits = remember(id, refresh) { vm.visits(id) }
    val orders = remember(id, refresh) { vm.orders(id) }
    val consumption = remember(id, refresh) { vm.consumption(id) }
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(title = { Text(client.businessName.ifBlank { client.name }) }, navigationIcon = { TextButton(onClick = back) { Text("<") } })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (client.businessName.isNotBlank()) Text(client.name, style = MaterialTheme.typography.titleMedium)
                Text(listOf(client.address, client.postalCode, client.city).filter(String::isNotBlank).joinToString(", "))
                if (client.contactPerson.isNotBlank()) Text("Contacto: " + client.contactPerson)
                if (client.phone.isNotBlank()) Text("Tel: " + client.phone)
                if (client.email.isNotBlank()) Text(client.email)
                if (client.observations.isNotBlank()) {
                    Text("Observaciones", fontWeight = FontWeight.Bold)
                    Text(client.observations)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = order) { Text("Nuevo pedido") }
                    Button(onClick = visit) { Text("Nueva cita") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.phone.isNotBlank()) OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + client.phone)))
                    }) { Text("Llamar") }
                    if (client.address.isNotBlank()) OutlinedButton(onClick = {
                        val place = Uri.encode(client.address + " " + client.city)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + place)))
                    }) { Text("Mapa") }
                }
            }
        }
        item { Heading("Productos que consume") }
        if (consumption.isEmpty()) item { Text("Todavía no hay consumo registrado.", Modifier.padding(horizontal = 16.dp)) }
        items(consumption) { x ->
            InfoCard {
                Text(x.name, fontWeight = FontWeight.Bold)
                Text("Unidades históricas: " + x.units)
                Text("Último pedido: " + date(x.lastOrderedAt) + " · " + money(x.lastPrice))
            }
        }
        item { Heading("Últimos pedidos") }
        if (orders.isEmpty()) item { Text("Sin pedidos todavía.", Modifier.padding(horizontal = 16.dp)) }
        items(orders, key = { it.id }) { x ->
            InfoCard {
                Text(date(x.createdAt) + " · " + money(x.total), fontWeight = FontWeight.Bold)
                Text(x.status)
            }
        }
        item { Heading("Historial de visitas") }
        if (visits.isEmpty()) item { Text("Sin visitas registradas.", Modifier.padding(horizontal = 16.dp)) }
        items(visits, key = { it.id }) { x ->
            InfoCard {
                Text(dateTime(x.scheduledAt), fontWeight = FontWeight.Bold)
                Text(x.status + " · " + x.purpose)
                if (x.notes.isNotBlank()) Text(x.notes)
                if (x.status != "REALIZADA") TextButton(onClick = { vm.completeVisit(x.id); refresh++ }) { Text("Marcar realizada") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewClientScreen(vm: AppViewModel, back: () -> Unit, saved: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var postal by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Nuevo cliente") }, navigationIcon = { TextButton(onClick = back) { Text("<") } })
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Field("Nombre *", name) { name = it } }
            item { Field("Empresa / establecimiento", business) { business = it } }
            item { Field("Persona de contacto", contact) { contact = it } }
            item { Field("Teléfono", phone, KeyboardType.Phone) { phone = it } }
            item { Field("Email", email, KeyboardType.Email) { email = it } }
            item { Field("Dirección", address) { address = it } }
            item { Field("Ciudad", city) { city = it } }
            item { Field("Código postal", postal, KeyboardType.Number) { postal = it } }
            item { Field("Observaciones", notes) { notes = it } }
            item {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = { saved(vm.addClient(name.trim(), business.trim(), contact.trim(), phone.trim(), email.trim(), address.trim(), city.trim(), postal.trim(), notes.trim())) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar cliente") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitScreen(vm: AppViewModel, id: Long, done: () -> Unit) {
    var days by remember { mutableStateOf("1") }
    var purpose by remember { mutableStateOf("Visita comercial") }
    var notes by remember { mutableStateOf("") }
    Column {
        TopAppBar(title = { Text("Programar visita") }, navigationIcon = { TextButton(onClick = done) { Text("<") } })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Dentro de cuántos días", days, KeyboardType.Number) { days = it.filter(Char::isDigit).take(3) }
            Field("Motivo", purpose) { purpose = it }
            Field("Notas", notes) { notes = it }
            Button(onClick = { vm.addVisit(id, days.toIntOrNull() ?: 0, purpose, notes); done() }, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar cita a las 10:00")
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
        TopAppBar(title = { Text("Nuevo pedido") }, navigationIcon = { TextButton(onClick = done) { Text("<") } })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { p ->
                InfoCard {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    Text(p.sku + " · " + money(p.price))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { qty[p.id] = ((qty[p.id] ?: 0) - 1).coerceAtLeast(0) }) { Text("-") }
                        Text((qty[p.id] ?: 0).toString(), Modifier.padding(top = 12.dp))
                        OutlinedButton(onClick = { qty[p.id] = (qty[p.id] ?: 0) + 1 }) { Text("+") }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Total: " + money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button(enabled = total > 0, onClick = { vm.addOrder(id, qty.toMap()); done() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Guardar pedido")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth())
}

private fun dateTime(v: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(v))
private fun date(v: Long) = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(v))
private fun money(v: Double) = NumberFormat.getCurrencyInstance(Locale("es", "ES")).format(v)
