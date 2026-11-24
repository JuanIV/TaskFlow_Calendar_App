package com.example.taskflow.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.taskflow.R
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.ui.theme.TaskFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProjectWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            TaskFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectPickerContent(onPick = { projectId -> save(projectId) })
                }
            }
        }
    }

    private fun save(projectId: Long) {
        lifecycleScope.launch {
            try {
                val id = GlanceAppWidgetManager(this@ProjectWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@ProjectWidgetConfigActivity, PreferencesGlanceStateDefinition, id) { prefs ->
                    val m = prefs.toMutablePreferences()
                    m[PROJECT_WIDGET_ID_KEY] = projectId
                    m
                }
                ProjectWidget().update(this@ProjectWidgetConfigActivity, id)
            } catch (e: Exception) {
                // si falla, el widget conserva su configuracion anterior
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun ProjectPickerContent(onPick: (Long) -> Unit) {
    val entry = remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        entry.value = widgetEntryPoint(context).projectRepository().getActiveProjectsOnce()
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.p2e_widget_pick_title), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(entry.value, key = { it.id }) { project ->
                TextButton(onClick = { onPick(project.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(project.name)
                }
            }
        }
    }
}
