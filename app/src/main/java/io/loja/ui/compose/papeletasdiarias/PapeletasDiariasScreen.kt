package io.loja.ui.compose.papeletasdiarias

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.loja.network.PriceSign
import io.loja.ui.ModeloEditavelActivity
import io.loja.ui.compose.MinhaLojaRed
import io.loja.ui.compose.components.DepartamentoSelector
import io.loja.ui.compose.components.SpinnerDropdown
import io.loja.ui.compose.components.GreenBadgeContainer
import io.loja.ui.compose.components.QtyBoxDiaria
import io.loja.ui.compose.components.StatusBadge
import io.loja.ui.compose.components.WhiteTypeBadge
import io.loja.utils.Constants
import io.loja.utils.SessionExpiredHandler
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CINZA_LABEL = Color(0xFF8C9BA5)
private val CINZA_TEXTO = Color(0xFF1E293B)

private val MOVIMENTOS = listOf("Todos", "De/Por", "Leve e Ganhe", "Normal")
private val TIPOS = listOf("Normal", "Promocional", "Promocional (Modelo)", "Normal (Modelo)")
private val STATUSES = listOf("Todos os status", "Impressas", "Não impressas")

@Composable
fun PapeletasDiariasScreen(
    vm: PapeletasDiariasViewModel,
    modifier: Modifier = Modifier,
    onTitulo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var dialogDept by remember { mutableStateOf(false) }

    LaunchedEffect(vm.isTomorrow) {
        onTitulo(if (vm.isTomorrow) "Papeletas de amanhã" else "Papeletas de hoje")
    }

    LaunchedEffect(Unit) {
        vm.eventos.collect { ev ->
            when (ev) {
                is PapeletaDiariaEvento.Toast -> Toast.makeText(context, ev.msg, Toast.LENGTH_LONG).show()
                PapeletaDiariaEvento.SessaoExpirada -> activity?.let { SessionExpiredHandler.handleSessionExpired(it) }
                is PapeletaDiariaEvento.AbrirModelo -> {
                    val i = Intent(context, ModeloEditavelActivity::class.java).apply {
                        putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, ev.semOverlay)
                        putExtra(ModeloEditavelActivity.EXTRA_SIZE, ev.size)
                        putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, ev.enviarApi)
                        putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, ev.apiSemOverlay)
                        putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, ev.modoEditavel)
                        putExtra(ModeloEditavelActivity.EXTRA_HIDE_GERAR_PREVIEW, ev.hideGerarPreview)
                        putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, ev.mostrarCheckboxOverlay)
                    }
                    context.startActivity(i)
                }
                is PapeletaDiariaEvento.AbrirPdf -> {
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
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DatePill("Hoje", !vm.isTomorrow, Modifier.weight(1f)) { if (vm.isTomorrow) vm.voltarHoje() }
                        DatePill("Amanhã", vm.isTomorrow, Modifier.weight(1f)) { if (!vm.isTomorrow) vm.irParaAmanha() }
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

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpinnerDropdown("MOVIMENTO", io.loja.R.drawable.ic_receipt_small, MOVIMENTOS, vm.movementIdx, { it }, { vm.alterarMovement(it) }, Modifier.weight(1f))
                        SpinnerDropdown("TIPO", io.loja.R.drawable.ic_qr_code_small, TIPOS, vm.tipoIdx, { it }, { vm.alterarTipo(it) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpinnerDropdown("TAMANHO", io.loja.R.drawable.ic_aspect_ratio_small, vm.sizes, vm.tamanhoIdx, { it }, { vm.alterarTamanho(it) }, Modifier.weight(1f))
                        SpinnerDropdown("STATUS", io.loja.R.drawable.ic_tune_small, STATUSES, vm.statusIdx, { it }, { vm.alterarStatus(it) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    DepartamentoSelector(
                        texto = vm.textoDepartamentos(),
                        buscando = vm.carregando,
                        onAbrir = { dialogDept = true },
                        onBuscar = { vm.carregarPapeletas() },
                    )
                }
            }
        }

        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = vm.todosSelecionados,
                        onCheckedChange = { c -> if (c) vm.selecionarTodos() else vm.limparSelecao() },
                    )
                    Text("Selecionados: ${vm.selecionadosCount}", color = CINZA_TEXTO, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.gerarRelatorio() }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("RELATÓRIO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.imprimir() },
                    enabled = !vm.imprimindo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed, contentColor = Color.White),
                ) { Text(if (vm.imprimindo) "IMPRIMINDO..." else "🖨️ Imprimir Papeletas", fontWeight = FontWeight.Bold) }
            }
        }

        if (vm.carregando && vm.items.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MinhaLojaRed)
                }
            }
        } else if (vm.items.isEmpty()) {
            item {
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhuma papeleta", color = Color(0xFF999999), fontSize = 14.sp)
                    if (vm.currentDate == hoje && vm.tomorrowCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Você tem ${vm.tomorrowCount} papeleta(s) de amanhã para imprimir. Selecione os filtros e clique em BUSCAR.",
                            color = Color(0xFF999999), fontSize = 13.sp,
                        )
                    }
                }
            }
        } else {
            items(vm.items, key = { it.id }) { item ->
                PapeletaDiariaCard(
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
        DialogDepartamentos(vm = vm, onFechar = { aplicar: Boolean -> if (aplicar) vm.carregarPapeletas(); dialogDept = false })
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
private fun DialogDepartamentos(vm: PapeletasDiariasViewModel, onFechar: (Boolean) -> Unit) {
    val depts = vm.departamentosComItens()
    AlertDialog(
        onDismissRequest = { onFechar(false) },
        modifier = Modifier.fillMaxWidth(0.92f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color.White,
        titleContentColor = Color(0xFF1E293B),
        textContentColor = Color(0xFF333333),
        title = { Text("Todos os departamentos") },
        text = {
            Column {
                androidx.compose.material3.TextButton(onClick = { vm.selecionarTodosDepartamentos() }) {
                    Text("Selecionar todos", color = MinhaLojaRed, fontWeight = FontWeight.Bold)
                }
                if (depts.isEmpty()) {
                    Text("Nenhum departamento com impressão")
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(depts, key = { it.id }) { d ->
                            val (pendente, impressas) = vm.contagemDepartamento(d.id)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { vm.toggleDepartamento(d.id) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = vm.departamentosSelecionados.contains(d.id), onCheckedChange = { vm.toggleDepartamento(d.id) })
                                Text("${d.id.padStart(2, '0')} - ${d.label}", modifier = Modifier.weight(1f), fontSize = 14.sp)
                                if (pendente > 0) Text("$pendente", color = Color(0xFFD7193F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (impressas > 0) { Spacer(Modifier.width(8.dp)); Text("$impressas", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onFechar(true) }, colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed, contentColor = Color.White)) { Text("OK") } },
        dismissButton = { OutlinedButton(onClick = { vm.limparDepartamentos() }) { Text("Limpar") } },
    )
}

@Composable
private fun PapeletaDiariaCard(
    item: PriceSign,
    selecionado: Boolean,
    qtd: Int,
    onCheck: (Boolean) -> Unit,
    onQtdChange: (Int) -> Unit,
) {
    val f = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val num = remember { NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
    val pd = item.printingData
    val precoNormal = pd?.price ?: 0.0
    val precoPromo = pd?.promotionPrice
    val takeQty = pd?.takeAndWinQuantity?.takeIf { it > 0 }
    val takePrice = takeQty?.let { pd?.takeAndWinPrice?.takeIf { p -> p > 0 } }
    val movement = item.movement.uppercase()

    var tipo = "normal"
    var tipoCor = Color(0xFF9E9E9E)
    var promoText: String? = null
    var separador = false
    var precoTexto = if (precoNormal > 0) f.format(precoNormal) else item.price

    when {
        movement.contains("LEVE") || movement.contains("GANHE") -> {
            tipo = "leve e ganhe"; tipoCor = Color(0xFF2E7D32)
            if (takeQty != null && takePrice != null) {
                val total = Constants.totalLeveGanhePorTemplate(pd?.template, precoPromo, takePrice, takeQty)
                promoText = "Leve $takeQty por ${f.format(total)}"
                separador = true
                precoTexto = if (precoNormal > 0) f.format(precoNormal) else item.price
            }
        }
        movement.contains("DE") || movement.contains("POR") || movement.contains("PROMOCIONAL") -> {
            tipo = "de por"; tipoCor = Color(0xFFFF9800)
            if (precoPromo != null && precoPromo > 0 && precoPromo < precoNormal) {
                promoText = "De ${f.format(precoNormal)} | Por ${f.format(precoPromo)}"
                separador = false
                precoTexto = ""
            }
        }
        ((pd?.installmentQuantity ?: 0) > 0) -> {
            tipo = "parcelado"; tipoCor = Color(0xFFFF9800)
            val q = pd?.installmentQuantity?.takeIf { it > 0 }
            val v = pd?.installmentPrice?.takeIf { it > 0 } ?: precoPromo?.takeIf { it > 0 } ?: precoNormal
            val por = precoPromo?.takeIf { it > 0 }
            if (q != null && por != null && v > 0) {
                promoText = "R$${num.format(por)} | ${q}x de R$${num.format(v)}"
                separador = false
                precoTexto = ""
            } else {
                promoText = if (precoNormal > 0) f.format(precoNormal) else item.price
                separador = true
                precoTexto = if (q != null) "${q}x de R$${String.format(Locale("pt", "BR"), "%.2f", v)}" else f.format(v)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selecionado,
                    onCheckedChange = onCheck,
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1B5E20)),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(item.description, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                QtyBoxDiaria(valor = qtd, onChange = onQtdChange)
            }
            Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dep: ${item.department} • EAN: ${item.ean}", color = Color(0xFF666666), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (item.status.equals("Impressa", true)) { Spacer(Modifier.width(8.dp)); StatusBadge("IMPRESSA", Color(0xFF4CAF50)) }
                else if (item.status.equals("Não impressa", true)) { Spacer(Modifier.width(8.dp)); StatusBadge("NÃO IMPRESSA", Color(0xFFD81B3A)) }
            }
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GreenBadgeContainer {
                    if (promoText != null) Text(promoText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (separador) Text("|", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    if (precoTexto.isNotEmpty()) Text(precoTexto, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    WhiteTypeBadge(tipo, tipoCor, Modifier.padding(start = 6.dp))
                }
            }
        }
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
                modifier = Modifier.fillMaxWidth().clickable(enabled = options.isNotEmpty()) { expanded = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(options.getOrNull(selectedIndex)?.let(labelOf) ?: "-", color = CINZA_TEXTO, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MinhaLojaRed)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(labelOf(o)) }, onClick = { expanded = false; onSelect(i) }) }
            }
        }
    }
}
