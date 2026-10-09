package io.loja.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.loja.R
import io.loja.adapters.PapeletaDiariaAdapter
import io.loja.network.ApiClient
import io.loja.network.Department
import io.loja.network.PriceSign
import io.loja.network.PapeletaPrintingData
import io.loja.network.PriceSignFilterResponse
import io.loja.network.SendPriceSignRequest
import io.loja.network.SessionManager
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PapeletasDiariasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: io.loja.network.ApiService
    private lateinit var adapter: PapeletaDiariaAdapter

    private lateinit var tvTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDateLabel: TextView
    private lateinit var tvDateContainer: View
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPrintedCount: TextView
    private lateinit var tvUnprintedCount: TextView
    private lateinit var tvSelectedCount: TextView
    private lateinit var cbSelectAll: android.widget.CheckBox
    private lateinit var spinnerMovement: Spinner
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerSize: Spinner
    private lateinit var spinnerStatus: Spinner
    private lateinit var tvDepartmentSelector: TextView
    private lateinit var btnBuscar: Button
    private lateinit var btnPrint: Button
    private lateinit var btnReport: Button
    private lateinit var btnNextDay: View
    private lateinit var btnNextDayDate: TextView
    private lateinit var rvPriceSigns: RecyclerView
    private lateinit var layoutLoading: View
    private lateinit var layoutEmptyState: View
    private lateinit var tvTomorrowMessage: TextView

    private var currentDate: String = ""
    private var allItems = listOf<PriceSign>()
    private var unfilteredItems: List<PriceSign> = emptyList()
    private var departments = listOf<Department>()
    private var selectedMovement: String = Constants.MOVEMENT_ALL
    private var selectedType: String = Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
    private var usarModeloPersonalizado = false
    private var usarModeloComum = false
    private var selectedSize: String = Constants.SIGN_SIZE_1X1
    private var selectedStatus: String = Constants.STATUS_ALL
    private var selectedDepartments: MutableSet<String> = mutableSetOf()
    private var isLoading = false
    private var tomorrowCount: Int = 0
    private lateinit var btnMenuHamburguer: ImageView
    private lateinit var menuOverlay: View
    private lateinit var menuBackdrop: View
    private lateinit var containerMenuRetangular: View
    private var menuAberto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_papeletas_diarias)

        // Header branco -> status bar branca com ícones escuros.
        window.statusBarColor = Color.WHITE
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
        spinnerMovement = findViewById(R.id.spinnerMovement)
        spinnerType = findViewById(R.id.spinnerType)
        spinnerSize = findViewById(R.id.spinnerSize)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        tvDepartmentSelector = findViewById(R.id.tvDepartmentSelector)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnPrint = findViewById(R.id.btnPrint)
        btnReport = findViewById(R.id.btnReport)
        btnNextDay = findViewById(R.id.btnNextDay)
        btnNextDayDate = findViewById(R.id.btnNextDayDate)
        rvPriceSigns = findViewById(R.id.rvPriceSigns)
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

        val loadingAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Carregando...")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerMovement.adapter = loadingAdapter
        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Normal", "Promocional", "Promocional (Modelo)", "Normal (Modelo)")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSize.adapter = loadingAdapter
        spinnerStatus.adapter = loadingAdapter

        rvPriceSigns.layoutManager = LinearLayoutManager(this)
        adapter = PapeletaDiariaAdapter(emptyList()) { _, _, _ ->
            atualizarContadorSelecionados()
        }
        rvPriceSigns.adapter = adapter
    }

    private fun setupListeners() {
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = when (position) {
                    0, 3 -> Constants.SIGN_TYPE_PAPELETA_COMUM
                    else -> Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
                }
                usarModeloPersonalizado = (position == 2 || position == 3)
                usarModeloComum = (position == 3)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerType.setSelection(1)

        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) adapter.selectAll()
            else adapter.deselectAll()
            atualizarContadorSelecionados()
        }

        btnBuscar.setOnClickListener {
            btnBuscar.text = "BUSCANDO..."
            btnBuscar.isEnabled = false
            carregarPapeletas()
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

        tvDate.setOnClickListener {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
            if (currentDate != today) {
                val intent = Intent(this, PapeletasDiariasActivity::class.java)
                intent.putExtra(EXTRA_DATE, today)
                startActivity(intent)
            }
        }

        tvDateContainer.setOnClickListener {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
            if (currentDate != today) {
                val intent = Intent(this, PapeletasDiariasActivity::class.java)
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
        findViewById<View>(R.id.btnMenuEtiquetasDiarias).setOnClickListener {
            if (this.javaClass.simpleName != "EtiquetasActivity") {
                fecharMenu()
                finish()
                startActivity(Intent(this, EtiquetasActivity::class.java))
            } else fecharMenu()
        }
        findViewById<View>(R.id.btnMenuPapeletasDiarias).setOnClickListener { fecharMenu() }

        tvTitle.setOnClickListener { finish() }

        tvDepartmentSelector.setOnClickListener {
            mostrarDialogoDepartamentos()
        }

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

                val loja = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
                val filterResponse = apiService.getPriceSignFilters(loja)
                val deptResponse = apiService.getPriceTagFilters(loja, currentDate)

                val calAmanha = Calendar.getInstance()
                calAmanha.time = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).parse(currentDate)!!
                calAmanha.add(Calendar.DAY_OF_MONTH, 1)
                val amanhaStr = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(calAmanha.time)
                val tomorrowResponse = apiService.getPriceSignDashboard(loja, amanhaStr)
                tomorrowCount = try {
                    val body = tomorrowResponse.body()
                    if (body != null) {
                        val json = body.string()
                        val root = com.google.gson.JsonParser().parse(json).asJsonObject
                        root.getAsJsonObject("promoCardInfo")?.get("totalAvailable")?.asInt ?: 0
                    } else 0
                } catch (e: Exception) {
                    0
                }

                withContext(Dispatchers.Main) {
                    if (filterResponse.isSuccessful) {
                        val filters = filterResponse.body()
                        departments = (deptResponse.body()?.departments ?: emptyList())
                            .filter { val idNum = it.id.toIntOrNull(); idNum == null || idNum < 68 || idNum > 98 }
                            .sortedBy { it.id.toIntOrNull() ?: 0 }
                        configurarSpinnersComDados(filters)
                    } else {
                        configurarSpinnersFallback()
                        mostrarLoading(false)
                    }
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
        filters: PriceSignFilterResponse?
    ) {
        val movementOptions = arrayOf("Todos", "De/Por", "Leve e Ganhe", "Normal")
        val movementAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, movementOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerMovement.adapter = movementAdapter
        spinnerMovement.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMovement = when (position) {
                    1 -> Constants.MOVEMENT_DEPOR
                    2 -> Constants.MOVEMENT_LEVE
                    3 -> Constants.MOVEMENT_COMUM
                    else -> Constants.MOVEMENT_ALL
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val sizes = filters?.size ?: listOf(Constants.SIGN_SIZE_1X1, Constants.SIGN_SIZE_2X1, Constants.SIGN_SIZE_4X1, Constants.SIGN_SIZE_6X1)
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sizes.toTypedArray()).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSize.adapter = sizeAdapter
        spinnerSize.setSelection(sizes.indexOf(Constants.SIGN_SIZE_1X1).coerceAtLeast(0))
        spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSize = sizes[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val statusOptions = arrayOf("Todos os status", "Impressas", "Não impressas")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerStatus.adapter = statusAdapter
        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStatus = when (position) {
                    1 -> Constants.STATUS_IMPRESSAS
                    2 -> Constants.STATUS_NAO_IMPRESSAS
                    else -> Constants.STATUS_ALL
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        atualizarTextoDepartamento()

        mostrarLoading(false)

        carregarPapeletas()
    }

    private fun mostrarDialogoDepartamentos() {
        if (departments.isEmpty()) return
        val deptsComItens = departments.filter { dept ->
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
                val row = convertView ?: LayoutInflater.from(this@PapeletasDiariasActivity)
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
                carregarPapeletas()
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
            val names = departments
                .filter { it.id in selectedDepartments }
                .map { "${it.id.padStart(2, '0')} - ${it.label}" }
            if (names.size <= 2) names.joinToString(", ")
            else "${names.size} departamentos selecionados"
        }
    }

    private fun configurarSpinnersFallback() {
        val movementOptions = arrayOf("Todos", "De/Por", "Leve e Ganhe", "Normal")
        val movementAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, movementOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerMovement.adapter = movementAdapter
        spinnerMovement.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMovement = when (position) {
                    1 -> Constants.MOVEMENT_DEPOR
                    2 -> Constants.MOVEMENT_LEVE
                    3 -> Constants.MOVEMENT_COMUM
                    else -> Constants.MOVEMENT_ALL
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val sizeOptions = arrayOf(Constants.SIGN_SIZE_1X1, Constants.SIGN_SIZE_2X1, Constants.SIGN_SIZE_4X1, Constants.SIGN_SIZE_6X1)
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sizeOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSize.adapter = sizeAdapter
        spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSize = sizeOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Todos os status", "Impressas", "Não impressas")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerStatus.adapter = statusAdapter

        selectedDepartments.clear()
        tvDepartmentSelector.text = "Todos os departamentos"
    }

    private fun carregarPapeletas() {
        if (isLoading) return
        isLoading = true
        btnBuscar.text = "BUSCANDO..."
        btnBuscar.isEnabled = false

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

                val loja = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE

                // Comum e Promocional em PARALELO (antes eram em série).
                val sizeParam = if (selectedSize.isNotEmpty()) selectedSize else null
                val responseComum = async {
                    apiService.getPriceSigns(
                        storeId = loja,
                        type = Constants.SIGN_TYPE_PAPELETA_COMUM,
                        department = null,
                        size = sizeParam,
                        status = selectedStatus,
                        startDate = currentDate
                    )
                }
                val responsePromo = async {
                    apiService.getPriceSigns(
                        storeId = loja,
                        type = Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL,
                        department = null,
                        size = sizeParam,
                        status = selectedStatus,
                        startDate = currentDate
                    )
                }
                val responseComumR = responseComum.await()
                val responsePromoR = responsePromo.await()

                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    val items = (responseComumR.body()?.priceSigns ?: emptyList()) +
                        (responsePromoR.body()?.priceSigns ?: emptyList())
                    unfilteredItems = items

                    var filtered = if (selectedDepartments.isNotEmpty()) {
                        val deptNums = selectedDepartments.mapNotNull { it.toIntOrNull() }
                        items.filter { item ->
                            val itemDeptNum = item.department.substringBefore(" -").trim().toIntOrNull()
                            itemDeptNum != null && itemDeptNum in deptNums
                        }
                    } else {
                        items
                    }

                    if (selectedMovement == Constants.MOVEMENT_COMUM) {
                        filtered = filtered.filter { item ->
                            val m = item.movement.uppercase()
                            !m.contains("DE") && !m.contains("POR") && !m.contains("LEVE") && !m.contains("GANHE") && !m.contains("PROMOCIONAL")
                        }
                    } else if (selectedMovement.isNotEmpty()) {
                        filtered = filtered.filter { item ->
                            item.movement.equals(selectedMovement, ignoreCase = true)
                        }
                    }

                    allItems = filtered
                    adapter.updateItems(filtered.sortedBy { it.department.substringBefore(" -").trim().toIntOrNull() ?: 999 })
                    atualizarResumo(filtered)
                    atualizarContadorSelecionados()
                    btnBuscar.text = "BUSCAR"
                    btnBuscar.isEnabled = true
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    btnBuscar.text = "BUSCAR"
                    btnBuscar.isEnabled = true
                    isLoading = false
                }
            }
        }
    }

    private fun atualizarResumo(items: List<PriceSign>) {
        tvTotalCount.text = "${items.size}"
        tvPrintedCount.text = "${items.count { it.status.equals("Impressa", true) }}"
        tvUnprintedCount.text = "${items.count { it.status.equals("Não impressa", true) }}"
    }

    private fun enviarParaImpressao() {
        val itemsWithQty = adapter.getSelectedItemsWithQuantities()
        if (itemsWithQty.isEmpty()) {
            ToastUtils.show(this, "Selecione pelo menos uma papeleta")
            return
        }

        if (isLoading) return
        isLoading = true

        btnPrint.text = "IMPRIMINDO PAPELETAS..."
        btnPrint.isEnabled = false

        if (usarModeloPersonalizado) {
            abrirModeloEditavel(itemsWithQty)
        } else {
            enviarParaImpressaoNormal(itemsWithQty)
        }
    }

    // Porta fiel do Flutter (_onPrint): monta a lista de PapeletaPrintingData
    // com o template certo e abre o ModeloEditavelActivity, que compõe a grade
    // (6X1 = 3x2), aplica overlay só onde precisa e imprime via socket.
    private fun abrirModeloEditavel(itemsWithQty: List<Pair<PriceSign, Int>>) {
        val lista = mutableListOf<PapeletaPrintingData>()
        for ((item, qty) in itemsWithQty) {
            val pd = item.printingData ?: continue
            val movement = item.movement.lowercase()
            val temLeveGanhe = (pd.takeAndWinQuantity ?: 0) > 0
            val itemTemplate = if (usarModeloComum) {
                // Fiel ao backend: usa o template ORIGINAL (fallback "Normal").
                pd.template ?: Constants.SIGN_TEMPLATE_NORMAL
            } else {
                if (temLeveGanhe) {
                    Constants.SIGN_TEMPLATE_MODELO
                } else {
                    pd.template ?: when {
                        movement == "de/por" -> Constants.SIGN_TEMPLATE_DEPOR
                        movement == "parcelado" -> Constants.SIGN_TEMPLATE_DEPOR_PARCELADO
                        else -> Constants.SIGN_TEMPLATE_MODELO
                    }
                }
            }
            val base = pd.copy(template = itemTemplate, size = selectedSize, quantity = 1)
            repeat(if (qty > 0) qty else 1) { lista.add(base) }
        }

        if (lista.isEmpty()) {
            isLoading = false
            btnPrint.text = "🖨️ Imprimir Papeletas"
            btnPrint.isEnabled = true
            ToastUtils.show(this, "Nenhum item para imprimir")
            return
        }

        // Igual ao Flutter: se todos forem comuns (sem promo/leve-ganhe),
        // envia pela API; caso contrário gera PDF e imprime via socket.
        val todosComuns = lista.all { d ->
            val hasPromo = (d.promotionPrice ?: 0.0) > 0
            val hasTake = (d.takeAndWinQuantity ?: 0) > 0
            !(hasPromo || hasTake)
        }

        ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
        startActivity(Intent(this, ModeloEditavelActivity::class.java).apply {
            putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, false)
            putExtra(ModeloEditavelActivity.EXTRA_SIZE, selectedSize)
            putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, usarModeloComum && todosComuns)
            putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, false)
            putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, usarModeloComum)
            putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, true)
            putExtra(ModeloEditavelActivity.EXTRA_HIDE_GERAR_PREVIEW, true)
        })
        isLoading = false
        btnPrint.text = "🖨️ Imprimir Papeletas"
        btnPrint.isEnabled = true
    }

    // Parcelado é tipo próprio (normal, parcelado, comum, promocional):
    // não usa overlay, renderiza direto como o comum.
    private fun ehParcelado(item: PriceSign): Boolean {
        return item.movement.equals("Parcelado", ignoreCase = true) ||
            (item.printingData?.template?.lowercase()?.contains("parcelado") == true)
    }

    private fun visualizarPreview(itemsWithQty: List<Pair<PriceSign, Int>>) {
        ToastUtils.show(this, "Preparando visualização com modelo...")

        mostrarLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        isLoading = false
                        btnPrint.text = "🖨️ Imprimir Papeletas"
                        btnPrint.isEnabled = true
                        redirectToLogin()
                    }
                    return@launch
                }

                val is6x1 = selectedSize == Constants.SIGN_SIZE_6X1
                val overlay = if (!usarModeloComum) carregarOverlayAssets() else null
                LogHelper.d("visualizarPreview: usarModeloComum=$usarModeloComum, overlay=${overlay != null}, is6x1=$is6x1")
                val pdfDoc = PdfDocument()

                if (is6x1) {
                    adicionarPaginasGrid6x1(pdfDoc, itemsWithQty, overlay)
                } else {
                    for ((item, qty) in itemsWithQty) {
                        val previewData = item.printingData?.copy(
                            template = if (usarModeloComum) {
                                item.printingData.template ?: Constants.SIGN_TEMPLATE_NORMAL
                            } else {
                                // Fiel ao backend: usa o template ORIGINAL do item
                                // (ex: "de_por", "de_por_parcelado") que a API de
                                // preview reconhece. Leve/Ganhe vira leve_ganhe_total.
                                if ((item.printingData.takeAndWinQuantity ?: 0) > 0) {
                                    Constants.SIGN_TEMPLATE_MODELO
                                } else {
                                    item.printingData.template ?: when {
                                        item.movement.equals("De/Por", ignoreCase = true) -> Constants.SIGN_TEMPLATE_DEPOR
                                        item.movement.equals("Parcelado", ignoreCase = true) -> Constants.SIGN_TEMPLATE_DEPOR_PARCELADO
                                        else -> Constants.SIGN_TEMPLATE_MODELO
                                    }
                                }
                            },
                            size = selectedSize,
                            quantity = qty
                        ) ?: continue

                        val response = apiService.previewPriceSign(previewData)
                        if (!response.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                ToastUtils.show(this@PapeletasDiariasActivity, "❌ Erro ao gerar preview: ${response.code()}")
                            }
                            continue
                        }

                        val pdfBytes = response.body()?.bytes() ?: continue
                        if (usarModeloComum || ehParcelado(item)) {
                            adicionarPaginaDireto(pdfDoc, pdfBytes)
                        } else {
                            adicionarPaginaComOverlay(pdfDoc, pdfBytes, overlay, pdfDoc.pages.size + 1)
                        }
                    }
                }

                if (pdfDoc.pages.size == 0) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        isLoading = false
                        btnPrint.text = "🖨️ Imprimir Papeletas"
                        btnPrint.isEnabled = true
                        ToastUtils.show(this@PapeletasDiariasActivity, "Nenhum preview gerado")
                    }
                    pdfDoc.close()
                    return@launch
                }

                val outputFile = File(cacheDir, "papeletas_preview_${System.currentTimeMillis()}.pdf")
                FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
                pdfDoc.close()

                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    isLoading = false
                    btnPrint.text = "🖨️ Imprimir Papeletas"
                    btnPrint.isEnabled = true
                    // PDF já sai final (overlay embutido onde precisa; direto p/ comum/parcelado)
                // — viewer só exibe, igual ao Flutter (composite aplica 1x).
                abrirPdfViewer(outputFile.absolutePath, true)
                }
            } catch (e: Exception) {
                LogHelper.e("PapeletasDiariasActivity: Erro preview", e)
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    isLoading = false
                    btnPrint.text = "🖨️ Imprimir Papeletas"
                    btnPrint.isEnabled = true
                    ToastUtils.show(this@PapeletasDiariasActivity, "Erro: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private suspend fun adicionarPaginasGrid6x1(pdfDoc: PdfDocument, itemsWithQty: List<Pair<PriceSign, Int>>, overlay: Bitmap?) {
        val cols = 3
        val rows = 2
        val gridCap = cols * rows

        for (batchStart in itemsWithQty.indices step gridCap) {
            val batch = itemsWithQty.drop(batchStart).take(gridCap)
            val bitmaps = mutableListOf<Triple<Int, Bitmap, Boolean>>()

            for ((localIdx, itemQty) in batch.withIndex()) {
                val (item, qty) = itemQty
                val previewData = item.printingData?.copy(
                    template = if (usarModeloComum) {
                        item.printingData.template ?: Constants.SIGN_TEMPLATE_NORMAL
                    } else {
                        if ((item.printingData.takeAndWinQuantity ?: 0) > 0) {
                            Constants.SIGN_TEMPLATE_MODELO
                        } else {
                            item.printingData.template ?: when {
                                item.movement.equals("De/Por", ignoreCase = true) -> Constants.SIGN_TEMPLATE_DEPOR
                                item.movement.equals("Parcelado", ignoreCase = true) -> Constants.SIGN_TEMPLATE_DEPOR_PARCELADO
                                else -> Constants.SIGN_TEMPLATE_MODELO
                            }
                        }
                    },
                    size = Constants.SIGN_SIZE_1X1,
                    quantity = qty
                ) ?: continue

                try {
                    val response = apiService.previewPriceSign(previewData)
                    if (!response.isSuccessful) continue
                    val pdfBytes = response.body()?.bytes() ?: continue
                    val bmp = renderPdfToBitmap(pdfBytes, 400, 425)
                    if (bmp != null) bitmaps.add(Triple(localIdx, bmp, !usarModeloComum && !ehParcelado(item)))
                } catch (e: Exception) {
                    LogHelper.e("Grid6x1: Erro fetch item $localIdx", e)
                }
            }

            if (bitmaps.isEmpty()) continue

            val pageW = 2480
            val pageH = 3508
            val pageBmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pageBmp)
            canvas.drawColor(Color.WHITE)

            val halfW = pageW / cols.toFloat()
            val halfH = pageH / rows.toFloat()

            for ((idx, bmp, comOverlay) in bitmaps) {
                val col = idx % cols
                val row = idx / cols
                val left = col * halfW
                val top = row * halfH

                if (!comOverlay) {
                    val scaled = Bitmap.createScaledBitmap(bmp, halfW.toInt(), halfH.toInt(), true)
                    canvas.drawBitmap(scaled, left, top, null)
                    scaled.recycle()
                } else {
                    if (overlay != null) {
                        val ovTile = Bitmap.createScaledBitmap(overlay, halfW.toInt(), halfH.toInt(), true)
                        canvas.drawBitmap(ovTile, left, top, null)
                        ovTile.recycle()
                    }
                    val semBranco = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    val pixels = IntArray(semBranco.width * semBranco.height)
                    semBranco.getPixels(pixels, 0, semBranco.width, 0, 0, semBranco.width, semBranco.height)
                    for (j in pixels.indices) {
                        val a = (pixels[j] shr 24) and 0xFF
                        val r = (pixels[j] shr 16) and 0xFF
                        val g = (pixels[j] shr 8) and 0xFF
                        val b = pixels[j] and 0xFF
                        if (a > 200 && r > 200 && g > 200 && b > 200) {
                            pixels[j] = Color.TRANSPARENT
                        }
                    }
                    semBranco.setPixels(pixels, 0, semBranco.width, 0, 0, semBranco.width, semBranco.height)
                    canvas.drawBitmap(semBranco, left, top, null)
                    semBranco.recycle()
                }
                bmp.recycle()
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pdfDoc.pages.size + 1).create()
            val docPage = pdfDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(pageBmp, 0f, 0f, null)
            pdfDoc.finishPage(docPage)
            pageBmp.recycle()
        }
    }

    private fun renderPdfToBitmap(pdfBytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
        try {
            val tempFile = File(cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")
            tempFile.outputStream().use { it.write(pdfBytes) }
            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            if (renderer.pageCount == 0) { renderer.close(); fd.close(); tempFile.delete(); return null }
            renderer.openPage(0).use { page ->
                val renderW = (page.width * 4).coerceAtMost(2400)
                val renderH = (page.height.toFloat() * (renderW.toFloat() / page.width.toFloat())).toInt()
                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                renderer.close()
                fd.close()
                tempFile.delete()
                return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            }
        } catch (e: Exception) {
            LogHelper.e("renderPdfToBitmap: Erro", e)
            return null
        }
    }

    private fun abrirPdfViewer(caminho: String, semOverlay: Boolean = false) {
        val intent = Intent(this, PdfViewerActivity::class.java).apply {
            putExtra(PdfViewerActivity.EXTRA_FILE_PATH, caminho)
            putExtra(PdfViewerActivity.EXTRA_OVERLAY_SIZE, selectedSize)
            putExtra(PdfViewerActivity.EXTRA_SEM_OVERLAY, semOverlay)
        }
        startActivity(intent)
    }

    private fun carregarOverlayAssets(): Bitmap? {
        val nomeArquivo = if (selectedSize == Constants.SIGN_SIZE_6X1) "1x1" else selectedSize.lowercase()
        var inputStream: java.io.InputStream? = null
        try {
            inputStream = assets.open("overlays/$nomeArquivo.png")
        } catch (_: Exception) {}
        if (inputStream == null) {
            try {
                inputStream = assets.open("overlays/$nomeArquivo.jpg")
            } catch (_: Exception) {}
        }
        if (inputStream != null) {
            return try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inDither = false
                }
                BitmapFactory.decodeStream(inputStream, null, opts)
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun adicionarPaginaComOverlay(pdfDoc: PdfDocument, pdfBytes: ByteArray, overlay: Bitmap?, startPage: Int) {
        try {
            val tempFile = File(cacheDir, "temp_page_${System.currentTimeMillis()}.pdf")
            tempFile.outputStream().use { it.write(pdfBytes) }

            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)

            for (p in 0 until renderer.pageCount) {
                renderer.openPage(p).use { page ->
                    val renderWidth = (page.width * 4).coerceAtMost(3600)
                    val renderHeight = (page.height.toFloat() * (renderWidth.toFloat() / page.width.toFloat())).toInt()

                    val pdfBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val output = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)

                    if (overlay != null) {
                        val bg = Bitmap.createScaledBitmap(overlay, renderWidth, renderHeight, true)
                        canvas.drawBitmap(bg, 0f, 0f, null)
                        val semBranco = pdfBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val pixels = IntArray(semBranco.width * semBranco.height)
                        semBranco.getPixels(pixels, 0, semBranco.width, 0, 0, semBranco.width, semBranco.height)
                        for (j in pixels.indices) {
                            val a = (pixels[j] shr 24) and 0xFF
                            val r = (pixels[j] shr 16) and 0xFF
                            val g = (pixels[j] shr 8) and 0xFF
                            val b = pixels[j] and 0xFF
                            if (a > 200 && r > 200 && g > 200 && b > 200) {
                                pixels[j] = Color.TRANSPARENT
                            }
                        }
                        semBranco.setPixels(pixels, 0, semBranco.width, 0, 0, semBranco.width, semBranco.height)
                        canvas.drawBitmap(semBranco, 0f, 0f, null)
                        semBranco.recycle()
                    } else {
                        canvas.drawBitmap(pdfBitmap, 0f, 0f, null)
                    }

                    val docPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(renderWidth, renderHeight, startPage + p).create())
                    docPage.canvas.drawBitmap(output, 0f, 0f, null)
                    pdfDoc.finishPage(docPage)

                    pdfBitmap.recycle()
                    output.recycle()
                }
            }

            renderer.close()
            fd.close()
            tempFile.delete()
        } catch (e: Exception) {
            LogHelper.e("PapeletasDiariasActivity: Erro adicionarPagina", e)
        }
    }

    private fun adicionarPaginaDireto(pdfDoc: PdfDocument, pdfBytes: ByteArray) {
        try {
            val tempFile = File(cacheDir, "temp_direct_${System.currentTimeMillis()}.pdf")
            tempFile.outputStream().use { it.write(pdfBytes) }
            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            for (p in 0 until renderer.pageCount) {
                renderer.openPage(p).use { page ->
                    val renderWidth = (page.width * 4).coerceAtMost(3600)
                    val renderHeight = (page.height.toFloat() * (renderWidth.toFloat() / page.width.toFloat())).toInt()
                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    val docPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(renderWidth, renderHeight, pdfDoc.pages.size + 1).create())
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDoc.finishPage(docPage)
                    bitmap.recycle()
                }
            }
            renderer.close()
            fd.close()
            tempFile.delete()
        } catch (e: Exception) {
            LogHelper.e("PapeletasDiariasActivity: Erro adicionarPaginaDireto", e)
        }
    }

    private fun enviarParaImpressaoNormal(itemsWithQty: List<Pair<PriceSign, Int>>) {
        mostrarLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        btnPrint.text = "🖨️ Imprimir Papeletas"
                        btnPrint.isEnabled = true
                        isLoading = false
                        redirectToLogin()
                    }
                    return@launch
                }

                val loja = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE

                val printingDataList = itemsWithQty.mapNotNull { (item, qty) ->
                    item.printingData?.copy(size = selectedSize, quantity = qty)
                }

                if (printingDataList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        mostrarLoading(false)
                        btnPrint.text = "🖨️ Imprimir Papeletas"
                        btnPrint.isEnabled = true
                        isLoading = false
                        ToastUtils.show(this@PapeletasDiariasActivity, "Dados de impressão não disponíveis")
                    }
                    return@launch
                }

                val response = apiService.sendPriceSigns(
                    storeId = loja,
                    request = SendPriceSignRequest(printingDataList)
                )

                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    btnPrint.text = "🖨️ Imprimir Papeletas"
                    btnPrint.isEnabled = true
                    isLoading = false
                    if (response.isSuccessful) {
                        val totalEnviadas = printingDataList.sumOf { it.quantity ?: 1 }
                        ToastUtils.show(this@PapeletasDiariasActivity, "$totalEnviadas papeletas enviadas para impressão!", Toast.LENGTH_LONG)
                        carregarPapeletas()
                    } else {
                        ToastUtils.show(this@PapeletasDiariasActivity, "❌ Erro ao enviar para impressão")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    btnPrint.text = "🖨️ Imprimir Papeletas"
                    btnPrint.isEnabled = true
                    isLoading = false
                    ToastUtils.show(this@PapeletasDiariasActivity, "Erro: ${e.message}")
                }
            }
        }
    }

    private fun atualizarTituloEBotoes(isTomorrow: Boolean) {
        tvTitle.text = if (isTomorrow) "Papeletas de amanhã" else "Papeletas de hoje"
        val tvDateLabel = findViewById<TextView>(R.id.tvDateLabel)
        val btnNextDayLabel = findViewById<TextView>(R.id.btnNextDayLabel)
        if (isTomorrow) {
            tvDateContainer.setBackgroundResource(R.drawable.bg_pill_transparent)
            tvDateLabel.setTextColor(Color.parseColor("#000000"))
            tvDate.setTextColor(Color.parseColor("#000000"))
            btnNextDay.setBackgroundResource(R.drawable.bg_date_pill_light_green)
            btnNextDayLabel.setTextColor(Color.parseColor("#000000"))
            btnNextDayDate.setTextColor(Color.parseColor("#000000"))
            btnNextDay.isEnabled = false
        } else {
            tvDateContainer.setBackgroundResource(R.drawable.bg_date_pill_light_green)
            tvDateLabel.setTextColor(Color.parseColor("#000000"))
            tvDate.setTextColor(Color.parseColor("#000000"))
            btnNextDay.setBackgroundResource(R.drawable.bg_pill_transparent)
            btnNextDayLabel.setTextColor(Color.parseColor("#000000"))
            btnNextDayDate.setTextColor(Color.parseColor("#000000"))
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
        rvPriceSigns.visibility = if (mostrar) View.GONE else View.VISIBLE
        if (!mostrar) atualizarEstadoVazio()
    }

    private fun atualizarEstadoVazio() {
        val isEmpty = adapter.itemCount == 0
        layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvPriceSigns.visibility = if (isEmpty) View.GONE else View.VISIBLE

        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
        if (isEmpty && currentDate == hoje) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val amanha = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(cal.time)
            tvTomorrowMessage.text = "Você tem $tomorrowCount papeleta(s) de amanhã ($amanha) para imprimir! Selecione o tipo, os departamentos e o status para começar a imprimir."
            tvTomorrowMessage.visibility = View.VISIBLE
        } else {
            tvTomorrowMessage.visibility = View.GONE
        }
    }

    private fun gerarRelatorio() {
        ToastUtils.show(this, "📄 Gerando relatório...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { ToastUtils.show(this@PapeletasDiariasActivity, "Sessão expirada") }
                    return@launch
                }
                val loja = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR")).format(Date())
                val response = apiService.getPapeletasReport(loja, hoje)
                if (response.isSuccessful) {
                    val pdfBytes = response.body()?.bytes()
                    if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                        val outputFile = File(cacheDir, "relatorio_papeletas_${System.currentTimeMillis()}.pdf")
                        FileOutputStream(outputFile).use { it.write(pdfBytes) }
                        withContext(Dispatchers.Main) {
                            val uri = FileProvider.getUriForFile(
                                this@PapeletasDiariasActivity,
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
                        withContext(Dispatchers.Main) { ToastUtils.show(this@PapeletasDiariasActivity, "Relatório vazio") }
                    }
                } else {
                    withContext(Dispatchers.Main) { ToastUtils.show(this@PapeletasDiariasActivity, "Erro: ${response.code()}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ToastUtils.show(this@PapeletasDiariasActivity, "Erro: ${e.message}") }
            }
        }
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

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_DATE = "extra_date"
    }
}
