package io.loja.utils

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object LogHelper {
    private const val TAG = "MinhaLoja"
    private const val LOG_FILE = "app_log.txt"
    
    fun d(message: String) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timeStamp] $message"
        
        // Log no Logcat
        Log.d(TAG, logMessage)
        
        // Salvar em arquivo
        try {
            val file = File(Environment.getExternalStorageDirectory(), LOG_FILE)
            FileWriter(file, true).use { writer ->
                writer.write("$logMessage\n")
            }
        } catch (e: Exception) {
            // Ignora erro ao salvar arquivo
        }
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timeStamp] ERROR: $message"
        
        Log.e(TAG, logMessage, throwable)
        
        try {
            val file = File(Environment.getExternalStorageDirectory(), LOG_FILE)
            FileWriter(file, true).use { writer ->
                writer.write("$logMessage\n")
                if (throwable != null) {
                    writer.write("${throwable.stackTraceToString()}\n")
                }
            }
        } catch (e: Exception) {
            // Ignora erro ao salvar arquivo
        }
    }
}
