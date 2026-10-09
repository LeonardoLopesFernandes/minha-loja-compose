package io.loja.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.loja.R
import io.loja.adapters.PriceTagAdapter
import io.loja.network.ApiClient
import io.loja.network.Department
import io.loja.network.PrintingData
import io.loja.network.PriceTag
import io.loja.network.SendPriceTagsRequest
import io.loja.network.SessionManager
import io.loja.utils.ToastUtils
import io.loja.network.TagFilter
import io.loja.network.models.PriceTagFilterDepartment
import io.loja.network.models.PriceTagInstallments
import io.loja.network.models.PriceTagPrinter
import io.loja.network.models.PriceTagItem
import io.loja.network.models.PriceTagPrice
import io.loja.network.models.PriceTagPrintingData
import io.loja.network.models.PriceTagPromotionalPrice
import io.loja.network.models.PriceTagTakeAndWin
import io.loja.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class EtiquetasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: io.loja.network.ApiService
    private lateinit var adapter: PriceTagAdapter

    private lateinit var tvTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDateLabel: TextView
    private lateinit var tvDateContainer: View
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPrintedCount: TextView
    private lateinit var tvUnprintedCount: TextView
    private lateinit var tvSelectedCount: TextView
    private lateinit var cbSelectAll: android.widget.CheckBox
    private lateinit var tvDepartmentSelector: TextView
    private lateinit var spinnerPrinter: Spinner
    private lateinit var spinnerStatus: Spinner
    private lateinit var spinnerModo: Spinner
    private lateinit var btnBuscar: Button
    private lateinit var btnPrint: Button
    private lateinit var btnReport: Button
    private lateinit var btnNextDay: View
    private lateinit var btnNextDayDate: TextView
    private lateinit var btnMenuHamburguer: ImageView
    private lateinit var menuOverlay: View
    private lateinit var menuBackdrop: View
    private lateinit var containerMenuRetangular: View
    private var menuAberto = false
    private lateinit var rvPriceTags: RecyclerView
    private lateinit var layoutLoading: View
    private lateinit var layoutEmptyState: View
    private lateinit var tvTomorrowMessage: TextView

    private var currentDate: String = ""
    private var allItems = listOf<PriceTagItem>()
    private var unfilteredItems: List<PriceTagItem> = emptyList()
    private var currentDepartments = listOf<PriceTagFilterDepartment>()
    private var currentPrinters = listOf<PriceTagPrinter>()
    private var selectedPrinter: String = "ZEBRA_1"
    private var selectedModo: String = "Normal"
    private var selectedDepartments: MutableSet<String> = mutableSetOf()
    private var selectedStatus: String = "IMPRESSAS,NAO_IMPRESSAS"
    private var tomorrowCount: Int = 0

    // Itens enviados para impressão recentemente (atualização otimista do
    // contador de Impressas sem depender do backend refletir na hora).
    private val enviadosOtimista = mutableSetOf<String>()
    private fun chaveEnviado(item: PriceTagItem) = "${item.ean}|${item.sap}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_etiquetas)

        // Header branco -> status bar branca com ícones escuros.
        window.statusBarColor = android.graphics.Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        sessionManager = SessionManager(this)
        sessionManager.setCurrentActivity(this)

        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }

        apiService = ApiClient.getSlApiService(sessionManager)

        currentDate = intent.getStringExtra(EXTRA_DATE)
            ?: SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())

        initViews()
        setupListeners()
        carregarDados()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvDate = findViewById(R.id.tvDate)
        tvDateLabel = findViewById(R.id.tvDateLabel)
        tvDateContainer = findViewById(R.id.tvDateContainer)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvPrintedCount = findViewById(R.id.tvPrintedCount)
        tvUnprintedCount = findViewById(R.id.tvUnprintedCount)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        tvDepartmentSelector = findViewById(R.id.tvDepartmentSelector)
        spinnerPrinter = findViewById(R.id.spinnerPrinter)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        spinnerModo = findViewById(R.id.spinnerModo)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnPrint = findViewById(R.id.btnPrint)
        btnReport = findViewById(R.id.btnReport)
        btnNextDay = findViewById(R.id.btnNextDay)
        btnNextDayDate = findViewById(R.id.btnNextDayDate)
        rvPriceTags = findViewById(R.id.rvPriceTags)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvTomorrowMessage = findViewById(R.id.tvTomorrowMessage)
        btnMenuHamburguer = findViewById(R.id.btnMenuHamburguer)
        menuOverlay = findViewById(R.id.menuOverlay)
        menuBackdrop = findViewById(R.id.menuBackdrop)
        containerMenuRetangular = findViewById(R.id.containerMenuRetangular)

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
        val todayDate = fmt.format(Date())
        val isTomorrow = currentDate != todayDate
        val dateForDisplay = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
            .format(fmt.parse(currentDate)!!)
        tvDate.text = dateForDisplay

        val cal = Calendar.getInstance()
        cal.time = fmt.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_MONTH, 1)
        btnNextDayDate.text = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(cal.time)

        atualizarTituloEBotoes(isTomorrow)

        ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Carregando...")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPrinter.adapter = it
            spinnerStatus.adapter = it
        }

        rvPriceTags.layoutManager = LinearLayoutManager(this)
        adapter = PriceTagAdapter(emptyList()) { _, _, _ ->
            atualizarContadorSelecionados()
        }
        rvPriceTags.adapter = adapter
    }

    private fun setupListeners() {
        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) adapter.selectAll()
            else adapter.deselectAll()
            atualizarContadorSelecionados()
        }

        btnBuscar.setOnClickListener {
            btnBuscar.text = "BUSCANDO..."
            btnBuscar.isEnabled = false
            carregarEtiquetas()
            btnBuscar.text = "BUSCAR"
            btnBuscar.isEnabled = true
        }

        btnPrint.setOnClickListener {
            enviarParaImpressao()
        }

        val btnToggleCard = findViewById<ImageView>(R.id.btnToggleCard)
        btnToggleCard.setOnClickListener {
            val megaCard = findViewById<View>(R.id.layoutMegaCard)
            androidx.transition.TransitionManager.beginDelayedTransition(megaCard.parent as android.view.ViewGroup)
            val expanded = megaCard.visibility == View.VISIBLE
            megaCard.visibility = if (expanded) View.GONE else View.VISIBLE
            // Igual ao Flutter: expandido -> seta pra cima; recolhido -> pra baixo.
            btnToggleCard.setImageResource(if (expanded) R.drawable.ic_expand_more else R.drawable.ic_expand_less)
        }

        btnReport.setOnClickListener {
            gerarRelatorio()
        }

        tvDepartmentSelector.setOnClickListener {
            mostrarDialogoDepartamentos()
        }

        tvDate.setOnClickListener {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
            if (currentDate != today) {
                val intent = Intent(this, EtiquetasActivity::class.java)
                intent.putExtra(EXTRA_DATE, today)
                startActivity(intent)
            }
        }

        tvDateContainer.setOnClickListener {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
            if (currentDate != today) {
                val intent = Intent(this, EtiquetasActivity::class.java)
                intent.putExtra(EXTRA_DATE, today)
                startActivity(intent)
            }
        }

        btnMenuHamburguer.setOnClickListener {
            if (menuAberto) fecharMenu() else abrirMenu()
        }
        findViewById<View>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        menuBackdrop.setOnClickListener { if (menuAberto) fecharMenu() }
        findViewById<View>(R.id.btnMenuEtiquetas).setOnClickListener {
            fecharMenu()
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply { putExtra("open_fragment", "etiqueta_avulsa") })
        }
        findViewById<View>(R.id.btnMenuPapeletas).setOnClickListener {
            fecharMenu()
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply { putExtra("open_fragment", "papeleta_avulsa") })
        }
        findViewById<View>(R.id.btnMenuEtiquetasDiarias).setOnClickListener { fecharMenu() }
        findViewById<View>(R.id.btnMenuPapeletasDiarias).setOnClickListener {
            if (this.javaClass.simpleName != "PapeletasDiariasActivity") {
                fecharMenu()
                finish()
                startActivity(Intent(this, PapeletasDiariasActivity::class.java))
            } else fecharMenu()
        }

        tvTitle.setOnClickListener { finish() }

        btnNextDay.setOnClickListener {
            val cal = Calendar.getInstance()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
            val currentParsed = fmt.parse(currentDate)!!
            cal.time = currentParsed
            cal.add(Calendar.DAY_OF_MONTH, 1)
            currentDate = fmt.format(cal.time)
            val dateForDisplay = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(cal.time)
            tvDate.text = dateForDisplay
            // "Amanhã" é SEMPRE a data de hoje real + 1 (fica travado nela)
            val hoje = fmt.parse(fmt.format(Date()))!!
            val amanha = Calendar.getInstance()
            amanha.time = hoje
            amanha.add(Calendar.DAY_OF_MONTH, 1)
            btnNextDayDate.text = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(amanha.time)
            atualizarTituloEBotoes(true)
            // Aguarda o usuário selecionar os filtros e clicar em BUSCAR
            allItems = emptyList()
            unfilteredItems = emptyList()
            adapter.updateItems(emptyList())
            tvTotalCount.text = "0"
            tvPrintedCount.text = "0"
            tvUnprintedCount.text = "0"
            atualizarContadorSelecionados()
            mostrarLoading(false)
        }
    }

    private fun carregarDados() {
        mostrarLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        redirectToLogin()
                    }
                    return@launch
                }

                val loja = sessionManager.getUserStore() ?: "L291"

                val calAmanha = Calendar.getInstance()
                calAmanha.time = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).parse(currentDate)!!
                calAmanha.add(Calendar.DAY_OF_MONTH, 1)
                val amanhaStr = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(calAmanha.time)

                // As 3 chamadas em PARALELO (antes eram em série).
                val summaryDeferred = async { apiService.getPriceTags(loja, currentDate) }
                val filterDeferred = async { apiService.getPriceTagFilters(loja, currentDate) }
                val tomorrowDeferred = async { apiService.getPriceTags(loja, amanhaStr) }
                val summaryResponse = summaryDeferred.await()
                val filterResponse = filterDeferred.await()
                val tomorrowResponse = tomorrowDeferred.await()
                tomorrowCount = tomorrowResponse.body()?.page?.infoTag?.totalTags ?: 0

                withContext(Dispatchers.Main) {
                    if (summaryResponse.isSuccessful && filterResponse.isSuccessful) {
                        summaryResponse.body()?.let { summary ->
                            summary.page?.infoTag?.let { info ->
                                tvTotalCount.text = "${info.totalTags}"
                                tvPrintedCount.text = "${info.printedTags}"
                                tvUnprintedCount.text = "${info.unprintedTags}"
                            }
                        }

                        filterResponse.body()?.let { filters ->
                            currentPrinters = filters.tags
                                .filter { it.printerId.startsWith("ZEBRA", ignoreCase = true) }
                                .map { it.toPriceTagPrinter() }

                            currentDepartments = filters.departments
                                .filter { val idNum = it.id.toIntOrNull(); idNum == null || idNum < 68 || idNum > 98 }
                                .sortedBy { it.id.toIntOrNull() ?: 0 }
                                .map { it.toPriceTagFilterDepartment() }
                                .sortedBy { dept ->
                                    val numStr = dept.id.takeWhile { it.isDigit() }
                                    numStr.toIntOrNull() ?: 0
                                }

                            configurarSpinnersComDados(currentPrinters, currentDepartments)
                            return@withContext
                        }
                    }
                    configurarSpinnersFallback()
                    mostrarLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    configurarSpinnersFallback()
                    mostrarLoading(false)
                }
            }
        }
    }

    private fun configurarSpinnersComDados(
        printers: List<PriceTagPrinter>,
        departments: List<PriceTagFilterDepartment>
    ) {
        val printerDisplayNames = printers.map { p ->
            when {
                p.printerId.contains("1", ignoreCase = true) -> "Zebra 1"
                p.printerId.contains("2", ignoreCase = true) -> "Zebra 2"
                else -> p.label
            }
        }.toTypedArray()
        val printerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, printerDisplayNames)
        printerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrinter.adapter = printerAdapter
        spinnerPrinter.setSelection(0)
        spinnerPrinter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPrinter = printers[position].printerId
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        atualizarTextoDepartamento()

        configurarSpinnerStatus()
        configurarSpinnerModo()
        mostrarLoading(false)

        carregarEtiquetas()
    }

    private fun configurarSpinnersFallback() {
        val printers = listOf(
            PriceTagPrinter("ZEBRA_1", "GONDOLA", "Zebra 1"),
            PriceTagPrinter("ZEBRA_2", "GONDOLA", "Zebra 2")
        )
        currentPrinters = printers

        val printerNames = printers.map { it.label }.toTypedArray()
        val printerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, printerNames)
        printerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrinter.adapter = printerAdapter
        spinnerPrinter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPrinter = printers[position].printerId
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        currentDepartments = emptyList()
        selectedDepartments.clear()
        tvDepartmentSelector.text = "Todos os departamentos"

        configurarSpinnerStatus()
        configurarSpinnerModo()
        mostrarLoading(false)

        carregarEtiquetas()
    }

    private fun mostrarDialogoDepartamentos() {
        if (currentDepartments.isEmpty()) return
        val deptsComItens = currentDepartments.filter { dept ->
            val (pendente, impressas) = contagemDepartamento(dept.id)
            pendente > 0 || impressas > 0
        }
        if (deptsComItens.isEmpty()) {
            ToastUtils.show(this, "Nenhum departamento com impressão")
            return
        }
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_departamentos, null)
        val listView = view.findViewById<android.widget.ListView>(R.id.listDepartamentos)
        val tvSelecionarTodos = view.findViewById<android.widget.TextView>(R.id.tvSelecionarTodos)

        listView.adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = deptsComItens.size
            override fun getItem(position: Int): Any = deptsComItens[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView ?: LayoutInflater.from(this@EtiquetasActivity)
                    .inflate(R.layout.item_dialog_departamento, parent, false)
                val dept = deptsComItens[position]
                val cb = row.findViewById<android.widget.CheckBox>(R.id.cbDepartamento)
                val tvNome = row.findViewById<TextView>(R.id.tvDepartamentoNome)
                val tvPendente = row.findViewById<TextView>(R.id.tvCountPendente)
                val tvImpressas = row.findViewById<TextView>(R.id.tvCountImpressas)
                tvNome.text = "${dept.id.padStart(2, '0')} - ${dept.label}"
                cb.isChecked = dept.id in selectedDepartments
                val (pendente, impressas) = contagemDepartamento(dept.id)
                tvPendente.text = if (pendente > 0) "$pendente" else ""
                tvImpressas.text = if (impressas > 0) "$impressas" else ""
                row.setOnClickListener {
                    if (dept.id in selectedDepartments) selectedDepartments.remove(dept.id)
                    else selectedDepartments.add(dept.id)
                    cb.isChecked = dept.id in selectedDepartments
                }
                return row
            }
        }

        tvSelecionarTodos.setOnClickListener {
            for (dept in deptsComItens) selectedDepartments.add(dept.id)
            (listView.adapter as android.widget.BaseAdapter).notifyDataSetChanged()
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Todos os departamentos")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                atualizarTextoDepartamento()
                carregarEtiquetas()
            }
            .setNegativeButton("Limpar", null)
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            selectedDepartments.clear()
            (listView.adapter as android.widget.BaseAdapter).notifyDataSetChanged()
        }
    }

    private fun contagemDepartamento(deptId: String): Pair<Int, Int> {
        val deptNum = deptId.toIntOrNull() ?: return 0 to 0
        var pendente = 0
        var impressas = 0
        for (item in unfilteredItems) {
            val n = item.department.substringBefore(" -").trim().toIntOrNull() ?: continue
            if (n == deptNum) {
                if (item.status.equals("Impressa", true)) impressas++ else pendente++
            }
        }
        return pendente to impressas
    }

    private fun atualizarTextoDepartamento() {
        tvDepartmentSelector.text = if (selectedDepartments.isEmpty()) {
            "Todos os departamentos"
        } else {
            val names = currentDepartments
                .filter { it.id in selectedDepartments }
                .map { "${it.id.padStart(2, '0')} - ${it.label}" }
            if (names.size <= 2) names.joinToString(", ")
            else "${names.size} departamentos selecionados"
        }
    }

    private fun configurarSpinnerStatus() {
        val statusOptions = arrayOf("Todos os status", "Impressas", "Não impressas")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = statusAdapter
        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStatus = when (position) {
                    1 -> "IMPRESSAS"
                    2 -> "NAO_IMPRESSAS"
                    else -> "IMPRESSAS,NAO_IMPRESSAS"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun configurarSpinnerModo() {
        val modoOptions = arrayOf("Normal", "Edição")
        val modoAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modoOptions)
        modoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModo.adapter = modoAdapter
        spinnerModo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModo = modoOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun carregarEtiquetas() {
        mostrarLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        redirectToLogin()
                    }
                    return@launch
                }

                val loja = sessionManager.getUserStore() ?: "L291"

                val response = apiService.getPriceTagsByStatus(
                    storeId = loja,
                    status = selectedStatus,
                    startDate = currentDate
                )

                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    if (response.isSuccessful) {
                        val items = response.body()?.priceTags?.map { it.toPriceTagItem() } ?: emptyList()
                        unfilteredItems = items

                        val filtered = if (selectedDepartments.isNotEmpty()) {
                            val deptNums = selectedDepartments.mapNotNull { it.toIntOrNull() }
                            items.filter { item ->
                                val itemDeptNum = item.department.substringBefore(" -").trim().toIntOrNull()
                                itemDeptNum != null && itemDeptNum in deptNums
                            }
                        } else {
                            items
                        }

                        allItems = filtered
                        adapter.updateItems(filtered.sortedBy { it.department.substringBefore(" -").trim().toIntOrNull() ?: 999 })
                        atualizarContadorSelecionados()
                        val countTotal = filtered.size
                        val countPrinted = filtered.count { it.status.equals("Impressa", true) || enviadosOtimista.contains(chaveEnviado(it)) }
                        val countUnprinted = filtered.count { it.status.equals("Não impressa", true) && !enviadosOtimista.contains(chaveEnviado(it)) }
                        tvTotalCount.text = "$countTotal"
                        tvPrintedCount.text = "$countPrinted"
                        tvUnprintedCount.text = "$countUnprinted"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                }
            }
        }
    }

    private fun enviarParaImpressao() {
        val itemsWithQty = adapter.getSelectedItemsWithQuantities()
        if (itemsWithQty.isEmpty()) {
            ToastUtils.show(this, "Selecione pelo menos uma etiqueta")
            return
        }

        if (selectedModo == "Edição") {
            val printingDataList = itemsWithQty.mapNotNull { (item, qty) ->
                item.printingData?.toPrintingData()?.copy(quantity = qty)
            }
            if (printingDataList.isEmpty()) {
                ToastUtils.show(this, "Dados de impressão não disponíveis")
                return
            }
            val json = com.google.gson.Gson().toJson(printingDataList)
            val intent = Intent(this, GondolaEditavelActivity::class.java)
            intent.putExtra("ITEMS_JSON", json)
            intent.putExtra("PRINTER_ID", selectedPrinter)
            startActivity(intent)
            return
        }

        btnPrint.text = "IMPRIMINDO..."
        btnPrint.isEnabled = false
        mostrarLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        btnPrint.text = "🖨️ Imprimir"
                        btnPrint.isEnabled = true
                        redirectToLogin()
                    }
                    return@launch
                }

                val loja = sessionManager.getUserStore() ?: "L291"

                val printingDataList = itemsWithQty.mapNotNull { (item, qty) ->
                    item.printingData?.toPrintingData()?.copy(quantity = qty)
                }
                if (printingDataList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        btnPrint.text = "🖨️ Imprimir"
                        btnPrint.isEnabled = true
                        ToastUtils.show(this@EtiquetasActivity, "Dados de impressão não disponíveis")
                    }
                    return@launch
                }

                val selectedTagId = currentPrinters.firstOrNull { it.printerId == selectedPrinter }?.tagId ?: "GONDOLA"
                val response = apiService.sendPriceTagsToPrinter(
                    storeId = loja,
                    printerId = selectedPrinter,
                    tagId = selectedTagId,
                    request = SendPriceTagsRequest(printingDataList)
                )

                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    btnPrint.text = "🖨️ Imprimir"
                    btnPrint.isEnabled = true
                    if (response.isSuccessful) {
                        val totalEnviadas = printingDataList.sumOf { it.quantity ?: 1 }
                        ToastUtils.show(this@EtiquetasActivity, "$totalEnviadas etiquetas enviadas para impressão!", Toast.LENGTH_LONG)
                        // Marca os enviados como impressos imediatamente (otimista)
                        itemsWithQty.forEach { enviadosOtimista.add(chaveEnviado(it.first)) }
                        carregarEtiquetas()
                    } else {
                        ToastUtils.show(this@EtiquetasActivity, "❌ Erro ao enviar para impressão")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    btnPrint.text = "🖨️ Imprimir"
                    btnPrint.isEnabled = true
                    ToastUtils.show(this@EtiquetasActivity, "Erro: ${e.message}")
                }
            }
        }
    }

    private fun gerarRelatorio() {
        ToastUtils.show(this, "📄 Gerando relatório...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { ToastUtils.show(this@EtiquetasActivity, "Sessão expirada") }
                    return@launch
                }
                val loja = sessionManager.getUserStore() ?: "L291"
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
                val response = apiService.getEtiquetasReport(loja, hoje)
                if (response.isSuccessful) {
                    val pdfBytes = response.body()?.bytes()
                    if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                        val outputFile = File(cacheDir, "relatorio_etiquetas_${System.currentTimeMillis()}.pdf")
                        FileOutputStream(outputFile).use { it.write(pdfBytes) }
                        withContext(Dispatchers.Main) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                this@EtiquetasActivity,
                                "${packageName}.fileprovider",
                                outputFile
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(intent, "Abrir relatório"))
                        }
                    } else {
                        withContext(Dispatchers.Main) { ToastUtils.show(this@EtiquetasActivity, "Relatório vazio") }
                    }
                } else {
                    withContext(Dispatchers.Main) { ToastUtils.show(this@EtiquetasActivity, "Erro: ${response.code()}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ToastUtils.show(this@EtiquetasActivity, "Erro: ${e.message}") }
            }
        }
    }

    private fun atualizarTituloEBotoes(isTomorrow: Boolean) {
        tvTitle.text = if (isTomorrow) "Etiquetas de amanhã" else "Etiquetas de hoje"
        val tvDateLabel = findViewById<TextView>(R.id.tvDateLabel)
        val btnNextDayLabel = findViewById<TextView>(R.id.btnNextDayLabel)
        if (isTomorrow) {
            // Hoje (não selecionado) transparente, Amanhã (selecionado) verde claro
            tvDateContainer.setBackgroundResource(R.drawable.bg_pill_transparent)
            tvDateLabel.setTextColor(android.graphics.Color.parseColor("#000000"))
            tvDate.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnNextDay.setBackgroundResource(R.drawable.bg_date_pill_light_green)
            btnNextDayLabel.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnNextDayDate.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnNextDay.isEnabled = false
        } else {
            tvDateContainer.setBackgroundResource(R.drawable.bg_date_pill_light_green)
            tvDateLabel.setTextColor(android.graphics.Color.parseColor("#000000"))
            tvDate.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnNextDay.setBackgroundResource(R.drawable.bg_pill_transparent)
            btnNextDayLabel.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnNextDayDate.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnNextDay.isEnabled = true
        }
    }

    private fun atualizarContadorSelecionados() {
        val count = adapter.getSelectedCount()
        val total = adapter.itemCount
        tvSelectedCount.text = "Selecionados: $count"
        cbSelectAll.setOnCheckedChangeListener(null)
        cbSelectAll.isChecked = total > 0 && count == total
        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) adapter.selectAll()
            else adapter.deselectAll()
            atualizarContadorSelecionados()
        }
    }

    private fun mostrarLoading(mostrar: Boolean) {
        layoutLoading.visibility = if (mostrar) View.VISIBLE else View.GONE
        rvPriceTags.visibility = if (mostrar) View.GONE else View.VISIBLE
        if (!mostrar) atualizarEstadoVazio()
    }

    private fun atualizarEstadoVazio() {
        val isEmpty = adapter.itemCount == 0
        layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvPriceTags.visibility = if (isEmpty) View.GONE else View.VISIBLE

        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
        if (isEmpty && currentDate == hoje) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val amanha = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(cal.time)
            tvTomorrowMessage.text = "Você tem $tomorrowCount etiqueta(s) de amanhã ($amanha) para imprimir! Selecione o tipo, os departamentos e o status para começar a imprimir."
            tvTomorrowMessage.visibility = View.VISIBLE
        } else {
            tvTomorrowMessage.visibility = View.GONE
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_DATE = "extra_date"
        private fun PriceTag.toPriceTagItem(): PriceTagItem = PriceTagItem(
            id = id, sap = sap, department = department, ean = ean,
            description = description, startDate = startDate,
            endDate = endDate, duration = duration, price = price,
            movement = movement, status = status, checkbox = checkbox,
            quantity = quantity,
            printingData = if (printingData?.price != null) printingData.toPriceTagPrintingData() else null
        )

        private fun PrintingData.toPriceTagPrintingData(): PriceTagPrintingData = PriceTagPrintingData(
            template = template, ean = ean, description = description, department = department,
            displayPrice = displayPrice,
            price = PriceTagPrice(price?.value ?: 0.0, price?.discountPercent ?: 0, price?.startAt ?: "", price?.endAt ?: ""),
            promotionalPrice = promotionalPrice?.let {
                PriceTagPromotionalPrice(it.value, it.discountPercent, it.startAt, it.endAt)
            },
            quantity = quantity, movementType = movementType, unit = unit,
            unitQty = unitQty, unitValue = unitValue, printUnitValue = printUnitValue,
            codSap = codSap,
            takeAndWin = takeAndWin?.let {
                PriceTagTakeAndWin(it.discountValue, it.qty, it.totalValue)
            },
            installments = installments?.let {
                PriceTagInstallments(it.installmentQuantity, it.installmentValue)
            },
            referenceDate = referenceDate
        )

        private fun PriceTagPrintingData.toPrintingData(): PrintingData = PrintingData(
            ean = ean, description = description, department = department,
            displayPrice = displayPrice,
            price = io.loja.network.PriceInfo(price.value, price.discountPercent, price.startAt, price.endAt),
            promotionalPrice = promotionalPrice?.let {
                io.loja.network.PromotionalPriceInfo(it.value, it.discountPercent, it.startAt, it.endAt)
            },
            quantity = quantity, movementType = movementType, unit = unit,
            unitQty = unitQty, unitValue = unitValue, printUnitValue = printUnitValue,
            codSap = codSap,
            takeAndWin = takeAndWin?.let {
                io.loja.network.TakeAndWin(it.discountValue, it.qty, it.totalValue)
            } ?: io.loja.network.TakeAndWin(0.0, 0, 0.0),
            referenceDate = referenceDate,
            template = null, productName = null, promotionPrice = null,
            takeAndWinQuantity = null, takeAndWinPrice = null, takeAndWinPercent = null,
            installments = installments?.let {
                io.loja.network.Installments(it.installmentQuantity, it.installmentValue)
            }
        )

        private fun TagFilter.toPriceTagPrinter(): PriceTagPrinter = PriceTagPrinter(
            printerId = printerId, tagId = tagId, label = label
        )

        private fun Department.toPriceTagFilterDepartment(): PriceTagFilterDepartment = PriceTagFilterDepartment(
            id = id, label = label
        )
    }

    private fun abrirMenu() {
        if (menuAberto) return
        menuAberto = true
        btnMenuHamburguer.setImageResource(R.drawable.ic_close)
        containerMenuRetangular.pivotX = 0f
        containerMenuRetangular.pivotY = 0f
        containerMenuRetangular.scaleX = 0f
        containerMenuRetangular.scaleY = 0f
        containerMenuRetangular.alpha = 0f
        menuOverlay.visibility = View.VISIBLE
        containerMenuRetangular.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).start()
        menuBackdrop.animate().alpha(1f).setDuration(200).start()
    }

    private fun fecharMenu() {
        if (!menuAberto) return
        menuAberto = false
        btnMenuHamburguer.setImageResource(R.drawable.ic_menu)
        containerMenuRetangular.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).start()
        menuBackdrop.animate().alpha(0f).setDuration(200).withEndAction { menuOverlay.visibility = View.GONE }.start()
    }

    override fun onBackPressed() {
        if (menuAberto) fecharMenu() else super.onBackPressed()
    }
}