package com.campusflow.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.campusflow.app.R

@Composable
fun ProfileDialog(initialName: String, firstRun: Boolean, saving: Boolean, error: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        KairosCard(Modifier.widthIn(max = 440.dp).imePadding(), accent = MaterialTheme.colorScheme.primary) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.kairos_logo), null, Modifier.size(64.dp))
                Text(if (firstRun) "Bienvenido a Kairos" else "Tu perfil", style = MaterialTheme.typography.headlineMedium)
                Text("¿Cómo te llamás?", style = MaterialTheme.typography.titleMedium)
                Text("Elegí el nombre con el que querés que te saludemos. Se guarda solo en este dispositivo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it.take(40) }, enabled = !saving, label = { Text("Nombre preferido") },
                    supportingText = { Text(if (firstRun) "Es opcional." else "Dejalo vacío para usar un saludo genérico.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !saving && (!firstRun || name.isNotBlank()), onClick = { onSave(name) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saving) "Guardando…" else if (firstRun) "Empezar" else "Guardar nombre")
                }
                TextButton(enabled = !saving, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (firstRun) "Ahora no" else "Cancelar") }
            }
        }
    }
}
