package com.example.taskflow.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.taskflow.R
import com.example.taskflow.data.entities.ProjectDependencyEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.domain.dependencyCandidates

// Lista de tareas que "successor" espera. onToggle(predecessorId, marcado).
@Composable
fun DependencyDialog(
    successor: ProjectNodeEntity,
    nodes: List<ProjectNodeEntity>,
    dependencies: List<ProjectDependencyEntity>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val current = dependencies
        .filter { it.successorId == successor.id }
        .map { it.predecessorId }
        .toSet()
    val candidates = dependencyCandidates(nodes, successor.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.p2a_dep_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.p2a_dep_none))
                }
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = current.contains(candidate.id),
                            onCheckedChange = { onToggle(candidate.id, it) }
                        )
                        Text(candidate.title)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p1c_cancel)) } }
    )
}
