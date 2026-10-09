package io.loja.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.loja.R
import io.loja.network.SessionManager
import io.loja.ui.fragments.EtiquetasFragment
import io.loja.ui.fragments.PapeletasFragment

class MainActivity : AppCompatActivity() {

    private lateinit var btnMenuHamburguer: TextView
    private lateinit var imgLogoHeader: ImageView
    private lateinit var layoutHeaderTitle: View
    private lateinit var tvHeaderTitle: TextView
    private lateinit var imgHeaderMenuIcon: ImageView
    private lateinit var menuBackdrop: View
    private lateinit var containerMenuRetangular: View
    private lateinit var menuOverlay: View
    private lateinit var rootLayout: View

    private var menuAberto = false
    private var touchStartX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.WHITE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContentView(R.layout.activity_main)

        btnMenuHamburguer = findViewById(R.id.btnMenuHamburguer)
        imgLogoHeader = findViewById(R.id.imgLogoHeader)
        layoutHeaderTitle = findViewById(R.id.layoutHeaderTitle)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        imgHeaderMenuIcon = findViewById(R.id.imgHeaderMenuIcon)
        menuBackdrop = findViewById(R.id.menuBackdrop)
        containerMenuRetangular = findViewById(R.id.containerMenuRetangular)
        menuOverlay = findViewById(R.id.menuOverlay)
        rootLayout = findViewById(R.id.rootLayout)

        if (savedInstanceState == null) {
            abrirFragment(EtiquetasFragment())
        }

