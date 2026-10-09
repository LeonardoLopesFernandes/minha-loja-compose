package io.loja.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.loja.R
import io.loja.ui.compose.etiquetaavulsa.EtiquetaAvulsaScreen
import io.loja.ui.compose.etiquetaavulsa.EtiquetaAvulsaViewModel
import io.loja.ui.compose.etiquetasdiarias.EtiquetasDiariasScreen
import io.loja.ui.compose.etiquetasdiarias.EtiquetasDiariasViewModel
import io.loja.ui.compose.papeletaavulsa.PapeletaAvulsaScreen
import io.loja.ui.compose.papeletaavulsa.PapeletaAvulsaViewModel
import io.loja.ui.compose.papeletasdiarias.PapeletasDiariasScreen
import io.loja.ui.compose.papeletasdiarias.PapeletasDiariasViewModel
import kotlinx.coroutines.launch

private data class Secao(val titulo: String, val icone: Int)

/** Seções do menu, na mesma ordem do Flutter (MainScreen). */
private val SECOES = listOf(
    Secao("Etiqueta avulsa", R.drawable.eti_avulsa),
    Secao("Papeleta avulsa", R.drawable.pap_avulsa),
    Secao("Etiquetas diárias", R.drawable.eti_diaria),
    Secao("Papeletas diárias", R.drawable.pap_diaria),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPerfil: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selecionada by rememberSaveable { mutableStateOf(0) }
    var tituloConteudo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selecionada) { tituloConteudo = null }

    MinhaLojaTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // Menu que termina no último item (não ocupa a tela toda),
                // igual ao Flutter: painel vermelho com cantos à direita.
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .background(MinhaLojaRed, RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                        .statusBarsPadding()
                        .padding(vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { drawerState.close() } }
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.logo_menu),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("minha loja", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    SECOES.forEachIndexed { index, secao ->
                        NavigationDrawerItem(
                            icon = { Icon(painterResource(secao.icone), contentDescription = null, tint = Color.White) },
                            label = { Text(secao.titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
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
                        titulo = tituloConteudo ?: SECOES[selecionada].titulo,
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
                        val vm: EtiquetaAvulsaViewModel = viewModel()
                        EtiquetaAvulsaScreen(vm = vm, modifier = conteudoPadding)
                    }
                    1 -> {
                        val vm: PapeletaAvulsaViewModel = viewModel()
                        PapeletaAvulsaScreen(vm = vm, modifier = conteudoPadding)
                    }
                    2 -> {
                        val vm: EtiquetasDiariasViewModel = viewModel()
                        EtiquetasDiariasScreen(vm = vm, modifier = conteudoPadding, onTitulo = { tituloConteudo = it })
                    }
                    3 -> {
                        val vm: PapeletasDiariasViewModel = viewModel()
                        PapeletasDiariasScreen(vm = vm, modifier = conteudoPadding, onTitulo = { tituloConteudo = it })
                    }
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
            textAlign = TextAlign.Center,
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
