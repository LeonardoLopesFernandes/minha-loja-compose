package io.loja.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView

/** Dialog de carregamento com o logo sendo desenhado (barras + "a"). */
object LoadingLogoDialog {
    private var dialog: Dialog? = null

    fun show(context: Context, message: String = "Carregando...") {
        dismiss()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        val logo = LogoAnimadoView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100))
            setLogoColor(Color.parseColor("#C61033"))
        }
        val text = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        layout.addView(logo)
        layout.addView(text)

        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(layout)
            setCancelable(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }
    }

    fun dismiss() {
        try {
            dialog?.dismiss()
        } catch (_: Exception) {
        }
        dialog = null
    }
}
