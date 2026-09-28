package com.campusflow.app.data

import androidx.room.withTransaction
import com.campusflow.app.domain.CalendarRules
import com.campusflow.app.domain.Repeat
import java.time.LocalDate

class CampusRepository(val db: CampusDatabase, private val onChanged: () -> Unit = {}) {
    val dao = db.dao()
    val activities = dao.activities()
    val categories = dao.categories()
    val subjects = dao.subjects()
    val routines = dao.routines()

    suspend fun initialize() {
        db.withTransaction { dao.insertCategories(Categories.defaults); generate() }
        onChanged()
    }

    suspend fun generate(today: Long = LocalDate.now().toEpochDay()) {
        generateRange(today - 30, today + 180)
    }

    suspend fun generateRange(from: Long, to: Long) {
        require(to - from in 0..730) { "El rango del calendario es demasiado grande." }
        dao.activeSchedules().forEach { schedule ->
            val dates = CalendarRules.dates(schedule.startDay, schedule.endDay, schedule.repeat, schedule.weekdays, from, to)
            dao.insertOccurrences(dates.map { day -> Occurrence(scheduleId = schedule.id, originalDay = day, originalMinute = schedule.startMinute, day = day, minute = schedule.startMinute) })
            // A one-off entry can intentionally be outside the rolling horizon.
            if (schedule.repeat == Repeat.ONCE.name) dao.insertOccurrences(listOf(Occurrence(scheduleId = schedule.id, originalDay = schedule.startDay, originalMinute = schedule.startMinute, day = schedule.startDay, minute = schedule.startMinute)))
        }
    }

    suspend fun saveSchedule(value: Schedule): Long {
        require(value.title.isNotBlank()) { "Escribe un nombre." }
        require(value.startMinute in 0..1439 && value.durationMinutes in 1..1440) { "Revisa la hora y la duración (1–1440 min)." }
        require(value.reminderMinutes in listOf(-1, 0, 5, 10, 15, 30, 60)) { "Recordatorio inválido." }
        require(value.repeat != Repeat.WEEKLY.name || value.weekdays.split(',').any { it.toIntOrNull() in 1..7 }) { "Selecciona al menos un día." }
        require(value.endDay == null || value.endDay >= value.startDay) { "La fecha final debe ser posterior al inicio." }
        val result = db.withTransaction {
            dao.insertCategories(Categories.defaults)
            val existing = if (value.id == 0L) null else dao.schedule(value.id)
            val actualId: Long
            if (existing != null && existing.repeat != Repeat.ONCE.name) {
                // Version a recurring rule so past results and moved exceptions retain their data.
                val start = maxOf(value.startDay, LocalDate.now().toEpochDay())
                val occurrences = dao.seriesOccurrences(existing.id)
                dao.saveSchedule(existing.copy(active = false))
                actualId = dao.saveSchedule(value.copy(id = 0, title = value.title.trim(), startDay = start))
                val blocked = occurrences.filter { it.originalDay >= start && (it.status != Status.PENDING || it.moved) }
                dao.insertOccurrences(blocked.map { Occurrence(scheduleId = actualId, originalDay = it.originalDay, originalMinute = value.startMinute, day = it.originalDay, minute = value.startMinute, status = Status.CANCELLED) })
                occurrences.filter { it.day >= start && it.status == Status.PENDING && !it.moved }.forEach {
                    dao.updateOccurrence(it.copy(status = Status.CANCELLED))
                    dao.clearTimerFor(it.id)
                }
            } else {
                val id = dao.saveSchedule(value.copy(title = value.title.trim()))
                actualId = if (value.id == 0L) id else value.id
            }
            if (value.id != 0L && existing?.repeat == Repeat.ONCE.name) {
                val today = LocalDate.now().toEpochDay()
                val valid = CalendarRules.dates(value.startDay, value.endDay, value.repeat, value.weekdays, today, today + 180).toSet()
                dao.seriesOccurrences(value.id).filter { it.day >= today && it.status == Status.PENDING && !it.moved }.forEach {
                    dao.updateOccurrence(it.copy(minute = value.startMinute, status = if (it.originalDay in valid) Status.PENDING else Status.CANCELLED))
                }
            }
            generate()
            actualId
        }
        onChanged()
        return result
    }

    suspend fun addSubject(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Escribe el nombre de la materia." }
        return db.withTransaction {
            dao.subjectByName(trimmed)?.id ?: dao.insertSubject(Subject(name = trimmed))
        }
    }

    suspend fun complete(id: Long, studiedSeconds: Long? = null): Occurrence = db.withTransaction {
        val item = requireNotNull(dao.activity(id))
        val old = item.occurrence
        require(old.status != Status.CANCELLED) { "Esta actividad fue eliminada." }
        if (old.status != Status.DONE) {
            val now = System.currentTimeMillis()
            dao.updateOccurrence(old.copy(status = Status.DONE, completedAt = now))
            if (item.schedule.category == Categories.STUDY) dao.insertStudyRecord(StudyRecord(occurrenceId = id, completedAt = now, seconds = studiedSeconds ?: item.schedule.durationMinutes * 60L))
            if (item.schedule.category == Categories.WORKOUT) dao.insertWorkoutRecord(WorkoutRecord(occurrenceId = id, completedAt = now))
            dao.clearTimerFor(id)
        }
        old
    }.also { onChanged() }

