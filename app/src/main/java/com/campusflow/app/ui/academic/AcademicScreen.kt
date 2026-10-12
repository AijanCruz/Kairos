package com.campusflow.app.ui.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusflow.app.data.*
import com.campusflow.app.domain.asDate
import com.campusflow.app.domain.pretty
import com.campusflow.app.domain.AcademicRules
import com.campusflow.app.domain.GradeGoalStatus
import com.campusflow.app.ui.AppViewModel
import com.campusflow.app.ui.components.*

@Composable
fun AcademicScreen(vm: AppViewModel, subjectId: Long, initialEvaluationId: Long, onBack: () -> Unit) {
    val all by vm.evaluations.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val goals by vm.academicGoals.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val saving by vm.academicSaving.collectAsStateWithLifecycle()
    val error by vm.academicError.collectAsStateWithLifecycle()
    val subject = subjects.find { it.id == subjectId }
    val evaluations = remember(all, subjectId) { all.filter { it.evaluation.subjectId == subjectId }.sortedBy { it.evaluation.completed } }
    val grades = remember(evaluations, subjectId) { AcademicRules.gradeSummary(evaluations.map { it.evaluation }, subjectId) }
    val goal = goals.find { it.subjectId == subjectId }
    val goalResult = remember(evaluations, goal) { goal?.let { AcademicRules.goalResult(evaluations.map { row -> row.evaluation }, subjectId, it.targetGrade) } }
    val topics = remember(evaluations, subjectId) { AcademicRules.topicSummary(evaluations, subjectId) }
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var goalText by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(initialEvaluationId.takeIf { it > 0 }) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var topicEvaluationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var topicId by rememberSaveable { mutableLongStateOf(0) }
    var topicName by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(subjectId) { vm.academicError.value = null }
    LaunchedEffect(initialEvaluationId, evaluations.isEmpty(), subject?.id) {
        val index = evaluations.indexOfFirst { it.evaluation.id == initialEvaluationId }
        if (index >= 0 && subject != null) listState.scrollToItem(index + 2)
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Text("Volver") }
            PageHeading("Evaluaciones", subject?.name ?: "Materia no disponible")
            if (subject != null) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Calificaciones", style = MaterialTheme.typography.titleMedium)
                        Text("Puntos acumulados: ${AcademicRules.numberLabel(grades.accumulatedPoints)} / 100", style = MaterialTheme.typography.bodyLarge)
                        Text("Promedio ponderado: ${grades.weightedAverage?.let { "${AcademicRules.numberLabel(it)} / 100" } ?: "Sin calificaciones con peso"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Porcentaje pendiente: ${AcademicRules.numberLabel(grades.pendingPercentage)} %", style = MaterialTheme.typography.bodyMedium)
                        Text("Solo cuentan las evaluaciones con nota y porcentaje. El pendiente incluye el porcentaje aún no asignado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = !saving, onClick = { goalText = goal?.targetGrade?.toString().orEmpty(); vm.academicError.value = null; goalDialog = true }) {
                            Text(goal?.let { "Meta final: ${AcademicRules.numberLabel(it.targetGrade)} / 100" } ?: "Establecer meta final")
                        }
                        goalResult?.let { result ->
                            val label = when (result.status) {
                                GradeGoalStatus.SECURED -> "Meta asegurada"
                                GradeGoalStatus.REACHABLE -> "Meta alcanzable"
                                GradeGoalStatus.IMPOSSIBLE -> "Meta matemáticamente imposible"
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall, color = if (result.status == GradeGoalStatus.IMPOSSIBLE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            if (result.status == GradeGoalStatus.REACHABLE) Text("Necesitas un promedio de ${AcademicRules.requiredGradeLabel(result.requiredAverage!!)} / 100 en el ${AcademicRules.numberLabel(grades.pendingPercentage)} % restante.", style = MaterialTheme.typography.bodySmall)
                            if (result.status == GradeGoalStatus.IMPOSSIBLE) Text("Incluso con 100 en todo lo pendiente no se alcanza la meta con las notas actuales.", style = MaterialTheme.typography.bodySmall)
                            if (result.unassignedPercentage > 0) Text("Falta asignar ${AcademicRules.numberLabel(result.unassignedPercentage)} % a evaluaciones. El cálculo supone que completarás el 100 % de la materia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                        Text("Temas de la materia", style = MaterialTheme.typography.titleSmall)
                        Text("Pendientes: ${topics.pending} · Practicando: ${topics.practicing} · Dominados: ${topics.mastered}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (subject != null) item {
            OutlinedButton(onClick = { vm.academicError.value = null; editId = 0 }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("+ Evaluación") }
        }
        if (error != null && editId == null && topicEvaluationId == null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (evaluations.isEmpty() && subject != null) item { Text("Agrega una fecha de entrega o un examen. Los temas te ayudarán a decidir qué estudiar.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(evaluations, key = { it.evaluation.id }) { item ->
            val evaluation = item.evaluation
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column {
                    EvaluationRow(item, now.toLocalDate().toEpochDay(), showGrades = true) { expandedId = if (expandedId == evaluation.id) null else evaluation.id }
                    if (expandedId == evaluation.id) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(evaluation.date.asDate().pretty(), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !saving, onClick = { vm.changeAcademic({ vm.academic.setCompleted(evaluation.id, !evaluation.completed) }) }, modifier = Modifier.weight(1f)) {
                                Text(if (evaluation.completed) "Marcar pendiente" else "Completar evaluación")
                            }
                            IconButton(enabled = !saving, onClick = { vm.academicError.value = null; editId = evaluation.id }) { Icon(Icons.Outlined.Edit, "Editar evaluación") }
                            IconButton(enabled = !saving, onClick = { deleteId = evaluation.id }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar evaluación") }
                        }
                        if (AcademicRules.supportsTopics(evaluation.type)) {
                            HorizontalDivider()
                            Text(if (evaluation.type == EvaluationType.QUIZ.name) "Temas del quiz" else "Temas del examen", style = MaterialTheme.typography.titleSmall)
                            item.topics.sortedBy { it.id }.forEach { topic ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(topic.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        IconButton(enabled = !saving, onClick = { vm.academicError.value = null; topicEvaluationId = evaluation.id; topicId = topic.id; topicName = topic.name }) { Icon(Icons.Outlined.Edit, "Editar tema ${topic.name}") }
                                        IconButton(enabled = !saving, onClick = { vm.changeAcademic({ vm.academic.deleteTopic(topic.id) }) }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar tema ${topic.name}") }
                                    }
                                    ChoiceField("Estado de ${topic.name}", topic.status, TopicStatus.entries.map { it.name }, { TopicStatus.valueOf(it).label }, enabled = !saving) { status ->
                                        vm.changeAcademic({ vm.academic.setTopicStatus(topic.id, status) })
                                    }
                                }
                            }
                            TextButton(enabled = !saving, onClick = { vm.academicError.value = null; topicEvaluationId = evaluation.id; topicId = 0; topicName = "" }) { Text("+ Tema") }
                        }
                    }
                }
            }
        }
    }
    if (goalDialog) {
        val parsed = AcademicRules.parseGradingNumber(goalText)
        AlertDialog(onDismissRequest = { if (!saving) goalDialog = false }, title = { Text("Meta final de la materia") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(goalText, { goalText = it }, enabled = !saving, label = { Text("Nota objetivo (0–100)") },
                    supportingText = { Text("Deja el campo vacío para quitar la meta.") }, isError = goalText.isNotBlank() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = !saving && (goalText.isBlank() || parsed != null), onClick = {
            vm.changeAcademic({ vm.academic.saveGoal(subjectId, parsed) }, { goalDialog = false })
        }) { Text("Guardar meta") } }, dismissButton = { TextButton(enabled = !saving, onClick = { goalDialog = false }) { Text("Cancelar") } })
    }
    if (subject != null) editId?.let { id ->
        val initial = evaluations.find { it.evaluation.id == id }?.evaluation
        if (id == 0L || initial != null) key(id) {
            EvaluationEditor(initial, subject, saving, error, { editId = null }) { value ->
                vm.changeAcademic({ expandedId = vm.academic.saveEvaluation(value) }, { editId = null })
            }
        }
    }
    topicEvaluationId?.let { evaluationId ->
        AlertDialog(onDismissRequest = { if (!saving) topicEvaluationId = null }, title = { Text(if (topicId == 0L) "Nuevo tema" else "Editar tema") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(topicName, { topicName = it }, enabled = !saving, label = { Text("Nombre del tema") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(enabled = !saving && topicName.isNotBlank(), onClick = {
                vm.changeAcademic({ vm.academic.saveTopic(ExamTopic(topicId, evaluationId, topicName)) }, { topicEvaluationId = null })
            }) { Text("Guardar tema") }
        }, dismissButton = { TextButton(enabled = !saving, onClick = { topicEvaluationId = null }) { Text("Cancelar") } })
    }
    deleteId?.let { id ->
        AlertDialog(onDismissRequest = { if (!saving) deleteId = null }, title = { Text("¿Eliminar evaluación?") }, text = { Text("También se eliminarán sus temas. Las actividades del horario se conservarán.") }, confirmButton = {
            TextButton(enabled = !saving, onClick = { vm.changeAcademic({ vm.academic.deleteEvaluation(id) }, { deleteId = null }) }) { Text("Eliminar") }
        }, dismissButton = { TextButton(enabled = !saving, onClick = { deleteId = null }) { Text("Cancelar") } })
    }
}
