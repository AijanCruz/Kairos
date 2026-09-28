package com.campusflow.app.data

import androidx.room.withTransaction
import com.campusflow.app.domain.TimerMath

class StudyRepository(private val repository: CampusRepository) {
    private val dao = repository.dao
    val timer = dao.timerFlow()
    val records = dao.studyRecords()

    suspend fun start(id: Long) = repository.db.withTransaction {
        val activity = requireNotNull(dao.activity(id))
        require(activity.schedule.category == Categories.STUDY && activity.occurrence.status == Status.PENDING) { "Selecciona una sesión de estudio pendiente." }
        val existing = dao.timer()
        require(existing == null || existing.occurrenceId == id) { "Finaliza o descarta el temporizador actual antes de iniciar otro." }
        val timer = existing ?: StudyTimer(occurrenceId = id, remainingMillis = activity.schedule.durationMinutes * 60_000L, totalMillis = activity.schedule.durationMinutes * 60_000L)
        if (timer.runningSince == null) dao.saveTimer(timer.copy(runningSince = System.currentTimeMillis()))
    }

    suspend fun pause() = repository.db.withTransaction {
        dao.timer()?.let {
            dao.saveTimer(it.copy(remainingMillis = TimerMath.remaining(it.remainingMillis, it.runningSince, System.currentTimeMillis()), runningSince = null))
        }
    }

    suspend fun finish(): Occurrence? = repository.db.withTransaction {
        val timer = dao.timer() ?: return@withTransaction null
        val left = TimerMath.remaining(timer.remainingMillis, timer.runningSince, System.currentTimeMillis())
        repository.complete(timer.occurrenceId, ((timer.totalMillis - left) / 1000).coerceAtLeast(0))
    }

    suspend fun discard() = dao.clearTimer()

    suspend fun renameSubject(subject: Subject) {
        require(subject.name.isNotBlank()) { "Escribe el nombre de la materia." }
        val existing = dao.subjectByName(subject.name.trim())
        require(existing == null || existing.id == subject.id) { "Ya existe una materia con ese nombre." }
        dao.updateSubject(subject.copy(name = subject.name.trim()))
    }
}
