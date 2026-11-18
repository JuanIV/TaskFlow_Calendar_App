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
import com.example.taskflow.capture.CaptureContract
import com.example.taskflow.lock.LockState
import com.example.taskflow.scheduling.AlarmContract
import com.example.taskflow.ui.capture.QuickCaptureDialog
import com.example.taskflow.ui.lock.LockScreen
import com.example.taskflow.ui.navigation.AppNavigation
import com.example.taskflow.ui.onboarding.OnboardingScreen
import com.example.taskflow.ui.onboarding.OnboardingViewModel
import com.example.taskflow.ui.permissions.PermissionsDialog
import com.example.taskflow.ui.theme.TaskFlowTheme
import com.example.taskflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class mainactivity : ComponentActivity() {

    @Inject lateinit var lockState: LockState

    private val themeViewModel: ThemeViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)
    private var quickCaptureText by mutableStateOf<String?>(null)

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    quickCaptureText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                }
            }
            CaptureContract.ACTION_QUICK_ADD -> quickCaptureText = ""
            else -> pendingRoute = intent.getStringExtra(AlarmContract.EXTRA_OPEN_ROUTE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsState()
            val dynamic by themeViewModel.dynamicColor.collectAsState()
            val accent by themeViewModel.accent.collectAsState()
            val onboardingDone by onboardingViewModel.done.collectAsState()
            val lockEnabled by lockState.enabled.collectAsState()
            val locked by lockState.locked.collectAsState()
            TaskFlowTheme(themeMode = themeMode, dynamicColor = dynamic, accentArgb = accent) {
                when {
                    onboardingDone == null || lockEnabled == null -> Unit
                    onboardingDone == false -> OnboardingScreen(onFinish = { loadSample -> onboardingViewModel.finish(loadSample) })
                    lockEnabled == true && locked -> LockScreen(onUnlocked = { lockState.unlock() })
                    else -> {
                        PermissionsDialog()
                        AppNavigation(
                            pendingRoute = pendingRoute,
                            onRouteHandled = { pendingRoute = null }
                        )
                        val captured = quickCaptureText
                        if (captured != null) {
                            QuickCaptureDialog(initialText = captured, onDismiss = { quickCaptureText = null })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lockState.onReturned()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) lockState.onLeft()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}