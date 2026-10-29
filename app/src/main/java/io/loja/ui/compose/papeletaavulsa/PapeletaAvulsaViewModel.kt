package io.loja.ui.compose.papeletaavulsa

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
import io.loja.network.ApiClient
import io.loja.network.PapeletaPrintingData
import io.loja.network.PriceSign
import io.loja.network.SendPriceSignRequest
import io.loja.network.SessionManager
import io.loja.ui.EletroSemanaActivity
import io.loja.ui.ListaViewModel
import io.loja.ui.ModeloEditavelActivity
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

data class ModeloConfig(
    val size: String,
    val semOverlay: Boolean,
    val enviarApi: Boolean,
    val apiSemOverlay: Boolean,
    val modoEditavel: Boolean,
    val modoVencimentos: Boolean,
    val hideGerarPreview: Boolean,
    val mostrarCheckboxOverlay: Boolean,
    val quantidadesCsv: String? = null,
)

sealed interface AcaoSucesso {
    data object Nenhuma : AcaoSucesso
    data class RemoverEan(val ean: String) : AcaoSucesso
    data object Limpar : AcaoSucesso
}

sealed interface PapeletaEvento {
    data class Toast(val msg: String) : PapeletaEvento
    data object SessaoExpirada : PapeletaEvento
    data class AbrirModelo(
        val list: List<PapeletaPrintingData>,
        val config: ModeloConfig,
        val acaoSucesso: AcaoSucesso,
    ) : PapeletaEvento

    data class AbrirEletro(
        val list: List<PapeletaPrintingData>,
        val size: String,
        val acaoSucesso: AcaoSucesso,
    ) : PapeletaEvento
}

class PapeletaAvulsaViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val api = ApiClient.getSlApiService(session)
    private val listaStore = ListaViewModel(app)

    val items: SnapshotStateList<PriceSign> = mutableStateListOf<PriceSign>().also {
        it.addAll(listaStore.getPapeletas())
    }
    val quantidades: SnapshotStateMap<String, Int> = mutableStateMapOf()
    val saldos: SnapshotStateMap<String, Double> = mutableStateMapOf()

    var tipoIdx by mutableStateOf(1)         // 0 Comum, 1 Promocional
        private set
    var modeloIdx by mutableStateOf(0)       // 0 Misto,1 Promocional,2 Prom.Ed.,3 Comum,4 Comum Ed.,5 Vencimentos
        private set
    var buscaTipo by mutableStateOf("EAN")
        private set
    var tamanhoIdx by mutableStateOf(2)      // 0 1x1, 1 2x1, 2 4x1, 3 6x1
        private set
    var colagem by mutableStateOf(listaStore.getColagem())
        private set
    var buscando by mutableStateOf(false)
        private set
    var processando by mutableStateOf(false)
        private set

    private val _eventos = MutableSharedFlow<PapeletaEvento>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventos: SharedFlow<PapeletaEvento> = _eventos

    val usarMisto get() = modeloIdx == 0
    val usarModeloEditavel get() = modeloIdx == 1 || modeloIdx == 2
    val usarComumEditavel get() = modeloIdx == 4
    val usarVencimentos get() = modeloIdx == 5
    val usarEletro get() = modeloIdx == 6
    val isModoEditavel get() = modeloIdx == 2 || modeloIdx == 4
    val tipoPapeleta: String
        get() = when {
            modeloIdx == 2 -> Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
            modeloIdx == 4 -> Constants.SIGN_TYPE_PAPELETA_COMUM
            tipoIdx == 0 -> Constants.SIGN_TYPE_PAPELETA_COMUM
            else -> Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
        }
    val tamanhoSelecionado: String
        get() = when (tamanhoIdx) {
            0 -> Constants.SIGN_SIZE_1X1
            1 -> Constants.SIGN_SIZE_2X1
            2 -> Constants.SIGN_SIZE_4X1
            else -> Constants.SIGN_SIZE_6X1
        }

    init {
        items.forEach { quantidades[it.ean] = it.quantity.coerceAtLeast(1) }
        carregarSaldos()
    }

    fun alterarTipo(i: Int) { tipoIdx = i }
    fun alterarModelo(i: Int) {
        modeloIdx = i
        if (i == 6 && tamanhoIdx == 3) tamanhoIdx = 2
    }
    fun alterarBuscaTipo(t: String) { buscaTipo = t }
    fun alterarTamanho(i: Int) { tamanhoIdx = i }

    fun quantidadeDe(ean: String): Int = quantidades[ean] ?: 1
    fun setQuantidade(ean: String, q: Int) { quantidades[ean] = q.coerceIn(1, 999) }

    fun definirColagem(code: String) {
        listaStore.setColagem(code)
        colagem = code
    }

    private fun emitir(e: PapeletaEvento) { _eventos.tryEmit(e) }

    fun buscar(texto: String, onFim: () -> Unit) {
        val termo = texto.trim()
        if (termo.isEmpty()) {
            emitir(PapeletaEvento.Toast(
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
                val data = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val responses = withContext(Dispatchers.IO) {
                    listOf(Constants.TIPO_COMUM, Constants.TIPO_PROMOCIONAL).map { type ->
                        try {
                            api.getPriceSignStandalone(
                                storeId = Constants.DEFAULT_STORE,
                                type = type,
                                ean = if (buscaTipo == "EAN") termo else null,
                                sapId = if (buscaTipo == "SAP") termo else null,
                                description = if (buscaTipo != "EAN" && buscaTipo != "SAP") termo else null,
                                startDate = data,
                            )
                        } catch (e: Exception) { null }
                    }
                }
                buscando = false
                onFim()
                var adicionados = 0
                var incrementados = 0
                responses.forEach { response ->
                    if (response != null && response.isSuccessful) {
                        response.body()?.items?.forEach { item ->
                            val idx = items.indexOfFirst { it.ean == item.ean }
                            if (idx >= 0) {
                                quantidades[item.ean] = quantidadeDe(item.ean) + 1
                                incrementados++
                            } else {
                                items.add(item)
                                quantidades[item.ean] = 1
                                adicionados++
                            }
                        }
                    }
                }
                if (adicionados > 0 || incrementados > 0) {
                    persistir()
                    val msg = buildString {
                        if (adicionados > 0) append("$adicionados adicionado(s)")
                        if (adicionados > 0 && incrementados > 0) append(" | ")
                        if (incrementados > 0) append("$incrementados incrementado(s)")
                    }
                    emitir(PapeletaEvento.Toast(msg))
                } else {
                    val anySuccess = responses.any { it?.isSuccessful == true }
                    if (anySuccess) {
                        emitir(PapeletaEvento.Toast("Nenhum item encontrado"))
                    } else {
                        val firstErr = responses.firstOrNull { it != null && !it.isSuccessful }
                        if (firstErr?.code() == 401) emitir(PapeletaEvento.SessaoExpirada)
                        else emitir(PapeletaEvento.Toast("Erro: ${firstErr?.code() ?: "?"}"))
                    }
                }
            } catch (e: Exception) {
                buscando = false
                onFim()
                LogHelper.e("PapeletaAvulsaVM: erro na busca", e)
                emitir(PapeletaEvento.Toast("Erro: ${e.message}"))
            }
        }
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

    fun remover(item: PriceSign) {
        val pos = items.indexOfFirst { it.id == item.id && it.ean == item.ean }
        if (pos >= 0) {
            items.removeAt(pos)
            quantidades.remove(item.ean)
            persistir()
        }
    }

    fun removerPorEan(ean: String) {
        val pos = items.indexOfFirst { it.ean == ean }
        if (pos >= 0) {
            items.removeAt(pos)
            quantidades.remove(ean)
            persistir()
        }
    }

    fun limpar() {
        if (items.isEmpty()) return
        items.clear(); quantidades.clear(); persistir()
        emitir(PapeletaEvento.Toast("Lista limpa"))
    }

    fun limparSilencioso() {
        items.clear(); quantidades.clear(); persistir()
    }

    fun imprimir(item: PriceSign, quantidade: Int) {
        val signData = item.printingData
        if (signData == null) {
            emitir(PapeletaEvento.Toast("Dados de impressão não disponíveis"))
            return
        }
        val tamanhoAtual = tamanhoSelecionado

        if (usarEletro) {
            val lista = (0 until quantidade.coerceAtLeast(1)).map {
                signData.copy(
                    template = Constants.SIGN_TEMPLATE_ELETRO_SEMANA,
                    quantity = 1,
                    size = tamanhoAtual,
                    installmentQuantity =
                        (signData.installmentQuantity ?: 0).takeIf { q -> q > 0 } ?: 10,
                    installmentPrice = signData.installmentPrice ?: 0.0,
                )
            }
            abrirEletro(lista, tamanhoAtual, AcaoSucesso.RemoverEan(item.ean))
            return
        }

        if (usarMisto || usarModeloEditavel || usarComumEditavel || usarVencimentos) {
            if (usarMisto) {
                val lista = (0 until quantidade).map {
                    ajustarDadosLeveGanhe(signData.copy(
                        template = signData.template ?: templatePorMovimento(item.movement),
                        quantity = 1,
                        size = tamanhoSelecionado,
                    ))
                }
                abrirModelo(lista, ModeloConfig(
                    size = tamanhoSelecionado, semOverlay = false, enviarApi = false,
                    apiSemOverlay = true, modoEditavel = false, modoVencimentos = false,
                    hideGerarPreview = false, mostrarCheckboxOverlay = true,
                ), AcaoSucesso.Nenhuma)
                return
            }
            if (usarModeloEditavel && isItemComum(item)) {
                emitir(PapeletaEvento.Toast("Esta papeleta é do tipo Comum, selecione o modo Comum"))
                return
            }
            if (usarComumEditavel && !isItemComum(item)) {
                emitir(PapeletaEvento.Toast("Esta papeleta é do tipo Promocional, selecione o modo Promocional"))
                return
            }
            val lista = (0 until quantidade).map {
                ajustarDadosLeveGanhe(signData.copy(
                    template = signData.template ?: templatePorMovimento(item.movement),
                    quantity = 1,
                    size = tamanhoAtual,
                ))
            }
            if (isModoEditavel || usarVencimentos) {
                abrirModelo(lista, ModeloConfig(
                    size = tamanhoAtual, semOverlay = usarComumEditavel, enviarApi = false,
                    apiSemOverlay = (usarMisto || usarModeloEditavel || usarVencimentos),
                    modoEditavel = true, modoVencimentos = usarVencimentos,
                    hideGerarPreview = false, mostrarCheckboxOverlay = !usarComumEditavel,
                ), AcaoSucesso.RemoverEan(item.ean))
            } else {
                abrirModelo(lista, ModeloConfig(
                    size = tamanhoAtual, semOverlay = usarComumEditavel, enviarApi = false,
                    apiSemOverlay = (usarMisto || usarModeloEditavel || usarVencimentos),
                    modoEditavel = false, modoVencimentos = false,
                    hideGerarPreview = (usarModeloEditavel || usarComumEditavel),
                    mostrarCheckboxOverlay = true,
                ), AcaoSucesso.RemoverEan(item.ean))
            }
            return
        }

        // Sem modelo -> envia direto pela API.
        val printingList = (0 until quantidade).map { signData.copy(quantity = 1, size = tamanhoAtual) }
        processando = true
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.sendPriceSigns(Constants.DEFAULT_STORE, SendPriceSignRequest(printingList))
                }
                processando = false
                if (response.isSuccessful) {
                    emitir(PapeletaEvento.Toast("${quantidade}x ${item.description}"))
                    remover(item)
                } else if (response.code() == 401) {
                    emitir(PapeletaEvento.SessaoExpirada)
                } else {
                    emitir(PapeletaEvento.Toast("Erro: ${response.code()}"))
                }
            } catch (e: Exception) {
                processando = false
                LogHelper.e("PapeletaAvulsaVM: erro imprimir", e)
                emitir(PapeletaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    fun imprimirTodas() {
        if (items.isEmpty()) { emitir(PapeletaEvento.Toast("Nenhuma papeleta para imprimir")); return }

        if (usarEletro) {
            val lista = items.flatMap { item ->
                val data = item.printingData ?: return@flatMap emptyList()
                (0 until quantidadeDe(item.ean)).map {
                    data.copy(
                        template = Constants.SIGN_TEMPLATE_ELETRO_SEMANA,
                        quantity = 1,
                        size = tamanhoSelecionado,
                        installmentQuantity =
                            (data.installmentQuantity ?: 0).takeIf { q -> q > 0 } ?: 10,
                        installmentPrice = data.installmentPrice ?: 0.0,
                    )
                }
            }
            if (lista.isEmpty()) {
                emitir(PapeletaEvento.Toast("Dados de impressão não disponíveis"))
                return
            }
            abrirEletro(lista, tamanhoSelecionado, AcaoSucesso.Limpar)
            return
        }

        if (usarMisto || usarModeloEditavel || usarComumEditavel || usarVencimentos) {
            if (usarMisto) {
                val lista = items.flatMap { item ->
                    val qtd = quantidadeDe(item.ean)
                    val data = item.printingData ?: return@flatMap emptyList()
                    (0 until qtd).map {
                        ajustarDadosLeveGanhe(data.copy(
                            template = data.template ?: templatePorMovimento(item.movement),
                            quantity = 1, size = tamanhoSelecionado,
                        ))
                    }
                }
                if (lista.isEmpty()) { emitir(PapeletaEvento.Toast("Nenhum item para imprimir")); return }
                abrirModelo(lista, ModeloConfig(
                    size = tamanhoSelecionado, semOverlay = false, enviarApi = false,
                    apiSemOverlay = true, modoEditavel = false, modoVencimentos = false,
                    hideGerarPreview = false, mostrarCheckboxOverlay = true,
                ), AcaoSucesso.Nenhuma)
                return
            }

            if (isModoEditavel) {
                val lista = items.flatMap { item ->
                    val ehComum = isItemComum(item)
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM && !ehComum) return@flatMap emptyList()
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL && ehComum) return@flatMap emptyList()
                    val passa = if (usarComumEditavel) ehComum else !ehComum
                    if (!passa) return@flatMap emptyList()
                    if (ehComum && !isComumElegivel(item)) return@flatMap emptyList()
                    val qtd = quantidadeDe(item.ean)
                    val data = item.printingData ?: return@flatMap emptyList()
                    (0 until qtd).map {
                        ajustarDadosLeveGanhe(data.copy(
                            template = data.template ?: templatePorMovimento(item.movement),
                            quantity = 1, size = tamanhoSelecionado,
                        ))
                    }
                }
                if (lista.isEmpty()) {
                    emitir(PapeletaEvento.Toast(if (usarComumEditavel) "Nenhum item do tipo Comum" else "Nenhum item na lista"))
                    return
                }
                abrirModelo(lista, ModeloConfig(
                    size = tamanhoSelecionado, semOverlay = usarComumEditavel, enviarApi = false,
                    apiSemOverlay = true, modoEditavel = true, modoVencimentos = false,
                    hideGerarPreview = false, mostrarCheckboxOverlay = !usarComumEditavel,
                ), AcaoSucesso.Limpar)
                return
            }

            val tamanhoAtual = tamanhoSelecionado
            val lista = mutableListOf<PapeletaPrintingData>()
            val qtds = mutableListOf<Int>()
            items.toList().forEach { item ->
                val ehComum = isItemComum(item)
                if (!usarVencimentos) {
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM && !ehComum) return@forEach
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL && ehComum) return@forEach
                    if (ehComum && !isComumElegivel(item)) return@forEach
                }
                val qtd = quantidadeDe(item.ean)
                val data = item.printingData ?: return@forEach
                val base = ajustarDadosLeveGanhe(data.copy(
                    template = data.template ?: templatePorMovimento(item.movement),
                    quantity = 1, size = tamanhoAtual,
                ))
                repeat(qtd) {
                    lista.add(base)
                    if (usarVencimentos) qtds.add(1)
                }
            }
            if (lista.isEmpty()) {
                emitir(PapeletaEvento.Toast(
                    when {
                        usarVencimentos -> "Nenhum item na lista"
                        usarComumEditavel -> "Nenhum item do tipo Comum"
                        else -> "Nenhum item do tipo Modelo"
                    }
                ))
                return
            }
            abrirModelo(lista, ModeloConfig(
                size = tamanhoAtual, semOverlay = usarComumEditavel, enviarApi = false,
                apiSemOverlay = (usarMisto || usarModeloEditavel || usarVencimentos),
                modoEditavel = usarVencimentos, modoVencimentos = usarVencimentos,
                hideGerarPreview = (usarModeloEditavel || usarComumEditavel),
                mostrarCheckboxOverlay = true,
                quantidadesCsv = if (usarVencimentos) qtds.joinToString(",") else null,
            ), AcaoSucesso.Limpar)
            return
        }

        // Sem modelo -> envia direto pela API.
        val printingDataList = mutableListOf<PapeletaPrintingData>()
        items.forEach { item ->
            val signData = item.printingData ?: return@forEach
            val ehComum = isItemComum(item)
            if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM && (!ehComum || !isComumElegivel(item))) return@forEach
            if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL && ehComum) return@forEach
            repeat(quantidadeDe(item.ean)) {
                printingDataList.add(signData.copy(quantity = 1, size = tamanhoSelecionado))
            }
        }
        if (printingDataList.isEmpty()) {
            emitir(PapeletaEvento.Toast(
                if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM) "Nenhum item do tipo Comum" else "Nenhum item do tipo Promocional"
            ))
            return
        }
        processando = true
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.sendPriceSigns(Constants.DEFAULT_STORE, SendPriceSignRequest(printingDataList))
                }
                processando = false
                if (response.isSuccessful) {
                    emitir(PapeletaEvento.Toast("${printingDataList.size} papeletas enviadas!"))
                    limparSilencioso()
                } else if (response.code() == 401) {
                    emitir(PapeletaEvento.SessaoExpirada)
                } else {
                    emitir(PapeletaEvento.Toast("Erro: ${response.code()}"))
                }
            } catch (e: Exception) {
                processando = false
                LogHelper.e("PapeletaAvulsaVM: erro imprimir todas", e)
                emitir(PapeletaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    private fun abrirEletro(list: List<PapeletaPrintingData>, size: String, acao: AcaoSucesso) {
        EletroSemanaActivity.dadosPendentesList = list.toMutableList()
        emitir(PapeletaEvento.AbrirEletro(list, size, acao))
    }

    private fun abrirModelo(list: List<PapeletaPrintingData>, config: ModeloConfig, acao: AcaoSucesso) {
        ModeloEditavelActivity.dadosPendentesList = list.toMutableList()
        emitir(PapeletaEvento.AbrirModelo(list, config, acao))
    }

    fun sincronizarDoScanner() {
        try {
            val fresh = listaStore.getPapeletas()
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
            LogHelper.e("PapeletaAvulsaVM: erro sincronizar scanner", e)
        }
    }

    fun persistir() {
        try {
            items.forEach { it.quantity = quantidadeDe(it.ean) }
            listaStore.savePapeletas(items.toList())
        } catch (e: Exception) {
            LogHelper.e("PapeletaAvulsaVM: erro persistir", e)
        }
    }

    private fun templatePorMovimento(movement: String?): String = when {
        movement?.equals("De/Por", ignoreCase = true) == true -> Constants.SIGN_TEMPLATE_DEPOR
        movement?.equals("Parcelado", ignoreCase = true) == true -> Constants.SIGN_TEMPLATE_DEPOR_PARCELADO
        else -> Constants.SIGN_TEMPLATE_MODELO_EDITAVEL
    }

    private fun ajustarDadosLeveGanhe(data: PapeletaPrintingData): PapeletaPrintingData =
        Constants.ajustarDadosLeveGanhe(data)

    private fun isItemComum(item: PriceSign): Boolean {
        val data = item.printingData ?: return true
        if (data.template == "por_de_parcelado") return true
        val hasPromo = (data.promotionPrice ?: 0.0) > 0
        val hasTakeAndWin = (data.takeAndWinQuantity ?: 0) > 0
        return !(hasPromo || hasTakeAndWin)
    }

    private fun isComumElegivel(item: PriceSign): Boolean {
        val data = item.printingData ?: return true
        val hasInstallment = (data.installmentQuantity ?: 0) > 0
        val hasPromo = (data.promotionPrice ?: 0.0) > 0
        val hasTakeAndWin = (data.takeAndWinQuantity ?: 0) > 0
        if (hasInstallment && !hasPromo && !hasTakeAndWin) return data.price > 149.99
        return true
    }
}
