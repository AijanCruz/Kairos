package com.campusflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataBackupTest {
    private lateinit var db: CampusDatabase
    private lateinit var campus: CampusRepository
    private lateinit var academic: AcademicRepository
    private lateinit var backup: DataBackup
    private var subjectId = 0L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CampusDatabase::class.java).build()
        campus = CampusRepository(db)
        campus.initialize()
        subjectId = campus.addSubject("Cálculo")
        academic = AcademicRepository(db)
        backup = DataBackup(db)
    }
    @After fun close() { db.close() }
    private suspend fun seedAcademic(subject: Long = subjectId) {
        val exam = academic.saveEvaluation(AcademicEvaluation(subjectId = subject, title = "Parcial", date = 21000, completed = true, percentage = 20.5, grade = 80.25))
        val quiz = academic.saveEvaluation(AcademicEvaluation(subjectId = subject, title = "Quiz", type = "QUIZ", date = 21001, percentage = 79.5))
        academic.saveTopic(ExamTopic(evaluationId = exam, name = "Límites", status = "DOMINADO"))
        academic.saveTopic(ExamTopic(evaluationId = quiz, name = "Derivadas", status = "PRACTICANDO"))
        academic.saveTopic(ExamTopic(evaluationId = quiz, name = "Integrales"))
        academic.saveGoal(subject, 85.5)
    }
    private suspend fun data() = JSONObject(backup.snapshot()).getJSONObject("data").toString()
    private suspend fun legacySnapshot(): String = JSONObject(backup.snapshot()).apply {
        put("version", 1); put("databaseVersion", 2)
        listOf("academic_evaluations", "exam_topics", "subject_goals").forEach { getJSONObject("data").remove(it) }
    }.toString()
    private suspend fun assertRejected(json: String) {
        val before = data()
        try { backup.restore(json); fail("Invalid restore must fail") } catch (_: Exception) { }
        assertEquals(before, data())
    }

    @Test fun fullRoundTripPreservesAcademicRelationsDecimalGradesAndGoals() = runBlocking {
        seedAcademic()
        val snapshot = backup.snapshot()
        val before = data()
        backup.clear()
        assertTrue(academic.evaluations.first().isEmpty())
        assertTrue(academic.goals.first().isEmpty())
        backup.restore(snapshot)
        assertEquals(before, data())
        val rows = academic.evaluations.first()
        assertEquals(2, rows.size)
        assertEquals(80.25, rows.first().evaluation.grade!!, 0.0)
        assertEquals(20.5, rows.first().evaluation.percentage, 0.0)
        assertNull(rows.last().evaluation.grade)
        assertEquals(setOf("DOMINADO", "PRACTICANDO", "PENDIENTE"), rows.flatMap { it.topics }.map { it.status }.toSet())
        assertTrue(rows.all { it.subject.id == subjectId })
        assertEquals(85.5, academic.goals.first().single().targetGrade, 0.0)
    }

    @Test fun originalBackupRestoresIntoEmptyDatabase() = runBlocking {
        val old = legacySnapshot()
        backup.clear()
        backup.restore(old)
        assertEquals("Cálculo", campus.subjects.first().single().name)
        assertTrue(academic.evaluations.first().isEmpty())
        assertTrue(academic.goals.first().isEmpty())
    }

    @Test fun legacyRestoreKeepsCurrentAcademicData() = runBlocking {
        val old = legacySnapshot()
        seedAcademic()
        val before = academic.evaluations.first()
        val goals = academic.goals.first()
        backup.restore(old)
        assertEquals(before, academic.evaluations.first())
        assertEquals(goals, academic.goals.first())
    }

    @Test fun legacyRestoreRetainsProtectedSubjectsMissingFromTheOldCopy() = runBlocking {
        val old = legacySnapshot()
        val extra = campus.addSubject("Física")
        seedAcademic(extra)
        val goalOnly = campus.addSubject("Álgebra")
        academic.saveGoal(goalOnly, 80.0)
        val before = academic.evaluations.first()
        backup.restore(old)
        assertEquals(before, academic.evaluations.first())
        assertEquals(setOf(subjectId, extra, goalOnly), campus.subjects.first().map { it.id }.toSet())
        assertEquals(2, academic.goals.first().size)
    }

    @Test fun legacySubjectIdOrNameConflictNeverOverwritesAcademicData() = runBlocking {
        seedAcademic()
        val old = legacySnapshot()
        val differentId = JSONObject(old)
        differentId.getJSONObject("data").getJSONArray("subjects").getJSONObject(0).put("id", 999)
        assertRejected(differentId.toString())
        campus.dao.updateSubject(Subject(subjectId, "Cálculo avanzado"))
        assertRejected(old)
    }

    @Test fun invalidRelationshipsRollbackAllTables() = runBlocking {
        seedAcademic()
        val json = backup.snapshot()
        for ((table, key) in listOf("academic_evaluations" to "subjectId", "exam_topics" to "evaluationId", "subject_goals" to "subjectId")) {
            val invalid = JSONObject(json)
            invalid.getJSONObject("data").getJSONArray(table).getJSONObject(0).put(key, 999999)
            assertRejected(invalid.toString())
        }
    }

    @Test fun invalidAcademicValuesAndTotalsAreRejectedAtomically() = runBlocking {
        seedAcademic()
        val json = backup.snapshot()
        val changes = listOf(
            Triple("academic_evaluations", "percentage", 21.0), // 21 + 79.5 > 100
            Triple("academic_evaluations", "grade", 100.1),
            Triple("academic_evaluations", "grade", "80.25"),
            Triple("academic_evaluations", "type", "TAREA"), // still has a topic
            Triple("academic_evaluations", "completed", 2),
            Triple("exam_topics", "status", "UNKNOWN"),
            Triple("subject_goals", "targetGrade", -1),
            Triple("subject_goals", "targetGrade", JSONObject.NULL),
            Triple("subjects", "id", 1.5),
        )
        changes.forEach { (table, column, value) ->
            val invalid = JSONObject(json)
            invalid.getJSONObject("data").getJSONArray(table).getJSONObject(0).put(column, value)
            assertRejected(invalid.toString())
        }
    }

    @Test fun duplicateIdsRollBackAfterInsertsHaveStarted() = runBlocking {
        seedAcademic()
        val invalid = JSONObject(backup.snapshot())
        val rows = invalid.getJSONObject("data").getJSONArray("academic_evaluations")
        rows.put(JSONObject(rows.getJSONObject(0).toString()).put("percentage", 0))
        assertRejected(invalid.toString())
    }

    @Test fun missingAcademicTablesOrUnsupportedVersionsNeverDiscardData() = runBlocking {
        seedAcademic()
        val json = backup.snapshot()
        val missing = JSONObject(json)
        missing.getJSONObject("data").remove("exam_topics")
        assertRejected(missing.toString())
        assertRejected(JSONObject(json).put("version", 99).toString())
        assertRejected(JSONObject(json).put("version", 1).put("databaseVersion", 2).toString())
    }
}
