package com.campusflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
