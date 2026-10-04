package com.campusflow.app.ui.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.campusflow.app.data.*
import com.campusflow.app.domain.asDate
import com.campusflow.app.ui.components.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationEditor(initial: AcademicEvaluation?, subject: Subject, saving: Boolean, error: String?, onClose: () -> Unit, onSave: (AcademicEvaluation) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: EvaluationType.EXAMEN.name) }
    var date by rememberSaveable { mutableLongStateOf(initial?.date ?: LocalDate.now().toEpochDay()) }
    Dialog(onDismissRequest = { if (!saving) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), topBar = {
            TopAppBar(title = { Text(if (initial == null) "Nueva evaluación" else "Editar evaluación") }, navigationIcon = {
                IconButton(onClick = onClose, enabled = !saving) { Icon(Icons.Outlined.Close, "Cerrar evaluación") }
            }, actions = {
                TextButton(enabled = !saving && title.isNotBlank(), onClick = {
                    onSave(AcademicEvaluation(initial?.id ?: 0, subject.id, title, type, date, initial?.completed ?: false))
                }) { Text(if (saving) "Guardando…" else "Guardar") }
            })
        }) { padding ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(title, { title = it }, enabled = !saving, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ChoiceField("Tipo", type, EvaluationType.entries.map { it.name }, { EvaluationType.valueOf(it).label }, enabled = !saving) { type = it }
                DateField(date.asDate(), "Fecha límite", enabled = !saving) { date = it.toEpochDay() }
                if (type == EvaluationType.EXAMEN.name) Text("Después de guardar podrás agregar los temas del examen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
