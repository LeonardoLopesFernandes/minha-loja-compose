package io.loja.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.loja.R
import io.loja.network.SessionManager
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var imgProfilePhoto: ImageView
    private lateinit var imgProfileIcon: ImageView

    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processarFoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        val nome = sessionManager.getUserName() ?: "Usuário"
        val email = sessionManager.getUserEmail() ?: "email@exemplo.com"
        val loja = sessionManager.getUserStore() ?: "L291"

        findViewById<TextView>(R.id.tvProfileName).text = nome
        findViewById<TextView>(R.id.tvProfileEmail).text = email
        findViewById<TextView>(R.id.tvProfileStore).text = "Loja $loja"

        imgProfilePhoto = findViewById(R.id.imgProfilePhoto)
        imgProfileIcon = findViewById(R.id.imgProfileIcon)
        carregarFotoSalva()

        val btnAvatar = findViewById<FrameLayout>(R.id.btnAvatar)
        btnAvatar.setOnClickListener {
            photoPicker.launch("image/*")
        }
        btnAvatar.setOnLongClickListener {
            sessionManager.clearProfilePhoto()
            imgProfilePhoto.visibility = android.view.View.GONE
            imgProfileIcon.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "Foto removida", Toast.LENGTH_SHORT).show()
            true
        }

        val etPrinterIp = findViewById<EditText>(R.id.etPrinterIp)
        etPrinterIp.setText(sessionManager.getPrinterIp())
        findViewById<TextView>(R.id.btnSalvarIp).setOnClickListener {
            val ip = etPrinterIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Informe o IP da impressora", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sessionManager.savePrinterIp(ip)
            Toast.makeText(this, "IP da impressora salvo: $ip", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        findViewById<FrameLayout>(R.id.btnSairConta).setOnClickListener {
            mostrarDialogSair(sessionManager)
        }
    }

    private fun carregarFotoSalva() {
        val path = sessionManager.getProfilePhoto() ?: return
        val bmp = BitmapFactory.decodeFile(path)
        if (bmp != null) {
            imgProfilePhoto.setImageBitmap(bmp)
            imgProfilePhoto.visibility = android.view.View.VISIBLE
            imgProfileIcon.visibility = android.view.View.GONE
        }
    }

    private fun processarFoto(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            if (bmp == null) {
                Toast.makeText(this, "Não foi possível carregar a imagem", Toast.LENGTH_SHORT).show()
                return
            }
            val quad = quadrarBitmap(bmp)
            val circular = aplicarMascaraCircular(quad)
            val photoFile = File(filesDir, "profile_photo.png")
            FileOutputStream(photoFile).use { out ->
                circular.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            sessionManager.saveProfilePhoto(photoFile.absolutePath)
            imgProfilePhoto.setImageBitmap(circular)
            imgProfilePhoto.visibility = android.view.View.VISIBLE
            imgProfileIcon.visibility = android.view.View.GONE
            Toast.makeText(this, "Foto atualizada!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun quadrarBitmap(bmp: Bitmap): Bitmap {
        val side = minOf(bmp.width, bmp.height)
        val x = (bmp.width - side) / 2
        val y = (bmp.height - side) / 2
        val quad = Bitmap.createBitmap(bmp, x, y, side, side)
        val size = 512
        return if (side > size) Bitmap.createScaledBitmap(quad, size, size, true) else quad
    }

    private fun aplicarMascaraCircular(bmp: Bitmap): Bitmap {
        val size = bmp.width.coerceAtMost(bmp.height)
        val x = (bmp.width - size) / 2
        val y = (bmp.height - size) / 2
        val src = if (bmp.width != size || bmp.height != size) Bitmap.createBitmap(bmp, x, y, size, size) else bmp
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return output
    }

    private fun mostrarDialogSair(sessionManager: SessionManager) {
        val dialog = Dialog(this, R.style.DialogCenter)
        dialog.setContentView(R.layout.dialog_exit_account)

        dialog.findViewById<android.view.View>(R.id.btnDialogNao).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<android.view.View>(R.id.btnDialogSim).setOnClickListener {
            dialog.dismiss()
            sessionManager.clearAll()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        dialog.show()
    }
}
