package io.loja.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.loja.ui.compose.MinhaLojaRed

/** Spinner com ícone no rótulo (igual ao Kotlin). */
@Composable
fun <T> SpinnerDropdown(
    label: String,
    icone: Int?,
    options: List<T>,
    selectedIndex: Int,
    labelOf: (T) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icone != null) {
                Icon(
                    painter = painterResource(icone),
                    contentDescription = null,
                    tint = MinhaLojaRed,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = Color(0xFF8C9BA5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = options.isNotEmpty()) { expanded = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.getOrNull(selectedIndex)?.let(labelOf) ?: "-",
                    color = Color(0xFF1E293B),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MinhaLojaRed)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(
                        text = { Text(labelOf(o)) },
                        onClick = { expanded = false; onSelect(i) },
                    )
                }
            }
        }
    }
}
