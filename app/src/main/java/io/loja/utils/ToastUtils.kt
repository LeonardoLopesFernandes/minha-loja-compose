package io.loja.utils

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import io.loja.R

object ToastUtils {

    fun show(context: Context, message: String) {
        show(context, message, Toast.LENGTH_SHORT)
    }

    fun show(context: Context, message: String, duration: Int) {
        try {
            val layout = LayoutInflater.from(context).inflate(R.layout.toast_custom, null)
            val textView = layout.findViewById<TextView>(android.R.id.text1)
            textView?.text = message
            textView?.setTextColor(context.getColor(android.R.color.black))
            textView?.setTypeface(null, Typeface.BOLD)
            Toast(context).apply {
                this.duration = duration
                view = layout
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 120)
                show()
            }
        } catch (_: Exception) {
            try { Toast.makeText(context, message, duration).show() } catch (_: Exception) {}
        }
    }

    fun showSuccess(context: Context, message: String) { show(context, message, Toast.LENGTH_LONG) }
    fun showError(context: Context, message: String) { show(context, message, Toast.LENGTH_LONG) }
    fun showInfo(context: Context, message: String) { show(context, message, Toast.LENGTH_LONG) }
}
