package io.loja.ui.compose.etiquetasdiarias

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.loja.network.models.PriceTagItem
import io.loja.ui.GondolaEditavelActivity
import io.loja.ui.compose.MinhaLojaRed
import io.loja.utils.Constants
import io.loja.utils.SessionExpiredHandler
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CINZA_LABEL = Color(0xFF8C9BA5)
private val CINZA_TEXTO = Color(0xFF1E293B)

@Composable
fun EtiquetasDiariasScreen(vm: EtiquetasDiariasViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    var mostrarFiltros by remember { mutableStateOf(true) }
    var dialogDept by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.eventos.collect { ev ->
            when (ev) {
                is EtiquetaDiariaEvento.Toast -> Toast.makeText(context, ev.msg, Toast.LENGTH_LONG).show()
                EtiquetaDiariaEvento.SessaoExpirada -> activity?.let { SessionExpiredHandler.handleSessionExpired(it) }
                is EtiquetaDiariaEvento.AbrirGondola -> {
                    context.startActivity(Intent(context, GondolaEditavelActivity::class.java).apply {
                        putExtra("ITEMS_JSON", ev.json); putExtra("PRINTER_ID", ev.printerId)
                    })
                }
                is EtiquetaDiariaEvento.AbrirPdf -> {
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(ev.filePath))
                        val i = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(i, "Abrir relatório"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao abrir relatório: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFEDEFF2)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ---- Card header: Hoje/Amanhã + contadores ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DatePill(
                            titulo = "Hoje",
                            selecionado = !vm.isTomorrow,
                            modifier = Modifier.weight(1f),
                            onClick = { if (vm.isTomorrow) vm.voltarHoje() },
                        )
                        DatePill(
                            titulo = "Amanhã",
                            selecionado = vm.isTomorrow,
                            modifier = Modifier.weight(1f),
                            onClick = { if (!vm.isTomorrow) vm.irParaAmanha() },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Contador("NÃO IMPRESSA", vm.unprinted, Color(0xFFD7193F), Modifier.weight(1f))
                        Contador("IMPRESSA", vm.printed, Color(0xFF4CAF50), Modifier.weight(1f))
                        Contador("TOTAL", vm.total, Color(0xFF2196F3), Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- Filtros ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (vm.isTomorrow) "Etiquetas de amanhã" else "Etiquetas de hoje",
                            color = CINZA_TEXTO, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown, contentDescription = null, tint = MinhaLojaRed,
                            modifier = Modifier.clickable { mostrarFiltros = !mostrarFiltros },
                        )
                    }
                    if (mostrarFiltros) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dropdown(
                                "IMPRESSORA",
                                vm.printers.map { it.label },
                                vm.printerIdx,
                                { it },
                                { vm.alterarPrinter(it) },
                                Modifier.weight(1f),
                            )
                            Dropdown(
                                "STATUS",
                                listOf("Todos os status", "Impressas", "Não impressas"),
                                vm.statusIdx, { it }, { vm.alterarStatus(it) }, Modifier.weight(1f),
                            )
                            Dropdown(
                                "MODO",
                                listOf("Normal", "Edição"),
                                vm.modoIdx, { it }, { vm.alterarModo(it) }, Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                .clickable { dialogDept = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(vm.textoDepartamentos(), color = CINZA_TEXTO, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MinhaLojaRed)
                        }
                    }
                }
            }
        }

        // ---- Ações ----
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = vm.todosSelecionados,
                        onCheckedChange = { checked -> if (checked) vm.selecionarTodos() else vm.limparSelecao() },
                    )
                    Text("Selecionados: ${vm.selecionadosCount}", color = CINZA_TEXTO, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.gerarRelatorio() }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("RELATÓRIO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.carregarEtiquetas() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text(if (vm.carregando) "BUSCANDO..." else "BUSCAR", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { vm.imprimir() },
                        enabled = !vm.imprimindo,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed),
                    ) { Text(if (vm.imprimindo) "IMPRIMINDO..." else "🖨️ Imprimir", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // ---- Lista / estados ----
        if (vm.carregando && vm.items.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MinhaLojaRed)
                }
            }
        } else if (vm.items.isEmpty()) {
            item {
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nenhuma etiqueta", color = Color(0xFF999999), fontSize = 14.sp)
                    if (vm.currentDate == hoje && vm.tomorrowCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Você tem ${vm.tomorrowCount} etiqueta(s) de amanhã para imprimir. Selecione os filtros e clique em BUSCAR.",
                            color = Color(0xFF999999), fontSize = 13.sp,
                        )
                    }
                }
            }
        } else {
            items(vm.items, key = { it.id + it.ean }) { item ->
                PriceTagItemCard(
                    item = item,
                    selecionado = vm.isSelecionado(item),
                    qtd = vm.quantidadeDe(item),
                    onCheck = { vm.toggleSelecionado(item, it) },
                    onQtdChange = { vm.setQuantidade(item, it) },
                )
            }
        }
    }

    if (dialogDept) {
        DialogDepartamentos(vm = vm, onFechar = { aplicar: Boolean -> if (aplicar) vm.carregarEtiquetas(); dialogDept = false })
    }
}

