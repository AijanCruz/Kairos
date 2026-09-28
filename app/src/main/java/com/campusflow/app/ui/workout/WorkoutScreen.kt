package com.campusflow.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import com.campusflow.app.ui.AppViewModel
import com.campusflow.app.ui.components.*
import java.time.LocalDate

@Composable
fun WorkoutScreen(vm: AppViewModel, activities: List<ActivityItem>, routines: List<RoutineWithExercises>, onOpen: (ActivityItem) -> Unit, onAssign: (WorkoutRoutine) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val daily = activities.filter { it.schedule.category == Categories.WORKOUT && it.occurrence.day == today }
    val upcoming = activities.filter { it.schedule.category == Categories.WORKOUT && it.occurrence.day in today + 1..today + 7 && it.occurrence.status == Status.PENDING }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Muévete a tu ritmo", "Fuerza, equilibrio y constancia.") }
        item { SectionLabel("ENTRENO DE HOY") }
        if (daily.isEmpty()) item { EmptyState("Recuperar también cuenta", "Hoy no tienes entrenamientos programados.", Icons.Outlined.FitnessCenter) }
        items(daily, key = { it.occurrence.id }) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.occurrence.status == Status.PENDING) Text("Hoy toca ${item.schedule.title} 💪", style = MaterialTheme.typography.titleMedium)
                ActivityCard(item, { onOpen(item) }, { vm.complete(item) })
                routines.find { it.routine.id == item.schedule.routineId }?.let { ExerciseList(it) }
            }
        }
        item {
            Row {
                Text("TUS RUTINAS", Modifier.weight(1f).padding(top = 14.dp), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { editId = null; editor = true }) { Text("+ Rutina") }
            }
        }
        if (routines.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Diseña tu propia rutina o empieza con un ejemplo editable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.perform { vm.workout.createExample(); vm.message("Rutina Torso A creada") } }) { Text("Crear Torso A de ejemplo") }
            }
        }
        items(routines, key = { "routine-${it.routine.id}" }) { routine ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(routine.routine.name, style = MaterialTheme.typography.titleLarge)
                            Text("${routine.routine.minutes} min · ${routine.exercises.size} ejercicios", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editId = routine.routine.id; editor = true }) { Icon(Icons.Outlined.Edit, "Editar rutina") }
                        IconButton(onClick = { deleteId = routine.routine.id }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar rutina") }
                    }
                    if (routine.routine.notes.isNotBlank()) Text(routine.routine.notes)
                    TextButton(onClick = { expandedId = if (expandedId == routine.routine.id) null else routine.routine.id }) { Text(if (expandedId == routine.routine.id) "Ocultar ejercicios" else "Ver ejercicios") }
                    if (expandedId == routine.routine.id) ExerciseList(routine)
                    OutlinedButton(onClick = { onAssign(routine.routine) }, modifier = Modifier.fillMaxWidth()) { Text("Asignar a días") }
                }
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionLabel("PRÓXIMOS ENTRENAMIENTOS") }
            items(upcoming, key = { it.occurrence.id }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.occurrence.day.asDate().pretty(), style = MaterialTheme.typography.labelMedium)
                    ActivityCard(item, { onOpen(item) }, { vm.complete(item) })
                }
            }
        }
    }
    if (editor) RoutineEditor(routines.find { it.routine.id == editId }, saving, { editor = false }) { routine, exercises ->
        if (!saving) {
            saving = true
            vm.perform { try { vm.workout.save(routine, exercises); editor = false; vm.message("Rutina guardada") } finally { saving = false } }
        }
    }
    deleteId?.let { id -> routines.find { it.routine.id == id }?.let { routine ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("¿Eliminar ${routine.routine.name}?") }, text = { Text("Sus ejercicios se eliminarán. Las actividades del horario se conservarán sin rutina asociada.") }, confirmButton = {
            TextButton(onClick = { vm.perform { vm.workout.delete(routine.routine) }; deleteId = null }) { Text("Eliminar") }
        }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancelar") } })
    } }
}

@Composable
private fun ExerciseList(routine: RoutineWithExercises) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        routine.exercises.sortedBy { it.position }.forEachIndexed { index, exercise ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%02d".format(index + 1), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 3.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text("${exercise.sets} series · ${exercise.reps} reps · ${exercise.restSeconds}s descanso${if (exercise.weight.isNotBlank()) " · ${exercise.weight}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (exercise.notes.isNotBlank()) Text(exercise.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
