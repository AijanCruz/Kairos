package com.campusflow.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import com.campusflow.app.domain.clock
import com.campusflow.app.domain.pretty
import java.time.*

@Composable
fun <T> ChoiceField(label: String, value: T, options: List<T>, text: (T) -> String, enabled: Boolean = true, accent: Color? = null, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        PickerField(label, text(value), Icons.Outlined.ExpandMore, enabled, accent) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, label: String = "Fecha", enabled: Boolean = true, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, date.pretty(), Icons.Outlined.CalendarToday, enabled) { open = true }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text("Elegir") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(minute: Int, label: String = "Hora", enabled: Boolean = true, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, minute.clock(), Icons.Outlined.Schedule, enabled) { open = true }
    if (open) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = { TimeInput(state) }, confirmButton = {
            TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text("Elegir") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun PickerField(label: String, value: String, icon: ImageVector, enabled: Boolean, accent: Color? = null, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, accent?.copy(alpha = .4f) ?: MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = accent ?: MaterialTheme.colorScheme.onSurface)
            }
            Icon(icon, null, tint = accent ?: MaterialTheme.colorScheme.primary)
        }
    }
}
