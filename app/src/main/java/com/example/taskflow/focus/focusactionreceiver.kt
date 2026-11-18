package com.example.taskflow.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FocusActionReceiver : BroadcastReceiver() {

    @Inject lateinit var focusManager: FocusManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    FocusIds.ACTION_STOP -> focusManager.stop()
                    FocusIds.ACTION_SKIP -> focusManager.skip()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
