package com.campusflow.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.campusflow.app.CampusApp
import com.campusflow.app.MainActivity
import com.campusflow.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AcademicUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as CampusApp
    private var subjectId = 0L
    private val subjectName = "Cálculo UI ${System.nanoTime()}"
    private val ownedSchedules = mutableSetOf<Long>()
    @Before fun prepare() = runBlocking { subjectId = app.repository.addSubject(subjectName) }
    @After fun clean() = runBlocking {
        val application = app
        ownedSchedules.forEach { id -> application.repository.dao.seriesOccurrences(id).forEach { application.repository.cancel(it.id) } }
        application.reminders.reconcile()
        withContext(Dispatchers.IO) {
            ownedSchedules.forEach { application.database.openHelper.writableDatabase.execSQL("DELETE FROM schedules WHERE id = ?", arrayOf(it)) }
        }
        application.repository.dao.deleteSubject(Subject(subjectId, subjectName))
    }
    private fun awaitText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
    private fun openSubject() {
        compose.onNode(hasText("Estudio") and hasClickAction()).performClick()
        awaitText("Modo enfoque")
        scrollTo("Evaluaciones de $subjectName")
        compose.onNodeWithText("Evaluaciones de $subjectName").performClick()
        awaitText("Evaluaciones")
        scrollTo("+ Evaluación")
    }

    @Test fun createExamRecreateDraftAddTopicPrepareAndCompleteFromHome() {
        openSubject()
        compose.onNodeWithText("+ Evaluación").performClick()
        compose.onNodeWithText("Título").performTextInput("Parcial UI")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Título").assertTextContains("Parcial UI")
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        awaitText("+ Tema")
        compose.onNodeWithText("+ Tema").performScrollTo().performClick()
        compose.onNodeWithText("Nombre del tema").performTextInput("Límites laterales")
        compose.onNodeWithText("Guardar tema").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar tema").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Estado de Límites laterales").performScrollTo().performClick()
        compose.onNodeWithText("Practicando").performClick()
        compose.waitUntil(10_000) { runBlocking { AcademicRepository(app.database).evaluations.first().first { it.evaluation.subjectId == subjectId }.topics.single().status == "PRACTICANDO" } }
        compose.onNodeWithText("Estado de Límites laterales").performClick()
        compose.onNodeWithText("Dominado").performClick()
        compose.waitUntil(10_000) { runBlocking { AcademicRepository(app.database).evaluations.first().first { it.evaluation.subjectId == subjectId }.topics.single().status == "DOMINADO" } }
        compose.onNodeWithText("Inicio").performClick()
        awaitText("Próximas evaluaciones")
        scrollTo("Parcial UI")
        compose.onNodeWithText("Hoy · Preparado").assertExists()
        compose.onNodeWithText("Parcial UI").performClick()
        awaitText("Completar evaluación")
        compose.onNodeWithText("Completar evaluación").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { AcademicRepository(app.database).evaluations.first().first { it.evaluation.subjectId == subjectId }.evaluation.completed } }
        compose.onNodeWithText("Inicio").performClick()
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Parcial UI").fetchSemanticsNodes().isEmpty() }
        } catch (error: AssertionError) { throw AssertionError(compose.onRoot().printToString(), error) }
    }

    @Test fun selectingSubjectSuggestsWithoutCreatingSessionAnd40MinutesReachExistingTimer() = runBlocking {
        val academic = AcademicRepository(app.database)
        val id = academic.saveEvaluation(AcademicEvaluation(subjectId = subjectId, title = "Examen UI", date = LocalDate.now().plusDays(11).toEpochDay()))
        academic.saveTopic(ExamTopic(evaluationId = id, name = "Derivadas", status = "PRACTICANDO"))
        academic.saveTopic(ExamTopic(evaluationId = id, name = "Límites laterales"))
        val before = app.repository.activities.first()
        compose.onNode(hasText("Estudio") and hasClickAction()).performClick()
        awaitText("Modo enfoque")
        scrollTo("Materia para estudiar")
        compose.onNodeWithText("Materia para estudiar").performClick()
        compose.onNode(hasText(subjectName) and hasAnyAncestor(isPopup())).performClick()
        awaitText("Sugerencia: practicar Límites laterales")
        assertEquals(before, app.repository.activities.first())
        scrollTo("Duración de la nueva sesión")
        compose.onNodeWithText("Duración de la nueva sesión").performClick()
        compose.onNodeWithText("40 minutos").performClick()
        compose.onNodeWithText("Planificar estudio").performScrollTo().performClick()
        compose.onNodeWithText("Duración en minutos").assertTextContains("40")
        val title = "Estudiar UI ${System.nanoTime()}"
        compose.onNodeWithText("Nombre").performTextInput(title)
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        var item: ActivityItem? = null
        compose.waitUntil(30_000) {
            item = runBlocking { app.repository.activities.first().firstOrNull { it.schedule.title == title } }
            item != null
        }
        val created = requireNotNull(item)
        ownedSchedules += created.schedule.id
        assertEquals(subjectId, created.schedule.subjectId)
        assertEquals(40, created.schedule.durationMinutes)
        StudyRepository(app.repository).start(created.occurrence.id)
        assertEquals(40 * 60_000L, app.repository.dao.timer()!!.totalMillis)
        StudyRepository(app.repository).discard()
        assertEquals(Status.PENDING, app.repository.dao.occurrence(created.occurrence.id)!!.status)
    }
    @Test fun quizGradesUpdateSubjectSummaryAndBlankGradeIsNotZero() = runBlocking {
        val academic = AcademicRepository(app.database)
        val id = academic.saveEvaluation(AcademicEvaluation(subjectId = subjectId, title = "Quiz con nota", type = "QUIZ", date = LocalDate.now().toEpochDay()))
        academic.saveTopic(ExamTopic(evaluationId = id, name = "Funciones"))
        openSubject()
        scrollTo("Quiz con nota")
        compose.onNodeWithText("Quiz con nota").performClick()
        compose.onNodeWithText("Temas del quiz").assertExists()

        fun editNumbers(percentage: String, grade: String) {
            scrollTo("Quiz con nota")
            compose.onNodeWithContentDescription("Editar evaluación").performScrollTo().performClick()
            compose.onNodeWithText("Porcentaje de la materia (%)").performScrollTo().performTextReplacement(percentage)
            compose.onNodeWithText("Nota obtenida (0–100)").performScrollTo().performTextReplacement(grade)
            compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar").fetchSemanticsNodes().isEmpty() }
        }

        editNumbers("20", "80,5")
        scrollTo("Puntos acumulados: 16,1 / 100")
        compose.onNodeWithText("Promedio ponderado: 80,5 / 100").assertExists()
        compose.onNodeWithText("Porcentaje pendiente: 80 %").assertExists()
        editNumbers("40", "90")
        scrollTo("Puntos acumulados: 36 / 100")
        compose.onNodeWithText("Promedio ponderado: 90 / 100").assertExists()
        compose.onNodeWithText("Porcentaje pendiente: 60 %").assertExists()
        editNumbers("40", "")
        scrollTo("Puntos acumulados: 0 / 100")
        compose.onNodeWithText("Promedio ponderado: Sin calificaciones con peso").assertExists()
        compose.onNodeWithText("Porcentaje pendiente: 100 %").assertExists()
        val stored = app.database.academicDao().evaluation(id)!!
        assertNull(stored.evaluation.grade)
        assertEquals("Funciones", stored.topics.single().name)
        assertEquals("PENDIENTE", stored.topics.single().status)
    }
    @Test fun subjectGoalPersistsAndUpdatesWithGradesAndTopicSummary() = runBlocking {
        val academic = AcademicRepository(app.database)
        val id = academic.saveEvaluation(AcademicEvaluation(subjectId = subjectId, title = "Examen meta", date = LocalDate.now().toEpochDay(), percentage = 40.0, grade = 80.0))
        val quiz = academic.saveEvaluation(AcademicEvaluation(subjectId = subjectId, title = "Quiz meta", type = "QUIZ", date = LocalDate.now().plusDays(1).toEpochDay(), percentage = 60.0))
        TopicStatus.entries.forEach { academic.saveTopic(ExamTopic(evaluationId = quiz, name = it.label, status = it.name)) }
        openSubject()
        scrollTo("Establecer meta final")
        compose.onNodeWithText("Establecer meta final").performClick()
        compose.onNodeWithText("Nota objetivo (0–100)").performTextInput("80")
        compose.onNodeWithText("Guardar meta").performClick()
        awaitText("Meta alcanzable")
        compose.onNodeWithText("Necesitas un promedio de 80 / 100 en el 60 % restante.").assertExists()
        compose.onNodeWithText("Pendientes: 1 · Practicando: 1 · Dominados: 1").assertExists()
        compose.activityRule.scenario.recreate()
        awaitText("Meta final: 80 / 100")
        assertEquals(80.0, AcademicRepository(app.database).goals.first().first { it.subjectId == subjectId }.targetGrade, 0.0)
        val evaluation = app.database.academicDao().evaluation(id)!!.evaluation
        academic.saveEvaluation(evaluation.copy(grade = 20.0))
        awaitText("Meta matemáticamente imposible")
        academic.saveGoal(subjectId, 5.0)
        awaitText("Meta asegurada")
        compose.onNodeWithText("Meta final: 5 / 100").performScrollTo().performClick()
        compose.onNodeWithText("Nota objetivo (0–100)").performTextReplacement("")
        compose.onNodeWithText("Guardar meta").performClick()
        awaitText("Establecer meta final")
        assertTrue(academic.goals.first().none { it.subjectId == subjectId })
    }
}
