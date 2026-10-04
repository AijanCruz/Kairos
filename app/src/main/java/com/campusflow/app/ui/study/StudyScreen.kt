package com.campusflow.app.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import com.campusflow.app.ui.AppViewModel
import com.campusflow.app.ui.components.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun StudyScreen(vm: AppViewModel, activities: List<ActivityItem>, subjects: List<Subject>, onOpen: (ActivityItem) -> Unit, onAdd: (Long?, Int) -> Unit, onEvaluations: (Subject) -> Unit) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val records by vm.studyRecords.collectAsStateWithLifecycle()
    val evaluations by vm.evaluations.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var selectedSubjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var subjectDialog by rememberSaveable { mutableStateOf(false) }
    var subjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var subjectName by rememberSaveable { mutableStateOf("") }
    var deleteSubject by remember { mutableStateOf<Subject?>(null) }
    val today = now.toLocalDate().toEpochDay()
    val selectedSubject = subjects.find { it.id == selectedSubjectId }
    val suggestion = remember(evaluations, selectedSubject?.id, today) { AcademicRules.suggestion(evaluations, selectedSubject?.id, today) }
    val sessions = activities.filter { it.schedule.category == Categories.STUDY && it.occurrence.status == Status.PENDING && it.occurrence.day <= today + 14 }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Modo enfoque", "Aprende un poco. Avanza mucho.") }
        timer?.let { active ->
            item {
                StudyTimerCard(active, activities.find { it.occurrence.id == active.occurrenceId }?.schedule?.title ?: "Sesión de estudio",
                    onStart = { vm.perform { vm.study.start(active.occurrenceId) } },
                    onPause = { vm.perform { vm.study.pause() } },
                    onFinish = { vm.perform { vm.study.finish()?.let { previous -> vm.message("Sesión completada") { vm.repository.restore(previous) } } } },
                    onDiscard = { vm.perform { vm.study.discard(); vm.message("Temporizador descartado; la actividad sigue pendiente") } },
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TUS MATERIAS", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { subjectId = null; subjectName = ""; subjectDialog = true }) { Text("+ Materia") }
            }
        }
        if (subjects.isEmpty()) item { Text("Agrega tus materias y asócialas a tus clases o sesiones.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(subjects, key = { "subject-${it.id}" }) { subject ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(subject.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { subjectId = subject.id; subjectName = subject.name; subjectDialog = true }) { Icon(Icons.Outlined.Edit, "Editar materia") }
                        IconButton(onClick = { deleteSubject = subject }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar materia") }
                    }
                    TextButton(onClick = { onEvaluations(subject) }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) { Text("Evaluaciones de ${subject.name}") }
                }
            }
        }
        if (subjects.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceField("Materia para estudiar", selectedSubject?.id, listOf(null) + subjects.map { it.id }, { id -> subjects.find { it.id == id }?.name ?: "Sin seleccionar" }) { selectedSubjectId = it }
                suggestion?.let { Text("Sugerencia: practicar ${it.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
        item { SectionLabel("SESIONES PENDIENTES", "Próximos 14 días") }
        if (sessions.isEmpty()) item { EmptyState("Un momento para aprender", "Programa una sesión breve. La constancia hace la diferencia.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField("Duración de la nueva sesión", selectedMinutes ?: preferences.studyMinutes, listOf(15, 20, 25, 30, 40, 45, 60, 90, 120), { "$it minutos" }) { selectedMinutes = it }
                OutlinedButton(onClick = { onAdd(selectedSubject?.id, selectedMinutes ?: preferences.studyMinutes) }, modifier = Modifier.fillMaxWidth()) { Text("Planificar estudio") }
            }
        }
        items(sessions, key = { it.occurrence.id }) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.occurrence.day.asDate().pretty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActivityCard(item, { onOpen(item) }, { vm.complete(item) })
                TextButton(onClick = { vm.perform { vm.study.start(item.occurrence.id) } }, enabled = timer == null || timer?.occurrenceId == item.occurrence.id) {
                    Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Iniciar temporizador")
                }
            }
        }
        if (records.isNotEmpty()) {
            item { SectionLabel("TU CONSTANCIA", "Últimas sesiones") }
            items(records.take(20), key = { "record-${it.id}" }) { record ->
                val item = activities.find { it.occurrence.id == record.occurrenceId }
                ListItem(headlineContent = { Text(item?.schedule?.title ?: "Sesión de estudio") }, supportingContent = { Text("${record.seconds / 60} min registrados · ${java.time.Instant.ofEpochMilli(record.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().shortDate()}") }, leadingContent = { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest))
            }
        }
    }
    if (subjectDialog) AlertDialog(onDismissRequest = { subjectDialog = false }, title = { Text(if (subjectId == null) "Nueva materia" else "Editar materia") }, text = {
        OutlinedTextField(subjectName, { subjectName = it }, label = { Text("Nombre") }, singleLine = true)
    }, confirmButton = {
        TextButton(enabled = subjectName.isNotBlank(), onClick = {
            vm.perform {
                if (subjectId == null) vm.repository.addSubject(subjectName) else vm.study.renameSubject(Subject(subjectId!!, subjectName))
                subjectDialog = false
            }
        }) { Text("Guardar") }
    }, dismissButton = { TextButton(onClick = { subjectDialog = false }) { Text("Cancelar") } })
    deleteSubject?.let { subject -> AlertDialog(onDismissRequest = { deleteSubject = null }, title = { Text("¿Eliminar ${subject.name}?") }, text = { Text("Tus actividades se conservarán sin la asociación a esta materia. Sus evaluaciones y temas se eliminarán.") }, confirmButton = {
        TextButton(onClick = { vm.perform { vm.repository.dao.deleteSubject(subject) }; deleteSubject = null }) { Text("Eliminar") }
    }, dismissButton = { TextButton(onClick = { deleteSubject = null }) { Text("Cancelar") } }) }
}

@Composable
private fun StudyTimerCard(timer: StudyTimer, title: String, onStart: () -> Unit, onPause: () -> Unit, onFinish: () -> Unit, onDiscard: () -> Unit) {
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) { while (true) { clock = System.currentTimeMillis(); delay(250) } }
    val remaining = TimerMath.remaining(timer.remainingMillis, timer.runningSince, clock)
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(TimerMath.display(remaining), style = MaterialTheme.typography.displayLarge, fontSize = 54.sp)
            if (remaining == 0L) Text("Tiempo cumplido. ¡Buen trabajo!")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (remaining > 0) OutlinedButton(onClick = if (timer.runningSince == null) onStart else onPause) { Text(if (timer.runningSince == null) "Iniciar" else "Pausar") }
                Button(onClick = onFinish) { Text("Finalizar") }
            }
            TextButton(onClick = onDiscard) { Text("Descartar temporizador") }
        }
    }
}
