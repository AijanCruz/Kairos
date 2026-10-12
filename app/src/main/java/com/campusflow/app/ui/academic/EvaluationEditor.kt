package com.campusflow.app.ui.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import com.campusflow.app.domain.asDate
import com.campusflow.app.domain.AcademicRules
import com.campusflow.app.ui.components.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationEditor(initial: AcademicEvaluation?, subject: Subject, saving: Boolean, error: String?, onClose: () -> Unit, onSave: (AcademicEvaluation) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: EvaluationType.EXAMEN.name) }
    var date by rememberSaveable { mutableLongStateOf(initial?.date ?: LocalDate.now().toEpochDay()) }
    var percentageText by rememberSaveable { mutableStateOf((initial?.percentage ?: 0.0).toString().removeSuffix(".0").replace('.', ',')) }
    var gradeText by rememberSaveable { mutableStateOf(initial?.grade?.toString()?.removeSuffix(".0")?.replace('.', ',').orEmpty()) }
    val percentage = AcademicRules.parseGradingNumber(percentageText)
    val grade = AcademicRules.parseGradingNumber(gradeText)
    val validNumbers = percentage != null && (gradeText.isBlank() || grade != null)
    Dialog(onDismissRequest = { if (!saving) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), topBar = {
            TopAppBar(title = { Text(if (initial == null) "Nueva evaluación" else "Editar evaluación") }, navigationIcon = {
                IconButton(onClick = onClose, enabled = !saving) { Icon(Icons.Outlined.Close, "Cerrar evaluación") }
            }, actions = {
                TextButton(enabled = !saving && title.isNotBlank() && validNumbers, onClick = {
                    onSave(AcademicEvaluation(initial?.id ?: 0, subject.id, title, type, date, initial?.completed ?: false, percentage = percentage!!, grade = grade))
                }) { Text(if (saving) "Guardando…" else "Guardar") }
            })
        }) { padding ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(title, { title = it }, enabled = !saving, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ChoiceField("Tipo", type, EvaluationType.entries.map { it.name }, { EvaluationType.valueOf(it).label }, enabled = !saving) { type = it }
                DateField(date.asDate(), "Fecha límite", enabled = !saving) { date = it.toEpochDay() }
                OutlinedTextField(percentageText, { percentageText = it }, enabled = !saving,
                    label = { Text("Porcentaje de la materia (%)") }, isError = percentage == null,
                    supportingText = { Text("De 0 a 100. Usa 0 si aún no asignaste un porcentaje.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(gradeText, { gradeText = it }, enabled = !saving,
                    label = { Text("Nota obtenida (0–100)") }, isError = gradeText.isNotBlank() && grade == null,
                    supportingText = { Text("Déjala vacía mientras no tengas calificación.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (percentage != null && grade != null) {
                    val contribution = AcademicRules.contribution(AcademicEvaluation(subjectId = subject.id, title = title, date = date, percentage = percentage, grade = grade))!!
                    Text("Aporte a la nota final: ${AcademicRules.numberLabel(contribution)} puntos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (AcademicRules.supportsTopics(type)) Text("Después de guardar podrás agregar los temas para estudiar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
