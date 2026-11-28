package com.example.taskflow.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

// true mientras una pantalla pide ocultar la barra de navegacion inferior (pantalla completa)
object ImmersiveState {
    val active = MutableStateFlow(false)
}
