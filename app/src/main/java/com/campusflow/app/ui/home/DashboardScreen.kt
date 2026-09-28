package com.campusflow.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import com.campusflow.app.ui.components.*
import java.time.LocalDateTime

@Composable
fun DashboardScreen(activities: List<ActivityItem>, now: LocalDateTime, remindersReady: Boolean, onOpen: (ActivityItem) -> Unit, onComplete: (ActivityItem) -> Unit, onAdd: () -> Unit, onReminders: () -> Unit) {
    val today = now.toLocalDate()
    val daily = activities.filter { it.occurrence.day == today.toEpochDay() }
    val pending = daily.filter { it.occurrence.status == Status.PENDING }
    val overdue = activities.filter { it.occurrence.day < today.toEpochDay() && it.occurrence.status == Status.PENDING }
    val weekStart = CalendarRules.weekStart(today).toEpochDay()
    val weekly = activities.filter { it.occurrence.day in weekStart..weekStart + 6 }
    var showOverdue by rememberSaveable { mutableStateOf(false) }
    val greeting = when (now.hour) { in 5..11 -> "Buenos días"; in 12..19 -> "Buenas tardes"; else -> "Buenas noches" }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageHeading(greeting, today.pretty().replaceFirstChar(Char::uppercase)) }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("UN DÍA A LA VEZ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(if (pending.isEmpty()) "Respira.\nHoy tienes espacio." else "Tus planes,\na tu ritmo.", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (pending.isEmpty()) "Todo listo por hoy 🎉" else "${pending.size} ${if (pending.size == 1) "actividad pendiente" else "actividades pendientes"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = onAdd) { Icon(Icons.Outlined.ArrowOutward, "Planificar actividad") }
                    }
                }
            }
        }
        item { SectionLabel("HOY", "${daily.count { it.occurrence.status == Status.DONE }} / ${daily.size} completadas") }
        if (daily.isEmpty()) item { EmptyState("Tu agenda empieza contigo", "Toca + para agregar tu primera clase, sesión o entrenamiento.") }
        items(daily, key = { it.occurrence.id }) { item -> ActivityCard(item, { onOpen(item) }, { onComplete(item) }) }
        if (!remindersReady && activities.any { it.schedule.reminderMinutes >= 0 && it.occurrence.status == Status.PENDING }) item {
            OutlinedCard(onClick = onReminders, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Deja que te avisemos", style = MaterialTheme.typography.titleMedium)
                    Text("Revisa los permisos de notificaciones y alarmas exactas.", style = MaterialTheme.typography.bodySmall)
                    Text("Configurar recordatorios →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item {
                OutlinedCard(onClick = { showOverdue = !showOverdue }, modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${overdue.size} pendientes de otros días", style = MaterialTheme.typography.titleMedium)
                        Text(if (showOverdue) "Ocultar" else "Revísalas, complétalas o hazles espacio otro día.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (showOverdue) items(overdue, key = { "overdue-${it.occurrence.id}" }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.occurrence.day.asDate().shortDate(), style = MaterialTheme.typography.labelMedium)
                    ActivityCard(item, { onOpen(item) }, { onComplete(item) })
                }
            }
        }
        item { SectionLabel("ESTA SEMANA", "Pequeños pasos cuentan") }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf(Categories.STUDY to "Estudio", Categories.WORKOUT to "Entrenamientos").forEach { (category, label) ->
                        val entries = weekly.filter { it.schedule.category == category }
                        val completed = activities.count {
                            it.schedule.category == category && it.occurrence.status == Status.DONE && it.occurrence.completedAt?.let { stamp ->
                                java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() in weekStart..weekStart + 6
                            } == true
                        }
                        val total = maxOf(entries.size, completed)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text("$completed / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(progress = { if (total == 0) 0f else completed.toFloat() / total }, modifier = Modifier.fillMaxWidth(), gapSize = 0.dp, drawStopIndicator = {})
                        }
                    }
                }
            }
        }
    }
}
