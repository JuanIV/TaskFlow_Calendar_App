package com.example.taskflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.taskflow.R

object IconKeys {
    val all: List<Pair<String, ImageVector>> = listOf(
        Pair("fitness", Icons.Default.FitnessCenter),
        Pair("run", Icons.Default.DirectionsRun),
        Pair("book", Icons.Default.MenuBook),
        Pair("school", Icons.Default.School),
        Pair("work", Icons.Default.Work),
        Pair("computer", Icons.Default.Computer),
        Pair("code", Icons.Default.Code),
        Pair("home", Icons.Default.Home),
        Pair("cart", Icons.Default.ShoppingCart),
        Pair("food", Icons.Default.Restaurant),
        Pair("coffee", Icons.Default.LocalCafe),
        Pair("health", Icons.Default.LocalHospital),
        Pair("heart", Icons.Default.Favorite),
        Pair("sun", Icons.Default.WbSunny),
        Pair("alarm", Icons.Default.Alarm),
        Pair("pets", Icons.Default.Pets),
        Pair("music", Icons.Default.MusicNote),
        Pair("flight", Icons.Default.Flight),
        Pair("car", Icons.Default.DirectionsCar),
        Pair("group", Icons.Default.Group),
        Pair("phone", Icons.Default.Phone),
        Pair("build", Icons.Default.Build),
        Pair("cake", Icons.Default.Cake),
        Pair("star", Icons.Default.Star)
    )

    fun iconFor(key: String?): ImageVector? {
        if (key == null) return null
        return all.firstOrNull { it.first == key }?.second
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.s3_icon_none)) }
        )
        IconKeys.all.forEach { (key, vector) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    vector,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
