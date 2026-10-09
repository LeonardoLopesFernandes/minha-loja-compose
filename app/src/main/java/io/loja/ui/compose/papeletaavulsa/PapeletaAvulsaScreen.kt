package io.loja.ui.compose.papeletaavulsa

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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.DismissValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.loja.network.PriceSign
import io.loja.ui.BarcodeScannerActivity
import io.loja.ui.ModeloEditavelActivity
import io.loja.ui.compose.MinhaLojaRed
import io.loja.ui.compose.components.GreenBadgeContainer
import io.loja.ui.compose.components.QtyBoxAvulsa
import io.loja.ui.compose.components.SearchField
import io.loja.ui.compose.components.SpinnerDropdown
import io.loja.ui.compose.components.WhiteTypeBadge
import io.loja.utils.Constants
import io.loja.utils.SessionExpiredHandler
import java.text.NumberFormat
import java.util.Locale

private val CINZA_LABEL = Color(0xFF8C9BA5)
private val CINZA_TEXTO = Color(0xFF1E293B)

private val TIPOS = listOf("Comum", "Promocional")
private val MODELOS = listOf("Misto", "Promocional", "Promocional Editável", "Comum", "Comum Editável", "Vencimentos")
private val BUSCAS = listOf("EAN", "SAP", "Descrição do item")
private val TAMANHOS = listOf("1×1", "2×1", "4×1", "6×1")

