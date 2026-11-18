package com.example.taskflow.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R

@Composable
fun TransferSection(viewModel: TransferViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val folder by viewModel.syncFolder.collectAsState()
    var showDownloadConfirm by remember { mutableStateOf(false) }

    val saveResult: (Boolean) -> Unit = { ok ->
        Toast.makeText(
            context,
            context.getString(if (ok) R.string.s3_export_ok else R.string.s3_export_fail),
            Toast.LENGTH_SHORT
        ).show()
    }

    val icsExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) viewModel.exportIcs(uri, saveResult)
    }
    val tasksCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportTasksCsv(uri, saveResult)
    }
    val eventsCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportEventsCsv(uri, saveResult)
    }
    val icsImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importIcs(uri) { count ->
                val message = if (count >= 0) context.getString(R.string.s3_import_result, count) else context.getString(R.string.s3_import_fail)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // el permiso puede no ser persistible en algunos proveedores
            }
            viewModel.setFolder(uri.toString())
        }
    }

    Text(
        stringResource(R.string.s3_set_transfer),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
    OutlinedButton(onClick = { icsExport.launch("taskflow_eventos.ics") }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s3_ics_export))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { icsImport.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s3_ics_import))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { tasksCsv.launch("taskflow_tareas.csv") }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s3_csv_tasks))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { eventsCsv.launch("taskflow_eventos.csv") }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s3_csv_events))
    }
    Text(
        stringResource(R.string.s3_ics_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
        stringResource(R.string.s3_set_sync),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
    Text(
        stringResource(R.string.s3_sync_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        if (folder.isEmpty()) stringResource(R.string.s3_sync_folder_none) else (Uri.parse(folder).lastPathSegment ?: folder),
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s3_sync_folder_pick))
    }
    if (folder.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                viewModel.syncUp { ok ->
                    Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.s3_sync_up_ok else R.string.s3_sync_up_fail),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.s3_sync_upload)) }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { showDownloadConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.s3_sync_download), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDownloadConfirm) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirm = false },
            title = { Text(stringResource(R.string.s2_restore_title)) },
            text = { Text(stringResource(R.string.s2_restore_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadConfirm = false
                    viewModel.syncDown { ok ->
                        if (ok) {
                            Toast.makeText(context, context.getString(R.string.s2_restore_ok), Toast.LENGTH_LONG).show()
                            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            if (launch != null) context.startActivity(launch)
                            Runtime.getRuntime().exit(0)
                        } else {
                            Toast.makeText(context, context.getString(R.string.s3_sync_down_fail), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.s2_yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDownloadConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
