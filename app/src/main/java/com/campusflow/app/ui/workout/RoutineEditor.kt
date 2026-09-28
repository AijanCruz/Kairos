package com.campusflow.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.campusflow.app.data.*
import java.io.Serializable

private data class ExerciseDraft(val name: String = "", val sets: String = "3", val reps: String = "8–12", val rest: String = "60", val weight: String = "", val notes: String = "") : Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditor(initial: RoutineWithExercises?, saving: Boolean, onClose: () -> Unit, onSave: (WorkoutRoutine, List<Exercise>) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.routine?.name ?: "") }
    var minutes by rememberSaveable { mutableStateOf((initial?.routine?.minutes ?: 50).toString()) }
    var notes by rememberSaveable { mutableStateOf(initial?.routine?.notes ?: "") }
    var exercises by rememberSaveable { mutableStateOf<List<ExerciseDraft>>(ArrayList(initial?.exercises?.sortedBy { it.position }?.map { ExerciseDraft(it.name, it.sets.toString(), it.reps, it.restSeconds.toString(), it.weight, it.notes) } ?: listOf(ExerciseDraft()))) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun update(index: Int, value: ExerciseDraft) { exercises = ArrayList(exercises.toMutableList().also { it[index] = value }) }
    Dialog(onDismissRequest = { if (!saving) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), topBar = {
            TopAppBar(title = { Text(if (initial == null) "Nueva rutina" else "Editar rutina") }, navigationIcon = { IconButton(onClick = onClose, enabled = !saving) { Icon(Icons.Outlined.Close, "Cerrar") } }, actions = {
                TextButton(enabled = !saving, onClick = {
                    error = when {
                        name.isBlank() -> "Escribe un nombre."
                        minutes.toIntOrNull() !in 1..1440 -> "Revisa la duración."
                        exercises.isEmpty() -> "Agrega un ejercicio."
                        exercises.any { it.name.isBlank() || it.sets.toIntOrNull() !in 1..100 || it.rest.toIntOrNull() !in 0..3600 || it.reps.isBlank() } -> "Revisa nombre, series (1–100), repeticiones y descanso (0–3600 s)."
                        else -> null
                    }
                    if (error == null) onSave(WorkoutRoutine(initial?.routine?.id ?: 0, name, minutes.toInt(), notes), exercises.mapIndexed { index, e -> Exercise(routineId = initial?.routine?.id ?: 0, position = index, name = e.name, sets = e.sets.toInt(), reps = e.reps, restSeconds = e.rest.toInt(), weight = e.weight, notes = e.notes) })
                }) { Text(if (saving) "Guardando…" else "Guardar") }
            })
        }) { padding ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        OutlinedTextField(name, { name = it }, label = { Text("Nombre de la rutina") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(minutes, { minutes = it }, label = { Text("Duración aproximada (min)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(notes, { notes = it }, label = { Text("Notas de la rutina") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                itemsIndexed(exercises) { index, draft ->
                    OutlinedCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row {
                                Text("Ejercicio ${index + 1}", Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { exercises = ArrayList(exercises.toMutableList().also { it.removeAt(index) }) }) { Icon(Icons.Outlined.DeleteOutline, "Quitar ejercicio") }
                            }
                            OutlinedTextField(draft.name, { update(index, draft.copy(name = it)) }, label = { Text("Ejercicio") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(draft.sets, { update(index, draft.copy(sets = it)) }, label = { Text("Series") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                OutlinedTextField(draft.reps, { update(index, draft.copy(reps = it)) }, label = { Text("Repeticiones") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(draft.rest, { update(index, draft.copy(rest = it)) }, label = { Text("Descanso (s)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                OutlinedTextField(draft.weight, { update(index, draft.copy(weight = it)) }, label = { Text("Peso (opcional)") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            OutlinedTextField(draft.notes, { update(index, draft.copy(notes = it)) }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item { OutlinedButton(onClick = { exercises = ArrayList(exercises + ExerciseDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar ejercicio") } }
            }
        }
    }
}
