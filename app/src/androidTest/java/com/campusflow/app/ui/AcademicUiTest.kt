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
        awaitText("+ Evaluación")
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
}
