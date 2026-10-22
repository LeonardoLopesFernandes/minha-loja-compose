package io.loja.ui.compose.etiquetaavulsa

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import io.loja.network.ApiClient
import io.loja.network.PriceTag
import io.loja.network.PrintingData
import io.loja.network.SendPriceTagsRequest
import io.loja.network.SessionManager
import io.loja.network.SingleLabelItem
import io.loja.ui.ListaViewModel
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrinterOpcao(val id: String, val nome: String)

sealed interface EtiquetaEvento {
    data class Toast(val msg: String) : EtiquetaEvento
    data object SessaoExpirada : EtiquetaEvento
    data class AbrirEdicao(val json: String, val printerId: String) : EtiquetaEvento
}

class EtiquetaAvulsaViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val api = ApiClient.getSlApiService(session)
    private val listaStore = ListaViewModel(app)

    val items: SnapshotStateList<PriceTag> = mutableStateListOf<PriceTag>().also {
        it.addAll(listaStore.getEtiquetas())
    }
    val quantidades: SnapshotStateMap<String, Int> = mutableStateMapOf()
    val saldos: SnapshotStateMap<String, Double> = mutableStateMapOf()

    var buscaTipo by mutableStateOf("EAN")
        private set
    var modoEdicao by mutableStateOf(false)
        private set
    var printers by mutableStateOf(
        listOf(
            PrinterOpcao(Constants.PRINTER_ZEBRA_1, "Zebra 1"),
            PrinterOpcao(Constants.PRINTER_ZEBRA_2, "Zebra 2"),
        )
    )
        private set
    var printerSelecionado by mutableStateOf(0)
        private set
    var colagem by mutableStateOf(listaStore.getColagem())
        private set
    var buscando by mutableStateOf(false)
        private set
    var processando by mutableStateOf(false)
        private set

    private val _eventos = MutableSharedFlow<EtiquetaEvento>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val eventos: SharedFlow<EtiquetaEvento> = _eventos

    init {
        // Quantidades salvas (se houver) — mantém persistência entre sessões.
        items.forEach { quantidades[it.ean] = it.quantity.coerceAtLeast(1) }
        carregarImpressoras()
        carregarSaldos()
    }

    private fun emitir(e: EtiquetaEvento) {
        _eventos.tryEmit(e)
    }

    fun alterarBuscaTipo(t: String) { buscaTipo = t }
    fun alterarModoEdicao(v: Boolean) { modoEdicao = v }
    fun setPrinter(i: Int) { printerSelecionado = i }

    fun printerId(): String =
        printers.getOrNull(printerSelecionado)?.id ?: Constants.PRINTER_ZEBRA_1

    fun quantidadeDe(ean: String): Int = quantidades[ean] ?: 1

    fun setQuantidade(ean: String, q: Int) {
        quantidades[ean] = q.coerceIn(1, 999)
    }

    fun definirColagem(code: String) {
        listaStore.setColagem(code)
        colagem = code
    }

    fun carregarSaldos() {
        viewModelScope.launch {
            val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
            items.toList().forEach { item ->
                try {
                    val resp = withContext(Dispatchers.IO) {
                        api.getProductStore(loja, ean = item.ean, sapCode = item.sap)
                    }
                    resp.body()?.let { saldos[item.ean] = it.stock }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun carregarImpressoras() {
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
                val resp = withContext(Dispatchers.IO) { api.getPrinters(loja) }
                val lista = resp.body()?.printers?.filter { p ->
                    p.tags.any { t -> t.id.equals("GONDOLA", true) || t.name.equals("GONDOLA", true) }
                } ?: emptyList()
                if (lista.isNotEmpty()) {
                    printers = lista.map { PrinterOpcao(it.id, it.name) }
                    printerSelecionado = 0
                }
            } catch (e: Exception) {
                LogHelper.e("EtiquetaAvulsaVM: erro ao carregar impressoras", e)
            }
        }
    }

    fun buscar(texto: String, onFim: () -> Unit) {
        val termo = texto.trim()
        if (termo.isEmpty()) {
            emitir(EtiquetaEvento.Toast(
                when (buscaTipo) {
                    "EAN" -> "Digite um EAN"
                    "SAP" -> "Digite um SAP"
                    else -> "Digite uma descrição"
                }
            ))
            return
        }
        buscando = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
                val data = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val resp = withContext(Dispatchers.IO) {
                    when (buscaTipo) {
                        "EAN" -> api.getSingleLabelByEan(storeId = loja, ean = termo, startDate = data)
                        "SAP" -> api.getSingleLabelByEan(storeId = loja, sapId = termo, startDate = data)
                        else -> api.getSingleLabelByEan(storeId = loja, description = termo, startDate = data)
                    }
                }
                buscando = false
                onFim()
                if (resp.isSuccessful) {
                    val dataResp = resp.body()
                    if (dataResp != null && dataResp.items.isNotEmpty()) {
                        var adicionados = 0
                        var incrementados = 0
                        dataResp.items.forEach { item ->
                            val idx = items.indexOfFirst { it.ean == item.ean }
                            if (idx >= 0) {
                                quantidades[item.ean] = quantidadeDe(item.ean) + 1
                                incrementados++
                            } else {
                                items.add(item.toPriceTag())
                                quantidades[item.ean] = 1
                                adicionados++
                            }
                        }
                        persistir()
                        val msg = buildString {
                            if (adicionados > 0) append("$adicionados adicionado(s)")
                            if (adicionados > 0 && incrementados > 0) append(" | ")
                            if (incrementados > 0) append("$incrementados incrementado(s)")
                        }
                        emitir(EtiquetaEvento.Toast(msg))
                    } else {
                        emitir(EtiquetaEvento.Toast("Nenhum item encontrado"))
                    }
                } else if (resp.code() == 401) {
                    emitir(EtiquetaEvento.SessaoExpirada)
                } else {
                    emitir(EtiquetaEvento.Toast("Erro: ${resp.code()}"))
                }
            } catch (e: Exception) {
                buscando = false
                onFim()
                LogHelper.e("EtiquetaAvulsaVM: erro na busca", e)
                emitir(EtiquetaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    fun remover(item: PriceTag) {
        val pos = items.indexOfFirst { it.id == item.id }
        if (pos >= 0) {
            items.removeAt(pos)
            quantidades.remove(item.ean)
            persistir()
        }
    }

    fun limpar() {
        if (items.isEmpty()) return
        items.clear()
        quantidades.clear()
        persistir()
        emitir(EtiquetaEvento.Toast("Lista limpa"))
    }

    fun imprimir(item: PriceTag, quantidade: Int) {
        val pd = item.printingData
        if (pd == null) {
            emitir(EtiquetaEvento.Toast("Dados de impressão não disponíveis"))
            return
        }
        val lista = (0 until quantidade).map {
            pd.copy(
                quantity = 1,
                template = if (pd.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO
                else Constants.templateNormalizado(pd.template),
            )
        }
        if (modoEdicao) {
            emitir(EtiquetaEvento.AbrirEdicao(Gson().toJson(lista), printerId()))
            return
        }
        processando = true
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.sendPriceTagsToPrinter(
                        session.getUserStore() ?: Constants.DEFAULT_STORE,
                        printerId(),
                        Constants.TAG_GONDOLA,
                        SendPriceTagsRequest(lista),
                    )
                }
                processando = false
                if (resp.isSuccessful) {
                    emitir(EtiquetaEvento.Toast("${quantidade}x ${item.description}"))
                    remover(item)
                } else if (resp.code() == 401) {
                    emitir(EtiquetaEvento.SessaoExpirada)
                } else {
                    emitir(EtiquetaEvento.Toast("Erro: ${resp.code()}"))
                }
            } catch (e: Exception) {
                processando = false
                LogHelper.e("EtiquetaAvulsaVM: erro ao imprimir", e)
                emitir(EtiquetaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    fun imprimirTodas() {
        if (items.isEmpty()) {
            emitir(EtiquetaEvento.Toast("Nenhuma etiqueta para imprimir"))
            return
        }
        val lista = mutableListOf<PrintingData>()
        items.forEach { item ->
            val pd = item.printingData ?: return@forEach
            repeat(quantidadeDe(item.ean)) {
                lista.add(
                    pd.copy(
                        quantity = 1,
                        template = if (pd.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO
                        else Constants.templateNormalizado(pd.template),
                    )
                )
            }
        }
        if (lista.isEmpty()) {
            emitir(EtiquetaEvento.Toast("Dados de impressão não disponíveis"))
            return
        }
        if (modoEdicao) {
            emitir(EtiquetaEvento.AbrirEdicao(Gson().toJson(lista), printerId()))
            return
        }
        processando = true
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.sendPriceTagsToPrinter(
                        session.getUserStore() ?: Constants.DEFAULT_STORE,
                        printerId(),
                        Constants.TAG_GONDOLA,
                        SendPriceTagsRequest(lista),
                    )
                }
                processando = false
                if (resp.isSuccessful) {
                    emitir(EtiquetaEvento.Toast("${lista.size} etiquetas enviadas!"))
                    items.clear()
                    quantidades.clear()
                    persistir()
                } else if (resp.code() == 401) {
                    emitir(EtiquetaEvento.SessaoExpirada)
                } else {
                    emitir(EtiquetaEvento.Toast("Erro: ${resp.code()}"))
                }
            } catch (e: Exception) {
                processando = false
                LogHelper.e("EtiquetaAvulsaVM: erro ao imprimir todas", e)
                emitir(EtiquetaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    /** Scanner contínuo: traz o que foi salvo e soma as quantidades sem dobrar. */
    fun sincronizarDoScanner() {
        try {
            val fresh = listaStore.getEtiquetas()
            fresh.forEach { f ->
                val delta = (f.quantity - 1).coerceAtLeast(0)
                val idx = items.indexOfFirst { it.ean == f.ean }
                if (idx >= 0) {
                    if (delta > 0) quantidades[f.ean] = quantidadeDe(f.ean) + delta
                } else {
                    items.add(f)
                    quantidades[f.ean] = if (f.quantity > 1) f.quantity else 1
                }
                f.quantity = 1
            }
            persistir()
        } catch (e: Exception) {
            LogHelper.e("EtiquetaAvulsaVM: erro ao sincronizar scanner", e)
        }
    }

    fun persistir() {
        try {
            items.forEach { it.quantity = quantidadeDe(it.ean) }
            listaStore.saveEtiquetas(items.toList())
        } catch (e: Exception) {
            LogHelper.e("EtiquetaAvulsaVM: erro ao persistir", e)
        }
    }

    private fun SingleLabelItem.toPriceTag(): PriceTag {
        val pd = printingData?.let { s ->
            PrintingData(
                ean = s.ean,
                description = s.description,
                department = s.department,
                displayPrice = s.displayPrice,
                price = s.price,
                promotionalPrice = s.promotionalPrice,
                quantity = 1,
                movementType = s.movementType,
                unit = s.unit,
                unitQty = s.unitQty,
                unitValue = s.unitValue,
                printUnitValue = s.printUnitValue,
                codSap = s.codSap,
                takeAndWin = s.takeAndWin,
                referenceDate = s.referenceDate,
                template = s.template ?: if (s.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO else null,
                productName = null,
                promotionPrice = null,
                takeAndWinQuantity = null,
                takeAndWinPrice = null,
                takeAndWinPercent = null,
                installments = s.installments,
            )
        }
        return PriceTag(
            id, sap, department, ean, description, startDate, endDate, duration,
            price, movement, status, false, 1, pd,
        )
    }
}
