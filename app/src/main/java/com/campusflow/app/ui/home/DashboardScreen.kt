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
import com.campusflow.app.ui.academic.EvaluationRow
import com.campusflow.app.ui.theme.categoryColors

@Composable
fun DashboardScreen(activities: List<ActivityItem>, now: LocalDateTime, remindersReady: Boolean, onOpen: (ActivityItem) -> Unit, onComplete: (ActivityItem) -> Unit, onAdd: () -> Unit, onReminders: () -> Unit, evaluations: List<EvaluationWithTopics>, onEvaluation: (EvaluationWithTopics) -> Unit, displayName: String, subjects: List<Subject>, studyRecords: List<StudyRecord>, onSchedule: () -> Unit, onStudy: () -> Unit, onWorkout: () -> Unit, onSubject: (Subject) -> Unit) {
    val today = now.toLocalDate()
    val daily = activities.filter { it.occurrence.day == today.toEpochDay() }
    val weekStart = CalendarRules.weekStart(today).toEpochDay()
    val weekly = activities.filter { it.occurrence.day in weekStart..weekStart + 6 }
    val upcoming = remember(evaluations, today) { AcademicRules.upcoming(evaluations, today.toEpochDay()).take(3) }
    val next = remember(activities, now) {
        val stamp = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        activities.filter { it.occurrence.status == Status.PENDING && CalendarRules.instant(it.occurrence.day, it.occurrence.minute) + it.schedule.durationMinutes * 60_000L > stamp }.take(3)
    }
    val nextIds = next.map { it.occurrence.id }.toSet()
    val overdue = activities.filter { it.occurrence.day < today.toEpochDay() && it.occurrence.status == Status.PENDING && it.occurrence.id !in nextIds }
    val studyMinutes = remember(studyRecords, today) { studyRecords.filter { java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }.sumOf { it.seconds } / 60 }
    var showOverdue by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { WelcomeCard(now, displayName, onAdd) }
        item { HomeSummary(daily.count { it.schedule.category == Categories.UNIVERSITY }, evaluations.count { !it.evaluation.completed && it.evaluation.type in setOf("TAREA", "PROYECTO", "EXPOSICION") }, studyMinutes, daily.count { it.schedule.category == Categories.WORKOUT }, onSchedule, onStudy, onWorkout) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Próximas actividades", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSchedule) { Text("Ver horario") }
        } }
        if (next.isEmpty()) item { Text("Sin próximas actividades programadas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(next, key = { it.occurrence.id }) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.occurrence.day != today.toEpochDay()) Text(item.occurrence.day.asDate().pretty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActivityCard(item, { onOpen(item) }, { onComplete(item) })
            }
        }
        item { HomeAcademicOverview(subjects, evaluations, onStudy, onSubject) }
        if (upcoming.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Próximas evaluaciones", style = MaterialTheme.typography.titleMedium)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column {
                        upcoming.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            EvaluationRow(item, today.toEpochDay()) { onEvaluation(item) }
                        }
                    }
                }
            }
        }
        item { SectionLabel("HOY", "${daily.count { it.occurrence.status == Status.DONE }} / ${daily.size} completadas") }
        if (daily.isEmpty()) item { EmptyState("Hoy tenés espacio", "No hay actividades programadas para hoy. Podés planificar desde +.") }
        items(daily.filterNot { it.occurrence.id in nextIds }, key = { it.occurrence.id }) { item -> ActivityCard(item, { onOpen(item) }, { onComplete(item) }) }
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
                        val accent = categoryColors(category).accent
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
                                Text("$completed / $total", style = MaterialTheme.typography.labelLarge, color = accent)
                            }
                            LinearProgressIndicator(progress = { if (total == 0) 0f else completed.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = accent, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, gapSize = 0.dp, drawStopIndicator = {})
                        }
                    }
                }
            }
        }
    }
}
