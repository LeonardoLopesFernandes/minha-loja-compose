package io.loja.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.loja.R
import io.loja.network.ApiClient
import io.loja.network.Installments
import io.loja.network.PrintingData
import io.loja.network.SendPriceTagsRequest
import io.loja.network.SessionManager
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class GondolaEditavelActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvPosicao: TextView
    private lateinit var preview: GondolaPreviewView

    private lateinit var etDescricao: EditText
    private lateinit var etEan: EditText
    private lateinit var layoutNormal: View
    private lateinit var layoutDepor: View
    private lateinit var layoutLeveGanhe: View
    private lateinit var layoutParcelado: View
    private lateinit var etPreco: EditText
    private lateinit var etPrecoDe: EditText
    private lateinit var etPrecoPor: EditText
    private lateinit var etQtd: EditText
    private lateinit var etPrecoPromo: EditText
    private lateinit var etQuantoFica: EditText
    private lateinit var etPrecoUnitario: EditText
    private lateinit var etPrecoParcelado: EditText
    private lateinit var etValorAnterior: EditText
    private lateinit var tvPrecoParceladoLabel: TextView
    private lateinit var etParcelas: EditText
    private lateinit var etValorParcela: EditText
    private lateinit var cbIncluir: CheckBox
    private lateinit var btnAnterior: Button
    private lateinit var btnProximo: Button
    private lateinit var btnEnviar: Button

    private lateinit var items: MutableList<PrintingData>
    private val selecionados = mutableSetOf<Int>()
    private var index = 0
    private var printerId = Constants.PRINTER_ZEBRA_1

    private var templateComum: Bitmap? = null
    private var templatePromocional: Bitmap? = null

    private val df = DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR")))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gondola_editavel)

        sessionManager = SessionManager(this)
        sessionManager.setCurrentActivity(this)
        printerId = intent.getStringExtra("PRINTER_ID") ?: Constants.PRINTER_ZEBRA_1

        val json = intent.getStringExtra("ITEMS_JSON") ?: ""
        items = try {
            val tipo = object : com.google.gson.reflect.TypeToken<List<PrintingData>>() {}.type
            com.google.gson.Gson().fromJson(json, tipo) ?: mutableListOf()
        } catch (e: Exception) {
            LogHelper.e("GondolaEditavelActivity: erro parse", e)
            mutableListOf()
        }

        if (items.isEmpty()) {
            ToastUtils.show(this, "Nenhuma etiqueta para editar")
            finish()
            return
        }

        inicializarViews()
        selecionados.addAll(items.indices)
        carregarItem()
    }

    private fun inicializarViews() {
        tvPosicao = findViewById(R.id.tvPosicao)
        preview = findViewById(R.id.previewGondola)
        etDescricao = findViewById(R.id.etDescricao)
        etEan = findViewById(R.id.etEan)
        layoutNormal = findViewById(R.id.layoutNormal)
        layoutDepor = findViewById(R.id.layoutDepor)
        layoutLeveGanhe = findViewById(R.id.layoutLeveGanhe)
        layoutParcelado = findViewById(R.id.layoutParcelado)
        etPreco = findViewById(R.id.etPreco)
        etPrecoDe = findViewById(R.id.etPrecoDe)
        etPrecoPor = findViewById(R.id.etPrecoPor)
        etQtd = findViewById(R.id.etQtd)
        etPrecoPromo = findViewById(R.id.etPrecoPromo)
        etQuantoFica = findViewById(R.id.etQuantoFica)
        etPrecoUnitario = findViewById(R.id.etPrecoUnitario)
        etPrecoParcelado = findViewById(R.id.etPrecoParcelado)
        etValorAnterior = findViewById(R.id.etValorAnterior)
        tvPrecoParceladoLabel = findViewById(R.id.tvPrecoParceladoLabel)
        etParcelas = findViewById(R.id.etParcelas)
        etValorParcela = findViewById(R.id.etValorParcela)
        cbIncluir = findViewById(R.id.cbIncluir)
        btnAnterior = findViewById(R.id.btnAnterior)
        btnProximo = findViewById(R.id.btnProximo)
        btnEnviar = findViewById(R.id.btnEnviar)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        cbIncluir.setOnCheckedChangeListener { _, checked ->
            if (checked) selecionados.add(index) else selecionados.remove(index)
        }

        btnAnterior.setOnClickListener { salvarItem(); if (index > 0) { index--; carregarItem() } }
        btnProximo.setOnClickListener {
            salvarItem()
            if (index < items.size - 1) { index++; carregarItem() } else {
                ToastUtils.show(this, "Última etiqueta")
            }
        }
        btnEnviar.setOnClickListener { salvarItem(); enviarSelecionados() }

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { atualizarPreview() }
        }
        etDescricao.addTextChangedListener(watcher)
        etEan.addTextChangedListener(watcher)
        etPreco.addTextChangedListener(watcher)
        etPrecoDe.addTextChangedListener(watcher)
        etPrecoPor.addTextChangedListener(watcher)
        etQtd.addTextChangedListener(watcher)
        etPrecoPromo.addTextChangedListener(watcher)
        etQuantoFica.addTextChangedListener(watcher)
        etPrecoUnitario.addTextChangedListener(watcher)
        etPrecoParcelado.addTextChangedListener(watcher)
        etValorAnterior.addTextChangedListener(watcher)
        etParcelas.addTextChangedListener(watcher)
        etValorParcela.addTextChangedListener(watcher)

        val decimalListener = android.text.method.DigitsKeyListener.getInstance("0123456789,.")
        etPreco.keyListener = decimalListener
        etPrecoDe.keyListener = decimalListener
        etPrecoPor.keyListener = decimalListener
        etPrecoPromo.keyListener = decimalListener
        etQuantoFica.keyListener = decimalListener
        etPrecoUnitario.keyListener = decimalListener
        etPrecoParcelado.keyListener = decimalListener
        etValorAnterior.keyListener = decimalListener
        etValorParcela.keyListener = decimalListener

        etPrecoParcelado.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val preco = parseDouble(etPrecoParcelado.text) ?: return
                val parcelas = etParcelas.text.toString().toIntOrNull() ?: 1
                val valorParcela = if (parcelas > 0) preco / parcelas else preco
                val valorAtual = parseDouble(etValorParcela.text)
                if (valorAtual == null || Math.abs(valorAtual - valorParcela) > 0.001) {
                    etValorParcela.setText(df.format(valorParcela))
                }
            }
        })

        var touchStartX = 0f
        preview.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { touchStartX = event.x; false }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchStartX
                    if (Math.abs(dx) > 120) {
                        salvarItem()
                        if (dx < 0 && index < items.size - 1) { index++; carregarItem() }
                        else if (dx > 0 && index > 0) { index--; carregarItem() }
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun ehDepor(d: PrintingData): Boolean {
        val promo = d.promotionalPrice?.value ?: 0.0
        val normal = d.price?.value ?: 0.0
        return (d.takeAndWin?.qty ?: 0) == 0 && promo > 0 && promo < normal
    }

    private fun ehLeveGanhe(d: PrintingData): Boolean = (d.takeAndWin?.qty ?: 0) > 0

    private fun ehParcelado(d: PrintingData): Boolean = (d.installments?.installmentQuantity ?: 0) > 0

    // Combinado depor+parcelado: tem parcelas E preço promocional válido
    private fun ehDeporParcelado(d: PrintingData): Boolean {
        if ((d.installments?.installmentQuantity ?: 0) <= 0) return false
        val promo = d.promotionalPrice?.value ?: 0.0
        if (promo <= 0) return false
        val normal = d.price?.value ?: 0.0
        return normal <= 0 || promo < normal
    }

    private fun precoAtual(d: PrintingData): Double {
        return d.displayPrice.takeIf { it > 0 }
            ?: d.unitValue.takeIf { it > 0 }
            ?: d.unitQty.takeIf { it > 0 }
            ?: (d.promotionalPrice?.value ?: 0.0).takeIf { it > 0 }
            ?: (d.price?.value ?: 0.0)
    }

    private fun carregarItem() {
        val d = items[index]
        tvPosicao.text = "${index + 1}/${items.size}"

        etDescricao.setText(d.description)
        etEan.setText(d.ean)

        val normal = d.price?.value ?: 0.0

        if (ehLeveGanhe(d)) {
            layoutNormal.visibility = View.GONE
            layoutDepor.visibility = View.GONE
            layoutLeveGanhe.visibility = View.VISIBLE
            layoutParcelado.visibility = View.GONE

            val qty = d.takeAndWin?.qty ?: 1
            val total = d.takeAndWin?.totalValue ?: 0.0
            val unit = d.promotionalPrice?.value ?: 0.0
            etPrecoUnitario.setText(df.format(d.price?.value ?: 0.0))
            etQtd.setText(qty.toString())
            etPrecoPromo.setText(df.format(total))
            etQuantoFica.setText(df.format(unit))
        } else if (ehDeporParcelado(d)) {
            layoutNormal.visibility = View.GONE
            layoutDepor.visibility = View.GONE
            layoutLeveGanhe.visibility = View.GONE
            layoutParcelado.visibility = View.VISIBLE

            etValorAnterior.setText(df.format(normal))
            etPrecoParcelado.setText(df.format(d.promotionalPrice?.value ?: 0.0))
            etParcelas.setText((d.installments?.installmentQuantity ?: 1).toString())
            etValorParcela.setText(df.format(d.installments?.installmentValue ?: 0.0))
        } else if (ehParcelado(d)) {
            layoutNormal.visibility = View.GONE
            layoutDepor.visibility = View.GONE
            layoutLeveGanhe.visibility = View.GONE
            layoutParcelado.visibility = View.VISIBLE

            etValorAnterior.setText(df.format(if (normal > 0) normal else precoAtual(d)))
            val promo = (d.promotionalPrice?.value ?: 0.0).takeIf { it > 0 } ?: precoAtual(d)
            etPrecoParcelado.setText(df.format(promo))
            etParcelas.setText((d.installments?.installmentQuantity ?: 1).toString())
            etValorParcela.setText(df.format(d.installments?.installmentValue ?: 0.0))
        } else if (ehDepor(d)) {
            layoutNormal.visibility = View.GONE
            layoutDepor.visibility = View.VISIBLE
            layoutLeveGanhe.visibility = View.GONE
            layoutParcelado.visibility = View.GONE

            val promo = d.promotionalPrice?.value ?: 0.0
            etPrecoDe.setText(df.format(normal))
            etPrecoPor.setText(df.format(promo))
        } else {
            layoutNormal.visibility = View.VISIBLE
            layoutDepor.visibility = View.GONE
            layoutLeveGanhe.visibility = View.GONE
            layoutParcelado.visibility = View.GONE
            etPreco.setText(df.format(normal))
        }

        cbIncluir.isChecked = index in selecionados
        atualizarPreview()
        atualizarBotoesNavegacao()
    }

    private fun atualizarBotoesNavegacao() {
        btnAnterior.isEnabled = index > 0
        btnProximo.isEnabled = index < items.size - 1
    }

    private fun atualizarPreview() {
        val d = items[index]
        val desc = etDescricao.text.toString().trim().uppercase().ifBlank { d.description }
        val ean = etEan.text.toString().trim().ifBlank { d.ean }
        val codigo = d.codSap.ifBlank { d.department }
        val unidade = "R$/PCE ${df.format(d.unitValue)}"

        if (ehLeveGanhe(d)) {
            val qty = etQtd.text.toString().toIntOrNull() ?: 1
            val total = parseDouble(etPrecoPromo.text) ?: 0.0
            val unit = parseDouble(etQuantoFica.text) ?: 0.0
            val unitario = parseDouble(etPrecoUnitario.text) ?: (d.price?.value ?: 0.0)
            preview.setTemplate(carregarTemplate(true), GondolaPreviewView.MODO_PROMOCIONAL)
            preview.setData(
                codigo, ean, desc,
                "R$", df.format(unitario), unidade,
                "${qty} por R$ ${df.format(total)}",
                "NA PROMOCAO A UND SAIRA POR R$ ${df.format(unit)}"
            )
        } else if (ehDeporParcelado(d)) {
            val valorAnterior = parseDouble(etValorAnterior.text) ?: (d.price?.value ?: 0.0)
            val por = parseDouble(etPrecoParcelado.text) ?: (d.promotionalPrice?.value ?: 0.0)
            val parcelas = etParcelas.text.toString().toIntOrNull() ?: (d.installments?.installmentQuantity ?: 1)
            val valorParcela = parseDouble(etValorParcela.text) ?: (d.installments?.installmentValue ?: 0.0)
            preview.setTemplate(carregarTemplate(true), GondolaPreviewView.MODO_PROMOCIONAL)
            preview.setData(
                codigo, ean, desc,
                "De R$", df.format(valorAnterior), unidade,
                "Por R$ ${df.format(por)}",
                "${parcelas}X de R$ ${df.format(valorParcela)}"
            )
        } else if (ehParcelado(d)) {
            val valorAnterior = parseDouble(etValorAnterior.text) ?: (d.price?.value ?: 0.0)
            val parcelas = etParcelas.text.toString().toIntOrNull() ?: (d.installments?.installmentQuantity ?: 1)
            val valorParcela = parseDouble(etValorParcela.text) ?: (d.installments?.installmentValue ?: 0.0)
            preview.setTemplate(carregarTemplate(true), GondolaPreviewView.MODO_PROMOCIONAL)
            preview.setData(
                codigo, ean, desc,
                "R$", df.format(valorAnterior), unidade,
                "${parcelas}X de R$ ${df.format(valorParcela)}",
                "SEM JUROS"
            )
        } else if (ehDepor(d)) {
            val de = parseDouble(etPrecoDe.text) ?: (d.price?.value ?: 0.0)
            val por = parseDouble(etPrecoPor.text) ?: (d.promotionalPrice?.value ?: 0.0)
            preview.setTemplate(carregarTemplate(true), GondolaPreviewView.MODO_PROMOCIONAL)
            preview.setData(
                codigo, ean, desc,
                "De R$", df.format(de), unidade,
                "Por R$ ${df.format(por)}",
                ""
            )
        } else {
            val preco = parseDouble(etPreco.text) ?: (d.price?.value ?: 0.0)
            preview.setTemplate(carregarTemplate(false), GondolaPreviewView.MODO_COMUM)
            preview.setData(
                codigo, ean, desc,
                "R$", df.format(preco), unidade,
                "", ""
            )
        }
    }

    private fun carregarTemplate(promocional: Boolean): Bitmap? {
        if (promocional) {
            if (templatePromocional == null) {
                templatePromocional = BitmapFactory.decodeResource(resources, R.drawable.gondola_promocional)
            }
            return templatePromocional
        }
        if (templateComum == null) {
            templateComum = BitmapFactory.decodeResource(resources, R.drawable.gondola_comum)
        }
        return templateComum
    }

    private fun parseDouble(ed: CharSequence): Double? {
        val s = ed.toString().trim()
        if (s.isEmpty()) return null
        return s.replace(".", "").replace(",", ".").toDoubleOrNull()
    }

    private fun salvarItem() {
        if (index !in items.indices) return
        val original = items[index]
        val desc = etDescricao.text.toString().trim()
        val ean = etEan.text.toString().trim().ifBlank { original.ean }

        var novo = original.copy(description = desc.ifBlank { original.description }, ean = ean)

        if (ehLeveGanhe(original)) {
            val qty = etQtd.text.toString().toIntOrNull() ?: (original.takeAndWin?.qty ?: 1)
            val total = parseDouble(etPrecoPromo.text) ?: (original.takeAndWin?.totalValue ?: 0.0)
            val unit = parseDouble(etQuantoFica.text) ?: (original.promotionalPrice?.value ?: 0.0)
            val unitario = parseDouble(etPrecoUnitario.text) ?: (original.price?.value ?: 0.0)
            novo = novo.copy(
                price = original.price?.copy(value = unitario),
                takeAndWin = original.takeAndWin?.copy(qty = qty, totalValue = total),
                promotionalPrice = original.promotionalPrice?.copy(value = unit),
                promotionPrice = unit,
                takeAndWinQuantity = qty,
                takeAndWinPrice = unit
            )
        } else if (ehDeporParcelado(original)) {
            val valorAnterior = parseDouble(etValorAnterior.text) ?: (original.price?.value ?: 0.0)
            val por = parseDouble(etPrecoParcelado.text) ?: (original.promotionalPrice?.value ?: 0.0)
            val parcelas = etParcelas.text.toString().toIntOrNull() ?: (original.installments?.installmentQuantity ?: 1)
            val valorParcela = parseDouble(etValorParcela.text) ?: (original.installments?.installmentValue ?: 0.0)
            novo = novo.copy(
                price = original.price?.copy(value = valorAnterior),
                promotionalPrice = (original.promotionalPrice ?: io.loja.network.PromotionalPriceInfo(por, 0, "", "")).copy(value = por),
                installments = Installments(parcelas, valorParcela)
            )
        } else if (ehParcelado(original)) {
            val valorAnterior = parseDouble(etValorAnterior.text) ?: (original.price?.value ?: 0.0)
            val por = parseDouble(etPrecoParcelado.text) ?: precoAtual(original)
            val parcelas = etParcelas.text.toString().toIntOrNull() ?: (original.installments?.installmentQuantity ?: 1)
            val valorParcela = parseDouble(etValorParcela.text) ?: (original.installments?.installmentValue ?: 0.0)
            novo = novo.copy(
                price = original.price?.copy(value = valorAnterior),
                promotionalPrice = (original.promotionalPrice ?: io.loja.network.PromotionalPriceInfo(por, 0, "", "")).copy(value = por),
                installments = Installments(parcelas, valorParcela)
            )
        } else if (ehDepor(original)) {
            val de = parseDouble(etPrecoDe.text) ?: (original.price?.value ?: 0.0)
            val por = parseDouble(etPrecoPor.text) ?: (original.promotionalPrice?.value ?: 0.0)
            novo = novo.copy(
                price = original.price?.copy(value = de),
                promotionalPrice = original.promotionalPrice?.copy(value = por)
            )
        } else {
            val preco = parseDouble(etPreco.text) ?: (original.price?.value ?: 0.0)
            novo = novo.copy(price = original.price?.copy(value = preco))
        }

        items[index] = novo
    }

    private fun enviarSelecionados() {
        val selecionadosOrdenados = selecionados.sorted()
        if (selecionadosOrdenados.isEmpty()) {
            ToastUtils.show(this, "Selecione ao menos uma etiqueta")
            return
        }
        val lista = selecionadosOrdenados.map { items[it] }

        btnEnviar.text = "ENVIANDO..."
        btnEnviar.isEnabled = false

        val apiService = ApiClient.getSlApiService(sessionManager)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.sendPriceTagsToPrinter(
                        sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                        printerId,
                        Constants.TAG_GONDOLA,
                        SendPriceTagsRequest(lista)
                    )
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        ToastUtils.show(this@GondolaEditavelActivity, "${lista.size} etiqueta(s) enviada(s) para impressão!", Toast.LENGTH_LONG)
                        finish()
                    } else {
                        ToastUtils.show(this@GondolaEditavelActivity, "Erro ao enviar: ${response.code()}", Toast.LENGTH_LONG)
                        btnEnviar.text = "ENVIAR"
                        btnEnviar.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("GondolaEditavelActivity: erro enviar", e)
                withContext(Dispatchers.Main) {
                    ToastUtils.show(this@GondolaEditavelActivity, "Erro: ${e.message}", Toast.LENGTH_LONG)
                    btnEnviar.text = "ENVIAR"
                    btnEnviar.isEnabled = true
                }
            }
        }
    }
}
