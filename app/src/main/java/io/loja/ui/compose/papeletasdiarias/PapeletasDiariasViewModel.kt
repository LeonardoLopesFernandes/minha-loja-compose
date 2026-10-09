package io.loja.ui.compose.papeletasdiarias

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
import com.google.gson.JsonParser
import io.loja.network.ApiClient
import io.loja.network.PriceSign
import io.loja.network.SendPriceSignRequest
import io.loja.network.SessionManager
import io.loja.network.models.PriceTagFilterDepartment
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface PapeletaDiariaEvento {
    data class Toast(val msg: String) : PapeletaDiariaEvento
    data object SessaoExpirada : PapeletaDiariaEvento
    data class AbrirModelo(
        val list: List<io.loja.network.PapeletaPrintingData>,
        val size: String,
        val semOverlay: Boolean,
        val enviarApi: Boolean,
        val apiSemOverlay: Boolean,
        val modoEditavel: Boolean,
        val hideGerarPreview: Boolean,
        val mostrarCheckboxOverlay: Boolean,
    ) : PapeletaDiariaEvento
    data class AbrirPdf(val filePath: String) : PapeletaDiariaEvento
}

class PapeletasDiariasViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val api = ApiClient.getSlApiService(session)

    val items: SnapshotStateList<PriceSign> = mutableStateListOf()
    val selecionados: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val quantidades: SnapshotStateMap<String, Int> = mutableStateMapOf()
    val departamentosSelecionados: SnapshotStateList<String> = mutableStateListOf()

    var currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
        private set
    var sizes: List<String> = listOf(Constants.SIGN_SIZE_1X1, Constants.SIGN_SIZE_2X1, Constants.SIGN_SIZE_4X1, Constants.SIGN_SIZE_6X1)
        private set
    var departments: List<PriceTagFilterDepartment> = emptyList()
        private set
    var movementIdx by mutableStateOf(0)      // Todos, De/Por, Leve e Ganhe, Normal
        private set
    var tipoIdx by mutableStateOf(1)          // Normal, Promocional, Promocional Modelo, Normal Modelo
        private set
    var tamanhoIdx by mutableStateOf(0)
        private set
    var statusIdx by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var printed by mutableStateOf(0)
        private set
    var unprinted by mutableStateOf(0)
        private set
    var tomorrowCount by mutableStateOf(0)
        private set
    var carregando by mutableStateOf(false)
        private set
    var imprimindo by mutableStateOf(false)
        private set

    private var unfilteredItems: List<PriceSign> = emptyList()

    private val _eventos = MutableSharedFlow<PapeletaDiariaEvento>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventos: SharedFlow<PapeletaDiariaEvento> = _eventos

    private fun emitir(e: PapeletaDiariaEvento) { _eventos.tryEmit(e) }

    val isTomorrow: Boolean
        get() = currentDate != SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())

    val selectedMovement: String
        get() = when (movementIdx) {
            1 -> Constants.MOVEMENT_DEPOR
            2 -> Constants.MOVEMENT_LEVE
            3 -> Constants.MOVEMENT_COMUM
            else -> Constants.MOVEMENT_ALL
        }
    val selectedType: String
        get() = if (tipoIdx == 0 || tipoIdx == 3) Constants.SIGN_TYPE_PAPELETA_COMUM else Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
    val usarModeloPersonalizado: Boolean get() = tipoIdx == 2 || tipoIdx == 3
    val usarModeloComum: Boolean get() = tipoIdx == 3
    val selectedSize: String get() = sizes.getOrElse(tamanhoIdx) { Constants.SIGN_SIZE_4X1 }
    val selectedStatus: String
        get() = when (statusIdx) {
            1 -> Constants.STATUS_IMPRESSAS
            2 -> Constants.STATUS_NAO_IMPRESSAS
            else -> Constants.STATUS_ALL
        }

    fun chave(item: PriceSign) = item.id
    fun isSelecionado(item: PriceSign) = selecionados[chave(item)] == true
    fun quantidadeDe(item: PriceSign) = quantidades[chave(item)] ?: 1
    fun setQuantidade(item: PriceSign, q: Int) { quantidades[chave(item)] = q.coerceIn(1, 999) }
    fun toggleSelecionado(item: PriceSign, checked: Boolean) { selecionados[chave(item)] = checked }
    fun selecionarTodos() { items.forEach { selecionados[chave(it)] = true } }
    fun limparSelecao() { items.forEach { selecionados[chave(it)] = false } }
    val selecionadosCount: Int get() = items.count { selecionados[chave(it)] == true }
    val todosSelecionados: Boolean get() = items.isNotEmpty() && selecionadosCount == items.size

    init { carregarDados() }

    fun alterarMovement(i: Int) { movementIdx = i }
    fun alterarTipo(i: Int) { tipoIdx = i }
    fun alterarTamanho(i: Int) { tamanhoIdx = i }
    fun alterarStatus(i: Int) { statusIdx = i }

    fun toggleDepartamento(id: String) {
        if (departamentosSelecionados.contains(id)) departamentosSelecionados.remove(id) else departamentosSelecionados.add(id)
    }
    fun limparDepartamentos() { departamentosSelecionados.clear() }
    fun selecionarTodosDepartamentos() { departamentosComItens().forEach { if (!departamentosSelecionados.contains(it.id)) departamentosSelecionados.add(it.id) } }

    fun departamentosComItens(): List<PriceTagFilterDepartment> {
        val base = if (departments.isNotEmpty()) departments else unfilteredItems
            .map { it.department }
            .distinct()
            .sortedBy { it.substringBefore(" -").trim().toIntOrNull() ?: 999 }
            .map {
                val idx = it.indexOf(" - ")
                if (idx >= 0) PriceTagFilterDepartment(it.substring(0, idx).trim(), it.substring(idx + 3).trim())
                else PriceTagFilterDepartment(it.trim(), it.trim())
            }
        return base.filter { val (p, i) = contagemDepartamento(it.id); p > 0 || i > 0 }
    }

    fun contagemDepartamento(deptId: String): Pair<Int, Int> {
        val n = deptId.toIntOrNull() ?: return 0 to 0
        var pendente = 0; var impressas = 0
        for (item in unfilteredItems) {
            val d = item.department.substringBefore(" -").trim().toIntOrNull() ?: continue
            if (d == n) { if (item.status.equals("Impressa", true)) impressas++ else pendente++ }
        }
        return pendente to impressas
    }

    fun textoDepartamentos(): String {
        if (departamentosSelecionados.isEmpty()) return "Todos os departamentos"
        val names = departments.filter { departamentosSelecionados.contains(it.id) }.map { "${it.id.padStart(2, '0')} - ${it.label}" }
        return if (names.size <= 2) names.joinToString(", ") else "${names.size} departamentos selecionados"
    }

    fun carregarDados() {
        carregando = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
                val calAmanha = Calendar.getInstance()
                calAmanha.time = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).parse(currentDate)!!
                calAmanha.add(Calendar.DAY_OF_MONTH, 1)
                val amanhaStr = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(calAmanha.time)

                val filterD = async { api.getPriceSignFilters(loja) }
                val deptD = async { api.getPriceTagFilters(loja, currentDate) }
                val tomorrowD = async { api.getPriceSignDashboard(loja, amanhaStr) }
                val filterResponse = filterD.await()
                val deptResponse = deptD.await()
                val tomorrowResponse = tomorrowD.await()
                tomorrowCount = try {
                    tomorrowResponse.body()?.string()?.let {
                        JsonParser().parse(it).asJsonObject.getAsJsonObject("promoCardInfo")?.get("totalAvailable")?.asInt ?: 0
                    } ?: 0
                } catch (e: Exception) { 0 }

                if (filterResponse.isSuccessful) {
                    filterResponse.body()?.let { f ->
                        if (f.size.isNotEmpty()) {
                            sizes = f.size
                            tamanhoIdx = sizes.indexOf(Constants.SIGN_SIZE_1X1).coerceAtLeast(0)
                        }
                    }
                    departments = (deptResponse.body()?.departments ?: emptyList())
                        .filter { val n = it.id.toIntOrNull(); n == null || n < 68 || n > 98 }
                        .map { PriceTagFilterDepartment(it.id, it.label) }
                        .sortedBy { it.id.toIntOrNull() ?: 0 }
                }
                carregarPapeletas()
            } catch (e: Exception) {
                LogHelper.e("PapeletasDiariasVM: erro carregarDados", e)
                carregarPapeletas()
            }
        }
    }

    fun carregarPapeletas() {
        carregando = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
                val sizeParam = selectedSize
                val comumD = async { api.getPriceSigns(loja, Constants.SIGN_TYPE_PAPELETA_COMUM, null, sizeParam, selectedStatus, currentDate) }
                val promoD = async { api.getPriceSigns(loja, Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL, null, sizeParam, selectedStatus, currentDate) }
                val comumR = comumD.await()
                val promoR = promoD.await()
                val all = (comumR.body()?.priceSigns ?: emptyList()) + (promoR.body()?.priceSigns ?: emptyList())
                unfilteredItems = all
                var filtered = if (departamentosSelecionados.isNotEmpty()) {
                    val nums = departamentosSelecionados.mapNotNull { it.toIntOrNull() }
                    all.filter { it.department.substringBefore(" -").trim().toIntOrNull()?.let { n -> n in nums } == true }
                } else all
                if (selectedMovement == Constants.MOVEMENT_COMUM) {
                    filtered = filtered.filter {
                        val m = it.movement.uppercase()
                        !m.contains("DE") && !m.contains("POR") && !m.contains("LEVE") && !m.contains("GANHE") && !m.contains("PROMOCIONAL")
                    }
                } else if (selectedMovement.isNotEmpty()) {
                    filtered = filtered.filter { it.movement.equals(selectedMovement, ignoreCase = true) }
                }
                items.clear()
                items.addAll(filtered.sortedBy { it.department.substringBefore(" -").trim().toIntOrNull() ?: 999 })
                total = filtered.size
                printed = filtered.count { it.status.equals("Impressa", true) }
                unprinted = filtered.count { it.status.equals("Não impressa", true) }
            } catch (e: Exception) {
                LogHelper.e("PapeletasDiariasVM: erro carregarPapeletas", e)
            } finally {
                carregando = false
            }
        }
    }

    fun irParaAmanha() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
        val cal = Calendar.getInstance(); cal.time = fmt.parse(currentDate)!!; cal.add(Calendar.DAY_OF_MONTH, 1)
        currentDate = fmt.format(cal.time)
        items.clear(); unfilteredItems = emptyList()
        total = 0; printed = 0; unprinted = 0
        selecionados.clear(); quantidades.clear()
    }

    fun voltarHoje() {
        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
        carregarPapeletas()
    }

    fun imprimir() {
        val sel = items.filter { selecionados[chave(it)] == true }
        if (sel.isEmpty()) { emitir(PapeletaDiariaEvento.Toast("Selecione pelo menos uma papeleta")); return }
        if (usarModeloPersonalizado) { abrirModeloEditavel(sel); return }
        imprimirNormal(sel)
    }

    private fun abrirModeloEditavel(sel: List<PriceSign>) {
        val lista = mutableListOf<io.loja.network.PapeletaPrintingData>()
        for (item in sel) {
            val pd = item.printingData ?: continue
            val movement = item.movement.lowercase()
            val temLeve = (pd.takeAndWinQuantity ?: 0) > 0
            val template = if (usarModeloComum) pd.template ?: Constants.SIGN_TEMPLATE_NORMAL
            else if (temLeve) Constants.SIGN_TEMPLATE_MODELO
            else pd.template ?: when {
                movement == "de/por" -> Constants.SIGN_TEMPLATE_DEPOR
                movement == "parcelado" -> Constants.SIGN_TEMPLATE_DEPOR_PARCELADO
                else -> Constants.SIGN_TEMPLATE_MODELO
            }
            val base = pd.copy(template = template, size = selectedSize, quantity = 1)
            repeat(quantidadeDe(item)) { lista.add(base) }
        }
        if (lista.isEmpty()) { emitir(PapeletaDiariaEvento.Toast("Nenhum item para imprimir")); return }
        val todosComuns = lista.all { val hp = (it.promotionPrice ?: 0.0) > 0; val ht = (it.takeAndWinQuantity ?: 0) > 0; !(hp || ht) }
        io.loja.ui.ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
        emitir(PapeletaDiariaEvento.AbrirModelo(
            list = lista,
            size = selectedSize,
            semOverlay = false,
            enviarApi = usarModeloComum && todosComuns,
            apiSemOverlay = false,
            modoEditavel = usarModeloComum,
            hideGerarPreview = true,
            mostrarCheckboxOverlay = true,
        ))
    }

    private fun imprimirNormal(sel: List<PriceSign>) {
        imprimindo = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
                val printingDataList = sel.mapNotNull { it.printingData?.copy(size = selectedSize, quantity = quantidadeDe(it)) }
                if (printingDataList.isEmpty()) {
                    imprimindo = false
                    emitir(PapeletaDiariaEvento.Toast("Dados de impressão não disponíveis"))
                    return@launch
                }
                val response = withContext(Dispatchers.IO) { api.sendPriceSigns(loja, SendPriceSignRequest(printingDataList)) }
                imprimindo = false
                if (response.isSuccessful) {
                    emitir(PapeletaDiariaEvento.Toast("${printingDataList.sumOf { it.quantity ?: 1 }} papeletas enviadas para impressão!"))
                    carregarPapeletas()
                } else if (response.code() == 401) {
                    emitir(PapeletaDiariaEvento.SessaoExpirada)
                } else {
                    emitir(PapeletaDiariaEvento.Toast("❌ Erro ao enviar para impressão"))
                }
            } catch (e: Exception) {
                imprimindo = false
                emitir(PapeletaDiariaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    fun gerarRelatorio() {
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: Constants.DEFAULT_STORE
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
                val response = withContext(Dispatchers.IO) { api.getPapeletasReport(loja, hoje) }
                if (response.isSuccessful) {
                    val bytes = response.body()?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val f = File(getApplication<Application>().cacheDir, "relatorio_papeletas_${System.currentTimeMillis()}.pdf")
                        withContext(Dispatchers.IO) { FileOutputStream(f).use { it.write(bytes) } }
                        emitir(PapeletaDiariaEvento.AbrirPdf(f.absolutePath))
                    } else emitir(PapeletaDiariaEvento.Toast("Relatório vazio"))
                } else emitir(PapeletaDiariaEvento.Toast("Erro: ${response.code()}"))
            } catch (e: Exception) {
                emitir(PapeletaDiariaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }
}
