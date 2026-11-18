package com.example.taskflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.taskflow.scheduling.AlarmContract
import com.example.taskflow.ui.navigation.AppNavigation
import com.example.taskflow.ui.permissions.PermissionsDialog
import com.example.taskflow.ui.theme.TaskFlowTheme
import com.example.taskflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class mainactivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingRoute = intent?.getStringExtra(AlarmContract.EXTRA_OPEN_ROUTE)
        }
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsState()
            val dynamic by themeViewModel.dynamicColor.collectAsState()
            val accent by themeViewModel.accent.collectAsState()
            TaskFlowTheme(themeMode = themeMode, dynamicColor = dynamic, accentArgb = accent) {
                PermissionsDialog()
                AppNavigation(
                    pendingRoute = pendingRoute,
                    onRouteHandled = { pendingRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(AlarmContract.EXTRA_OPEN_ROUTE)
    }
}
