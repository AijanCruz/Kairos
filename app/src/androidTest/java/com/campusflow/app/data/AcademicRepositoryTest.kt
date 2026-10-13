package com.campusflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import com.campusflow.app.domain.AcademicRules
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AcademicRepositoryTest {
    private lateinit var db: CampusDatabase
    private lateinit var repo: AcademicRepository
    private lateinit var campus: CampusRepository
    private var subjectId = 0L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CampusDatabase::class.java).build()
        campus = CampusRepository(db)
        campus.initialize()
        subjectId = campus.addSubject("Cálculo")
        repo = AcademicRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun create(type: String = "EXAMEN") = repo.saveEvaluation(AcademicEvaluation(subjectId = subjectId, title = "Parcial 1", type = type, date = LocalDate.now().plusDays(11).toEpochDay()))

    @Test fun evaluationAndTopicsPersistWithoutCreatingActivitiesOrTimers() = runBlocking {
        val id = create()
        repo.saveTopic(ExamTopic(evaluationId = id, name = " Límites laterales "))
        val first = repo.evaluations.first().single()
        assertEquals("Cálculo", first.subject.name)
        assertEquals("Límites laterales", first.topics.single().name)
        repo.setTopicStatus(first.topics.single().id, "PRACTICANDO")
        repo.saveTopic(first.topics.single().copy(name = "Límites"))
        repo.setCompleted(id, true)
        repo.saveEvaluation(first.evaluation.copy(title = "Parcial corregido"))
        val recreated = AcademicRepository(db).evaluations.first().single()
        assertTrue(recreated.evaluation.completed)
        assertEquals("Parcial corregido", recreated.evaluation.title)
        assertEquals("PRACTICANDO", recreated.topics.single().status)
        assertEquals("Límites", recreated.topics.single().name)
        assertTrue(campus.activities.first().isEmpty())
        assertNull(campus.dao.timer())
        repo.setCompleted(id, false)
        assertFalse(repo.evaluations.first().single().evaluation.completed)
    }

    @Test fun nonStudyTypesCannotHaveTopicsOrLoseExistingTopics() = runBlocking {
        val task = create("TAREA")
        try { repo.saveTopic(ExamTopic(evaluationId = task, name = "Tema")); fail("Only exams have topics") } catch (_: IllegalArgumentException) { }
        val exam = create()
        repo.saveTopic(ExamTopic(evaluationId = exam, name = "Tema"))
        val value = db.academicDao().evaluation(exam)!!
        try { repo.saveEvaluation(value.evaluation.copy(type = "TAREA")); fail("Must retain exam topics") } catch (_: IllegalArgumentException) { }
        assertEquals("EXAMEN", db.academicDao().evaluation(exam)!!.evaluation.type)
        assertEquals(1, db.academicDao().evaluation(exam)!!.topics.size)
        repo.deleteTopic(value.topics.single().id)
        repo.saveEvaluation(value.evaluation.copy(type = "TAREA"))
        assertEquals("TAREA", db.academicDao().evaluation(exam)!!.evaluation.type)
    }

    @Test fun invalidInputsAreRejectedWithoutRows() = runBlocking {
        for (type in listOf("", "OTRO")) {
            try { create(type); fail("Unknown type") } catch (_: IllegalArgumentException) { }
        }
        try { repo.saveEvaluation(AcademicEvaluation(subjectId = subjectId, title = " ", date = 20000)); fail("Blank title") } catch (_: IllegalArgumentException) { }
        assertTrue(repo.evaluations.first().isEmpty())
        val id = create()
        try { repo.saveTopic(ExamTopic(evaluationId = id, name = " ")); fail("Blank topic") } catch (_: IllegalArgumentException) { }
        try { repo.saveTopic(ExamTopic(evaluationId = id, name = "Tema", status = "UNKNOWN")); fail("Unknown status") } catch (_: IllegalArgumentException) { }
        assertTrue(repo.evaluations.first().single().topics.isEmpty())
    }

    @Test fun deletingSubjectCascadesAcademicRowsButKeepsSchedule() = runBlocking {
        val id = create()
        repo.saveTopic(ExamTopic(evaluationId = id, name = "Tema"))
        val topicId = repo.evaluations.first().single().topics.single().id
        campus.saveSchedule(Schedule(title = "Clase", category = Categories.UNIVERSITY, startDay = LocalDate.now().toEpochDay(), startMinute = 480, durationMinutes = 60, subjectId = subjectId))
        campus.dao.deleteSubject(Subject(subjectId, "Cálculo"))
        assertTrue(repo.evaluations.first().isEmpty())
        assertNull(db.academicDao().topic(topicId))
        assertNull(campus.activities.first().single().schedule.subjectId)
    }

    @Test fun deletingEvaluationKeepsSubjectAndRemovesTopics() = runBlocking {
        val id = create()
        repo.saveTopic(ExamTopic(evaluationId = id, name = "Tema"))
        val before = repo.evaluations.first().single()
        repo.deleteEvaluation(id)
        assertTrue(repo.evaluations.first().isEmpty())
        assertNull(db.academicDao().topic(before.topics.single().id))
        assertEquals(subjectId, campus.subjects.first().single().id)
        try { repo.saveEvaluation(before.evaluation); fail("Must not recreate deleted evaluation") } catch (_: IllegalArgumentException) { }
    }
    @Test fun switchingExamAndQuizPreservesAllTopicIdsAndStates() = runBlocking {
        val id = create("QUIZ")
        TopicStatus.entries.forEach { repo.saveTopic(ExamTopic(evaluationId = id, name = it.label, status = it.name)) }
        val before = db.academicDao().evaluation(id)!!
        for (type in listOf("EXAMEN", "QUIZ")) {
            repo.saveEvaluation(before.evaluation.copy(type = type))
            val after = db.academicDao().evaluation(id)!!
            assertEquals(type, after.evaluation.type)
            assertEquals(before.topics.sortedBy { it.id }, after.topics.sortedBy { it.id })
        }
    }

    @Test fun percentageCapAppliesOnInsertAndEditExcludingCurrentRow() = runBlocking {
        val firstId = create()
        val first = db.academicDao().evaluation(firstId)!!.evaluation.copy(percentage = 60.0)
        repo.saveEvaluation(first)
        val secondId = repo.saveEvaluation(first.copy(id = 0, percentage = 40.0))
        repo.saveEvaluation(first.copy(title = "Editada"))
        try { repo.saveEvaluation(first.copy(percentage = 60.01)); fail("Total must not exceed 100") } catch (_: IllegalArgumentException) { }
        try { repo.saveEvaluation(first.copy(id = 0, percentage = 1.0)); fail("Total must not exceed 100") } catch (_: IllegalArgumentException) { }
        assertEquals(60.0, db.academicDao().evaluation(firstId)!!.evaluation.percentage, 0.0)
        assertEquals(2, repo.evaluations.first().size)
        repo.deleteEvaluation(secondId)
        repo.saveEvaluation(first.copy(percentage = 100.0))
        assertEquals(100.0, repo.evaluations.first().single().evaluation.percentage, 0.0)
    }

    @Test fun percentagesAreIndependentPerSubjectAndDecimalSafe() = runBlocking {
        val prototype = AcademicEvaluation(subjectId = subjectId, title = "Parcial", date = 20000)
        listOf(33.3, 33.3, 33.4).forEach { repo.saveEvaluation(prototype.copy(percentage = it)) }
        val other = campus.addSubject("Física")
        val otherId = repo.saveEvaluation(prototype.copy(subjectId = other, percentage = 100.0))
        try { repo.saveEvaluation(prototype.copy(id = otherId, percentage = 1.0)); fail("Moving must respect destination cap") } catch (_: IllegalArgumentException) { }
        assertEquals(other, db.academicDao().evaluation(otherId)!!.evaluation.subjectId)
    }

    @Test fun concurrentSavesCannotExceedOneHundred() = runBlocking {
        val prototype = AcademicEvaluation(subjectId = subjectId, title = "Parcial", date = 20000, percentage = 60.0)
        val results = (1..2).map { async(Dispatchers.Default) { runCatching { repo.saveEvaluation(prototype) } } }.map { it.await() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is IllegalArgumentException })
        assertEquals(60.0, repo.evaluations.first().sumOf { it.evaluation.percentage }, 0.0)
    }

    @Test fun gradesPersistValidateAndCanBeClearedWithoutCompletingActivity() = runBlocking {
        val id = create()
        val value = db.academicDao().evaluation(id)!!.evaluation.copy(percentage = 20.0, grade = 80.5)
        repo.saveEvaluation(value)
        val stored = AcademicRepository(db).evaluations.first().single().evaluation
        assertEquals(80.5, stored.grade!!, 0.0)
        assertEquals(16.1, AcademicRules.gradeSummary(listOf(stored), subjectId).accumulatedPoints, 0.000001)
        for (invalid in listOf(-1.0, 100.1, Double.NaN)) {
            try { repo.saveEvaluation(value.copy(grade = invalid)); fail("Invalid grade") } catch (_: IllegalArgumentException) { }
        }
        repo.saveEvaluation(value.copy(grade = null))
        val cleared = repo.evaluations.first().single().evaluation
        assertNull(cleared.grade)
        assertFalse(cleared.completed)
        assertNull(AcademicRules.gradeSummary(listOf(cleared), subjectId).weightedAverage)
    }
    @Test fun goalPersistsThroughSubjectRenameAndCanBeRemoved() = runBlocking {
        repo.saveGoal(subjectId, 80.5)
        campus.dao.updateSubject(Subject(subjectId, "Cálculo avanzado"))
        assertEquals(SubjectGoal(subjectId, 80.5), AcademicRepository(db).goals.first().single())
        repo.saveGoal(subjectId, null)
        assertTrue(repo.goals.first().isEmpty())
        assertEquals("Cálculo avanzado", campus.subjects.first().single().name)
    }
    @Test fun invalidGoalKeepsPreviousValueAndSubjectDeletionRemovesIt() = runBlocking {
        repo.saveGoal(subjectId, 80.0)
        for (value in listOf(-1.0, 101.0, Double.NaN)) {
            try { repo.saveGoal(subjectId, value); fail("Invalid target") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(80.0, repo.goals.first().single().targetGrade, 0.0)
        campus.dao.deleteSubject(Subject(subjectId, "Cálculo"))
        assertTrue(repo.goals.first().isEmpty())
    }
}
