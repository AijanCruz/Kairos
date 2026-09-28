package com.campusflow.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusflow.app.CampusApp
import com.campusflow.app.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.campusflow.app.data.*

data class Feedback(val message: String, val undo: (suspend () -> Unit)? = null)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val container = application as CampusApp
    val preferences = container.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())
    val repository = container.repository
    val study = StudyRepository(repository)
    val workout = WorkoutRepository(repository)
    val timer = study.timer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val studyRecords = study.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activities = repository.activities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Categories.defaults)
    val subjects = repository.subjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routines = repository.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val messages = Channel<Feedback>(Channel.UNLIMITED)
    val feedback = messages.receiveAsFlow()
    val saving = kotlinx.coroutines.flow.MutableStateFlow(false)
    val saveError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val notificationTarget = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val now = kotlinx.coroutines.flow.flow {
        while (true) { emit(java.time.LocalDateTime.now()); kotlinx.coroutines.delay(15_000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), java.time.LocalDateTime.now())
    init { perform { repository.initialize() } }
    fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) { messages.send(Feedback(error.message ?: "No se pudo guardar. Intenta de nuevo.")) }
        }
    }
    suspend fun message(text: String, undo: (suspend () -> Unit)? = null) { messages.send(Feedback(text, undo)) }
    fun complete(item: ActivityItem) = perform {
        val previous = repository.complete(item.occurrence.id)
        message("✓ Completado") { repository.restore(previous) }
    }
    fun delete(item: ActivityItem) = perform {
        val previous = repository.cancel(item.occurrence.id)
        message("Actividad eliminada") { repository.restore(previous, Status.CANCELLED) }
    }
    fun saveSchedule(value: Schedule, occurrenceId: Long? = null, onSaved: () -> Unit) {
        if (saving.value) return
        saving.value = true
        saveError.value = null
        perform {
            try {
                if (occurrenceId == null) repository.saveSchedule(value) else repository.editOccurrence(occurrenceId, value)
                onSaved(); message("Actividad guardada")
            }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) { saveError.value = error.message ?: "No se pudo guardar. Intenta de nuevo." }
            finally { saving.value = false }
        }
    }
}
