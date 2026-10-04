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

    @Test fun onlyExamsAcceptTopicsAndTypeChangeCannotLoseTopics() = runBlocking {
        val task = create("TAREA")
        try { repo.saveTopic(ExamTopic(evaluationId = task, name = "Tema")); fail("Only exams have topics") } catch (_: IllegalArgumentException) { }
        val exam = create()
        repo.saveTopic(ExamTopic(evaluationId = exam, name = "Tema"))
        val value = db.academicDao().evaluation(exam)!!
        try { repo.saveEvaluation(value.evaluation.copy(type = "QUIZ")); fail("Must retain exam topics") } catch (_: IllegalArgumentException) { }
        assertEquals("EXAMEN", db.academicDao().evaluation(exam)!!.evaluation.type)
        assertEquals(1, db.academicDao().evaluation(exam)!!.topics.size)
        repo.deleteTopic(value.topics.single().id)
        repo.saveEvaluation(value.evaluation.copy(type = "QUIZ"))
        assertEquals("QUIZ", db.academicDao().evaluation(exam)!!.evaluation.type)
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
}
