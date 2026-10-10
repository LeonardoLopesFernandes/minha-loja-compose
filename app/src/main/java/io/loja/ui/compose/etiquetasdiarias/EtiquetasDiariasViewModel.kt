package io.loja.ui.compose.etiquetasdiarias

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
import io.loja.network.Department
import io.loja.network.PrintingData
import io.loja.network.PriceTag
import io.loja.network.SendPriceTagsRequest
import io.loja.network.SessionManager
import io.loja.network.TagFilter
import io.loja.network.models.PriceTagFilterDepartment
import io.loja.network.models.PriceTagInstallments
import io.loja.network.models.PriceTagItem
import io.loja.network.models.PriceTagPrice
import io.loja.network.models.PriceTagPrinter
import io.loja.network.models.PriceTagPrintingData
import io.loja.network.models.PriceTagPromotionalPrice
import io.loja.network.models.PriceTagTakeAndWin
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

sealed interface EtiquetaDiariaEvento {
    data class Toast(val msg: String) : EtiquetaDiariaEvento
    data object SessaoExpirada : EtiquetaDiariaEvento
    data class AbrirGondola(val json: String, val printerId: String) : EtiquetaDiariaEvento
    data class AbrirPdf(val filePath: String) : EtiquetaDiariaEvento
}

class EtiquetasDiariasViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val api = ApiClient.getSlApiService(session)

    val items: SnapshotStateList<PriceTagItem> = mutableStateListOf()
    val selecionados: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val quantidades: SnapshotStateMap<String, Int> = mutableStateMapOf()
    val departamentosSelecionados: SnapshotStateList<String> = mutableStateListOf()

    var currentDate by mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date()))
        private set
    var printers: List<PriceTagPrinter> = emptyList()
        private set
    var departments: List<PriceTagFilterDepartment> = emptyList()
        private set
    var printerIdx by mutableStateOf(0)
        private set
    var statusIdx by mutableStateOf(0)      // 0 Todos, 1 Impressas, 2 Não impressas
        private set
    var modoIdx by mutableStateOf(0)        // 0 Normal, 1 Edição
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

    private var unfilteredItems: List<PriceTagItem> = emptyList()
    private val enviadosOtimista = mutableSetOf<String>()

    private val _eventos = MutableSharedFlow<EtiquetaDiariaEvento>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventos: SharedFlow<EtiquetaDiariaEvento> = _eventos

    private fun emitir(e: EtiquetaDiariaEvento) { _eventos.tryEmit(e) }

    val isTomorrow: Boolean
        get() = currentDate != SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())

    val selectedStatus: String
        get() = when (statusIdx) {
            1 -> "IMPRESSAS"
            2 -> "NAO_IMPRESSAS"
            else -> "IMPRESSAS,NAO_IMPRESSAS"
        }

    val selectedPrinter: String
        get() = printers.getOrNull(printerIdx)?.printerId ?: "ZEBRA_1"

    // Labels como no Kotlin: Zebra 1 / Zebra 2 (senão usa o label da API).
    val printerLabels: List<String>
        get() = printers.map { p ->
            when {
                p.printerId.contains("1", ignoreCase = true) -> "Zebra 1"
                p.printerId.contains("2", ignoreCase = true) -> "Zebra 2"
                else -> p.label
            }
        }

    fun chave(item: PriceTagItem) = "${item.ean}|${item.sap}"

    fun isSelecionado(item: PriceTagItem) = selecionados[chave(item)] == true
    fun quantidadeDe(item: PriceTagItem) = quantidades[chave(item)] ?: 1
    fun setQuantidade(item: PriceTagItem, q: Int) { quantidades[chave(item)] = q.coerceIn(1, 999) }
    fun toggleSelecionado(item: PriceTagItem, checked: Boolean) { selecionados[chave(item)] = checked }

    fun selecionarTodos() { items.forEach { selecionados[chave(it)] = true } }
    fun limparSelecao() { items.forEach { selecionados[chave(it)] = false } }
    val selecionadosCount: Int get() = items.count { selecionados[chave(it)] == true }
    val todosSelecionados: Boolean get() = items.isNotEmpty() && selecionadosCount == items.size

    init {
        carregarDados()
    }

    fun carregarDados() {
        carregando = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: "L291"
                val calAmanha = Calendar.getInstance()
                calAmanha.time = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).parse(currentDate)!!
                calAmanha.add(Calendar.DAY_OF_MONTH, 1)
                val amanhaStr = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(calAmanha.time)

                val summaryDeferred = async { api.getPriceTags(loja, currentDate) }
                val filterDeferred = async { api.getPriceTagFilters(loja, currentDate) }
                val tomorrowDeferred = async { api.getPriceTags(loja, amanhaStr) }
                val summaryResponse = summaryDeferred.await()
                val filterResponse = filterDeferred.await()
                val tomorrowResponse = tomorrowDeferred.await()
                tomorrowCount = tomorrowResponse.body()?.page?.infoTag?.totalTags ?: 0

                if (summaryResponse.isSuccessful && filterResponse.isSuccessful) {
                    filterResponse.body()?.let { filters ->
                        printers = filters.tags
                            .filter { it.printerId.startsWith("ZEBRA", ignoreCase = true) }
                            .map { it.toPriceTagPrinter() }
                        departments = filters.departments
                            .filter { val n = it.id.toIntOrNull(); n == null || n < 68 || n > 98 }
                            .map { it.toPriceTagFilterDepartment() }
                            .sortedBy { it.id.toIntOrNull() ?: 0 }
                        printerIdx = 0
                    }
                } else {
                    aplicarFallback()
                }
                carregarEtiquetas()
            } catch (e: Exception) {
                LogHelper.e("EtiquetasDiariasVM: erro carregarDados", e)
                aplicarFallback()
                carregarEtiquetas()
            }
        }
    }

    private fun aplicarFallback() {
        if (printers.isEmpty()) {
            printers = listOf(
                PriceTagPrinter("ZEBRA_1", "GONDOLA", "Zebra 1"),
                PriceTagPrinter("ZEBRA_2", "GONDOLA", "Zebra 2"),
            )
        }
        departments = emptyList()
        departamentosSelecionados.clear()
    }

    fun alterarPrinter(i: Int) { printerIdx = i }
    fun alterarStatus(i: Int) { statusIdx = i }
    fun alterarModo(i: Int) { modoIdx = i }

    fun toggleDepartamento(id: String) {
        if (departamentosSelecionados.contains(id)) departamentosSelecionados.remove(id)
        else departamentosSelecionados.add(id)
    }

    fun selecionarTodosDepartamentos() {
        departamentosComItens().forEach { if (!departamentosSelecionados.contains(it.id)) departamentosSelecionados.add(it.id) }
    }

    fun limparDepartamentos() { departamentosSelecionados.clear() }

    fun textoDepartamentos(): String {
        if (departamentosSelecionados.isEmpty()) return "Todos os departamentos"
        val names = departments.filter { departamentosSelecionados.contains(it.id) }
            .map { "${it.id.padStart(2, '0')} - ${it.label}" }
        return if (names.size <= 2) names.joinToString(", ") else "${names.size} departamentos selecionados"
    }

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
        val deptNum = deptId.toIntOrNull() ?: return 0 to 0
        var pendente = 0; var impressas = 0
        for (item in unfilteredItems) {
            val n = item.department.substringBefore(" -").trim().toIntOrNull() ?: continue
            if (n == deptNum) {
                if (item.status.equals("Impressa", true)) impressas++ else pendente++
            }
        }
        return pendente to impressas
    }

    fun carregarEtiquetas() {
        carregando = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: "L291"
                val response = withContext(Dispatchers.IO) {
                    api.getPriceTagsByStatus(loja, selectedStatus, startDate = currentDate)
                }
                if (response.isSuccessful) {
                    val all = response.body()?.priceTags?.map { it.toPriceTagItem() } ?: emptyList()
                    unfilteredItems = all
                    val filtered = if (departamentosSelecionados.isNotEmpty()) {
                        val nums = departamentosSelecionados.mapNotNull { it.toIntOrNull() }
                        all.filter { it.department.substringBefore(" -").trim().toIntOrNull()?.let { n -> n in nums } == true }
                    } else all
                    items.clear()
                    items.addAll(filtered.sortedBy { it.department.substringBefore(" -").trim().toIntOrNull() ?: 999 })
                    total = filtered.size
                    printed = filtered.count { it.status.equals("Impressa", true) || enviadosOtimista.contains(chave(it)) }
                    unprinted = filtered.count { it.status.equals("Não impressa", true) && !enviadosOtimista.contains(chave(it)) }
                } else if (response.code() == 401) {
                    emitir(EtiquetaDiariaEvento.SessaoExpirada)
                }
            } catch (e: Exception) {
                LogHelper.e("EtiquetasDiariasVM: erro carregarEtiquetas", e)
            } finally {
                carregando = false
            }
        }
    }

    fun irParaAmanha() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
        val cal = Calendar.getInstance()
        cal.time = fmt.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_MONTH, 1)
        currentDate = fmt.format(cal.time)
        items.clear(); unfilteredItems = emptyList()
        total = 0; printed = 0; unprinted = 0
        selecionados.clear(); quantidades.clear()
        // Amanhã exige buscar manualmente.
    }

    fun voltarHoje() {
        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
        carregarEtiquetas()
    }

    fun imprimir() {
        val itensSel = items.filter { selecionados[chave(it)] == true }
        if (itensSel.isEmpty()) { emitir(EtiquetaDiariaEvento.Toast("Selecione pelo menos uma etiqueta")); return }

        if (modoIdx == 1) {
            val lista = itensSel.mapNotNull { it.printingData?.toPrintingData()?.copy(quantity = quantidadeDe(it)) }
            if (lista.isEmpty()) { emitir(EtiquetaDiariaEvento.Toast("Dados de impressão não disponíveis")); return }
            emitir(EtiquetaDiariaEvento.AbrirGondola(Gson().toJson(lista), selectedPrinter))
            return
        }

        imprimindo = true
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: "L291"
                val printingDataList = itensSel.mapNotNull { it.printingData?.toPrintingData()?.copy(quantity = quantidadeDe(it)) }
                if (printingDataList.isEmpty()) {
                    imprimindo = false
                    emitir(EtiquetaDiariaEvento.Toast("Dados de impressão não disponíveis"))
                    return@launch
                }
                val tagId = printers.firstOrNull { it.printerId == selectedPrinter }?.tagId ?: "GONDOLA"
                val response = withContext(Dispatchers.IO) {
                    api.sendPriceTagsToPrinter(loja, selectedPrinter, tagId, SendPriceTagsRequest(printingDataList))
                }
                imprimindo = false
                if (response.isSuccessful) {
                    val totalEnviadas = printingDataList.sumOf { it.quantity ?: 1 }
                    emitir(EtiquetaDiariaEvento.Toast("$totalEnviadas etiquetas enviadas para impressão!"))
                    itensSel.forEach { enviadosOtimista.add(chave(it)) }
                    carregarEtiquetas()
                } else if (response.code() == 401) {
                    emitir(EtiquetaDiariaEvento.SessaoExpirada)
                } else {
                    emitir(EtiquetaDiariaEvento.Toast("❌ Erro ao enviar para impressão"))
                }
            } catch (e: Exception) {
                imprimindo = false
                emitir(EtiquetaDiariaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    fun gerarRelatorio() {
        viewModelScope.launch {
            try {
                val loja = session.getUserStore() ?: "L291"
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
                val response = withContext(Dispatchers.IO) { api.getEtiquetasReport(loja, hoje) }
                if (response.isSuccessful) {
                    val bytes = response.body()?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val f = File(getApplication<Application>().cacheDir, "relatorio_etiquetas_${System.currentTimeMillis()}.pdf")
                        withContext(Dispatchers.IO) { FileOutputStream(f).use { it.write(bytes) } }
                        emitir(EtiquetaDiariaEvento.AbrirPdf(f.absolutePath))
                    } else emitir(EtiquetaDiariaEvento.Toast("Relatório vazio"))
                } else {
                    emitir(EtiquetaDiariaEvento.Toast("Erro: ${response.code()}"))
                }
            } catch (e: Exception) {
                emitir(EtiquetaDiariaEvento.Toast("Erro: ${e.message}"))
            }
        }
    }

    private fun PriceTag.toPriceTagItem(): PriceTagItem = PriceTagItem(
        id = id, sap = sap, department = department, ean = ean,
        description = description, startDate = startDate,
        endDate = endDate, duration = duration, price = price,
        movement = movement, status = status, checkbox = checkbox, quantity = quantity,
        printingData = if (printingData?.price != null) printingData.toPriceTagPrintingData() else null,
    )

    private fun PrintingData.toPriceTagPrintingData(): PriceTagPrintingData = PriceTagPrintingData(
        template = template, ean = ean, description = description, department = department,
        displayPrice = displayPrice,
        price = PriceTagPrice(price?.value ?: 0.0, price?.discountPercent ?: 0, price?.startAt ?: "", price?.endAt ?: ""),
        promotionalPrice = promotionalPrice?.let { PriceTagPromotionalPrice(it.value, it.discountPercent, it.startAt, it.endAt) },
        quantity = quantity, movementType = movementType, unit = unit,
        unitQty = unitQty, unitValue = unitValue, printUnitValue = printUnitValue, codSap = codSap,
        takeAndWin = takeAndWin?.let { PriceTagTakeAndWin(it.discountValue, it.qty, it.totalValue) },
        installments = installments?.let { PriceTagInstallments(it.installmentQuantity, it.installmentValue) },
        referenceDate = referenceDate,
    )

    private fun PriceTagPrintingData.toPrintingData(): PrintingData = PrintingData(
        ean = ean, description = description, department = department, displayPrice = displayPrice,
        price = io.loja.network.PriceInfo(price.value, price.discountPercent, price.startAt, price.endAt),
        promotionalPrice = promotionalPrice?.let { io.loja.network.PromotionalPriceInfo(it.value, it.discountPercent, it.startAt, it.endAt) },
        quantity = quantity, movementType = movementType, unit = unit,
        unitQty = unitQty, unitValue = unitValue, printUnitValue = printUnitValue, codSap = codSap,
        takeAndWin = takeAndWin?.let { io.loja.network.TakeAndWin(it.discountValue, it.qty, it.totalValue) }
            ?: io.loja.network.TakeAndWin(0.0, 0, 0.0),
        referenceDate = referenceDate, template = null, productName = null, promotionPrice = null,
        takeAndWinQuantity = null, takeAndWinPrice = null, takeAndWinPercent = null,
        installments = installments?.let { io.loja.network.Installments(it.installmentQuantity, it.installmentValue) },
    )

    private fun TagFilter.toPriceTagPrinter(): PriceTagPrinter = PriceTagPrinter(printerId, tagId, label)
    private fun Department.toPriceTagFilterDepartment(): PriceTagFilterDepartment = PriceTagFilterDepartment(id, label)
}
