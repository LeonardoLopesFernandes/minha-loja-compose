package io.loja

import android.app.Application
import android.content.res.Configuration
import java.util.Locale

/**
 * Força o app a usar sempre português (Brasil), independente do idioma
 * do aparelho.
 */
class MinhaLojaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val locale = Locale("pt", "BR")
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
