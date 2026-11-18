package com.example.taskflow.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.domain.EventColors

// Paleta base + colores propios del usuario
@Composable
fun rememberPalette(viewModel: ThemeViewModel = hiltViewModel()): List<Color> {
    val custom by viewModel.customPalette.collectAsState()
    return remember(custom) { EventColors + custom.map { Color(it) } }
}
