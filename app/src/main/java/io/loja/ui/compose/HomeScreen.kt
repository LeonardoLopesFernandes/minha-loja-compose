package io.loja.ui.compose

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.loja.R
import io.loja.ui.EtiquetasActivity
import io.loja.ui.MainActivity
import io.loja.ui.PapeletasDiariasActivity
import io.loja.ui.compose.etiquetaavulsa.EtiquetaAvulsaScreen
import io.loja.ui.compose.etiquetaavulsa.EtiquetaAvulsaViewModel
import io.loja.ui.compose.papeletaavulsa.PapeletaAvulsaScreen
import io.loja.ui.compose.papeletaavulsa.PapeletaAvulsaViewModel
import kotlinx.coroutines.launch

/** Seções do menu, na mesma ordem do Flutter (MainScreen). */
private val SECOES = listOf(
    "ETIQUETA AVULSA",
    "PAPELETA AVULSA",
    "ETIQUETAS DIÁRIAS",
    "PAPELETAS DIÁRIAS",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPerfil: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selecionada by rememberSaveable { mutableStateOf(0) }

    MinhaLojaTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(240.dp),
                    drawerContainerColor = MinhaLojaRed,
                    drawerContentColor = Color.White,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.logo_menu),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "minha loja",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                    SECOES.forEachIndexed { index, titulo ->
                        NavigationDrawerItem(
                            label = { Text(titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            selected = index == selecionada,
                            onClick = {
                                selecionada = index
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MinhaLojaDarkRed,
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.White,
                                selectedIconColor = Color.White,
                                unselectedIconColor = Color.White,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = MinhaLojaBg,
                topBar = {
                    HomeTopBar(
                        titulo = SECOES[selecionada],
                        onMenu = { scope.launch { drawerState.open() } },
                        onPerfil = onPerfil,
                    )
                },
            ) { innerPadding ->
                val conteudoPadding = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                when (selecionada) {
                    0 -> {
                        val etiquetaVm: EtiquetaAvulsaViewModel = viewModel()
                        EtiquetaAvulsaScreen(vm = etiquetaVm, modifier = conteudoPadding)
                    }
                    1 -> {
                        val papeletaVm: PapeletaAvulsaViewModel = viewModel()
                        PapeletaAvulsaScreen(vm = papeletaVm, modifier = conteudoPadding)
                    }
                    else -> ConteudoSecao(index = selecionada, modifier = conteudoPadding)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    titulo: String,
    onMenu: () -> Unit,
    onPerfil: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color(0xFF374151))
        }
        Text(
            text = titulo,
            modifier = Modifier.weight(1f),
            color = Color(0xFF374151),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MinhaLojaRed, CircleShape)
                .clickable { onPerfil() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = "Perfil",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ConteudoSecao(index: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            SECOES[index],
            style = MaterialTheme.typography.titleLarge,
            color = MinhaLojaRed,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tela em migração para Jetpack Compose",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6B7280),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { abrirTelaAtual(context, index) }) {
            Text("Abrir versão atual")
        }
    }
}

/** Ponte temporária: abre a tela nativa atual enquanto esta seção não é migrada. */
private fun abrirTelaAtual(context: android.content.Context, index: Int) {
    val intent = when (index) {
        0 -> Intent(context, MainActivity::class.java).putExtra("open_fragment", "etiqueta_avulsa")
        1 -> Intent(context, MainActivity::class.java).putExtra("open_fragment", "papeleta_avulsa")
        2 -> Intent(context, EtiquetasActivity::class.java)
        else -> Intent(context, PapeletasDiariasActivity::class.java)
    }
    context.startActivity(intent)
}