        carregarFotoPerfil()
        setupListeners()
        configurarAjusteTecladoProFAB()
    }

    override fun onResume() {
        super.onResume()
        carregarFotoPerfil()
    }

    private fun carregarFotoPerfil() {
        try {
            val photoPath = SessionManager(this).getProfilePhoto()
            val imgPhoto = findViewById<ImageView>(R.id.imgProfilePhoto)
            val imgIcon = findViewById<ImageView>(R.id.imgProfileIconDefault)
            if (photoPath != null) {
                val bmp = android.graphics.BitmapFactory.decodeFile(photoPath)
                if (bmp != null) {
                    imgPhoto.setImageBitmap(bmp)
                    imgPhoto.visibility = View.VISIBLE
                    imgIcon.visibility = View.GONE
                    return
                }
            }
            imgPhoto.visibility = View.GONE
            imgIcon.visibility = View.VISIBLE
        } catch (_: Exception) {
        }
    }

    private fun setupListeners() {
        btnMenuHamburguer.setOnClickListener {
            if (menuAberto) fecharMenu() else abrirMenu()
        }

        menuBackdrop.setOnClickListener {
            if (menuAberto) fecharMenu()
        }

        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> touchStartX = event.x
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    if (!menuAberto && deltaX > 150 && Math.abs(event.eventTime - event.downTime) < 400) {
                        abrirMenu()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        menuOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> touchStartX = event.x
                MotionEvent.ACTION_UP -> {
                    val deltaX = touchStartX - event.x
                    if (menuAberto && deltaX > 150 && Math.abs(event.eventTime - event.downTime) < 400) {
                        fecharMenu()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        val acaoEtiquetas = View.OnClickListener {
            animarHeaderPara("ETIQUETA AVULSA", R.drawable.eti_avulsa)
            abrirFragment(EtiquetasFragment())
            fecharMenu()
        }
        findViewById<View>(R.id.btnEtiquetas).setOnClickListener(acaoEtiquetas)
        findViewById<ImageView>(R.id.imgIconEtiquetas).setOnClickListener(acaoEtiquetas)

        val acaoPapeletas = View.OnClickListener {
            animarHeaderPara("PAPELETA AVULSA", R.drawable.pap_avulsa)
            abrirFragment(PapeletasFragment())
            fecharMenu()
        }
        findViewById<View>(R.id.btnPapeletas).setOnClickListener(acaoPapeletas)
        findViewById<ImageView>(R.id.imgIconPapeletas).setOnClickListener(acaoPapeletas)

        val acaoEtiquetaAvulsa = View.OnClickListener {
            val intent = Intent(this, EtiquetasActivity::class.java)
            startActivity(intent)
            fecharMenu()
        }
        findViewById<View>(R.id.btnEtiquetaAvulsa).setOnClickListener(acaoEtiquetaAvulsa)
        findViewById<ImageView>(R.id.imgIconEtiquetaAvulsa).setOnClickListener(acaoEtiquetaAvulsa)

        val acaoPapeletaDiaria = View.OnClickListener {
            val intent = Intent(this, PapeletasDiariasActivity::class.java)
            startActivity(intent)
            fecharMenu()
        }
        findViewById<View>(R.id.btnPapeletaDiaria).setOnClickListener(acaoPapeletaDiaria)
        findViewById<ImageView>(R.id.imgIconPapeletaDiaria).setOnClickListener(acaoPapeletaDiaria)

        findViewById<View>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

    }

    override fun onBackPressed() {
        if (menuAberto) {
            fecharMenu()
        } else {
            mostrarDialogSair()
        }
    }

    private fun mostrarDialogSair() {
        val dialog = Dialog(this, R.style.DialogCenter)
        dialog.setContentView(R.layout.dialog_exit)

        dialog.findViewById<View>(R.id.btnDialogNao).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnDialogSim).setOnClickListener {
            dialog.dismiss()
            val sessionManager = SessionManager(this)
            sessionManager.clearAll()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        dialog.show()
    }

    private fun configurarAjusteTecladoProFAB() {
        val rootContent = findViewById<View>(android.R.id.content)

        rootContent.viewTreeObserver.addOnGlobalLayoutListener {
            val fabBarcode = encontrarFabRecursivo(rootContent)

            if (fabBarcode != null) {
                fabBarcode.translationY = 0f
            }
        }
    }

    private fun encontrarFabRecursivo(view: View): FloatingActionButton? {
        if (view is FloatingActionButton) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val resultado = encontrarFabRecursivo(child)
                if (resultado != null) return resultado
            }
        }
        return null
    }

    private fun animarHeaderPara(texto: String, iconeRes: Int) {
        if (tvHeaderTitle.text == texto && layoutHeaderTitle.visibility == View.VISIBLE) return

        tvHeaderTitle.text = texto
        imgHeaderMenuIcon.setImageResource(iconeRes)
        imgHeaderMenuIcon.visibility = View.GONE

        if (imgLogoHeader.visibility == View.VISIBLE) {
            imgLogoHeader.animate()
                .translationX(300f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    imgLogoHeader.visibility = View.GONE
                    layoutHeaderTitle.visibility = View.VISIBLE
                    layoutHeaderTitle.translationX = 200f
                    layoutHeaderTitle.alpha = 0f
                    layoutHeaderTitle.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        } else {
            layoutHeaderTitle.animate()
                .translationX(200f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    tvHeaderTitle.text = texto
                    layoutHeaderTitle.translationX = 200f
                    layoutHeaderTitle.alpha = 0f
                    layoutHeaderTitle.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }
    }

    private fun resetarHeaderIcone() {
        if (imgLogoHeader.visibility == View.VISIBLE) return
        imgLogoHeader.translationX = 0f
        imgLogoHeader.alpha = 1f
        imgLogoHeader.visibility = View.VISIBLE
        layoutHeaderTitle.visibility = View.GONE
        layoutHeaderTitle.translationX = 0f
        layoutHeaderTitle.alpha = 1f
        tvHeaderTitle.text = ""
    }

    private fun abrirMenu() {
        if (menuAberto) return
        menuAberto = true

        btnMenuHamburguer.text = "✕"

        containerMenuRetangular.pivotX = 0f
        containerMenuRetangular.pivotY = 0f
        containerMenuRetangular.scaleX = 0f
        containerMenuRetangular.scaleY = 0f
        containerMenuRetangular.alpha = 0f

        menuOverlay.visibility = View.VISIBLE

        containerMenuRetangular.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        menuBackdrop.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun fecharMenu() {
        if (!menuAberto) return
        menuAberto = false

        btnMenuHamburguer.text = "☰"

        containerMenuRetangular.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        menuBackdrop.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                menuOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun abrirFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
