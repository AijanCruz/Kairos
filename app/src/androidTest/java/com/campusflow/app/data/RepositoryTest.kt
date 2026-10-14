package com.campusflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: CampusDatabase
    private lateinit var repo: CampusRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CampusDatabase::class.java).build()
        repo = CampusRepository(db)
        repo.initialize()
    }
    @After fun teardown() { db.close() }
    private suspend fun create(): ActivityItem {
        repo.saveSchedule(Schedule(title = "Programación II", category = Categories.STUDY, startDay = LocalDate.now().toEpochDay(), startMinute = 960, durationMinutes = 30))
        return repo.activities.first().first()
    }
    @Test fun completionIsIdempotentAndUndoRemovesProgress() = runBlocking {
        val item = create()
        val old = repo.complete(item.occurrence.id)
        repo.complete(item.occurrence.id)
        assertEquals(1, repo.dao.studyRecords().first().size)
        repo.restore(old)
        assertTrue(repo.dao.studyRecords().first().isEmpty())
        assertEquals(Status.PENDING, repo.dao.occurrence(old.id)?.status)
    }
    @Test fun cancellationDoesNotRegenerateOccurrence() = runBlocking {
        val item = create()
        repo.cancel(item.occurrence.id)
        repo.generate()
        assertTrue(repo.activities.first().isEmpty())
    }
    @Test fun timerPersistsAndCannotDoubleCount() = runBlocking {
        val item = create()
        val study = StudyRepository(repo)
        study.start(item.occurrence.id)
        assertNotNull(repo.dao.timer()?.runningSince)
        study.pause()
        assertNull(repo.dao.timer()?.runningSince)
        val recreated = StudyRepository(repo)
        recreated.finish()
        recreated.finish()
        assertEquals(1, repo.dao.studyRecords().first().size)
        assertNull(repo.dao.timer())
    }
    @Test fun movingPreservesOriginHistoryAndOtherOccurrences() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        repo.saveSchedule(Schedule(title = "Torso A", category = Categories.WORKOUT, startDay = today, startMinute = 1080, durationMinutes = 50, repeat = "DAILY"))
        val original = repo.activities.first().first()
        val tomorrow = repo.activities.first()[1]
        val receipt = repo.move(original.occurrence.id, today + 2, 1140)
        val moved = repo.dao.occurrence(original.occurrence.id)!!
        assertEquals(today, moved.originalDay)
        assertEquals(today + 2, moved.day)
        assertTrue(moved.moved)
        assertEquals(tomorrow.occurrence, repo.dao.occurrence(tomorrow.occurrence.id))
        assertEquals(1, repo.dao.history(moved.id).first().size)
        repo.undoMove(receipt)
        assertEquals(original.occurrence, repo.dao.occurrence(moved.id))
        assertTrue(repo.dao.history(moved.id).first().first().undone)
    }
    @Test fun moveUndoDoesNotOverwriteLaterCompletion() = runBlocking {
        val original = create()
        val receipt = repo.move(original.occurrence.id, LocalDate.now().plusDays(1).toEpochDay(), 1000)
        repo.complete(original.occurrence.id)
        try { repo.undoMove(receipt); fail("Must reject stale undo") } catch (_: IllegalArgumentException) { }
        assertEquals(Status.DONE, repo.dao.occurrence(original.occurrence.id)?.status)
    }
    @Test fun backupRoundTripPreservesHistoryAndRollsBackInvalidData() = runBlocking {
        val original = create()
        repo.move(original.occurrence.id, LocalDate.now().plusDays(1).toEpochDay(), 1000)
        val backup = DataBackup(db)
        val json = backup.snapshot()
        backup.clear()
        assertTrue(repo.activities.first().isEmpty())
        backup.restore(json)
        assertEquals(1, repo.activities.first().size)
        assertEquals(1, repo.dao.history(original.occurrence.id).first().size)
        val invalid = org.json.JSONObject(json)
        invalid.getJSONObject("data").getJSONArray("occurrences").getJSONObject(0).put("scheduleId", 9999999)
        try { backup.restore(invalid.toString()); fail("Must reject broken relationships") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, repo.activities.first().size)
    }
    @Test fun editingRecurringRulePreservesCompletedAndMovedData() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val id = repo.saveSchedule(Schedule(title = "Original", category = Categories.STUDY, startDay = today, startMinute = 900, durationMinutes = 30, repeat = "DAILY"))
        val items = repo.activities.first()
        repo.complete(items[0].occurrence.id)
        repo.move(items[1].occurrence.id, today + 3, 960)
        val edited = repo.saveSchedule(repo.dao.schedule(id)!!.copy(title = "Editada", startMinute = 1000))
        assertNotEquals(id, edited)
        assertEquals("Original", repo.dao.activity(items[0].occurrence.id)!!.schedule.title)
        assertEquals("Original", repo.dao.activity(items[1].occurrence.id)!!.schedule.title)
        assertEquals(1, repo.activities.first().count { it.occurrence.originalDay == today })
        assertEquals(1, repo.activities.first().count { it.occurrence.originalDay == today + 1 })
        assertTrue(repo.activities.first().any { it.schedule.id == edited && it.schedule.title == "Editada" && it.occurrence.minute == 1000 })
    }
    @Test fun editingTimePausesTimerAndChangingCategoryClearsIt() = runBlocking {
        val item = create()
        StudyRepository(repo).start(item.occurrence.id)
        val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
        repo.editOccurrence(item.occurrence.id, item.schedule.copy(startDay = tomorrow))
        val timer = requireNotNull(repo.dao.timer())
        assertNull(timer.runningSince)
        assertTrue(timer.remainingMillis in 0..timer.totalMillis)
        repo.editOccurrence(item.occurrence.id, item.schedule.copy(startDay = tomorrow, category = Categories.PERSONAL))
        assertNull(repo.dao.timer())
        assertTrue(repo.dao.studyRecords().first().isEmpty())
    }

    @Test fun undoCompletionCannotOverwriteAnEditedActivity() = runBlocking {
        val item = create()
        val before = repo.complete(item.occurrence.id)
        repo.editOccurrence(item.occurrence.id, item.schedule.copy(title = "Título corregido"))
        try { repo.restore(before); fail("Must reject undo after editing") } catch (_: IllegalArgumentException) { }
        assertEquals("Título corregido", repo.dao.activity(before.id)!!.schedule.title)
        assertEquals(Status.DONE, repo.dao.occurrence(before.id)!!.status)
        assertEquals(1, repo.dao.studyRecords().first().size)
    }

    @Test fun staleSeriesEditorCannotCreateASecondActiveVersion() = runBlocking {
        val item = create()
        val id = repo.saveSchedule(item.schedule.copy(id = 0, repeat = "DAILY"))
        val original = repo.dao.schedule(id)!!
        repo.saveSchedule(original.copy(title = "Primera edición"))
        val before = repo.activities.first()
        try { repo.saveSchedule(original.copy(title = "Edición obsoleta")); fail("Must reject stale series") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.activities.first())
    }

    @Test fun invalidBackupDatesAndTimersKeepExistingData() = runBlocking {
        val item = create()
        StudyRepository(repo).start(item.occurrence.id)
        val backup = DataBackup(db)
        val json = backup.snapshot()
        val before = repo.activities.first()
        val timer = repo.dao.timer()
        val invalidCopies = listOf<Pair<String, Pair<String, Any>>>(
            "schedules" to ("startDay" to Long.MAX_VALUE),
            "schedules" to ("repeat" to "UNKNOWN"),
            "occurrences" to ("originalDay" to Long.MAX_VALUE),
            "timers" to ("remainingMillis" to -1L),
            "timers" to ("totalMillis" to 0L),
            "timers" to ("id" to 2),
        )
        for ((table, mutation) in invalidCopies) {
            val invalid = org.json.JSONObject(json)
            invalid.getJSONObject("data").getJSONArray(table).getJSONObject(0).put(mutation.first, mutation.second)
            try { backup.restore(invalid.toString()); fail("Must reject $table.${mutation.first}") } catch (_: IllegalArgumentException) { }
            catch (_: java.time.DateTimeException) { }
            assertEquals(before, repo.activities.first())
            assertEquals(timer, repo.dao.timer())
        }
        val invalidRelation = org.json.JSONObject(json)
        invalidRelation.getJSONObject("data").getJSONArray("occurrences").getJSONObject(0).put("status", Status.DONE)
        try { backup.restore(invalidRelation.toString()); fail("Timer must reference pending study") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.activities.first())
        assertEquals(timer, repo.dao.timer())
    }

    @Test fun concurrentGenerationAndSeriesEditLeaveNoOldFutureOccurrences() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val id = repo.saveSchedule(Schedule(title = "Serie", category = Categories.STUDY, startDay = today, startMinute = 900, durationMinutes = 30, repeat = "DAILY"))
        kotlinx.coroutines.coroutineScope {
            val generation = async { repo.generateRange(today + 181, today + 365) }
            val edit = async { repo.saveSchedule(repo.dao.schedule(id)!!.copy(title = "Nueva")) }
            generation.await()
            edit.await()
        }
        assertTrue(repo.activities.first().none { it.schedule.id == id && it.occurrence.day >= today })
        assertEquals(repo.activities.first().size, repo.activities.first().map { it.occurrence.originalDay }.distinct().size)
    }

    @Test fun weeklyBaseSurvivesOccurrenceChangesAndKeepsOneOffSeparate() = runBlocking {
        val oneOff = create()
        val today = LocalDate.now().toEpochDay()
        val id = repo.saveSchedule(Schedule(title = "Horario base", category = Categories.UNIVERSITY, startDay = today, startMinute = 480, durationMinutes = 60, repeat = "WEEKLY", weekdays = "1,3"))
        val original = repo.recurringSchedules.first().single()
        val items = repo.dao.seriesOccurrences(id).sortedBy { it.day }
        repo.complete(items[0].id)
        repo.cancel(items[1].id)
        repo.move(items[2].id, today + 400, 600)
        repo.generateRange(today + 390, today + 410)
        assertEquals(listOf(original), repo.recurringSchedules.first())
        val replacement = repo.saveSchedule(original.copy(title = "Base editada", startMinute = 540))
        assertEquals(replacement, repo.recurringSchedules.first().single().id)
        assertEquals(Status.DONE, repo.dao.occurrence(items[0].id)!!.status)
        assertTrue(repo.dao.occurrence(items[2].id)!!.moved)
        repo.stopSeries(replacement)
        assertTrue(repo.recurringSchedules.first().isEmpty())
        assertEquals(oneOff, repo.dao.activity(oneOff.occurrence.id))
        assertEquals(Status.DONE, repo.dao.occurrence(items[0].id)!!.status)
    }

    @Test fun reviewedImportIsIdempotentWithoutCollapsingManualClasses() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val day = LocalDate.now().dayOfWeek.value.toString()
        val value = Schedule(title = "Cálculo importado", category = Categories.UNIVERSITY, startDay = today, startMinute = 480, durationMinutes = 60, repeat = "WEEKLY", weekdays = day, reminderMinutes = -1)
        repo.importReviewedSchedules(listOf(value, value))
        val initial = repo.activities.first()
        repo.importReviewedSchedules(listOf(value))
        assertEquals(initial, repo.activities.first())
        assertEquals(1, repo.recurringSchedules.first().size)
        assertEquals(1, com.campusflow.app.domain.HomeRules.classesToday(repo.activities.first(), today))
        repo.saveSchedule(value)
        assertEquals(2, com.campusflow.app.domain.HomeRules.classesToday(repo.activities.first(), today))
        val imported = initial.first()
        repo.editOccurrence(imported.occurrence.id, imported.schedule.copy(title = "Excepción", startDay = imported.occurrence.day))
        repo.generate()
        assertEquals(2, com.campusflow.app.domain.HomeRules.classesToday(repo.activities.first(), today))
        repo.cancel(imported.occurrence.id)
        assertEquals(1, com.campusflow.app.domain.HomeRules.classesToday(repo.activities.first(), today))
    }

    @Test fun batchImportGeneratesOnceAndRollsBackInvalidClass() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        var notifications = 0
        val importer = CampusRepository(db) { notifications++ }
        val value = Schedule(title = "Clase", category = Categories.UNIVERSITY, startDay = today, startMinute = 480, durationMinutes = 60, repeat = "WEEKLY", weekdays = "1")
        importer.importReviewedSchedules(listOf(value, value.copy(title = "Otra clase", startMinute = 600)))
        assertEquals(1, notifications)
        val before = repo.activities.first()
        try { importer.importReviewedSchedules(listOf(value.copy(title = "Nueva"), value.copy(title = "Inválida", durationMinutes = 0))); fail("Invalid import") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.activities.first())
        assertEquals(1, notifications)
    }

    @Test fun editingOneMovedOccurrencePreservesHistoryWithoutRegeneration() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        repo.saveSchedule(Schedule(title = "Rutina original", category = Categories.WORKOUT, startDay = today, startMinute = 900, durationMinutes = 50, repeat = "DAILY"))
        val item = repo.activities.first().first()
        repo.move(item.occurrence.id, today + 1, 1000)
        repo.editOccurrence(item.occurrence.id, item.schedule.copy(title = "Rutina adaptada", startDay = today + 1, startMinute = 1000))
        repo.generate()
        val changed = repo.dao.activity(item.occurrence.id)!!
        assertEquals("Rutina adaptada", changed.schedule.title)
        assertEquals(today, changed.occurrence.originalDay)
        assertEquals(1, repo.dao.history(item.occurrence.id).first().size)
        assertEquals(1, repo.activities.first().count { it.occurrence.originalDay == today })
        assertTrue(repo.activities.first().any { it.schedule.title == "Rutina original" && it.occurrence.originalDay == today + 2 })
    }
}
