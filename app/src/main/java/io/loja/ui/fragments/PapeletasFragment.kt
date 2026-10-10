package io.loja.ui.fragments

import android.app.Activity
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
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.loja.R
import io.loja.network.ApiClient
import io.loja.network.PriceSign
import io.loja.network.PapeletaPrintingData
import io.loja.network.SendPriceSignRequest
import io.loja.network.SessionManager
import io.loja.ui.BarcodeScannerActivity
import io.loja.ui.ListaViewModel
import io.loja.ui.ModeloEditavelActivity
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.SessionExpiredHandler
import io.loja.utils.ToastUtils
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PapeletasFragment : Fragment() {

    private lateinit var spinnerTipo: Spinner
    private lateinit var spinnerModelo: Spinner
    private lateinit var spinnerBuscaTipo: Spinner
    private lateinit var spinnerTamanho: Spinner
    private var tamanhoSelecionado: String = Constants.SIGN_SIZE_4X1
    private lateinit var etBusca: EditText
    private lateinit var btnBuscar: Button
    private lateinit var fabScanner: FloatingActionButton
    private lateinit var btnLimpar: Button
    private lateinit var btnImprimirTodas: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var tvColagem: TextView

    private var items = mutableListOf<PriceSign>()
    private var adapter: PapeletaAdapter? = null
    internal var tipoPapeleta = Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
    internal var usarMisto = false
    internal var usarModeloEditavel = false
    internal var usarComumEditavel = false
    internal var usarVencimentos = false
    internal var buscaTipo = "EAN"
    private var isModoEditavel = false
    private val IMPRIMIR_ITEM_REQUEST = 2001
    private val IMPRIMIR_TODAS_REQUEST = 2002
    private var itemEmImpressao: PriceSign? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var listaViewModel: ListaViewModel
    private var fabRevelado = false
    private var scannerContinuoAtivo = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val BARCODE_REQUEST = 1000
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_papeletas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            listaViewModel = ViewModelProvider(requireActivity())[ListaViewModel::class.java]
            items = listaViewModel.getPapeletas()

            sessionManager = SessionManager(requireContext())
            val token = sessionManager.getToken()
            if (token.isNullOrEmpty()) {
                LogHelper.e("PapeletasFragment: Token não encontrado!")
                ToastUtils.show(requireContext(), "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG)
                SessionExpiredHandler.handleSessionExpired(requireActivity())
                return
            }

            spinnerTipo = view.findViewById(R.id.spinnerTipo)
            spinnerModelo = view.findViewById(R.id.spinnerModelo)
            spinnerBuscaTipo = view.findViewById(R.id.spinnerBuscaTipo)
            spinnerTamanho = view.findViewById(R.id.spinnerTamanho)
            etBusca = view.findViewById(R.id.etBusca)
            btnBuscar = view.findViewById(R.id.btnBuscar)
            fabScanner = view.findViewById(R.id.fabScanner)
            val fabHideOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 55f, resources.displayMetrics)
            fabScanner.translationX = fabHideOffset
            fabScanner.setOnClickListener {
                if (!fabRevelado) {
                    fabRevelado = true
                    fabScanner.animate().translationX(0f).setDuration(300).start()
                } else {
                    abrirLeitorDeBarras()
                }
            }
            btnLimpar = view.findViewById(R.id.btnLimpar)
            btnImprimirTodas = view.findViewById(R.id.btnImprimirTodas)
            recyclerView = view.findViewById(R.id.recyclerView)
            tvTotal = view.findViewById(R.id.tvTotal)
            layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
            tvColagem = view.findViewById(R.id.tvColagem)

            configurarSpinners()
            carregarColagem()

            adapter = PapeletaAdapter(
                items = items,
                onImprimir = { item, quantidade ->
                    imprimirPapeleta(item, quantidade)
                },
                onRemover = { item ->
                    removerItem(item)
                }
            )
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
            configurarSwipeCards(recyclerView)

            atualizarLista()

            btnBuscar.setOnClickListener {
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
                buscarPapeleta(busca)
            }

            btnLimpar.setOnClickListener { limparLista() }
            btnImprimirTodas.setOnClickListener { imprimirTodasPapeletas() }

            view.setOnTouchListener { _, _ ->
                ocultarTeclado()
                false
            }

        } catch (e: Exception) {
            LogHelper.e("PapeletasFragment: ERRO", e)
            ToastUtils.show(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    override fun onDestroyView() {
        listaViewModel.savePapeletas(items)
        super.onDestroyView()
    }

    private var _tipoTocada = false
    private var _modeloTocada = false
    private var _buscaPapeletasTocada = false
    private var _tamanhoTocada = false

    private fun configurarSpinners() {
        val vermelho = android.graphics.Color.parseColor("#D7193F")
        var initTipo = true
        var initModelo = true
        var initBusca = true
        var initTamanho = true
        val tipos = arrayOf("Normal", "Promocional")
        val tipoAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, tipos) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (_tipoTocada) vermelho else android.graphics.Color.BLACK)
                return v
            }
        }
        tipoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipo.adapter = tipoAdapter
        spinnerTipo.setSelection(1)
        spinnerTipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initTipo) { initTipo = false } else if (!_tipoTocada) { _tipoTocada = true; (view as? TextView)?.setTextColor(vermelho) }
                tipoPapeleta = if (position == 0) Constants.SIGN_TYPE_PAPELETA_COMUM else Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val modelos = arrayOf("Misto", "Promocional", "Promocional Editável", "Normal", "Normal Editável", "Vencimentos")
        val modeloAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, modelos) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (_modeloTocada) vermelho else android.graphics.Color.BLACK)
                return v
            }
        }
        modeloAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModelo.adapter = modeloAdapter
        spinnerModelo.setSelection(0)
        spinnerModelo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initModelo) { initModelo = false } else if (!_modeloTocada) { _modeloTocada = true; (view as? TextView)?.setTextColor(vermelho) }
                usarMisto = (position == 0)
                usarModeloEditavel = (position == 1)
                usarComumEditavel = false
                usarVencimentos = (position == 5)
                isModoEditavel = false
                if (position == 2) {
                    usarModeloEditavel = true
                    tipoPapeleta = Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL
                    isModoEditavel = true
                }
                if (position == 4) {
                    usarComumEditavel = true
                    tipoPapeleta = Constants.SIGN_TYPE_PAPELETA_COMUM
                    isModoEditavel = true
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val buscaTipos = arrayOf("EAN", "SAP", "Descrição do item")
        val buscaAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, buscaTipos) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (_buscaPapeletasTocada) vermelho else android.graphics.Color.BLACK)
                return v
            }
        }
        buscaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBuscaTipo.adapter = buscaAdapter
        spinnerBuscaTipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initBusca) { initBusca = false } else if (!_buscaPapeletasTocada) { _buscaPapeletasTocada = true; (view as? TextView)?.setTextColor(vermelho) }
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

        val tamanhos = arrayOf("1×1", "2×1", "4×1", "6×1")
        val tamanhoAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, tamanhos) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (_tamanhoTocada) vermelho else android.graphics.Color.BLACK)
                return v
            }
        }
        tamanhoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTamanho.adapter = tamanhoAdapter
        spinnerTamanho.setSelection(2)
        spinnerTamanho.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initTamanho) { initTamanho = false } else if (!_tamanhoTocada) { _tamanhoTocada = true; (view as? TextView)?.setTextColor(vermelho) }
                tamanhoSelecionado = when (position) {
                    0 -> Constants.SIGN_SIZE_1X1
                    1 -> Constants.SIGN_SIZE_2X1
                    2 -> Constants.SIGN_SIZE_4X1
                    else -> Constants.SIGN_SIZE_6X1
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun carregarColagem() {
        val code = listaViewModel.getColagem()
        if (code.isNotEmpty()) {
            tvColagem.text = "Área de colagem: $code"
            tvColagem.visibility = View.VISIBLE
            tvColagem.setOnClickListener {
                etBusca.setText(code)
                buscarPapeleta(code)
            }
        } else {
            tvColagem.visibility = View.GONE
        }
    }

    private fun abrirLeitorDeBarras() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
        intent.putExtra(BarcodeScannerActivity.EXTRA_MODO_CONTINUO, true)
        intent.putExtra(BarcodeScannerActivity.EXTRA_TIPO_LISTA, BarcodeScannerActivity.TIPO_LISTA_PAPELETAS)
        scannerContinuoAtivo = true
        startActivityForResult(intent, BARCODE_REQUEST)
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
            val fresh = listaViewModel.getPapeletas()
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
            LogHelper.e("PapeletasFragment: Erro ao sincronizar scanner", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPRIMIR_ITEM_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                itemEmImpressao?.let { removerItem(it) }
            }
            itemEmImpressao = null
            return
        }
        if (requestCode == IMPRIMIR_TODAS_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                items.clear()
                adapter?.notifyDataSetChanged()
                atualizarLista()
                listaViewModel.savePapeletas(items)
            }
            return
        }
        if (requestCode == BARCODE_REQUEST && resultCode == Activity.RESULT_OK) {
            val ean = data?.getStringExtra("EAN") ?: ""
            if (ean.isNotEmpty()) {
                etBusca.setText(ean)
                listaViewModel.setColagem(ean)
                carregarColagem()
                try {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("EAN", ean)
                    clipboard.setPrimaryClip(clip)
                } catch (_: Exception) {}
                buscarPapeleta(ean)
            }

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

    internal fun buscarPapeleta(busca: String, onComplete: () -> Unit = {}) {
        val apiService = ApiClient.getSlApiService(sessionManager)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        btnBuscar.text = "BUSCANDO..."
        btnBuscar.isEnabled = false

        lifecycleScope.launch {
            try {
                val responses = withContext(Dispatchers.IO) {
                    listOf(Constants.TIPO_COMUM, Constants.TIPO_PROMOCIONAL).map { type ->
                        try {
                            apiService.getPriceSignStandalone(
                                storeId = Constants.DEFAULT_STORE,
                                type = type,
                                ean = if (buscaTipo == "EAN") busca else null,
                                sapId = if (buscaTipo == "SAP") busca else null,
                                description = if (buscaTipo != "EAN" && buscaTipo != "SAP") busca else null,
                                startDate = currentDate
                            )
                        } catch (e: Exception) { null }
                    }
                }

                withContext(Dispatchers.Main) {
                    btnBuscar.text = "BUSCAR"
                    btnBuscar.isEnabled = true
                    onComplete()

                    var itensAdicionados = 0
                    var itensIncrementados = 0

                    responses.forEach { response ->
                        if (response != null && response.isSuccessful) {
                            val data = response.body()
                            if (data != null && data.items.isNotEmpty()) {
                                data.items.forEach { item ->
                                    val existingIndex = items.indexOfFirst { it.ean == item.ean }
                                    if (existingIndex >= 0) {
                                        val currentQty = adapter?.getQuantityAt(existingIndex) ?: 1
                                        adapter?.setQuantityAt(existingIndex, currentQty + 1)
                                        adapter?.notifyItemChanged(existingIndex)
                                        itensIncrementados++
                                    } else {
                                        items.add(item)
                                        itensAdicionados++
                                    }
                                }
                            }
                        }
                    }

                    if (itensAdicionados > 0 || itensIncrementados > 0) {
                        adapter?.notifyDataSetChanged()
                        etBusca.text.clear()
                        atualizarLista()
                        listaViewModel.savePapeletas(items)
                        val msg = buildString {
                            if (itensAdicionados > 0) append("$itensAdicionados adicionado(s)")
                            if (itensAdicionados > 0 && itensIncrementados > 0) append(" | ")
                            if (itensIncrementados > 0) append("$itensIncrementados incrementado(s)")
                        }
                        ToastUtils.show(requireContext(), msg)
                    } else {
                        val anySuccess = responses.any { it?.isSuccessful == true }
                        if (anySuccess) {
                            ToastUtils.show(requireContext(), "Nenhum item encontrado")
                        } else {
                            val firstErr = responses.firstOrNull { it != null && !it.isSuccessful }
                            val errorMsg = if (firstErr != null) {
                                when (firstErr.code()) {
                                    401 -> { SessionExpiredHandler.handleSessionExpired(requireActivity()); "Sessão expirada." }
                                    403 -> "Acesso negado."
                                    else -> "Erro: ${firstErr.code()}"
                                }
                            } else {
                                "Erro na busca"
                            }
                            ToastUtils.show(requireContext(), errorMsg, Toast.LENGTH_LONG)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnBuscar.text = "BUSCAR"
                    btnBuscar.isEnabled = true
                    onComplete()
                    ToastUtils.show(requireContext(), "Erro: ${e.message}")
                }
                LogHelper.e("PapeletasFragment: Erro na busca", e)
            }
        }
    }

    private fun tamanhoDoSpinner(): String = when (spinnerTamanho.selectedItemPosition) {
        0 -> Constants.SIGN_SIZE_1X1
        1 -> Constants.SIGN_SIZE_2X1
        2 -> Constants.SIGN_SIZE_4X1
        else -> Constants.SIGN_SIZE_6X1
    }

    private fun imprimirPapeleta(item: PriceSign, quantidade: Int) {
        val signData = item.printingData ?: run {
            ToastUtils.show(requireContext(), "Dados de impressão não disponíveis")
            return
        }

        val tamanhoAtual = tamanhoDoSpinner()
        if (usarMisto || usarModeloEditavel || usarComumEditavel || usarVencimentos) {
            if (usarMisto) {
                val qtd = quantidade
                val lista = (0 until qtd).map {
                    ajustarDadosLeveGanhe(
                        signData.copy(
                            template = signData.template ?: templatePorMovimento(item.movement),
                            quantity = 1,
                            size = tamanhoSelecionado
                        )
                    )
                }
                ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
                startActivity(Intent(requireContext(), ModeloEditavelActivity::class.java).apply {
                    putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, false)
                    putExtra(ModeloEditavelActivity.EXTRA_SIZE, tamanhoSelecionado)
                    putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, false)
                    putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, usarMisto || usarModeloEditavel || usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, true)
                })
                return
            }
            if (usarModeloEditavel && isItemComum(item)) {
                ToastUtils.show(requireContext(), "Esta papeleta é do tipo Comum, selecione o modo Comum")
                return
            }
            if (usarComumEditavel && !isItemComum(item)) {
                ToastUtils.show(requireContext(), "Esta papeleta é do tipo Promocional, selecione o modo Promocional")
                return
            }
            val qtd = quantidade
            if (isModoEditavel || usarVencimentos) {
                val qtd = quantidade
                val lista = (0 until qtd).map {
                    ajustarDadosLeveGanhe(
                        signData.copy(
                            template = signData.template ?: templatePorMovimento(item.movement),
                            quantity = 1,
                            size = tamanhoAtual
                        )
                    )
                }
                ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
                itemEmImpressao = item
                startActivityForResult(Intent(requireContext(), ModeloEditavelActivity::class.java).apply {
                    putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, usarComumEditavel)
                    putExtra(ModeloEditavelActivity.EXTRA_SIZE, tamanhoAtual)
                    putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, false)
                    putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, usarMisto || usarModeloEditavel || usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, true)
                    putExtra(ModeloEditavelActivity.EXTRA_MODO_VENCIMENTOS, usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, !usarComumEditavel)
                }, IMPRIMIR_ITEM_REQUEST)
            } else {
                val lista = (0 until qtd).map {
                    ajustarDadosLeveGanhe(
                        signData.copy(
                            template = signData.template ?: templatePorMovimento(item.movement),
                            quantity = 1,
                            size = tamanhoAtual
                        )
                    )
                }
                ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
                itemEmImpressao = item
                startActivityForResult(Intent(requireContext(), ModeloEditavelActivity::class.java).apply {
                    putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, usarComumEditavel)
                    putExtra(ModeloEditavelActivity.EXTRA_SIZE, tamanhoAtual)
                    putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, false)
                    putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, usarMisto || usarModeloEditavel || usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_HIDE_GERAR_PREVIEW, usarModeloEditavel || usarComumEditavel)
                    putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, true)
                }, IMPRIMIR_ITEM_REQUEST)
            }
            return
        }

        val printingList = (0 until quantidade).map {
            signData.copy(quantity = 1, size = tamanhoAtual)
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getSlApiService(sessionManager)
                        .sendPriceSigns(Constants.DEFAULT_STORE, SendPriceSignRequest(printingList))
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        ToastUtils.show(requireContext(), "${quantidade}x ${item.description}")
                        removerItem(item)
                    } else {
                        val msg = if (response.code() == 401) { SessionExpiredHandler.handleSessionExpired(requireActivity()); "Sessão expirada" } else "Erro: ${response.code()}"
                        ToastUtils.show(requireContext(), msg, Toast.LENGTH_LONG)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ToastUtils.show(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG) }
                LogHelper.e("PapeletasFragment: Erro", e)
            }
        }
    }

    internal fun imprimirTodasPapeletas() {
        if (items.isEmpty()) { ToastUtils.show(requireContext(), "Nenhuma papeleta para imprimir"); return }
        sincronizarQuantidades()

        if (usarMisto || usarModeloEditavel || usarComumEditavel || usarVencimentos) {
            btnImprimirTodas.text = "CARREGANDO..."
            btnImprimirTodas.isEnabled = false

            if (usarMisto) {
                val lista = items.mapIndexedNotNull { i, item ->
                    val qtd = adapter?.getQuantityAt(i) ?: 1
                    val data = item.printingData ?: return@mapIndexedNotNull null
                    (0 until qtd).map {
                        ajustarDadosLeveGanhe(
                            data.copy(
                                template = data.template ?: templatePorMovimento(item.movement),
                                quantity = 1,
                                size = tamanhoSelecionado
                            )
                        )
                    }
                }.flatten().toList()
                if (lista.isEmpty()) {
                    btnImprimirTodas.text = "IMPRIMIR TODAS"
                    btnImprimirTodas.isEnabled = true
                    ToastUtils.show(requireContext(), "Nenhum item para imprimir")
                    return
                }
                ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
                startActivity(Intent(requireContext(), ModeloEditavelActivity::class.java).apply {
                    putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, false)
                    putExtra(ModeloEditavelActivity.EXTRA_SIZE, tamanhoSelecionado)
                    putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, false)
                    putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, usarMisto || usarModeloEditavel || usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, true)
                })
                btnImprimirTodas.text = "IMPRIMIR TODAS"
                btnImprimirTodas.isEnabled = true
                return
            }

            if (isModoEditavel) {
                val lista = items.mapIndexedNotNull { i, item ->
                    val ehComum = isItemComum(item)
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM && !ehComum) return@mapIndexedNotNull null
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL && ehComum) return@mapIndexedNotNull null
                    val passa = when {
                        usarComumEditavel -> ehComum
                        else -> !ehComum
                    }
                    if (!passa) return@mapIndexedNotNull null
                    if (ehComum && !isComumElegivel(item)) return@mapIndexedNotNull null
                    val qtd = adapter?.getQuantityAt(i) ?: 1
                    val data = item.printingData ?: return@mapIndexedNotNull null
                    (0 until qtd).map {
                        ajustarDadosLeveGanhe(
                            data.copy(
                                template = data.template ?: templatePorMovimento(item.movement),
                                quantity = 1,
                                size = tamanhoDoSpinner()
                            )
                        )
                    }
                }.flatten().toList()
                if (lista.isEmpty()) {
                    btnImprimirTodas.text = "IMPRIMIR TODAS"
                    btnImprimirTodas.isEnabled = true
                    ToastUtils.show(requireContext(), if (usarComumEditavel) "Nenhum item do tipo Comum" else "Nenhum item na lista")
                    return
                }
                ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
                startActivityForResult(Intent(requireContext(), ModeloEditavelActivity::class.java).apply {
                    putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, usarComumEditavel)
                    putExtra(ModeloEditavelActivity.EXTRA_SIZE, tamanhoDoSpinner())
                    putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, lista.all { ehPapeletaComumData(it) } && !usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, usarMisto || usarModeloEditavel || usarVencimentos)
                    putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, true)
                    putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, !usarComumEditavel)
                }, IMPRIMIR_TODAS_REQUEST)
                btnImprimirTodas.text = "IMPRIMIR TODAS"
                btnImprimirTodas.isEnabled = true
                return
            }

            val tamanhoAtual = tamanhoDoSpinner()
            val lista = mutableListOf<PapeletaPrintingData>()
            val qtds = mutableListOf<Int>()
            for ((i, item) in items.withIndex()) {
                val ehComum = isItemComum(item)
                if (!usarVencimentos) {
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM && !ehComum) continue
                    if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL && ehComum) continue
                }
                if (!usarVencimentos && ehComum && !isComumElegivel(item)) continue
                val qtd = adapter?.getQuantityAt(i) ?: 1
                val data = item.printingData ?: continue
                val base = ajustarDadosLeveGanhe(
                    data.copy(
                        template = data.template ?: templatePorMovimento(item.movement),
                        quantity = 1,
                        size = tamanhoAtual
                    )
                )
                if (usarVencimentos) {
                    repeat(qtd) {
                        qtds.add(1)
                        lista.add(base)
                    }
                } else {
                    repeat(qtd) { lista.add(base) }
                }
            }
            if (lista.isEmpty()) {
                btnImprimirTodas.text = "IMPRIMIR TODAS"
                btnImprimirTodas.isEnabled = true
                ToastUtils.show(requireContext(), when {
                    usarVencimentos -> "Nenhum item na lista"
                    usarComumEditavel -> "Nenhum item do tipo Comum"
                    else -> "Nenhum item do tipo Modelo"
                })
                return
            }
            ModeloEditavelActivity.dadosPendentesList = lista.toMutableList()
            startActivityForResult(Intent(requireContext(), ModeloEditavelActivity::class.java).apply {
                putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, usarComumEditavel)
                putExtra(ModeloEditavelActivity.EXTRA_SIZE, tamanhoAtual)
                putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, false)
                putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, usarMisto || usarModeloEditavel || usarVencimentos)
                putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, usarVencimentos)
                putExtra(ModeloEditavelActivity.EXTRA_MODO_VENCIMENTOS, usarVencimentos)
                putExtra(ModeloEditavelActivity.EXTRA_HIDE_GERAR_PREVIEW, usarModeloEditavel || usarComumEditavel)
                putExtra(ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY, true)
                putExtra(ModeloEditavelActivity.EXTRA_QUANTIDADES, if (usarVencimentos) qtds.joinToString(",") else null)
            }, IMPRIMIR_TODAS_REQUEST)
            btnImprimirTodas.text = "IMPRIMIR TODAS"
            btnImprimirTodas.isEnabled = true
            return
        }

        val printingDataList = mutableListOf<PapeletaPrintingData>()
        for (i in items.indices) {
            val item = items[i]
            val signData = item.printingData ?: continue
            val ehComum = isItemComum(item)
            if (!usarMisto && tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM && (!ehComum || !isComumElegivel(item))) continue
            if (!usarMisto && tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL && ehComum) continue
            val qtd = adapter?.getQuantityAt(i) ?: 1
            repeat(qtd) { printingDataList.add(signData.copy(quantity = 1, size = tamanhoSelecionado)) }
        }
        if (printingDataList.isEmpty()) {
            val msg = if (tipoPapeleta == Constants.SIGN_TYPE_PAPELETA_COMUM) "Nenhum item do tipo Comum" else "Nenhum item do tipo Promocional"
            ToastUtils.show(requireContext(), msg); return
        }

        btnImprimirTodas.text = "IMPRIMINDO..."
        btnImprimirTodas.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getSlApiService(sessionManager)
                        .sendPriceSigns(Constants.DEFAULT_STORE, SendPriceSignRequest(printingDataList))
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        ToastUtils.show(requireContext(), "${printingDataList.size} papeletas enviadas!")
                        items.clear()
                        adapter?.notifyDataSetChanged()
                        atualizarLista()
                        listaViewModel.savePapeletas(items)
                    } else {
                        val msg = if (response.code() == 401) { SessionExpiredHandler.handleSessionExpired(requireActivity()); "Sessão expirada" } else "Erro: ${response.code()}"
                        ToastUtils.show(requireContext(), msg, Toast.LENGTH_LONG)
                    }
                    btnImprimirTodas.text = "IMPRIMIR TODAS"
                    btnImprimirTodas.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnImprimirTodas.text = "IMPRIMIR TODAS"
                    btnImprimirTodas.isEnabled = true
                    ToastUtils.show(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun removerItem(item: PriceSign) {
        val position = items.indexOf(item)
        if (position >= 0) {
            items.removeAt(position)
            adapter?.removerQuantidade(position)
            adapter?.notifyItemRemoved(position)
            adapter?.notifyItemRangeChanged(position, items.size)
            atualizarLista()
            listaViewModel.savePapeletas(items)
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

    private fun templatePorMovimento(movement: String?): String = when {
        movement?.equals("De/Por", ignoreCase = true) == true -> Constants.SIGN_TEMPLATE_DEPOR
        movement?.equals("Parcelado", ignoreCase = true) == true -> Constants.SIGN_TEMPLATE_DEPOR_PARCELADO
        else -> Constants.SIGN_TEMPLATE_MODELO_EDITAVEL
    }

    /**
     * Template leve_ganhe_cada: converte para leve_ganhe_total enviando o total
     * no takeAndWinPrice (etiqueta mostra "Leve X por R$ total") e o valor de cada
     * no promotionPrice (etiqueta mostra "preço unitário na promoção").
     */
    private fun ajustarDadosLeveGanhe(data: PapeletaPrintingData): PapeletaPrintingData {
        if (data.template != Constants.SIGN_TEMPLATE_LEVE_GANHE_CADA) return data
        val total = Constants.totalLeveGanhe(data.promotionPrice, data.takeAndWinPrice, data.takeAndWinQuantity)
        val qty = data.takeAndWinQuantity ?: 0
        val unitario = (data.takeAndWinPrice ?: 0.0).takeIf { it > 0 }
            ?: if (qty > 0) total / qty else total
        return data.copy(
            template = Constants.SIGN_TEMPLATE_LEVE_GANHE_TOTAL,
            promotionPrice = unitario,
            takeAndWinPrice = total,
            installmentPrice = null,
            installmentQuantity = null
        )
    }

    private fun ehPapeletaComumData(data: PapeletaPrintingData): Boolean {
        if (data.template == "por_de_parcelado") return true
        val hasPromo = data.promotionPrice != null && data.promotionPrice > 0
        val hasTakeAndWin = data.takeAndWinQuantity != null && data.takeAndWinQuantity > 0
        if (hasPromo || hasTakeAndWin) return false
        return true
    }

    private fun isItemComum(item: PriceSign): Boolean {
        val data = item.printingData ?: return true
        if (data.template == "por_de_parcelado") return true
        val hasPromo = data.promotionPrice != null && data.promotionPrice > 0
        val hasTakeAndWin = data.takeAndWinQuantity != null && data.takeAndWinQuantity > 0
        if (hasPromo || hasTakeAndWin) return false
        return true
    }

    private fun isComumElegivel(item: PriceSign): Boolean {
        val data = item.printingData ?: return true
        val hasInstallment = data.installmentQuantity != null && data.installmentQuantity > 0
        val hasPromo = data.promotionPrice != null && data.promotionPrice > 0
        val hasTakeAndWin = data.takeAndWinQuantity != null && data.takeAndWinQuantity > 0
        if (hasInstallment && !hasPromo && !hasTakeAndWin) return data.price > 149.99
        return true
    }

    internal fun limparLista() {
        if (items.isEmpty()) { ToastUtils.show(requireContext(), "Lista já está vazia"); return }
        AlertDialog.Builder(requireContext())
            .setTitle("Limpar Lista")
            .setMessage("Tem certeza que deseja remover todos os itens?")
            .setPositiveButton("Sim") { _, _ ->
                items.clear(); adapter?.notifyDataSetChanged(); atualizarLista()
                listaViewModel.savePapeletas(items); ToastUtils.show(requireContext(), "Lista limpa")
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun atualizarLista() {
        if (items.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvTotal.visibility = View.GONE
            btnLimpar.visibility = View.GONE
            btnImprimirTodas.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            tvTotal.visibility = View.VISIBLE
            tvTotal.text = if (items.size == 1) "1 item na lista" else "${items.size} itens na lista"
            btnLimpar.visibility = View.VISIBLE
            btnImprimirTodas.visibility = View.VISIBLE
        }
        adapter?.notifyDataSetChanged()
        listaViewModel.savePapeletas(items)
    }

    private fun ocultarTeclado() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
        etBusca.clearFocus()
    }

    private fun sincronizarQuantidades() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        for (i in lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i) as? PapeletaAdapter.ViewHolder ?: continue
            val qty = holder.etQuantidade.text.toString().toIntOrNull() ?: 1
            adapter?.setQuantityAt(i, qty.coerceIn(1, 999))
        }
    }

}

class PapeletaAdapter(
    private val items: List<PriceSign>,
    private val onImprimir: (PriceSign, Int) -> Unit,
    private val onRemover: (PriceSign) -> Unit
) : RecyclerView.Adapter<PapeletaAdapter.ViewHolder>() {

    private val quantities = mutableMapOf<Int, Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEan: TextView = view.findViewById(R.id.tvEan)
        val tvDescricao: TextView = view.findViewById(R.id.tvDescricao)
        val tvValor: TextView = view.findViewById(R.id.tvValor)
        val tvLeveGanhe: TextView = view.findViewById(R.id.tvLeveGanhe)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
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
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_papeleta, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val deptNum = item.department.substringBefore(" -").trim()
        holder.tvEan.text = "DEP: $deptNum | EAN: ${item.ean} / SAP: ${item.sap}"
        holder.tvDescricao.text = item.description

        val f = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val pd = item.printingData
        val precoNormal = pd?.price ?: 0.0
        val precoPromo = pd?.promotionPrice
        val takeQty = pd?.takeAndWinQuantity; val takePrice = pd?.takeAndWinPrice

        if (takeQty != null && takeQty > 0 && takePrice != null && takePrice > 0) {
            val totalLeve = Constants.totalLeveGanhePorTemplate(pd?.template, precoPromo, takePrice, takeQty)
            holder.layoutLeveGanhe.visibility = View.VISIBLE
            holder.tvLeveGanhe.text = "Leve $takeQty por ${f.format(totalLeve)}"
            holder.tvPrice.text = f.format(precoNormal)
            holder.tvBadgeTipoLeve.text = "leve e ganhe"
            holder.estilizarBadgeTipo(holder.tvBadgeTipoLeve, 0xFF2E7D32.toInt())
            holder.layoutDepor.visibility = View.GONE
        } else if (((pd?.installmentQuantity ?: 0) > 0)) {
            // Parcelado só com DADOS — igual ao Flutter (PapeletaCard).
            holder.layoutLeveGanhe.visibility = View.GONE
            holder.layoutDepor.visibility = View.VISIBLE
            holder.layoutDepor.setBackgroundResource(R.drawable.badge_green_container)
            holder.tvValor.setTextColor(0xFFFFFFFF.toInt())
            val qtdParc = pd?.installmentQuantity?.takeIf { it > 0 }
            val valorParc = pd?.installmentPrice?.takeIf { it > 0 }
                ?: precoPromo?.takeIf { it > 0 }
                ?: precoNormal
            val por = precoPromo?.takeIf { it > 0 }
            val numFmt = java.text.NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            holder.tvValor.text = if (qtdParc != null && por != null && valorParc > 0)
                "R$${numFmt.format(por)} | ${qtdParc}x de R$${numFmt.format(valorParc)}"
            else if (qtdParc != null)
                "${f.format(precoNormal)} | ${qtdParc}x de R$${String.format(Locale("pt", "BR"), "%.2f", valorParc)}"
            else if (valorParc > 0)
                f.format(valorParc)
            else
                item.price
            holder.tvBadgeTipoDepor.text = "parcelado"
            holder.estilizarBadgeTipo(holder.tvBadgeTipoDepor, 0xFFFF9800.toInt())
        } else {
            holder.layoutLeveGanhe.visibility = View.GONE
            holder.layoutDepor.visibility = View.VISIBLE
            holder.layoutDepor.setBackgroundResource(R.drawable.badge_green_container)
            holder.tvValor.setTextColor(0xFFFFFFFF.toInt())
            if (precoPromo != null && precoPromo > 0 && precoPromo < precoNormal) {
                holder.tvValor.text = "De ${f.format(precoNormal)} | Por ${f.format(precoPromo)}"
                holder.tvBadgeTipoDepor.text = "de por"
                holder.estilizarBadgeTipo(holder.tvBadgeTipoDepor, 0xFFFF9800.toInt())
            } else {
                holder.tvValor.text = f.format(precoNormal)
                holder.tvBadgeTipoDepor.text = "normal"
                holder.estilizarBadgeTipo(holder.tvBadgeTipoDepor, 0xFF9E9E9E.toInt())
            }
        }

        val savedQty = quantities[position] ?: 1
        holder.etQuantidade.setText(savedQty.toString())
        if (holder.etQuantidade.getTag(R.id.tagQtyListener) == null) {
            holder.etQuantidade.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val pos = holder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val qty = s.toString().toIntOrNull() ?: 1
                    quantities[pos] = qty.coerceIn(1, 999)
                }
            })
            holder.etQuantidade.setTag(R.id.tagQtyListener, true)
        }
        holder.btnImprimir.setOnClickListener {
            onImprimir(item, holder.etQuantidade.text.toString().toIntOrNull() ?: 1)
        }
        holder.btnRemover.setOnClickListener { onRemover(item) }
    }

    override fun getItemCount(): Int = items.size
    fun getQuantityAt(position: Int): Int = quantities[position] ?: 1
    fun setQuantityAt(position: Int, qty: Int) { quantities[position] = qty.coerceIn(1, 999) }
    fun removerQuantidade(position: Int) {
        quantities.remove(position)
        val ajustadas = mutableMapOf<Int, Int>()
        quantities.forEach { (p, q) -> ajustadas[if (p > position) p - 1 else p] = q }
        quantities.clear()
        quantities.putAll(ajustadas)
    }
}