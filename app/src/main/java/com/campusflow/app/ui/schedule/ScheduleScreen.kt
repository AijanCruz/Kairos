package com.campusflow.app.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.campusflow.app.data.ActivityItem
import com.campusflow.app.data.Schedule
import com.campusflow.app.domain.*
import com.campusflow.app.ui.components.*
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun ScheduleScreen(activities: List<ActivityItem>, recurringSchedules: List<Schedule>, onOpen: (ActivityItem) -> Unit, onComplete: (ActivityItem) -> Unit, onOpenBlock: (Schedule) -> Unit, onAddBlock: (Int, Int) -> Unit, onRange: (Long, Long) -> Unit) {
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var agenda by rememberSaveable { mutableStateOf(false) }
    var allDays by rememberSaveable { mutableStateOf(false) }
    val week = CalendarRules.weekStart(selectedDay.asDate())
    val dates = (0L..6L).map { week.plusDays(it) }
    LaunchedEffect(agenda, week) { if (agenda) onRange(week.toEpochDay(), week.plusDays(6).toEpochDay()) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PageHeading("Tu semana", if (agenda) "Actividades con fecha y excepciones." else "Tu horario base, semana tras semana.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!agenda, { agenda = false }, label = { Text("Semana") })
                FilterChip(agenda, { agenda = true }, label = { Text("Agenda") })
            }
        }
        if (!agenda) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Toca un bloque para editarlo o una hora libre para agregar. Desliza para ver todos los días y horas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onAddBlock(LocalDate.now().dayOfWeek.value, 8 * 60) }) { Text("+ Bloque semanal") }
            }
            WeeklyTimetableGrid(recurringSchedules, onOpenBlock, onAddBlock, Modifier.weight(1f).padding(horizontal = 12.dp))
        } else LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedDay -= 7 }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Semana anterior") }
                Text("${week.shortDate()} – ${week.plusDays(6).shortDate()}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { selectedDay += 7 }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Semana siguiente") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!allDays, { allDays = false }, label = { Text("Día") })
                FilterChip(allDays, { allDays = true }, label = { Text("7 días") })
                TextButton(onClick = { selectedDay = LocalDate.now().toEpochDay() }) { Text("Hoy") }
            }
        }
        if (!allDays) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dates.forEach { date ->
                        val selected = date.toEpochDay() == selectedDay
                        val hasActivities = activities.any { it.occurrence.day == date.toEpochDay() }
                        Surface(onClick = { selectedDay = date.toEpochDay() }, modifier = Modifier.weight(1f).semantics {
                            this.selected = selected
                            contentDescription = "${date.pretty()}${if (hasActivities) ", con actividades" else ", sin actividades"}"
                        }, shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.clearAndSetSemantics { }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Spanish).uppercase(), style = MaterialTheme.typography.labelSmall)
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                                Text(if (hasActivities) "•" else "·")
                            }
                        }
                    }
                }
            }
        }
        val shownDates = if (allDays) dates else listOf(selectedDay.asDate())
        shownDates.forEach { date ->
            item(key = "day-$date") { SectionLabel(date.pretty().replaceFirstChar(Char::uppercase)) }
            val daily = activities.filter { it.occurrence.day == date.toEpochDay() }
            if (daily.isEmpty()) item(key = "empty-$date") { EmptyState("Espacio libre", "Puedes reservarlo para ti o agregar una actividad.") }
            val (oneOff, recurring) = daily.partition { it.schedule.repeat == Repeat.ONCE.name }
            if (oneOff.isNotEmpty()) {
                item(key = "one-off-$date") { Text("Puntuales y excepciones", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(oneOff, key = { it.occurrence.id }) { item -> ActivityCard(item, { onOpen(item) }, { onComplete(item) }) }
            }
            if (recurring.isNotEmpty()) {
                item(key = "recurring-$date") { Text("Recurrentes de este día", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(recurring, key = { it.occurrence.id }) { item -> ActivityCard(item, { onOpen(item) }, { onComplete(item) }) }
            }
        }
        }
    }
}
