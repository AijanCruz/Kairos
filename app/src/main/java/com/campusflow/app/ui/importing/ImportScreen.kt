package com.campusflow.app.ui.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusflow.app.domain.*
import com.campusflow.app.ocr.*
import com.campusflow.app.ui.components.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val entries by vm.candidates.collectAsStateWithLifecycle()
    val source by vm.text.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showText by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::recognize) }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Text("Volver") }
            PageHeading("Revisar horario\ndetectado", "Tu imagen se procesa en este dispositivo.")
        }
        item {
            OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(if (source.isEmpty()) "Elegir foto del horario" else "Analizar otra imagen")
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Procesando…", Modifier.padding(top = 10.dp)) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Text("Nada se guarda automáticamente. Comprueba cada materia, día y hora. Las tablas complejas o fotos borrosas pueden requerir correcciones.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (source.isNotBlank()) TextButton(onClick = { showText = !showText }) { Text(if (showText) "Ocultar texto reconocido" else "Ver texto reconocido") }
            if (showText) androidx.compose.foundation.text.selection.SelectionContainer { Text(source, style = MaterialTheme.typography.bodySmall) }
        }
        if (entries.isNotEmpty()) item { DateField(start.asDate(), "Repetir semanalmente a partir de", enabled = !busy) { start = it.toEpochDay() } }
        items(entries, key = { it.id }) { entry ->
            fun update(value: DetectedClass) { vm.update(entries.map { if (it.id == entry.id) value else it }) }
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Text(if (entry.uncertain) "Revisar interpretación" else "Materia detectada", Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                        IconButton(enabled = !busy, onClick = { vm.update(entries.filterNot { it.id == entry.id }) }) { Icon(Icons.Outlined.DeleteOutline, "Quitar materia detectada") }
                    }
                    OutlinedTextField(entry.title, { update(entry.copy(title = it)) }, enabled = !busy, label = { Text("Materia") }, modifier = Modifier.fillMaxWidth())
                    ChoiceField("Día", entry.weekday, listOf(null) + (1..7).toList(), { day -> day?.let { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Spanish).replaceFirstChar(Char::uppercase) } ?: "Seleccionar día" }, enabled = !busy) { update(entry.copy(weekday = it)) }
                    TimeField(entry.startMinute ?: 480, if (entry.startMinute == null) "Confirmar inicio" else "Inicio", enabled = !busy) { update(entry.copy(startMinute = it)) }
                    TimeField(entry.endMinute ?: 540, if (entry.endMinute == null) "Confirmar fin" else "Fin", enabled = !busy) { update(entry.copy(endMinute = it)) }
                    if (entry.startMinute == null || entry.endMinute == null) Text("Confirma las horas; no se pudieron leer con seguridad.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedButton(enabled = !busy && entries.size < 100, onClick = { vm.update(entries + DetectedClass(weekday = LocalDate.now().dayOfWeek.value, startMinute = 480, endMinute = 540)) }, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar materia faltante") }
        }
        if (entries.isNotEmpty()) item { Button(enabled = !busy, onClick = { vm.save(start, onSaved) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar horario (${entries.size})") } }
    }
}