@Composable
fun PapeletaAvulsaScreen(vm: PapeletaAvulsaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity

    var buscaTexto by remember { mutableStateOf("") }
    var confirmarLimpar by remember { mutableStateOf(false) }
    var pendingAcao by remember { mutableStateOf<AcaoSucesso>(AcaoSucesso.Nenhuma) }

    val launcherModelo = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when (val a = pendingAcao) {
                is AcaoSucesso.RemoverEan -> vm.removerPorEan(a.ean)
                AcaoSucesso.Limpar -> vm.limparSilencioso()
                AcaoSucesso.Nenhuma -> {}
            }
        }
        pendingAcao = AcaoSucesso.Nenhuma
    }

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
                putExtra(BarcodeScannerActivity.EXTRA_TIPO_LISTA, BarcodeScannerActivity.TIPO_LISTA_PAPELETAS)
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
                is PapeletaEvento.Toast ->
                    Toast.makeText(context, evento.msg, Toast.LENGTH_LONG).show()
                PapeletaEvento.SessaoExpirada ->
                    activity?.let { SessionExpiredHandler.handleSessionExpired(it) }
                is PapeletaEvento.AbrirModelo -> {
                    pendingAcao = evento.acaoSucesso
                    val c = evento.config
                    val i = Intent(context, ModeloEditavelActivity::class.java).apply {
                        putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, c.semOverlay)
                        putExtra(ModeloEditavelActivity.EXTRA_SIZE, c.size)
                        putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, c.enviarApi)
                        putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, c.apiSemOverlay)
                        putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, c.modoEditavel)
                        putExtra(ModeloEditavelActivity.EXTRA_MODO_VENCIMENTOS, c.modoVencimentos)
                        putExtra(ModeloEditavelActivity.EXTRA_HIDE_GERAR_PREVIEW, c.hideGerarPreview)
                        putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, c.mostrarCheckboxOverlay)
                        c.quantidadesCsv?.let { putExtra(ModeloEditavelActivity.EXTRA_QUANTIDADES, it) }
                    }
                    launcherModelo.launch(i)
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpinnerDropdown("TIPO", io.loja.R.drawable.ic_receipt_small, TIPOS, vm.tipoIdx, { it }, { vm.alterarTipo(it) }, Modifier.weight(1f))
                    SpinnerDropdown("MODELO", io.loja.R.drawable.ic_tune_small, MODELOS, vm.modeloIdx, { it }, { vm.alterarModelo(it) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpinnerDropdown(
                        "BUSCA", io.loja.R.drawable.ic_qr_code_small, BUSCAS,
                        BUSCAS.indexOf(vm.buscaTipo).coerceAtLeast(0),
                        { it }, { vm.alterarBuscaTipo(BUSCAS[it]) }, Modifier.weight(1f),
                    )
                    SpinnerDropdown("TAMANHO", io.loja.R.drawable.ic_aspect_ratio_small, TAMANHOS, vm.tamanhoIdx, { it }, { vm.alterarTamanho(it) }, Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        SearchField(
            value = buscaTexto,
            onValueChange = { buscaTexto = it },
            placeholder = when (vm.buscaTipo) {
                "EAN" -> "Digite o EAN"
                "SAP" -> "Digite o SAP"
                else -> "Buscar Por Descrição"
            },
            numerico = vm.buscaTipo != "Descrição",
            buscando = vm.buscando,
            onBuscar = { vm.buscar(buscaTexto) { buscaTexto = "" } },
        )

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
                    colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed, contentColor = Color.White),
                ) {
                    Text(if (vm.processando) "IMPRIMINDO..." else "IMPRIMIR TODAS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (vm.items.size == 1) "1 item na lista" else "${vm.items.size} itens na lista",
                color = MinhaLojaRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

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
                        PapeletaCard(
                            item = item,
                            qtd = vm.quantidadeDe(item.ean),
                            onQtdChange = { vm.setQuantidade(item.ean, it) },
                            onImprimir = { vm.imprimir(item, vm.quantidadeDe(item.ean)) },
                            onRemover = { vm.remover(item) },
                        )
                    }
                }
            }

            FabScanner(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
            .clickable { if (!revelado) revelado = true else onAbrir() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear", tint = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PapeletaCard(
    item: PriceSign,
    qtd: Int,
    onQtdChange: (Int) -> Unit,
    onImprimir: () -> Unit,
    onRemover: () -> Unit,
) {
    val dismissState = rememberDismissState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == DismissValue.DismissedToEnd) onRemover()
    }
    val f = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val num = remember { NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
    val pd = item.printingData
    val precoNormal = pd?.price ?: 0.0
    val precoPromo = pd?.promotionPrice
    val takeQty = pd?.takeAndWinQuantity
    val takePrice = pd?.takeAndWinPrice

    var leve: String? = null
    var valor = f.format(precoNormal)
    var tipo = "normal"
    var tipoCor = Color(0xFF9E9E9E)

    if (takeQty != null && takeQty > 0 && takePrice != null && takePrice > 0) {
        val totalLeve = Constants.totalLeveGanhePorTemplate(pd?.template, precoPromo, takePrice, takeQty)
        leve = "Leve $takeQty por ${f.format(totalLeve)}"
        valor = f.format(precoNormal)
        tipo = "leve e ganhe"; tipoCor = Color(0xFF2E7D32)
    } else if ((pd?.installmentQuantity ?: 0) > 0) {
        val qtdParc = pd?.installmentQuantity?.takeIf { it > 0 }
        val valorParc = pd?.installmentPrice?.takeIf { it > 0 } ?: precoPromo?.takeIf { it > 0 } ?: precoNormal
        val por = precoPromo?.takeIf { it > 0 }
        valor = if (qtdParc != null && por != null && valorParc > 0)
            "R$${num.format(por)} | ${qtdParc}x de R$${num.format(valorParc)}"
        else if (qtdParc != null)
            "${f.format(precoNormal)} | ${qtdParc}x de R$${String.format(Locale("pt", "BR"), "%.2f", valorParc)}"
        else if (valorParc > 0) f.format(valorParc)
        else item.price
        tipo = "parcelado"; tipoCor = Color(0xFFFF9800)
    } else if (precoPromo != null && precoPromo > 0 && precoPromo < precoNormal) {
        valor = "De ${f.format(precoNormal)} | Por ${f.format(precoPromo)}"
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
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Color.White)
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
                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                    Text(
                        "DEP: $deptNum | EAN: ${item.ean} / SAP: ${item.sap}",
                        color = Color(0xFF757575), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (leve != null) {
                        GreenBadgeContainer {
                            Text(leve, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            Text("|", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            Text(valor, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            WhiteTypeBadge(tipo, tipoCor, Modifier.padding(start = 6.dp))
                        }
                    } else {
                        GreenBadgeContainer {
                            Text(valor, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            WhiteTypeBadge(tipo, tipoCor, Modifier.padding(start = 8.dp))
                        }
                    }
                }
                IconButton(onClick = onRemover, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = Color(0xFFD81B3A), modifier = Modifier.size(20.dp))
                }
            }
        }
            }
        },
    )
}
