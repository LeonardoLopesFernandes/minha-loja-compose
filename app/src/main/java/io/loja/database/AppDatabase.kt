package io.loja.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import io.loja.network.PriceSign
import io.loja.network.PriceTag

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "MinhaLoja.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cache (key TEXT PRIMARY KEY, json TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cache")
        onCreate(db)
    }

    fun salvarEtiquetas(list: List<PriceTag>) {
        val json = Gson().toJson(list)
        val values = ContentValues().apply {
            put("key", "etiquetas")
            put("json", json)
        }
        writableDatabase.insertWithOnConflict("cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recuperarEtiquetas(): MutableList<PriceTag> {
        val cursor = readableDatabase.query("cache", arrayOf("json"), "key = ?", arrayOf("etiquetas"), null, null, null)
        return if (cursor.moveToFirst()) {
            val json = cursor.getString(0)
            cursor.close()
            try {
                Gson().fromJson(json, Array<PriceTag>::class.java).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            cursor.close()
            mutableListOf()
        }
    }

    fun salvarPapeletas(list: List<PriceSign>) {
        val json = Gson().toJson(list)
        val values = ContentValues().apply {
            put("key", "papeletas")
            put("json", json)
        }
        writableDatabase.insertWithOnConflict("cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recuperarPapeletas(): MutableList<PriceSign> {
        val cursor = readableDatabase.query("cache", arrayOf("json"), "key = ?", arrayOf("papeletas"), null, null, null)
        return if (cursor.moveToFirst()) {
            val json = cursor.getString(0)
            cursor.close()
            try {
                Gson().fromJson(json, Array<PriceSign>::class.java).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            cursor.close()
            mutableListOf()
        }
    }

    // 🔥 ÁREA DE COLAGEM (GUARDA APENAS O ÚLTIMO CÓDIGO)
    fun salvarColagem(code: String) {
        val values = ContentValues().apply {
            put("key", "colagem")
            put("json", code)
        }
        writableDatabase.insertWithOnConflict("cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recuperarColagem(): String {
        val cursor = readableDatabase.query("cache", arrayOf("json"), "key = ?", arrayOf("colagem"), null, null, null)
        return if (cursor.moveToFirst()) {
            val code = cursor.getString(0)
            cursor.close()
            code
        } else {
            cursor.close()
            ""
        }
    }

    fun limparCache() {
        writableDatabase.execSQL("DELETE FROM cache")
    }
}