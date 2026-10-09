package io.loja.ui.fragments

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

import io.loja.R
import io.loja.network.ApiClient
import io.loja.network.PriceTag
import io.loja.network.PrintingData
import io.loja.network.SendPriceTagsRequest
import io.loja.network.SessionManager
import io.loja.ui.BarcodeScannerActivity
import io.loja.ui.ListaViewModel
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.SessionExpiredHandler
import io.loja.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class EtiquetasFragment : Fragment() {

    internal lateinit var spinnerPrinter: Spinner
    internal lateinit var spinnerBuscaTipo: Spinner
    private lateinit var spinnerModo: Spinner
    private var modoEdicao = false
    private lateinit var etBusca: EditText
    private lateinit var btnBuscar: Button
    private lateinit var fabScanner: FloatingActionButton
    private var fabEscondido = true
    private lateinit var btnLimpar: Button
    private lateinit var btnImprimirTodas: Button
    private lateinit var btnPdf: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var tvColagem: TextView
    private lateinit var layoutColagem: View

    private var items = mutableListOf<PriceTag>()
    private var adapter: EtiquetaAdapter? = null
    internal var buscaTipo = "EAN"
    private lateinit var sessionManager: SessionManager
    private lateinit var listaViewModel: ListaViewModel

    private val printerIds = mutableListOf(Constants.PRINTER_ZEBRA_1, Constants.PRINTER_ZEBRA_2)
    private val printerNomes = mutableListOf("Zebra 1", "Zebra 2")

    private var fabPulseHandler: Handler? = null
    private var fabPulseRunnable: Runnable? = null
    private var scannerContinuoAtivo = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val BARCODE_REQUEST = 1000
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_etiquetas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            listaViewModel = ViewModelProvider(requireActivity())[ListaViewModel::class.java]
            items = listaViewModel.getEtiquetas()

            sessionManager = SessionManager(requireContext())
            val token = sessionManager.getToken()
            if (token.isNullOrEmpty()) {
                LogHelper.e("EtiquetasFragment: Token não encontrado!")
                ToastUtils.show(requireContext(), "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG)
                SessionExpiredHandler.handleSessionExpired(requireActivity())
                return
            }
            LogHelper.d("EtiquetasFragment: Token OK: ${token.take(20)}...")

            spinnerPrinter = view.findViewById(R.id.spinnerPrinter)
            spinnerBuscaTipo = view.findViewById(R.id.spinnerBuscaTipo)
            spinnerModo = view.findViewById(R.id.spinnerModo)
            etBusca = view.findViewById(R.id.etBusca)
            btnBuscar = view.findViewById(R.id.btnBuscar)
            fabScanner = view.findViewById(R.id.fabScanner)
            fabScanner.translationX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 55f, resources.displayMetrics)
            btnLimpar = view.findViewById(R.id.btnLimpar)
            btnImprimirTodas = view.findViewById(R.id.btnImprimirTodas)
            btnPdf = view.findViewById(R.id.btnPdf)
            recyclerView = view.findViewById(R.id.recyclerView)
            tvTotal = view.findViewById(R.id.tvTotal)
            layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
            tvColagem = view.findViewById(R.id.tvColagem)
            layoutColagem = view.findViewById(R.id.layoutColagem)
            view.findViewById<View>(R.id.btnLimparColagem).setOnClickListener {
                listaViewModel.setColagem("")
                carregarColagem()
            }

            configurarSpinners()
            carregarImpressoras()
            carregarColagem()

            adapter = EtiquetaAdapter(
                items = items,
                onImprimir = { item, quantidade ->
                    try {
                        imprimirEtiqueta(item, quantidade)
                    } catch (e: Exception) {
                        LogHelper.e("EtiquetasFragment: Erro ao imprimir", e)
                        ToastUtils.show(requireContext(), "Erro: ${e.message}")
                    }
                },
                onRemover = { item ->
                    try {
                        removerItem(item)
                    } catch (e: Exception) {
                        LogHelper.e("EtiquetasFragment: Erro ao remover", e)
                    }
                }
            )
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
            configurarSwipeCards(recyclerView)

            atualizarLista()

            btnBuscar.setOnClickListener {
                try {
                    val busca = etBusca.text.toString().trim()
                    if (busca.isEmpty()) {
                        val msg = when (buscaTipo) {
                            "EAN" -> "Digite um EAN"
                            "SAP" -> "Digite um SAP"
                            else -> "Digite uma descrição"
                        }
                        ToastUtils.show(requireContext(), msg)
                        return@setOnClickListener
                    }
                    buscarEtiqueta(busca)
                } catch (e: Exception) {
                    LogHelper.e("EtiquetasFragment: Erro ao buscar", e)
                    ToastUtils.show(requireContext(), "Erro: ${e.message}")
                }
            }

            iniciarPulsacaoFab()

            fabScanner.setOnClickListener {
                try {
                    if (fabEscondido) {
                        fabEscondido = false
                        fabScanner.animate().translationX(0f).setDuration(300).start()
                    } else {
                        pararPulsacaoFab()
                        abrirLeitorDeBarras()
                    }
                } catch (e: Exception) {
                    LogHelper.e("EtiquetasFragment: Erro no scanner", e)
                    ToastUtils.show(requireContext(), "Erro ao abrir scanner: ${e.message}")
                }
            }

            btnLimpar.setOnClickListener {
                try {
                    limparLista()
                } catch (e: Exception) {
                    LogHelper.e("EtiquetasFragment: Erro ao limpar", e)
                }
            }

            btnImprimirTodas.setOnClickListener {
                try {
                    imprimirTodasEtiquetas()
                } catch (e: Exception) {
                    LogHelper.e("EtiquetasFragment: Erro ao imprimir todas", e)
                    ToastUtils.show(requireContext(), "Erro: ${e.message}")
                }
            }

            btnPdf.setOnClickListener {
                try {
                    gerarPdfEtiquetas()
                } catch (e: Exception) {
                    LogHelper.e("EtiquetasFragment: Erro ao gerar PDF", e)
                    ToastUtils.show(requireContext(), "Erro ao gerar o PDF")
                }
            }

            view.setOnTouchListener { _, _ ->
                ocultarTeclado()
                false
            }

        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: ERRO", e)
            ToastUtils.show(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    override fun onDestroyView() {
        pararPulsacaoFab()
        listaViewModel.saveEtiquetas(items)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        if (scannerContinuoAtivo) {
            scannerContinuoAtivo = false
            sincronizarListaDoScanner()
        }
    }

    // Traz para a tela o que foi escaneado no modo contínuo (sem somar 2x):
    // delta = (qtd salva - 1) soma no mapa do adapter; depois zera e salva.
    private fun sincronizarListaDoScanner() {
        try {
            val fresh = listaViewModel.getEtiquetas()
            fresh.forEach { f ->
                val delta = (f.quantity - 1).coerceAtLeast(0)
                val pos = items.indexOfFirst { it.ean == f.ean }
                if (pos >= 0) {
                    if (delta > 0) {
                        adapter?.setQuantityAt(pos, (adapter?.getQuantityAt(pos) ?: 1) + delta)
                    }
                } else {
                    items.add(f)
                    val newPos = items.size - 1
                    if (f.quantity > 1) adapter?.setQuantityAt(newPos, f.quantity)
                }
                f.quantity = 1
            }
            adapter?.notifyDataSetChanged()
            atualizarLista()
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao sincronizar scanner", e)
        }
    }

    private var _printerTocada = false
    private var _buscaTocada = false
    private var initPrinter = true

    private fun configurarSpinnerPrinter() {
        val vermelho = android.graphics.Color.parseColor("#D7193F")
        val nomes = printerNomes.toTypedArray()
        val printerAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, nomes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (_printerTocada) vermelho else android.graphics.Color.BLACK)
                return v
            }
        }
        printerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrinter.adapter = printerAdapter
        spinnerPrinter.setSelection(0)
        initPrinter = true
        spinnerPrinter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initPrinter) { initPrinter = false; return }
                if (!_printerTocada) { _printerTocada = true; (view as? TextView)?.setTextColor(vermelho) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun carregarImpressoras() {
        val apiService = ApiClient.getSlApiService(sessionManager)
        val loja = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { apiService.getPrinters(loja) }
                val lista = resp.body()?.printers?.filter { p ->
                    p.tags.any { t -> t.id.equals("GONDOLA", true) || t.name.equals("GONDOLA", true) }
                } ?: emptyList()
                if (lista.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        printerIds.clear()
                        printerIds.addAll(lista.map { it.id })
                        printerNomes.clear()
                        printerNomes.addAll(lista.map { it.name })
                        configurarSpinnerPrinter()
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("EtiquetasFragment: erro ao carregar impressoras", e)
            }
        }
    }

    private fun configurarSpinners() {
        try {
            val vermelho = android.graphics.Color.parseColor("#D7193F")
            var initBusca = true
            val modoOptions = arrayOf("Normal", "Edição")
            spinnerModo.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modoOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerModo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    modoEdicao = (position == 1)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            spinnerModo.setSelection(0)

            configurarSpinnerPrinter()

            val buscaTipos = arrayOf("EAN", "SAP", "Descrição")
            val buscaAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, buscaTipos) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(if (_buscaTocada) vermelho else android.graphics.Color.BLACK)
                    return v
                }
            }
            buscaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerBuscaTipo.adapter = buscaAdapter

            spinnerBuscaTipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (initBusca) { initBusca = false } else if (!_buscaTocada) { _buscaTocada = true; (view as? TextView)?.setTextColor(vermelho) }
                    buscaTipo = when (position) { 0 -> "EAN"; 1 -> "SAP"; else -> "Descrição" }
                    etBusca.hint = when (position) {
                        0 -> "Digite o EAN"
                        1 -> "Digite o SAP"
                        else -> "Buscar Por Descrição"
                    }
                    etBusca.inputType = when (position) {
                        0, 1 -> android.text.InputType.TYPE_CLASS_NUMBER
                        else -> android.text.InputType.TYPE_CLASS_TEXT
                    }
                    etBusca.text.clear()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao configurar spinners", e)
        }
    }

    private fun carregarColagem() {
        val code = listaViewModel.getColagem()
        if (code.isNotEmpty()) {
            tvColagem.text = "Área de colagem: $code"
            layoutColagem.visibility = View.VISIBLE
            tvColagem.setOnClickListener {
                etBusca.setText(code)
                buscarEtiqueta(code)
            }
        } else {
            layoutColagem.visibility = View.GONE
        }
    }

    private fun abrirLeitorDeBarras() {
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST
                )
                return
            }

            val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
            intent.putExtra(BarcodeScannerActivity.EXTRA_MODO_CONTINUO, true)
            scannerContinuoAtivo = true
            startActivityForResult(intent, BARCODE_REQUEST)
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao abrir leitor", e)
            ToastUtils.show(requireContext(), "Erro ao abrir leitor: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            if (requestCode == BARCODE_REQUEST && resultCode == android.app.Activity.RESULT_OK) {
                val ean = data?.getStringExtra("EAN") ?: ""
                if (ean.isNotEmpty()) {
                    etBusca.setText(ean)
                    listaViewModel.setColagem(ean)
                    carregarColagem()
                    try {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("EAN", ean)
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        LogHelper.e("Erro ao copiar para o clipboard", e)
                    }
                    buscarEtiqueta(ean)
                }
            }
            iniciarPulsacaoFab()
            fabEscondido = true
            fabScanner.animate().translationX(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 55f, resources.displayMetrics)).setDuration(300).start()
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro no onActivityResult", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                abrirLeitorDeBarras()
            } else {
                ToastUtils.show(requireContext(), "Permissão da câmera necessária", Toast.LENGTH_LONG)
            }
        }
    }

    internal fun buscarEtiqueta(busca: String, onComplete: () -> Unit = {}) {
        val apiService = ApiClient.getSlApiService(sessionManager)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        btnBuscar.text = "BUSCANDO..."
        btnBuscar.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    when (buscaTipo) {
                        "EAN" -> apiService.getSingleLabelByEan(
                            storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                            ean = busca,
                            description = null,
                            sapId = null,
                            startDate = currentDate
                        )
                        "SAP" -> apiService.getSingleLabelByEan(
                            storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                            ean = null,
                            description = null,
                            sapId = busca,
                            startDate = currentDate
                        )
                        else -> apiService.getSingleLabelByEan(
                            storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                            ean = null,
                            description = busca,
                            sapId = null,
                            startDate = currentDate
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    btnBuscar.text = "BUSCAR"
                    btnBuscar.isEnabled = true
                    onComplete()

                    if (response.isSuccessful) {
                        val data = response.body()
                        if (data != null && data.items?.isNotEmpty() == true) {
                            var itensAdicionados = 0
                            var itensIncrementados = 0
                            data.items.forEach { item ->
                                val existingIndex = items.indexOfFirst { it.ean == item.ean }
                                if (existingIndex >= 0) {
                                    val currentQty = adapter?.getQuantityAt(existingIndex) ?: 1
                                    adapter?.setQuantityAt(existingIndex, currentQty + 1)
                                    adapter?.notifyItemChanged(existingIndex)
                                    itensIncrementados++
                                } else {
                                    val printingData = item.printingData?.let { singleData ->
                                        PrintingData(
                                            ean = singleData.ean,
                                            description = singleData.description,
                                            department = singleData.department,
                                            displayPrice = singleData.displayPrice,
                                            price = singleData.price,
                                            promotionalPrice = singleData.promotionalPrice,
                                            quantity = 1,
                                            movementType = singleData.movementType,
                                            unit = singleData.unit,
                                            unitQty = singleData.unitQty,
                                            unitValue = singleData.unitValue,
                                            printUnitValue = singleData.printUnitValue,
                                            codSap = singleData.codSap,
                                            takeAndWin = singleData.takeAndWin,
                                            referenceDate = singleData.referenceDate,
                                            template = singleData.template ?: if (singleData.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO else null,
                                            productName = null,
                                            promotionPrice = null,
                                            takeAndWinQuantity = null,
                                            takeAndWinPrice = null,
                                            takeAndWinPercent = null,
                                            installments = singleData.installments
                                        )
                                    }
                                    items.add(
                                        PriceTag(
                                            item.id, item.sap, item.department, item.ean, item.description,
                                            item.startDate, item.endDate, item.duration, item.price, item.movement,
                                            item.status, false, 1, printingData
                                        )
                                    )
                                    itensAdicionados++
                                }
                            }
                            adapter?.notifyDataSetChanged()
                            etBusca.text.clear()
                            atualizarLista()
                            listaViewModel.saveEtiquetas(items)
                            val msg = buildString {
                                if (itensAdicionados > 0) append("$itensAdicionados adicionado(s)")
                                if (itensAdicionados > 0 && itensIncrementados > 0) append(" | ")
                                if (itensIncrementados > 0) append("$itensIncrementados incrementado(s)")
                            }
                            ToastUtils.show(requireContext(), msg)
                        } else {
                            ToastUtils.show(requireContext(), "Nenhum item encontrado")
                        }
                    } else {
                        val errorMsg = when (response.code()) {
                            401 -> {
                                SessionExpiredHandler.handleSessionExpired(requireActivity())
                                "Sessão expirada. Faça login novamente."
                            }
                            403 -> "Acesso negado."
                            else -> "Erro: ${response.code()}"
                        }
                        ToastUtils.show(requireContext(), errorMsg, Toast.LENGTH_LONG)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnBuscar.text = "BUSCAR"
                    btnBuscar.isEnabled = true
                    onComplete()
                    ToastUtils.show(requireContext(), "Erro: ${e.message}")
                }
                LogHelper.e("EtiquetasFragment: Erro na busca", e)
            }
        }
    }

    private fun getPrinterIdFromSpinner(): String {
        val pos = spinnerPrinter.selectedItemPosition
        return if (pos >= 0 && pos < printerIds.size) printerIds[pos]
        else Constants.PRINTER_ZEBRA_1
    }

    private fun imprimirEtiqueta(item: PriceTag, quantidade: Int) {
        val printingData = item.printingData
        if (printingData == null) {
            ToastUtils.show(requireContext(), "Dados de impressão não disponíveis")
            return
        }

        val listaParaImprimir = mutableListOf<PrintingData>()
        for (i in 0 until quantidade) {
            listaParaImprimir.add(
                printingData.copy(
                    quantity = 1,
                    template = if (printingData.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO else Constants.templateNormalizado(printingData.template)
                )
            )
        }

        if (modoEdicao) {
            abrirEdicao(listaParaImprimir)
            return
        }

        val apiService = ApiClient.getSlApiService(sessionManager)
        val printerAtPrintTime = getPrinterIdFromSpinner()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.sendPriceTagsToPrinter(
                        sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                        printerAtPrintTime,
                        Constants.TAG_GONDOLA,
                        SendPriceTagsRequest(listaParaImprimir)
                    )
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        ToastUtils.show(requireContext(), "${quantidade}x ${item.description}", Toast.LENGTH_LONG)
                        removerItem(item)
                    } else {
                        val errorMsg = when (response.code()) {
                            401 -> {
                                SessionExpiredHandler.handleSessionExpired(requireActivity())
                                "Sessão expirada. Faça login novamente."
                            }
                            else -> "Erro: ${response.code()}"
                        }
                        ToastUtils.show(requireContext(), errorMsg, Toast.LENGTH_LONG)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("EtiquetasFragment: Erro", e)
                withContext(Dispatchers.Main) {
                    ToastUtils.show(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun abrirEdicao(lista: List<PrintingData>) {
        try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(lista)
            val intent = Intent(requireContext(), io.loja.ui.GondolaEditavelActivity::class.java).apply {
                putExtra("ITEMS_JSON", json)
                putExtra("PRINTER_ID", getPrinterIdFromSpinner())
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao abrir edição", e)
            ToastUtils.show(requireContext(), "Erro: ${e.message}")
        }
    }

    private fun removerItem(item: PriceTag) {
        try {
            val position = items.indexOf(item)
            if (position >= 0) {
                items.removeAt(position)
                adapter?.notifyItemRemoved(position)
                adapter?.notifyItemRangeChanged(position, items.size)
                atualizarLista()
                listaViewModel.saveEtiquetas(items)
                ToastUtils.show(requireContext(), "Item removido")
            }
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao remover", e)
        }
    }

    private fun configurarSwipeCards(lista: RecyclerView) {
        val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.remove)?.mutate()?.apply { setTint(0xFFFFFFFF.toInt()) }
        val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val swipeHelper = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.4f

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                if (position >= items.size) return
                val item = items[position]
                if (direction == ItemTouchHelper.RIGHT) removerItem(item)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0f) {
                    val density = resources.displayMetrics.density
                    val cardMargin = 4 * density
                    val top = itemView.top.toFloat() + cardMargin
                    val bottom = itemView.bottom.toFloat() - cardMargin
                    val radius = 8 * density
                    val revealLeft = (itemView.left.toFloat() + dX).coerceAtLeast(itemView.left.toFloat())
                    desenharFundoSwipe(c, swipePaint, deleteIcon, itemView.left.toFloat(), top, revealLeft, bottom, radius, 0xFFD32F2F.toInt(), density, true)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHelper).attachToRecyclerView(lista)
    }

    private fun desenharFundoSwipe(c: Canvas, paint: Paint, icon: Drawable?, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int, density: Float, alignStart: Boolean) {
        if (right <= left || bottom <= top) return
        paint.color = color
        c.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        if (icon == null) return
        val iconSize = 28 * density
        val margin = 18 * density
        val iconLeft = if (alignStart) left + margin else right - margin - iconSize
        val iconTop = top + (bottom - top - iconSize) / 2
        icon.setBounds(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
        icon.draw(c)
    }

    internal fun gerarPdfEtiquetas() {
        if (items.isEmpty()) {
            ToastUtils.show(requireContext(), "Nenhuma etiqueta para gerar PDF")
            return
        }
        btnPdf.isEnabled = false
        try {
            io.loja.utils.RelatorioPdf.gerarRelatorioEtiquetas(requireContext(), items) { i ->
                adapter?.getQuantityAt(i) ?: 1
            }
        } finally {
            btnPdf.isEnabled = true
        }
    }

    internal fun imprimirTodasEtiquetas() {
        if (items.isEmpty()) {
            ToastUtils.show(requireContext(), "Nenhuma etiqueta para imprimir")
            return
        }

        val printingDataList = mutableListOf<PrintingData>()
        for (i in items.indices) {
            val item = items[i]
            val printingData = item.printingData ?: continue
            
            val quantidadeDigitada = adapter?.getQuantityAt(i) ?: 1
            
            for (j in 0 until quantidadeDigitada) {
                val templateNorm = Constants.templateNormalizado(printingData.template)
                printingDataList.add(
                    printingData.copy(
                        quantity = 1,
                        template = if (printingData.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO else templateNorm
                    )
                )
            }
        }

        if (printingDataList.isEmpty()) {
            ToastUtils.show(requireContext(), "Dados de impressão não disponíveis")
            return
        }

        if (modoEdicao) {
            abrirEdicao(printingDataList)
            return
        }

        btnImprimirTodas.text = "IMPRIMINDO..."
        btnImprimirTodas.isEnabled = false

        val apiService = ApiClient.getSlApiService(sessionManager)
        val printerAtPrintTime = getPrinterIdFromSpinner()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.sendPriceTagsToPrinter(
                        sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                        printerAtPrintTime,
                        Constants.TAG_GONDOLA,
                        SendPriceTagsRequest(printingDataList)
                    )
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val totalImpresso = printingDataList.size
                        ToastUtils.show(requireContext(), "$totalImpresso etiquetas enviadas!", Toast.LENGTH_LONG)
                        items.clear()
                        adapter?.notifyDataSetChanged()
                        atualizarLista()
                        listaViewModel.saveEtiquetas(items)
                        resetarBtnImprimirTodas()
                    } else {
                        val errorMsg = when (response.code()) {
                            401 -> {
                                SessionExpiredHandler.handleSessionExpired(requireActivity())
                                "Sessão expirada. Faça login novamente."
                            }
                            else -> "Erro: ${response.code()}"
                        }
                        ToastUtils.show(requireContext(), errorMsg, Toast.LENGTH_LONG)
                        resetarBtnImprimirTodas()
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("EtiquetasFragment: Erro", e)
                withContext(Dispatchers.Main) {
                    ToastUtils.show(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG)
                    resetarBtnImprimirTodas()
                }
            }
        }
    }
    
    private fun resetarBtnImprimirTodas() {
        btnImprimirTodas.text = "IMPRIMIR TODAS"
        btnImprimirTodas.isEnabled = true
    }

    internal fun limparLista() {
        try {
            if (items.isEmpty()) {
                ToastUtils.show(requireContext(), "Lista já está vazia")
                return
            }
            
            btnLimpar.text = "LIMPANDO..."
            btnLimpar.isEnabled = false
            btnLimpar.setBackgroundResource(R.drawable.btn_rounded_gray)
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Limpar Lista")
                .setMessage("Tem certeza que deseja remover todos os itens?")
                .setPositiveButton("Sim") { _, _ ->
                    items.clear()
                    adapter?.notifyDataSetChanged()
                    atualizarLista()
                    listaViewModel.saveEtiquetas(items)
                    ToastUtils.show(requireContext(), "Lista limpa")
                    resetarBtnLimpar()
                }
                .setNegativeButton("Não") { _, _ ->
                    resetarBtnLimpar()
                }
                .setOnCancelListener {
                    resetarBtnLimpar()
                }
                .show()
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao limpar", e)
            resetarBtnLimpar()
        }
    }
    
    private fun resetarBtnLimpar() {
        btnLimpar.text = "LIMPAR LISTA"
        btnLimpar.isEnabled = true
        btnLimpar.setBackgroundResource(R.drawable.btn_outline_red)
    }

    private fun atualizarLista() {
        try {
            if (items.isEmpty()) {
                layoutEmptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                tvTotal.visibility = View.GONE
                btnLimpar.visibility = View.GONE
                btnImprimirTodas.visibility = View.GONE
                btnPdf.visibility = View.GONE
            } else {
                layoutEmptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                tvTotal.visibility = View.VISIBLE
                tvTotal.text = if (items.size == 1) "1 item na lista" else "${items.size} itens na lista"
                btnLimpar.visibility = View.VISIBLE
                btnImprimirTodas.visibility = View.VISIBLE
                btnPdf.visibility = View.VISIBLE
            }
            adapter?.notifyDataSetChanged()
            listaViewModel.saveEtiquetas(items)
        } catch (e: Exception) {
            LogHelper.e("EtiquetasFragment: Erro ao atualizar lista", e)
        }
    }

    private fun ocultarTeclado() {
        try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(requireView().windowToken, 0)
            etBusca.clearFocus()
        } catch (e: Exception) {
            LogHelper.e("Erro ao ocultar teclado", e)
        }
    }

    private fun iniciarPulsacaoFab() {
        pararPulsacaoFab()
        fabPulseHandler = Handler(Looper.getMainLooper())
        fabPulseRunnable = object : Runnable {
            override fun run() {
                try {
                    fabScanner.animate()
                        .alpha(0.6f)
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(600)
                        .withEndAction {
                            fabScanner.animate()
                                .alpha(1.0f)
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(600)
                                .start()
                        }
                        .start()
                    fabPulseHandler?.postDelayed(this, 1400)
                } catch (e: Exception) {
                    LogHelper.e("Erro no pulso do FAB", e)
                }
            }
        }
        fabPulseRunnable?.let { fabPulseHandler?.post(it) }
    }

    private fun pararPulsacaoFab() {
        fabPulseRunnable?.let { fabPulseHandler?.removeCallbacks(it) }
        fabPulseRunnable = null
        fabPulseHandler = null
        fabScanner.animate().cancel()
        fabScanner.alpha = 1.0f
        fabScanner.scaleX = 1.0f
        fabScanner.scaleY = 1.0f
    }
}

class EtiquetaAdapter(
    private val items: List<PriceTag>,
    private val onImprimir: (PriceTag, Int) -> Unit,
    private val onRemover: (PriceTag) -> Unit
) : RecyclerView.Adapter<EtiquetaAdapter.ViewHolder>() {

    private val quantities = mutableMapOf<Int, Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEan: TextView = view.findViewById(R.id.tvEan)
        val tvDescricao: TextView = view.findViewById(R.id.tvDescricao)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvLeveGanhe: TextView = view.findViewById(R.id.tvLeveGanhe)
        val tvValor: TextView = view.findViewById(R.id.tvValor)
        val tvBadgeTipoLeve: TextView = view.findViewById(R.id.tvBadgeTipoLeve)
        val tvBadgeTipoDepor: TextView = view.findViewById(R.id.tvBadgeTipoDepor)
        val layoutLeveGanhe: View = view.findViewById(R.id.layoutLeveGanhe)
        val layoutDepor: View = view.findViewById(R.id.layoutDepor)
        val btnImprimir: ImageButton = view.findViewById(R.id.btnImprimir)
        val btnRemover: ImageButton = view.findViewById(R.id.btnRemover)
        val etQuantidade: EditText = view.findViewById(R.id.etQuantidade)

        init {
            (itemView as MaterialCardView).apply {
                strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
                strokeColor = ContextCompat.getColor(context, R.color.card_border_color)
            }
        }

        fun estilizarBadgeTipo(view: TextView, color: Int) {
            view.background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(0xFFFFFFFF.toInt())
            }
            view.setTextColor(color)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_etiqueta, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            if (position < items.size) {
                val item = items[position]
                val deptNum = item.department.substringBefore(" -").trim()
                holder.tvEan.text = "DEP: $deptNum | EAN: ${item.ean} / SAP: ${item.sap}"
                holder.tvDescricao.text = item.description
                
                val formatador = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                
                val printingData = item.printingData
                val precoNormal = printingData?.price?.value ?: 0.0
                val precoPromocional = printingData?.promotionalPrice?.value
                val takeAndWin = printingData?.takeAndWin

                if (takeAndWin != null && takeAndWin.qty > 0 && takeAndWin.totalValue > 0) {
                    holder.layoutLeveGanhe.visibility = View.VISIBLE
                    val totalLeve = if (
                        printingData?.template == Constants.SIGN_TEMPLATE_LEVE_GANHE_CADA &&
                        precoPromocional != null && precoPromocional > 0
                    ) precoPromocional else takeAndWin.totalValue
                    holder.tvLeveGanhe.text = "Leve ${takeAndWin.qty} por R$ ${java.text.DecimalFormat("0.00").format(totalLeve)}"
                    holder.tvPrice.text = formatador.format(precoNormal)
                    holder.tvBadgeTipoLeve.text = "leve e ganhe"
                    holder.estilizarBadgeTipo(holder.tvBadgeTipoLeve, 0xFF2E7D32.toInt())
                    holder.layoutDepor.visibility = View.GONE
                } else {
                    holder.layoutLeveGanhe.visibility = View.GONE
                    holder.layoutDepor.visibility = View.VISIBLE
                    val installments = printingData?.installments
                    // Parcelado só com DADOS — igual ao Flutter (EtiquetaCard).
                    val isParcelado = (installments != null && installments.installmentQuantity > 0)
                    if (isParcelado) {
                        val qtdParc = installments?.installmentQuantity?.takeIf { it > 0 }
                        val precoParcelado = (installments?.installmentValue ?: 0.0).takeIf { it > 0 }
                            ?: (printingData?.displayPrice ?: 0.0).takeIf { it > 0 }
                            ?: (printingData?.unitValue ?: 0.0).takeIf { it > 0 }
                            ?: (printingData?.unitQty ?: 0.0).takeIf { it > 0 }
                            ?: precoPromocional?.takeIf { it > 0 }
                            ?: precoNormal
                        LogHelper.d("EtiquetasAdapter: parcelado display=${printingData?.displayPrice} unitValue=${printingData?.unitValue} unitQty=${printingData?.unitQty} promo=$precoPromocional normal=$precoNormal")
                        holder.layoutDepor.setBackgroundResource(R.drawable.badge_green_container)
                        val por = precoPromocional?.takeIf { it > 0 }
                        val numFmt = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
                            minimumFractionDigits = 2
                            maximumFractionDigits = 2
                        }
                        holder.tvValor.text = if (qtdParc != null && por != null && precoParcelado > 0)
                            "R$${numFmt.format(por)} | ${qtdParc}x de R$${numFmt.format(precoParcelado)}"
                        else if (qtdParc != null)
                            "${formatador.format(precoNormal)} | ${qtdParc}x de R$${String.format(Locale("pt", "BR"), "%.2f", precoParcelado)}"
                        else
                            formatador.format(precoParcelado)
                        holder.tvValor.setTextColor(0xFFFFFFFF.toInt())
                        holder.tvBadgeTipoDepor.text = "parcelado"
                        holder.estilizarBadgeTipo(holder.tvBadgeTipoDepor, 0xFFFF9800.toInt())
                    } else if (precoPromocional != null && precoPromocional > 0 && precoPromocional < precoNormal) {
                        holder.layoutDepor.setBackgroundResource(R.drawable.badge_green_container)
                        holder.tvValor.text = "De ${formatador.format(precoNormal)} | Por ${formatador.format(precoPromocional)}"
                        holder.tvValor.setTextColor(0xFFFFFFFF.toInt())
                        holder.tvBadgeTipoDepor.text = "de por"
                        holder.estilizarBadgeTipo(holder.tvBadgeTipoDepor, 0xFFFF9800.toInt())
                    } else {
                        holder.tvValor.text = formatador.format(precoNormal)
                        holder.layoutDepor.setBackgroundResource(R.drawable.badge_green_container)
                        holder.tvValor.setTextColor(0xFFFFFFFF.toInt())
                        holder.tvBadgeTipoDepor.text = "normal"
                        holder.estilizarBadgeTipo(holder.tvBadgeTipoDepor, 0xFF9E9E9E.toInt())
                    }
                }

                val savedQty = quantities[position] ?: 1
                holder.etQuantidade.setText(savedQty.toString())

                holder.etQuantidade.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val qty = s.toString().toIntOrNull() ?: 1
                        quantities[holder.adapterPosition] = qty.coerceIn(1, 999)
                    }
                })

                holder.btnImprimir.setOnClickListener {
                    val qtd = holder.etQuantidade.text.toString().toIntOrNull() ?: 1
                    onImprimir(item, qtd)
                }

                holder.btnRemover.setOnClickListener { onRemover(item) }
            }
        } catch (e: Exception) {
            LogHelper.e("EtiquetaAdapter: Erro ao bind", e)
        }
    }

    override fun getItemCount(): Int = items.size

    fun getQuantityAt(position: Int): Int = quantities[position] ?: 1

    fun setQuantityAt(position: Int, qty: Int) {
        quantities[position] = qty.coerceIn(1, 999)
    }
}