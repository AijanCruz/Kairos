package com.campusflow.app.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusflow.app.BuildConfig
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import com.campusflow.app.ui.AppViewModel
import com.campusflow.app.ui.components.*
import java.time.LocalDate
import androidx.core.net.toUri

@Composable
fun SettingsScreen(vm: AppViewModel, preferences: UserPreferences, onBack: () -> Unit) {
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var dataBusy by remember { mutableStateOf(false) }
    val backup = remember { DataBackup(vm.container.database) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.perform {
            dataBusy = true
            try { backup.exportTo(vm.container, it); vm.message("Copia exportada") } finally { dataBusy = false }
        } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri?.toString() }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Text("Volver") }
            PageHeading("A tu manera", "Pequeños ajustes para tu día a día.")
        }
        item { SectionLabel("APARIENCIA") }
        item {
            SettingsCard {
                ChoiceField("Tema", preferences.appearance, Appearance.entries, { it.label }) { vm.perform { vm.container.preferences.setAppearance(it) } }
                Row {
                    Column(Modifier.weight(1f)) { Text("Color dinámico", style = MaterialTheme.typography.titleMedium); Text("Colores de tu fondo de pantalla", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(preferences.dynamicColor, { vm.perform { vm.container.preferences.setDynamic(it) } }, enabled = Build.VERSION.SDK_INT >= 31)
                }
            }
        }
        item { SectionLabel("TUS PREFERENCIAS") }
        item {
            SettingsCard {
                ChoiceField("Recordatorio predeterminado", preferences.reminderMinutes, ReminderOptions, ::reminderLabel) { vm.perform { vm.container.preferences.setReminder(it) } }
                ChoiceField("Duración de estudio", preferences.studyMinutes, listOf(15, 20, 25, 30, 45, 60, 90, 120), { "$it minutos" }) { vm.perform { vm.container.preferences.setStudy(it) } }
                Text("Se aplican a nuevas actividades. Cada actividad puede tener su propia configuración.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionLabel("NOTIFICACIONES") }
        item { SettingsCard { NotificationControls(vm, preferences.notifications) } }
        item { SectionLabel("DATOS") }
        item {
            SettingsCard {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text("Tus planes se quedan contigo", style = MaterialTheme.typography.titleMedium)
                Text("Sin cuenta, sin anuncios y sin servidores. El OCR se procesa localmente. Las copias automáticas en la nube están desactivadas.", style = MaterialTheme.typography.bodyMedium)
                Text("La copia manual incluye actividades, materias, rutinas, sesiones e historial. Tú eliges dónde guardarla; las preferencias de apariencia no se incluyen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dataBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(enabled = !dataBusy, onClick = { export.launch("CampusFlow-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia de datos") }
                OutlinedButton(enabled = !dataBusy, onClick = { import.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar copia") }
                TextButton(enabled = !dataBusy, onClick = { clearDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Borrar todos los datos", color = MaterialTheme.colorScheme.error) }
            }
        }
        item { SectionLabel("ACERCA DE") }
        item {
            SettingsCard {
                Text("CampusFlow", style = MaterialTheme.typography.titleLarge)
                Text("Tu día, con intención.\nVersión ${BuildConfig.VERSION_NAME} · Android 8 o superior", style = MaterialTheme.typography.bodyMedium)
                Text("Hecho con Kotlin, Compose, Room y ML Kit. Revisa siempre los resultados del OCR antes de importar un horario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    importUri?.let { uri -> AlertDialog(onDismissRequest = { importUri = null }, title = { Text("¿Restaurar esta copia?") }, text = { Text("Reemplazará todos los datos actuales. Exporta una copia antes si quieres conservarlos. Las alarmas se reprogramarán.") }, confirmButton = {
        TextButton(onClick = {
            importUri = null; dataBusy = true
            vm.perform {
                try {
                    val json = backup.readFrom(vm.container, uri.toUri())
                    vm.container.reminders.replaceData { backup.restore(json) }
                    vm.message("Copia restaurada")
                } finally { dataBusy = false; vm.container.refreshReminders() }
            }
        }) { Text("Restaurar") }
    }, dismissButton = { TextButton(onClick = { importUri = null }) { Text("Cancelar") } }) }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("¿Borrar todos los datos?") }, text = { Text("Se eliminarán actividades, materias, rutinas e historial y se cancelarán los recordatorios. Esta acción no se puede deshacer.") }, confirmButton = {
        TextButton(onClick = {
            clearDialog = false; dataBusy = true
            vm.perform {
                try { vm.container.reminders.replaceData { backup.clear() }; vm.message("Datos eliminados") }
                finally { dataBusy = false; vm.container.refreshReminders() }
            }
        }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
    }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancelar") } })
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