    suspend fun restore(old: Occurrence, expectedStatus: String = Status.DONE) {
        db.withTransaction {
            require(dao.occurrence(old.id)?.status == expectedStatus) { "La actividad cambió. Ya no se puede deshacer esta acción." }
            dao.updateOccurrence(old)
            if (old.status != Status.DONE) { dao.deleteStudyRecord(old.id); dao.deleteWorkoutRecord(old.id) }
        }
        onChanged()
    }

    suspend fun cancel(id: Long): Occurrence = db.withTransaction {
        val old = requireNotNull(dao.occurrence(id))
        dao.updateOccurrence(old.copy(status = Status.CANCELLED))
        dao.clearTimerFor(id)
        old
    }.also { onChanged() }

    suspend fun stopSeries(id: Long) {
        db.withTransaction {
            val schedule = requireNotNull(dao.schedule(id))
            dao.saveSchedule(schedule.copy(active = false))
            dao.seriesOccurrences(id).filter { it.status == Status.PENDING && it.day >= LocalDate.now().toEpochDay() }.forEach {
                dao.updateOccurrence(it.copy(status = Status.CANCELLED)); dao.clearTimerFor(it.id)
            }
        }
        onChanged()
    }

    data class MoveReceipt(val before: Occurrence, val after: Occurrence, val historyId: Long)

    suspend fun editOccurrence(id: Long, value: Schedule) {
        require(value.title.isNotBlank() && value.durationMinutes in 1..1440 && value.startMinute in 0..1439) { "Revisa el nombre, la hora y la duración." }
        db.withTransaction {
            val old = requireNotNull(dao.occurrence(id))
            val changedTime = old.day != value.startDay || old.minute != value.startMinute
            if (changedTime) {
                require(old.status == Status.PENDING) { "No se puede cambiar la fecha de una actividad completada." }
                require(CalendarRules.instant(value.startDay, value.startMinute) > System.currentTimeMillis()) { "Elige una fecha y hora futuras." }
                dao.insertHistory(RescheduleHistory(occurrenceId = id, fromDay = old.day, fromMinute = old.minute, toDay = value.startDay, toMinute = value.startMinute, changedAt = System.currentTimeMillis()))
            }
            // An inactive one-off holds this occurrence's overrides without generating another.
            val newId = dao.saveSchedule(value.copy(id = 0, title = value.title.trim(), repeat = Repeat.ONCE.name, active = false, endDay = null))
            dao.updateOccurrence(old.copy(scheduleId = newId, day = value.startDay, minute = value.startMinute, moved = old.moved || changedTime))
            val oldSchedule = requireNotNull(dao.schedule(old.scheduleId))
            if (oldSchedule.repeat != Repeat.ONCE.name && oldSchedule.active) {
                dao.insertOccurrences(listOf(Occurrence(scheduleId = old.scheduleId, originalDay = old.originalDay, originalMinute = old.originalMinute, day = old.originalDay, minute = old.originalMinute, status = Status.CANCELLED)))
            } else if (oldSchedule.active) dao.saveSchedule(oldSchedule.copy(active = false))
        }
        onChanged()
    }

    suspend fun move(id: Long, day: Long, minute: Int): MoveReceipt {
        require(minute in 0..1439) { "Hora inválida." }
        require(CalendarRules.instant(day, minute) > System.currentTimeMillis()) { "Elige una fecha y hora futuras." }
        val receipt = db.withTransaction {
            val item = requireNotNull(dao.activity(id))
            val old = item.occurrence
            require(old.status == Status.PENDING) { "Solo puedes mover actividades pendientes." }
            require(old.day != day || old.minute != minute) { "Elige una fecha u hora diferente." }
            val next = old.copy(day = day, minute = minute, moved = true)
            dao.updateOccurrence(next)
            val historyId = dao.insertHistory(RescheduleHistory(occurrenceId = id, fromDay = old.day, fromMinute = old.minute, toDay = day, toMinute = minute, changedAt = System.currentTimeMillis()))
            dao.timer()?.takeIf { it.occurrenceId == id }?.let { timer ->
                dao.saveTimer(timer.copy(remainingMillis = com.campusflow.app.domain.TimerMath.remaining(timer.remainingMillis, timer.runningSince, System.currentTimeMillis()), runningSince = null))
            }
            MoveReceipt(old, next, historyId)
        }
        onChanged()
        return receipt
    }

    suspend fun undoMove(receipt: MoveReceipt) {
        db.withTransaction {
            require(dao.occurrence(receipt.after.id) == receipt.after) { "La actividad cambió. Ya no se puede deshacer este traslado." }
            dao.updateOccurrence(receipt.before)
            dao.undoHistory(receipt.historyId)
        }
        onChanged()
    }
}
