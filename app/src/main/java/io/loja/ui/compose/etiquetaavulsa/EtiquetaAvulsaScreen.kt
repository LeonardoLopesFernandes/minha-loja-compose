package io.loja.ui.compose.etiquetaavulsa

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.loja.network.PriceTag
import io.loja.ui.BarcodeScannerActivity
import io.loja.ui.GondolaEditavelActivity
import io.loja.ui.compose.MinhaLojaRed
import io.loja.ui.compose.components.GreenBadgeContainer
import io.loja.ui.compose.components.TrashDrawIcon
import io.loja.ui.compose.components.QtyBoxAvulsa
import io.loja.ui.compose.components.SearchField
import io.loja.ui.compose.components.SpinnerDropdown
import io.loja.ui.compose.components.FiltroCard
import io.loja.ui.compose.components.FiltroDivisorHorizontal
import io.loja.ui.compose.components.FiltroDivisorVertical
import io.loja.ui.compose.components.WhiteTypeBadge
import io.loja.utils.Constants
import io.loja.utils.RelatorioPdf
import io.loja.utils.SessionExpiredHandler
import java.text.NumberFormat
import java.util.Locale

private val CINZA_LABEL = Color(0xFF8C9BA5)
private val CINZA_TEXTO = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtiquetaAvulsaScreen(vm: EtiquetaAvulsaViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(vm.items.size) { vm.carregarSaldos() }
    val context = LocalContext.current
    val activity = context as? Activity
    val recursos = context.resources

    var buscaTexto by remember { mutableStateOf("") }
    var confirmarLimpar by remember { mutableStateOf(false) }

    // Ferramentas do scanner (contínuo).
    val launcherScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val ean = result.data?.getStringExtra("EAN")?.takeIf { it.isNotEmpty() }
            if (ean != null) {
                vm.definirColagem(ean)
                buscaTexto = ean
                vm.buscar(ean) { vm.sincronizarDoScanner() }
            } else {
                vm.sincronizarDoScanner()
            }
        } else {
            vm.sincronizarDoScanner()
        }
    }

    val abrirScanner: () -> Unit = {
        launcherScanner.launch(
            Intent(context, BarcodeScannerActivity::class.java).apply {
                putExtra(BarcodeScannerActivity.EXTRA_MODO_CONTINUO, true)
            }
        )
    }

    val launcherPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) abrirScanner()
        else Toast.makeText(context, "Permissão da câmera necessária", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        vm.eventos.collect { evento ->
            when (evento) {
                is EtiquetaEvento.Toast ->
                    Toast.makeText(context, evento.msg, Toast.LENGTH_LONG).show()
                EtiquetaEvento.SessaoExpirada ->
                    activity?.let { SessionExpiredHandler.handleSessionExpired(it) }
                is EtiquetaEvento.AbrirEdicao -> {
                    val i = Intent(context, GondolaEditavelActivity::class.java).apply {
                        putExtra("ITEMS_JSON", evento.json)
                        putExtra("PRINTER_ID", evento.printerId)
                    }
                    context.startActivity(i)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
            .padding(12.dp),
    ) {
        // ---- Filtros ----
        FiltroCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SpinnerDropdown(
                    label = "IMPRESSORA",
                    icone = io.loja.R.drawable.ic_printer_small,
                    options = vm.printers,
                    selectedIndex = vm.printerSelecionado,
                    labelOf = { it.nome },
                    onSelect = { vm.setPrinter(it) },
                    modifier = Modifier.weight(1f),
                )
                FiltroDivisorVertical()
                SpinnerDropdown(
                    label = "TIPO",
                    icone = io.loja.R.drawable.ic_qr_code_small,
                    options = listOf("EAN", "SAP", "Descrição"),
                    selectedIndex = listOf("EAN", "SAP", "Descrição").indexOf(vm.buscaTipo).coerceAtLeast(0),
                    labelOf = { it },
                    onSelect = { vm.alterarBuscaTipo(listOf("EAN", "SAP", "Descrição")[it]) },
                    modifier = Modifier.weight(1f),
                )
                FiltroDivisorVertical()
                SpinnerDropdown(
                    label = "MODO",
                    icone = io.loja.R.drawable.ic_tune_small,
                    options = listOf("Normal", "Edição"),
                    selectedIndex = if (vm.modoEdicao) 1 else 0,
                    labelOf = { it },
                    onSelect = { vm.alterarModoEdicao(it == 1) },
                    modifier = Modifier.weight(1f),
                )
            }
            FiltroDivisorHorizontal()
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                SearchField(
                    value = buscaTexto,
                    onValueChange = { buscaTexto = it },
                    placeholder = when (vm.buscaTipo) {
                        "EAN" -> "Digite o EAN"
                        "SAP" -> "Digite o SAP"
                        else -> "Buscar Por Descrição"
                    },
                    numerico = vm.buscaTipo == "EAN" || vm.buscaTipo == "SAP",
                    buscando = vm.buscando,
                    onBuscar = { vm.buscar(buscaTexto) { buscaTexto = "" } },
                )
            }
        }

        // ---- Colagem ----
        if (vm.colagem.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFEBEE))
                    .clickable {
                        buscaTexto = vm.colagem
                        vm.buscar(vm.colagem) { buscaTexto = "" }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Área de colagem: ${vm.colagem}",
                    color = Color(0xFFB71C1C),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.definirColagem("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Limpar colagem", tint = Color(0xFFB71C1C), modifier = Modifier.size(16.dp))
                }
            }
        }

        // ---- Ações ----
        if (vm.items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmarLimpar = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(painterResource(io.loja.R.drawable.ic_clean), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("LIMPAR LISTA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { vm.imprimirTodas() },
                    enabled = !vm.processando,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed, contentColor = Color.White),
                ) {
                    Icon(painterResource(io.loja.R.drawable.ic_print_all), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (vm.processando) "IMPRIMINDO..." else "IMPRIMIR TODAS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // ---- Total + PDF ----
        if (vm.items.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (vm.items.size == 1) "1 item na lista" else "${vm.items.size} itens na lista",
                    color = MinhaLojaRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (vm.items.isNotEmpty()) {
                        RelatorioPdf.gerarRelatorioEtiquetas(context, vm.items.toList()) { i ->
                            vm.quantidadeDe(vm.items[i].ean)
                        }
                    }
                }) {
                    Icon(
                        painterResource(io.loja.R.drawable.ic_pdf_outline),
                        contentDescription = "PDF",
                        tint = Color.Unspecified,
                    )
                }
            }
        }

        // ---- Lista / vazio ----
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (vm.items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "0 item(a) na lista\n\nEscaneie ou digite\npara adicionar à lista",
                        color = Color(0xFF999999),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(vm.items, key = { it.id + it.ean }) { item ->
                        EtiquetaCard(
                            item = item,
                            saldo = vm.saldos[item.ean],
                            qtd = vm.quantidadeDe(item.ean),
                            onQtdChange = { vm.setQuantidade(item.ean, it); vm.persistir() },
                            onImprimir = { vm.imprimir(item, vm.quantidadeDe(item.ean)) },
                            onRemover = { vm.remover(item) },
                        )
                    }
                }
            }

            // FAB scanner
            FabScanner(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                onAbrir = {
                    val temPermissao = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (temPermissao) abrirScanner() else launcherPermissao.launch(Manifest.permission.CAMERA)
                },
            )
        }
    }

    if (confirmarLimpar) {
        AlertDialog(
            onDismissRequest = { confirmarLimpar = false },
            containerColor = Color.White,
            titleContentColor = Color(0xFF1E293B),
            textContentColor = Color(0xFF333333),
            title = { Text("Limpar Lista") },
            text = { Text("Tem certeza que deseja remover todos os itens?") },
            confirmButton = {
                Button(
                    onClick = { vm.limpar(); confirmarLimpar = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed, contentColor = Color.White),
                ) { Text("Sim") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmarLimpar = false }) { Text("Não") }
            },
        )
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
                    color = CINZA_TEXTO,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MinhaLojaRed)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(
                        text = { Text(labelOf(o)) },
                        onClick = { expanded = false; onSelect(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FabScanner(modifier: Modifier = Modifier, onAbrir: () -> Unit) {
    var revelado by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "fab")
    val escala by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (revelado) 1f else 0.92f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "fabScale",
    )
    Box(
        modifier = modifier
            .size(56.dp)
            .scale(if (revelado) escala else 1f)
            .clip(CircleShape)
            .background(MinhaLojaRed)
            .clickable {
                if (!revelado) revelado = true else onAbrir()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(io.loja.R.drawable.ic_barcode_scan), contentDescription = "Escanear", tint = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EtiquetaCard(
    item: PriceTag,
    saldo: Double?,
    qtd: Int,
    onQtdChange: (Int) -> Unit,
    onImprimir: () -> Unit,
    onRemover: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dismissState = rememberDismissState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == DismissValue.DismissedToEnd) onRemover()
    }
    val formatador = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val num = remember { NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
    val pd = item.printingData
    val precoNormal = pd?.price?.value ?: 0.0
    val precoPromo = pd?.promotionalPrice?.value
    val takeAndWin = pd?.takeAndWin
    val installments = pd?.installments

    var leve: String? = null
    var valor = formatador.format(precoNormal)
    var tipo = "normal"
    var tipoCor = Color(0xFF9E9E9E)

    val valorParcCalc = installments?.installmentValue?.takeIf { it > 0 }
        ?: (pd?.displayPrice ?: 0.0).takeIf { it > 0 }
        ?: (pd?.unitValue ?: 0.0).takeIf { it > 0 }
        ?: precoPromo?.takeIf { it > 0 }
        ?: precoNormal
    val ehDeporParcelado = installments != null && installments.installmentQuantity > 0 &&
        precoPromo != null && precoPromo > 0 && (precoNormal <= 0 || precoPromo < precoNormal)

    if (takeAndWin != null && takeAndWin.qty > 0 && takeAndWin.totalValue > 0) {
        val totalLeve = if (pd?.template == Constants.SIGN_TEMPLATE_LEVE_GANHE_CADA && precoPromo != null && precoPromo > 0)
            precoPromo else takeAndWin.totalValue
        leve = "Leve ${takeAndWin.qty} por R$ ${String.format(Locale("pt", "BR"), "%.2f", totalLeve)}"
        valor = formatador.format(precoNormal)
        tipo = "leve e ganhe"; tipoCor = Color(0xFF2E7D32)
    } else if (ehDeporParcelado) {
        valor = "Por R$${num.format(precoNormal)}|Por R$${num.format(precoPromo!!)}|${installments!!.installmentQuantity}x de R$${num.format(valorParcCalc)}"
        tipo = "parcelado"; tipoCor = Color(0xFFFF9800)
    } else if (installments != null && installments.installmentQuantity > 0) {
        val qtdParc = installments.installmentQuantity
        valor = if (precoPromo != null && precoPromo > 0)
            "R$${num.format(precoPromo)} | ${qtdParc}x de R$${num.format(valorParcCalc)}"
        else "${formatador.format(precoNormal)} | ${qtdParc}x de R$${num.format(valorParcCalc)}"
        tipo = "parcelado"; tipoCor = Color(0xFFFF9800)
    } else if (precoPromo != null && precoPromo > 0 && precoPromo < precoNormal) {
        valor = "De ${formatador.format(precoNormal)} | Por ${formatador.format(precoPromo)}"
        tipo = "de por"; tipoCor = Color(0xFFFF9800)
    }

    val deptNum = item.department.substringBefore(" -").trim()

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFD32F2F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                TrashDrawIcon(
                    animate = dismissState.progress > 0f,
                    modifier = Modifier.size(30.dp),
                )
            }
        },
        dismissContent = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QtyBoxAvulsa(valor = qtd, onChange = onQtdChange)
                Text(
                    item.description,
                    color = Color(0xFF212121),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                IconButton(onClick = onImprimir, modifier = Modifier.size(28.dp)) {
                    Icon(painterResource(io.loja.R.drawable.printer), contentDescription = "Imprimir", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF757575))) {
                                append("DEP: $deptNum | EAN: ${item.ean} / SAP: ${item.sap}")
                            }
                            if (saldo != null) {
                                withStyle(
                                    SpanStyle(
                                        color = if (saldo < 0) Color(0xFFD81B3A) else Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(" | SALDO: ${saldo.toInt()}")
                                }
                            }
                        },
                        fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(item.ean))
                            Toast.makeText(context, "EAN copiado: ${item.ean}", Toast.LENGTH_SHORT).show()
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    if (leve != null) {
                        GreenBadgeContainer(
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(start = 10.dp),
                        ) {
                            Text(leve, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            Text("|", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            Text(valor, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            WhiteTypeBadge(
                                tipo, tipoCor,
                                modifier = Modifier.fillMaxHeight().padding(start = 6.dp),
                                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                            )
                        }
                    } else {
                        val parcelado = tipo == "parcelado"
                        GreenBadgeContainer(
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(start = 12.dp),
                        ) {
                            if (parcelado) {
                                var tamFonte by remember(valor) { mutableStateOf(14f) }
                                Text(
                                    valor,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = tamFonte.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.weight(1f, fill = false),
                                    onTextLayout = { if (it.didOverflowWidth && tamFonte > 11f) tamFonte -= 1f },
                                )
                            } else {
                                Text(valor, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            }
                            WhiteTypeBadge(
                                tipo, tipoCor,
                                modifier = Modifier.fillMaxHeight().padding(start = if (parcelado) 10.dp else 8.dp),
                                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                            )
                        }
                    }
                }
            }
        }
            }
        },
    )
}
