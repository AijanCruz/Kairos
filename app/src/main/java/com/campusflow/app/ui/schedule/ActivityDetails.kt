package com.campusflow.app.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.*
import com.campusflow.app.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetails(item: ActivityItem, history: List<RescheduleHistory>, onClose: () -> Unit, onEdit: () -> Unit, onEditSeries: () -> Unit, onDelete: () -> Unit, onComplete: () -> Unit, onStopSeries: () -> Unit, onMove: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(Categories.label(item.schedule.category).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.schedule.title, style = MaterialTheme.typography.headlineMedium)
            Text(item.occurrence.day.asDate().pretty())
            Text("${item.occurrence.minute.clock()} · ${item.schedule.durationMinutes} min")
            Text(reminderLabel(item.schedule.reminderMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.schedule.notes.isNotBlank()) Text(item.schedule.notes)
            if (item.occurrence.status == Status.DONE) Text("✓ Completado", color = MaterialTheme.colorScheme.primary)
            else Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text("Completar") }
            if (item.occurrence.status == Status.PENDING) OutlinedButton(onClick = onMove, modifier = Modifier.fillMaxWidth()) { Text("Posponer / reprogramar") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar esta actividad") }
            if (item.schedule.repeat != "ONCE" && item.schedule.active) TextButton(onClick = onEditSeries, modifier = Modifier.fillMaxWidth()) { Text("Editar próximas actividades de la serie") }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Eliminar esta actividad", color = MaterialTheme.colorScheme.error) }
            if (item.schedule.repeat != "ONCE" && item.schedule.active) TextButton(onClick = onStopSeries, modifier = Modifier.fillMaxWidth()) { Text("Detener repetición", color = MaterialTheme.colorScheme.error) }
            if (history.isNotEmpty()) {
                HorizontalDivider()
                Text("Historial de cambios", style = MaterialTheme.typography.titleMedium)
                Text("Original: ${item.occurrence.originalDay.asDate().shortDate()} · ${item.occurrence.originalMinute.clock()}", style = MaterialTheme.typography.bodySmall)
                history.forEach { entry ->
                    Text("${entry.fromDay.asDate().shortDate()} ${entry.fromMinute.clock()} → ${entry.toDay.asDate().shortDate()} ${entry.toMinute.clock()}${if (entry.undone) " · deshecho" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
