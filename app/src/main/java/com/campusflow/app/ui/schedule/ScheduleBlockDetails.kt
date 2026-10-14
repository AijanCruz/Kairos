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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import com.campusflow.app.ui.theme.categoryColors
import com.campusflow.app.ui.components.IconBadge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleBlockDetails(schedule: Schedule, onClose: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(Icons.Outlined.CalendarMonth, categoryColors(schedule.category).accent)
            Text("HORARIO BASE · ${Categories.label(schedule.category)}", style = MaterialTheme.typography.labelLarge, color = categoryColors(schedule.category).accent)
            Text(schedule.title, style = MaterialTheme.typography.headlineMedium)
            Text(if (schedule.repeat == Repeat.DAILY.name) "Todos los días" else schedule.weekdays.split(',').mapNotNull(String::toIntOrNull).filter { it in 1..7 }.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Spanish) })
            val end = schedule.startMinute + schedule.durationMinutes
            Text("${schedule.startMinute.clock()} – ${(end % 1440).clock()}${if (end >= 1440) " (+1 día)" else ""} · ${schedule.durationMinutes} min")
            Text("Desde ${schedule.startDay.asDate().pretty()}", style = MaterialTheme.typography.bodySmall)
            schedule.endDay?.let {
                Text("Hasta ${it.asDate().pretty()}${if (it < LocalDate.now().toEpochDay()) " · Vigencia finalizada" else ""}", style = MaterialTheme.typography.bodySmall)
            }
            if (schedule.notes.isNotBlank()) Text(schedule.notes)
            Text("Esta es la regla semanal. Para completar o mover una sola actividad, abre su fecha en Agenda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar bloque semanal") }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Eliminar del horario base", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