@Composable
private fun DatePill(titulo: String, selecionado: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selecionado) Color(0xFFA5D6A7) else Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(titulo, color = if (selecionado) Color(0xFF1B5E20) else Color(0xFF6B7280), fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun Contador(label: String, valor: Int, cor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CINZA_LABEL, fontSize = 10.sp)
        Text("$valor", color = cor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun DialogDepartamentos(vm: EtiquetasDiariasViewModel, onFechar: (Boolean) -> Unit) {
    val depts = vm.departamentosComItens()
    AlertDialog(
        onDismissRequest = { onFechar(false) },
        title = { Text("Todos os departamentos") },
        text = {
            if (depts.isEmpty()) {
                Text("Nenhum departamento com impressão")
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(depts, key = { it.id }) { d ->
                        val (pendente, impressas) = vm.contagemDepartamento(d.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleDepartamento(d.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = vm.departamentosSelecionados.contains(d.id), onCheckedChange = { vm.toggleDepartamento(d.id) })
                            Text("${d.id.padStart(2, '0')} - ${d.label}", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            if (pendente > 0) Text("$pendente", color = Color(0xFFD7193F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (impressas > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text("$impressas", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onFechar(true) }, colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed)) { Text("OK") } },
        dismissButton = {
            OutlinedButton(onClick = { vm.limparDepartamentos() }) { Text("Limpar") }
        },
    )
}

@Composable
private fun PriceTagItemCard(
    item: PriceTagItem,
    selecionado: Boolean,
    qtd: Int,
    onCheck: (Boolean) -> Unit,
    onQtdChange: (Int) -> Unit,
) {
    val f = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val pd = item.printingData
    val precoNormal = pd?.price?.value ?: 0.0
    val precoPromo = pd?.promotionalPrice?.value
    val takeAndWin = pd?.takeAndWin
    val movement = item.movement.uppercase()

    var badge = "normal"
    var badgeColor = Color(0xFF9E9E9E)
    var destaque: String? = null
    var precoTexto = if (precoNormal > 0) f.format(precoNormal) else item.price

    when {
        movement.contains("LEVE") || movement.contains("GANHE") -> {
            badge = "leve e ganhe"; badgeColor = Color(0xFF2E7D32)
            if (takeAndWin != null && takeAndWin.qty > 0 && takeAndWin.totalValue > 0) {
                val total = if (pd?.template == Constants.SIGN_TEMPLATE_LEVE_GANHE_CADA && precoPromo != null && precoPromo > 0)
                    precoPromo else takeAndWin.totalValue
                destaque = "Leve ${takeAndWin.qty} por ${f.format(total)}"
            }
        }
        movement.contains("DE") || movement.contains("POR") || movement.contains("PROMOCIONAL") -> {
            badge = "de por"; badgeColor = Color(0xFFFF9800)
            if (precoPromo != null && precoPromo > 0 && precoPromo < precoNormal) {
                destaque = "De ${f.format(precoNormal)} | Por ${f.format(precoPromo)}"
            }
        }
        ((pd?.installments?.installmentQuantity ?: 0) > 0) -> {
            badge = "parcelado"; badgeColor = Color(0xFFFF9800)
            val q = pd?.installments?.installmentQuantity
            val v = pd?.installments?.installmentValue ?: 0.0
            destaque = if (q != null) "${q}x de R$ ${String.format(Locale("pt", "BR"), "%.2f", v)}" else null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = selecionado, onCheckedChange = { onCheck(it) })
            Column(modifier = Modifier.weight(1f)) {
                Text("Dep: ${item.department} • EAN: ${item.ean}", color = CINZA_LABEL, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.description, color = CINZA_TEXTO, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (destaque != null) Text(destaque, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(precoTexto, color = CINZA_TEXTO, fontSize = 13.sp)
                    }
                    Badge(badge, badgeColor)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Qtd", color = CINZA_LABEL, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = qtd.toString(),
                        onValueChange = { t -> onQtdChange(t.filter { it.isDigit() }.toIntOrNull() ?: 1) },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.weight(1f))
                    if (item.status.equals("Impressa", true)) Badge("IMPRESSA", Color(0xFF4CAF50))
                    else if (item.status.equals("Não impressa", true)) Badge("NÃO IMPRESSA", Color(0xFFD81B3A))
                }
            }
        }
    }
}

@Composable
private fun Badge(texto: String, cor: Color) {
    Box(
        modifier = Modifier
            .background(cor, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(texto, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun <T> Dropdown(
    label: String,
    options: List<T>,
    selectedIndex: Int,
    labelOf: (T) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, color = CINZA_LABEL, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = options.isNotEmpty()) { expanded = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.getOrNull(selectedIndex)?.let(labelOf) ?: "-",
                    color = CINZA_TEXTO, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MinhaLojaRed)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(labelOf(o)) }, onClick = { expanded = false; onSelect(i) })
                }
            }
        }
    }
}
