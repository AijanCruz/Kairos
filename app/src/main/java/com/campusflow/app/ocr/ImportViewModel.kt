package com.campusflow.app.ocr

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.campusflow.app.CampusApp
import com.campusflow.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as CampusApp
    private val interpreter: ScheduleInterpreter = HeuristicScheduleInterpreter()
    val candidates = saved.getStateFlow("candidates", arrayListOf<DetectedClass>())
    val text = saved.getStateFlow("text", "")
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    fun update(entries: List<DetectedClass>) { saved["candidates"] = ArrayList(entries.take(100)) }
    fun recognize(uri: Uri) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try {
                try { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
                val document = OnDeviceOcr(app).recognize(uri)
                saved["text"] = document.text.take(40_000)
                update(withContext(Dispatchers.Default) { interpreter.interpret(document) })
                if (document.text.isBlank()) error.value = "No se detectó texto. Prueba una imagen más nítida o agrega las materias manualmente."
                else if (candidates.value.isEmpty()) error.value = "No se detectaron clases claras. Puedes agregarlas usando el texto reconocido."
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { error.value = e.message ?: "No se pudo analizar la imagen." }
            finally { busy.value = false }
        }
    }
    fun save(startDay: Long, onSaved: () -> Unit) {
        if (busy.value) return
        val entries = candidates.value.toList()
        if (entries.isEmpty() || entries.any { it.title.isBlank() || it.weekday !in 1..7 || it.startMinute == null || it.endMinute == null || it.endMinute <= it.startMinute }) {
            error.value = "Revisa todas las materias: nombre, día y horas de inicio/fin. El fin debe ser posterior al inicio."
            return
        }
        busy.value = true; error.value = null
        viewModelScope.launch {
            try {
                val reminder = app.preferences.flow.first().reminderMinutes
                app.database.withTransaction {
                    entries.distinctBy { listOf(it.title.trim(), it.weekday, it.startMinute, it.endMinute) }.forEach { detected ->
                        val subjectId = app.repository.addSubject(detected.title)
                        app.repository.saveSchedule(Schedule(title = detected.title, category = Categories.UNIVERSITY, startDay = startDay, startMinute = detected.startMinute!!, durationMinutes = detected.endMinute!! - detected.startMinute, repeat = "WEEKLY", weekdays = detected.weekday.toString(), reminderMinutes = reminder, subjectId = subjectId, notes = "Importado desde una imagen y revisado."))
                    }
                }
                update(emptyList()); saved["text"] = ""; onSaved()
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { error.value = e.message ?: "No se pudo guardar. Ninguna clase se ha importado." }
            finally { busy.value = false }
        }
    }
}
