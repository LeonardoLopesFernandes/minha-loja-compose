package io.loja.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.loja.database.AppDatabase
import io.loja.network.PriceSign
import io.loja.network.PriceTag

class ListaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase(application)

    // ========== ETIQUETAS ==========
    fun getEtiquetas(): MutableList<PriceTag> {
        return try {
            db.recuperarEtiquetas()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveEtiquetas(list: List<PriceTag>) {
        try {
            db.salvarEtiquetas(list)
        } catch (e: Exception) {
            // Ignora erro de banco para não crashar
        }
    }

    fun addEtiqueta(item: PriceTag) {
        val current = getEtiquetas()
        current.add(item)
        saveEtiquetas(current)
    }

    fun addEtiquetas(items: List<PriceTag>) {
        val current = getEtiquetas()
        current.addAll(items)
        saveEtiquetas(current)
    }

    fun removeEtiqueta(item: PriceTag) {
        val current = getEtiquetas()
        current.remove(item)
        saveEtiquetas(current)
    }

    fun clearEtiquetas() {
        saveEtiquetas(emptyList())
    }

    // ========== PAPELETAS ==========
    fun getPapeletas(): MutableList<PriceSign> {
        return try {
            db.recuperarPapeletas()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun savePapeletas(list: List<PriceSign>) {
        try {
            db.salvarPapeletas(list)
        } catch (e: Exception) {
            // Ignora erro de banco para não crashar
        }
    }

    fun addPapeleta(item: PriceSign) {
        val current = getPapeletas()
        current.add(item)
        savePapeletas(current)
    }

    fun addPapeletas(items: List<PriceSign>) {
        val current = getPapeletas()
        current.addAll(items)
        savePapeletas(current)
    }

    fun removePapeleta(item: PriceSign) {
        val current = getPapeletas()
        current.remove(item)
        savePapeletas(current)
    }

    fun clearPapeletas() {
        savePapeletas(emptyList())
    }

    // ========== ÁREA DE COLAGEM (NOVO) ==========
    fun getColagem(): String {
        return try {
            db.recuperarColagem()
        } catch (e: Exception) {
            ""
        }
    }

    fun setColagem(code: String) {
        try {
            db.salvarColagem(code)
        } catch (e: Exception) {
            // Ignora erro de banco
        }
    }
}