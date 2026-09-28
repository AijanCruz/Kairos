package com.campusflow.app.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import com.campusflow.app.ui.components.*
import java.time.LocalDate
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityEditor(
    initial: Schedule?, category: String, preferences: UserPreferences, categories: List<Category>,
    subjects: List<Subject>, routines: List<RoutineWithExercises>, saving: Boolean, editingSingle: Boolean = false, saveError: String? = null,
    onClose: () -> Unit, onSave: (Schedule) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var kind by rememberSaveable { mutableStateOf(initial?.category ?: category) }
    var day by rememberSaveable { mutableLongStateOf(initial?.startDay ?: LocalDate.now().toEpochDay()) }
    var minute by rememberSaveable { mutableIntStateOf(initial?.startMinute ?: 16 * 60) }
    var duration by rememberSaveable { mutableStateOf((initial?.durationMinutes ?: if (kind == Categories.STUDY) preferences.studyMinutes else 60).toString()) }
    var repeat by rememberSaveable { mutableStateOf(initial?.repeat ?: Repeat.ONCE.name) }
    var weekdays by rememberSaveable { mutableStateOf(initial?.weekdays ?: day.asDate().dayOfWeek.value.toString()) }
    var reminder by rememberSaveable { mutableIntStateOf(initial?.reminderMinutes ?: preferences.reminderMinutes) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var subjectId by rememberSaveable { mutableStateOf(initial?.subjectId) }
    var routineId by rememberSaveable { mutableStateOf(initial?.routineId) }
    var endDay by rememberSaveable { mutableStateOf(initial?.endDay) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var useEndTime by rememberSaveable { mutableStateOf(false) }
    val previewDuration = duration.toIntOrNull()?.coerceIn(1, 1440) ?: 60
    Dialog(onDismissRequest = { if (!saving) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(), topBar = {
            TopAppBar(title = { Text(if (!editingSingle && (initial == null || initial.id == 0L)) "Nueva actividad" else "Editar actividad") }, navigationIcon = {
                IconButton(onClick = onClose, enabled = !saving) { Icon(Icons.Outlined.Close, "Cerrar") }
            }, actions = {
                TextButton(enabled = !saving, onClick = {
                    val minutes = duration.toIntOrNull()
                    error = when {
                        title.isBlank() -> "Escribe un nombre para la actividad."
                        minutes == null || minutes !in 1..1440 -> "La duración debe estar entre 1 y 1440 minutos."
                        repeat == Repeat.WEEKLY.name && weekdays.isBlank() -> "Selecciona al menos un día."
                        endDay != null && endDay!! < day -> "Revisa la fecha de fin."
                        else -> null
                    }
                    if (error == null) onSave(Schedule(id = initial?.id ?: 0, title = title, category = kind, startDay = day, startMinute = minute, durationMinutes = minutes!!, repeat = repeat, weekdays = weekdays, endDay = endDay, reminderMinutes = reminder, notes = notes, subjectId = subjectId, routineId = if (kind == Categories.WORKOUT) routineId else null))
                }) { Text(if (saving) "Guardando…" else "Guardar") }
            })
        }) { padding ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (initial != null && initial.id != 0L && initial.repeat != Repeat.ONCE.name) Text("Editas las próximas actividades de la serie. Las completadas, anteriores y pospuestas conservan sus datos. Para cambiar la fecha de una actividad pospuesta, usa Reprogramar.", style = MaterialTheme.typography.bodySmall)
                if (editingSingle) Text("Los cambios afectan solo a esta actividad. Se conserva su origen e historial.", style = MaterialTheme.typography.bodySmall)
                (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceField("Categoría", kind, categories.map { it.key }, { key -> categories.find { it.key == key }?.name ?: key }) { kind = it }
                DateField(day.asDate(), "A partir de") { day = it.toEpochDay() }
                TimeField(minute) { minute = it }
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duración en minutos") }, supportingText = { Text("Finaliza a las ${((minute + previewDuration) % 1440).clock()}${if (minute + previewDuration >= 1440) " (+1 día)" else ""}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    Checkbox(useEndTime, { useEndTime = it })
                    Text("Elegir hora final (opcional)", Modifier.padding(top = 14.dp))
                }
                if (useEndTime) TimeField((minute + previewDuration) % 1440, "Hora final") {
                    duration = ((it - minute + 1440) % 1440).let { difference -> if (difference == 0) 1440 else difference }.toString()
                }
                if (!editingSingle) ChoiceField("Repetición", repeat, Repeat.entries.map { it.name }, { key -> Repeat.valueOf(key).label }) { repeat = it }
                if (repeat == Repeat.WEEKLY.name) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { number ->
                            val selected = number.toString() in weekdays.split(',')
                            FilterChip(selected = selected, onClick = {
                                val set = weekdays.split(',').mapNotNull(String::toIntOrNull).toMutableSet()
                                if (selected) set.remove(number) else set.add(number)
                                weekdays = set.sorted().joinToString(",")
                            }, label = { Text(java.time.DayOfWeek.of(number).getDisplayName(TextStyle.SHORT, Spanish)) })
                        }
                    }
                }
                if (repeat != Repeat.ONCE.name) {
                    Row { Checkbox(endDay != null, { endDay = if (it) day + 120 else null }); Text("Fecha de fin (opcional)", Modifier.padding(top = 14.dp)) }
                    endDay?.let { DateField(it.asDate(), "Hasta") { selected -> endDay = selected.toEpochDay() } }
                }
                ChoiceField("Recordatorio", reminder, ReminderOptions, ::reminderLabel) { reminder = it }
                if (kind == Categories.STUDY || kind == Categories.UNIVERSITY) {
                    ChoiceField("Materia", subjectId, listOf(null) + subjects.map { it.id }, { id -> subjects.find { it.id == id }?.name ?: "Sin asociar" }) {
                        subjectId = it
                        if (title.isBlank()) title = subjects.find { subject -> subject.id == it }?.name ?: ""
                    }
                }
                if (kind == Categories.WORKOUT) {
                    ChoiceField("Rutina", routineId, listOf(null) + routines.map { it.routine.id }, { id -> routines.find { it.routine.id == id }?.routine?.name ?: "Sin rutina" }) {
                        routineId = it
                        routines.find { r -> r.routine.id == it }?.routine?.let { r -> title = r.name; duration = r.minutes.toString() }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
