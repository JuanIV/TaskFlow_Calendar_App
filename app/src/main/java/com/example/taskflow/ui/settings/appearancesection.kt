package com.example.taskflow.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.ui.theme.ThemeViewModel
import com.example.taskflow.ui.theme.rememberPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> AppearanceDropdown(label: String, valueText: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(viewModel: ThemeViewModel = hiltViewModel()) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamic by viewModel.dynamicColor.collectAsState()
    val accent by viewModel.accent.collectAsState()
    val firstDay by viewModel.firstDay.collectAsState()
    val timeFormat by viewModel.timeFormat.collectAsState()
    val palette = rememberPalette(viewModel)
    var showPaletteDialog by remember { mutableStateOf(false) }

    val themeLabels = mapOf(
        "SYSTEM" to stringResource(R.string.s3_theme_system),
        "LIGHT" to stringResource(R.string.s3_theme_light),
        "DARK" to stringResource(R.string.s3_theme_dark)
    )
    val dayLabels = mapOf(
        1 to stringResource(R.string.s3_day_monday),
        6 to stringResource(R.string.s3_day_saturday),
        7 to stringResource(R.string.s3_day_sunday)
    )
    val timeLabels = mapOf(
        "SYSTEM" to stringResource(R.string.s3_time_system),
        "H24" to stringResource(R.string.s3_time_24),
        "H12" to stringResource(R.string.s3_time_12)
    )
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccent = !(dynamic && dynamicAvailable)

    Text(
        stringResource(R.string.s3_set_appearance),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )

    AppearanceDropdown(
        label = stringResource(R.string.s3_theme_mode),
        valueText = themeLabels[themeMode] ?: "",
        options = themeLabels.map { Pair(it.key, it.value) },
        onSelect = { viewModel.setThemeMode(it) }
    )

    if (dynamicAvailable) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s3_dynamic_color), modifier = Modifier.weight(1f))
            Switch(checked = dynamic, onCheckedChange = { viewModel.setDynamicColor(it) })
        }
    }

    if (showAccent) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.s3_accent_color), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(palette) { color ->
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(color).clickable { viewModel.setAccent(color.toArgb()) },
                    contentAlignment = Alignment.Center
                ) {
                    if (color.toArgb() == accent) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    AppearanceDropdown(
        label = stringResource(R.string.s3_first_day),
        valueText = dayLabels[firstDay] ?: "",
        options = dayLabels.map { Pair(it.key, it.value) },
        onSelect = { viewModel.setFirstDay(it) }
    )

    Spacer(modifier = Modifier.height(12.dp))
    AppearanceDropdown(
        label = stringResource(R.string.s3_time_format),
        valueText = timeLabels[timeFormat] ?: "",
        options = timeLabels.map { Pair(it.key, it.value) },
        onSelect = { viewModel.setTimeFormat(it) }
    )
    Text(
        stringResource(R.string.s3_time_picker_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { showPaletteDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s3_custom_palette))
    }

    if (showPaletteDialog) {
        CustomPaletteDialog(viewModel = viewModel, onDismiss = { showPaletteDialog = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomPaletteDialog(viewModel: ThemeViewModel, onDismiss: () -> Unit) {
    val custom by viewModel.customPalette.collectAsState()
    var red by remember { mutableStateOf(60f) }
    var green by remember { mutableStateOf(120f) }
    var blue by remember { mutableStateOf(200f) }
    val preview = Color(red.toInt(), green.toInt(), blue.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s3_palette_title)) },
        text = {
            Column {
                Text(stringResource(R.string.s3_palette_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                if (custom.isEmpty()) {
                    Text(stringResource(R.string.s3_palette_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        custom.forEach { argb ->
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(argb)).clickable { viewModel.removeCustomColor(argb) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(36.dp).clip(CircleShape).background(preview))
                Slider(value = red, onValueChange = { red = it }, valueRange = 0f..255f)
                Slider(value = green, onValueChange = { green = it }, valueRange = 0f..255f)
                Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..255f)
                OutlinedButton(
                    onClick = { viewModel.addCustomColor(preview.toArgb()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.s3_palette_add)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s2_close)) } }
    )
}
