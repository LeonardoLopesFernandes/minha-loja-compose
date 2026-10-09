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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import io.loja.utils.Constants
import io.loja.utils.RelatorioPdf
import io.loja.utils.SessionExpiredHandler
import java.text.NumberFormat
import java.util.Locale

private val CINZA_LABEL = Color(0xFF8C9BA5)
private val CINZA_TEXTO = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtiquetaAvulsaScreen(vm: EtiquetaAvulsaViewModel) {
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
            .padding(12.dp),
    ) {
        // ---- Filtros ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Dropdown(
                    label = "IMPRESSORA",
                    options = vm.printers,
                    selectedIndex = vm.printerSelecionado,
                    labelOf = { it.nome },
                    onSelect = { vm.setPrinter(it) },
                    modifier = Modifier.weight(1f),
                )
                Dropdown(
                    label = "TIPO",
                    options = listOf("EAN", "SAP", "Descrição"),
                    selectedIndex = listOf("EAN", "SAP", "Descrição").indexOf(vm.buscaTipo).coerceAtLeast(0),
                    labelOf = { it },
                    onSelect = { vm.setBuscaTipo(listOf("EAN", "SAP", "Descrição")[it]) },
                    modifier = Modifier.weight(1f),
                )
                Dropdown(
                    label = "MODO",
                    options = listOf("Normal", "Edição"),
                    selectedIndex = if (vm.modoEdicao) 1 else 0,
                    labelOf = { it },
                    onSelect = { vm.setModoEdicao(it == 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- Busca ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = buscaTexto,
                onValueChange = { buscaTexto = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        when (vm.buscaTipo) {
                            "EAN" -> "Digite o EAN"
                            "SAP" -> "Digite o SAP"
                            else -> "Buscar Por Descrição"
                        },
                        color = CINZA_LABEL,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (vm.buscaTipo == "Descrição") KeyboardType.Text else KeyboardType.Number,
                ),
            )
            Button(
                onClick = { vm.buscar(buscaTexto) { buscaTexto = "" } },
                enabled = !vm.buscando,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed),
            ) {
                Text(if (vm.buscando) "BUSCANDO..." else "BUSCAR", fontWeight = FontWeight.Bold)
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
                    Text("LIMPAR LISTA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { vm.imprimirTodas() },
                    enabled = !vm.processando,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed),
                ) {
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
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = MinhaLojaRed)
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
                            qtd = vm.quantidadeDe(item.ean),
                            onQtdChange = { vm.setQuantidade(item.ean, it) },
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
            title = { Text("Limpar Lista") },
            text = { Text("Tem certeza que deseja remover todos os itens?") },
            confirmButton = {
                Button(
                    onClick = { vm.limpar(); confirmarLimpar = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed),
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
        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear", tint = Color.White)
    }
}

@Composable
private fun EtiquetaCard(
    item: PriceTag,
    qtd: Int,
    onQtdChange: (Int) -> Unit,
    onImprimir: () -> Unit,
    onRemover: () -> Unit,
) {
    val formatador = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val pd = item.printingData
    val precoNormal = pd?.price?.value ?: 0.0
    val precoPromo = pd?.promotionalPrice?.value
    val takeAndWin = pd?.takeAndWin
    val installments = pd?.installments

    var precoTexto: String
    var badge: String
    var badgeColor: Color
    var leveGanhe: String? = null

    if (takeAndWin != null && takeAndWin.qty > 0 && takeAndWin.totalValue > 0) {
        val totalLeve = if (pd?.template == Constants.SIGN_TEMPLATE_LEVE_GANHE_CADA && precoPromo != null && precoPromo > 0)
            precoPromo else takeAndWin.totalValue
        leveGanhe = "Leve ${takeAndWin.qty} por R$ ${String.format(Locale("pt", "BR"), "%.2f", totalLeve)}"
        precoTexto = formatador.format(precoNormal)
        badge = "leve e ganhe"
        badgeColor = Color(0xFF2E7D32)
    } else if (installments != null && installments.installmentQuantity > 0) {
        val qtdParc = installments.installmentQuantity
        val valorParc = listOf(installments.installmentValue, pd?.displayPrice ?: 0.0, pd?.unitValue ?: 0.0, precoPromo ?: 0.0, precoNormal)
            .firstOrNull { it > 0 } ?: 0.0
        precoTexto = if (precoPromo != null && precoPromo > 0)
            "R$${String.format(Locale("pt", "BR"), "%.2f", precoPromo)} | ${qtdParc}x de R$${String.format(Locale("pt", "BR"), "%.2f", valorParc)}"
        else
            "${formatador.format(precoNormal)} | ${qtdParc}x de R$${String.format(Locale("pt", "BR"), "%.2f", valorParc)}"
        badge = "parcelado"
        badgeColor = Color(0xFFFF9800)
    } else if (precoPromo != null && precoPromo > 0 && precoPromo < precoNormal) {
        precoTexto = "De ${formatador.format(precoNormal)} | Por ${formatador.format(precoPromo)}"
        badge = "de por"
        badgeColor = Color(0xFFFF9800)
    } else {
        precoTexto = formatador.format(precoNormal)
        badge = "normal"
        badgeColor = Color(0xFF9E9E9E)
    }

    val deptNum = item.department.substringBefore(" -").trim()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "DEP: $deptNum | EAN: ${item.ean} / SAP: ${item.sap}",
                color = CINZA_LABEL,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.description,
                color = CINZA_TEXTO,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (leveGanhe != null) {
                        Text(leveGanhe, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(precoTexto, color = CINZA_TEXTO, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
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
                IconButton(onClick = onRemover) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = MinhaLojaRed)
                }
                Button(
                    onClick = onImprimir,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed),
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Imprimir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
