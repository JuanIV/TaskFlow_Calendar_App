package com.example.taskflow.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.taskflow.R

@Composable
fun OnboardingScreen(onFinish: (Boolean) -> Unit) {
    var page by remember { mutableStateOf(0) }
    val titles = listOf(R.string.s3_onb_p1_title, R.string.s3_onb_p2_title, R.string.s3_onb_p3_title, R.string.s3_onb_p4_title)
    val texts = listOf(R.string.s3_onb_p1_text, R.string.s3_onb_p2_text, R.string.s3_onb_p3_text, R.string.s3_onb_p4_text)
    val icons = listOf(Icons.Default.CheckCircle, Icons.Default.CalendarMonth, Icons.Default.Repeat, Icons.Default.Timer)
    val last = titles.size - 1

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < last) {
                    TextButton(onClick = { onFinish(false) }) { Text(stringResource(R.string.s3_onb_skip)) }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                icons[page],
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(titles[page]),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(texts[page]),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.Center) {
                for (i in 0..last) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (page < last) {
                Button(onClick = { page = page + 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.s3_onb_next))
                }
            } else {
                Button(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.s3_onb_sample))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.s3_onb_empty))
                }
            }
        }
    }
}
