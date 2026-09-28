package com.campusflow.app.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.ActivityItem
import com.campusflow.app.domain.asDate
import com.campusflow.app.ui.components.DateField
import com.campusflow.app.ui.components.TimeField
import java.time.LocalDate

@Composable
fun RescheduleDialog(item: ActivityItem, onClose: () -> Unit, onMove: (Long, Int) -> Unit) {
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var minute by rememberSaveable { mutableIntStateOf(item.occurrence.minute) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Hazle espacio otro día") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.schedule.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = day == LocalDate.now().plusDays(1).toEpochDay(), onClick = { day = LocalDate.now().plusDays(1).toEpochDay() }, label = { Text("Mañana") })
                FilterChip(selected = day == LocalDate.now().plusDays(2).toEpochDay(), onClick = { day = LocalDate.now().plusDays(2).toEpochDay() }, label = { Text("Otro día") })
            }
            DateField(day.asDate(), "Elegir fecha") { day = it.toEpochDay() }
            TimeField(minute, "Elegir hora") { minute = it }
            Text("Se moverá solo esta actividad. Su origen y el historial se conservan; el recordatorio se reprogramará.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onMove(day, minute) }) { Text("Posponer") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}
