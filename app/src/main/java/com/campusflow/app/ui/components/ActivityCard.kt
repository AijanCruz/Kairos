package com.campusflow.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.*
import com.campusflow.app.domain.clock

@Composable
fun ActivityCard(item: ActivityItem, onOpen: () -> Unit, onComplete: () -> Unit) {
    val schedule = item.schedule
    val occurrence = item.occurrence
    val done = occurrence.status == Status.DONE
    val color = when (schedule.category) {
        Categories.UNIVERSITY -> MaterialTheme.colorScheme.secondaryContainer
        Categories.WORKOUT -> MaterialTheme.colorScheme.tertiaryContainer
        Categories.STUDY -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val icon = when (schedule.category) {
        Categories.UNIVERSITY -> Icons.Outlined.School
        Categories.WORKOUT -> Icons.Outlined.FitnessCenter
        Categories.STUDY -> Icons.AutoMirrored.Outlined.MenuBook
        else -> Icons.Outlined.Event
    }
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = color, shape = MaterialTheme.shapes.small) { Icon(icon, null, Modifier.padding(12.dp).size(23.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(schedule.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (done) TextDecoration.LineThrough else null)
                Text("${Categories.label(schedule.category)} · ${schedule.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${occurrence.minute.clock()} – ${((occurrence.minute + schedule.durationMinutes) % 1440).clock()}${if (occurrence.minute + schedule.durationMinutes >= 1440) " (+1 día)" else ""}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (occurrence.moved) Text("↪ Pospuesta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            IconButton(onClick = onComplete, enabled = !done) {
                Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (done) "Completado" else "Completar", tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
        }
    }
}
