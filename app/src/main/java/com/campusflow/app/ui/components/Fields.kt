package com.campusflow.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusflow.app.domain.clock
import com.campusflow.app.domain.pretty
import java.time.*

@Composable
fun <T> ChoiceField(label: String, value: T, options: List<T>, text: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text(value), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, label: String = "Fecha", enabled: Boolean = true, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Icon(Icons.Outlined.CalendarToday, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date.pretty(), style = MaterialTheme.typography.bodyMedium)
        }
    }
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
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Icon(Icons.Outlined.Schedule, null)
        Spacer(Modifier.width(12.dp))
        Text("$label · ${minute.clock()}", Modifier.weight(1f))
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = { TimeInput(state) }, confirmButton = {
            TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text("Elegir") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } })
    }
}
