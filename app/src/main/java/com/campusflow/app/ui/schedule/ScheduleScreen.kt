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
import com.campusflow.app.domain.*
import com.campusflow.app.ui.components.*
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun ScheduleScreen(activities: List<ActivityItem>, onOpen: (ActivityItem) -> Unit, onComplete: (ActivityItem) -> Unit, onRange: (Long, Long) -> Unit) {
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var agenda by rememberSaveable { mutableStateOf(false) }
    val week = CalendarRules.weekStart(selectedDay.asDate())
    val dates = (0L..6L).map { week.plusDays(it) }
    LaunchedEffect(week) { onRange(week.toEpochDay(), week.plusDays(6).toEpochDay()) }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("Tu semana", "Un lugar para cada plan.") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedDay -= 7 }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Semana anterior") }
                Text("${week.shortDate()} – ${week.plusDays(6).shortDate()}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { selectedDay += 7 }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Semana siguiente") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!agenda, { agenda = false }, label = { Text("Semana") })
                FilterChip(agenda, { agenda = true }, label = { Text("Agenda") })
                TextButton(onClick = { selectedDay = LocalDate.now().toEpochDay() }) { Text("Hoy") }
            }
        }
        if (!agenda) {
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
        val shownDates = if (agenda) dates else listOf(selectedDay.asDate())
        shownDates.forEach { date ->
            item(key = "day-$date") { SectionLabel(date.pretty().replaceFirstChar(Char::uppercase)) }
            val daily = activities.filter { it.occurrence.day == date.toEpochDay() }
            if (daily.isEmpty()) item(key = "empty-$date") { EmptyState("Espacio libre", "Puedes reservarlo para ti o agregar una actividad.") }
            items(daily, key = { it.occurrence.id }) { item -> ActivityCard(item, { onOpen(item) }, { onComplete(item) }) }
        }
    }
}
