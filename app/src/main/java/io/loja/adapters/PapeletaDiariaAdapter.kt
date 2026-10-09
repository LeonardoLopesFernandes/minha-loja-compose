package io.loja.adapters

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.SparseIntArray
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.loja.R
import io.loja.network.PriceSign
import java.text.NumberFormat
import java.util.Locale

class PapeletaDiariaAdapter(
    private var items: List<PriceSign>,
    private val onItemChecked: (PriceSign, Boolean, Int) -> Unit
) : RecyclerView.Adapter<PapeletaDiariaAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<Int>()
    private val quantities = SparseIntArray()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelect)
        val tvEan: TextView = itemView.findViewById(R.id.tvEan)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvPromoText: TextView = itemView.findViewById(R.id.tvPromoText)
        val tvSeparator: TextView = itemView.findViewById(R.id.tvSeparator)
        val containerBadge: View = itemView.findViewById(R.id.containerBadge)
        val etQuantity: EditText = itemView.findViewById(R.id.etQuantity)
        private var qtyWatcher: TextWatcher? = null

        init {
            (itemView as MaterialCardView).apply {
                strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
                strokeColor = ContextCompat.getColor(context, R.color.card_border_color)
            }
        }

        fun bind(item: PriceSign, position: Int) {
            tvEan.text = "Dep: ${item.department} • EAN: ${item.ean}"
            tvDescription.text = item.description

            val formatador = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

            val printingData = item.printingData
            val precoNormal = printingData?.price ?: 0.0
            val precoPromocional = printingData?.promotionPrice
            val takeAndWin = printingData?.takeAndWinQuantity?.takeIf { it > 0 }
            val takeAndWinPrice = takeAndWin?.let { printingData?.takeAndWinPrice?.takeIf { it > 0 } }

            val movement = item.movement.uppercase()

            fun estilizarBadgeBranco(texto: String, cor: Int) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = texto
                tvBadge.setTextColor(cor)
                tvBadge.background = createBadgeBackground(0xFFFFFFFF.toInt())
            }

            containerBadge.visibility = View.VISIBLE

            when {
                movement.contains("LEVE") || movement.contains("GANHE") -> {
                    estilizarBadgeBranco("leve e ganhe", 0xFF2E7D32.toInt())

                    if (takeAndWin != null && takeAndWinPrice != null) {
                        tvPromoText.visibility = View.VISIBLE
                        val totalLeve = io.loja.utils.Constants.totalLeveGanhePorTemplate(printingData?.template, precoPromocional, takeAndWinPrice, takeAndWin)
                        tvPromoText.text = "Leve $takeAndWin por ${formatador.format(totalLeve)}"
                        tvPrice.visibility = View.VISIBLE
                        tvPrice.text = if (precoNormal > 0) formatador.format(precoNormal) else item.price
                        tvSeparator.visibility = View.VISIBLE
                    } else {
                        tvPromoText.visibility = View.GONE
                        tvSeparator.visibility = View.GONE
                        tvPrice.visibility = View.GONE
                    }
                }
                movement.contains("DE") || movement.contains("POR") || movement.contains("PROMOCIONAL") -> {
                    estilizarBadgeBranco("de por", 0xFFFF9800.toInt())
                    tvPrice.visibility = View.GONE

                    if (precoPromocional != null && precoPromocional > 0 && precoPromocional < precoNormal) {
                        tvPromoText.visibility = View.VISIBLE
                        tvPromoText.text = "De ${formatador.format(precoNormal)} | Por ${formatador.format(precoPromocional)}"
                    } else {
                        tvPromoText.visibility = View.GONE
                    }
                    tvSeparator.visibility = View.GONE
                }
                // Parcelado só com DADOS de parcela — igual ao Flutter (PapeletaDiariaCard).
                ((printingData?.installmentQuantity ?: 0) > 0) -> {
                    estilizarBadgeBranco("parcelado", 0xFFFF9800.toInt())
                    val qtdParc = printingData?.installmentQuantity?.takeIf { it > 0 }
                    val valorParc = printingData?.installmentPrice?.takeIf { it > 0 }
                        ?: precoPromocional?.takeIf { it > 0 }
                        ?: precoNormal
                    val por = precoPromocional?.takeIf { it > 0 }
                    val numFmt = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }
                    tvPromoText.visibility = View.VISIBLE
                    if (qtdParc != null && por != null && valorParc > 0) {
                        // Combinado depor+parcelado (compacto p/ caber no badge):
                        // R$por | Nx de R$parcela
                        tvPromoText.text = "R$${numFmt.format(por)} | ${qtdParc}x de R$${numFmt.format(valorParc)}"
                        tvSeparator.visibility = View.GONE
                        tvPrice.visibility = View.GONE
                    } else {
                        tvPromoText.text = if (precoNormal > 0) formatador.format(precoNormal) else item.price
                        tvSeparator.visibility = View.VISIBLE
                        tvPrice.visibility = View.VISIBLE
                        tvPrice.text = if (qtdParc != null)
                            "${qtdParc}x de R$${String.format(Locale("pt", "BR"), "%.2f", valorParc)}"
                        else if (valorParc > 0)
                            formatador.format(valorParc)
                        else
                            item.price
                    }
                }
                else -> {
                    estilizarBadgeBranco("normal", 0xFF9E9E9E.toInt())
                    tvPromoText.visibility = View.GONE
                    tvSeparator.visibility = View.GONE
                    tvPrice.visibility = View.VISIBLE
                    tvPrice.text = if (precoNormal > 0) formatador.format(precoNormal) else item.price
                }
            }

            val status = item.status
            if (status.equals("Impressa", true)) {
                tvStatusBadge.visibility = View.VISIBLE
                tvStatusBadge.text = "IMPRESSA"
                tvStatusBadge.background = createBadgeBackground(0xFF4CAF50.toInt())
            } else if (status.equals("Não impressa", true)) {
                tvStatusBadge.visibility = View.VISIBLE
                tvStatusBadge.text = "NÃO IMPRESSA"
                tvStatusBadge.background = createBadgeBackground(0xFFD81B3A.toInt())
            } else {
                tvStatusBadge.visibility = View.GONE
            }

            if (qtyWatcher != null) {
                etQuantity.removeTextChangedListener(qtyWatcher)
            }
            val savedQty = quantities.get(position, 1)
            etQuantity.setText(savedQty.toString())

            etQuantity.setOnClickListener {
                etQuantity.requestFocus()
                val imm = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etQuantity, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
            }
            qtyWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val qty = s.toString().toIntOrNull() ?: 1
                    quantities.put(adapterPosition, qty.coerceIn(1, 999))
                    if (selectedItems.contains(adapterPosition)) {
                        onItemChecked(item, true, adapterPosition)
                    }
                }
            }
            etQuantity.addTextChangedListener(qtyWatcher)

            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = selectedItems.contains(position)
            cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(position)
                    val currentQty = etQuantity.text.toString().toIntOrNull() ?: 1
                    quantities.put(position, currentQty.coerceIn(1, 999))
                } else {
                    selectedItems.remove(position)
                }
                onItemChecked(item, isChecked, position)
            }
        }

        private fun createBadgeBackground(color: Int): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 30f
                setColor(color)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_papeleta_diaria, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<PriceSign>) {
        items = newItems
        selectedItems.clear()
        quantities.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<PriceSign> {
        return selectedItems.map { items[it] }
    }

    fun getSelectedItemsWithQuantities(): List<Pair<PriceSign, Int>> {
        return selectedItems.map {
            items[it] to (quantities.get(it, 1))
        }
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(items.indices)
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedItems.size
}
