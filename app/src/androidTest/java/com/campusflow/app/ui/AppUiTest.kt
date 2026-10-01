package com.campusflow.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.campusflow.app.CampusApp
import com.campusflow.app.MainActivity
import com.campusflow.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import com.campusflow.app.domain.pretty

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as CampusApp
    private val ownedSchedules = mutableSetOf<Long>()
    private val ownedRoutines = mutableSetOf<Long>()
    private lateinit var oldAppearance: Appearance
    @Before fun prepare() = runBlocking { oldAppearance = app.preferences.flow.first().appearance }
    @After fun clean() = runBlocking {
        val application = app
        ownedSchedules.forEach { id ->
            application.repository.dao.seriesOccurrences(id).forEach { application.repository.cancel(it.id) }
        }
        application.reminders.reconcile()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ownedSchedules.forEach { application.database.openHelper.writableDatabase.execSQL("DELETE FROM schedules WHERE id = ?", arrayOf(it)) }
            ownedRoutines.forEach { application.database.openHelper.writableDatabase.execSQL("DELETE FROM routines WHERE id = ?", arrayOf(it)) }
        }
        application.preferences.setAppearance(oldAppearance)
    }
    private fun awaitText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitActivity(title: String): ActivityItem {
        var item: ActivityItem? = null
        compose.waitUntil(30_000) { item = runBlocking { app.repository.activities.first().firstOrNull { it.schedule.title == title } }; item != null }
        return item!!
    }
    @Test fun createRecreateCompleteUndoAndMoveFromUi() {
        val title = "Estudio UI ${System.nanoTime()}"
        compose.onNodeWithContentDescription("Agregar").performClick()
        compose.onNodeWithText("Agregar sesión de estudio").performClick()
        compose.onNodeWithText("Nombre").performTextInput(title)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Nombre").assertTextContains(title)
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        val item = awaitActivity(title)
        ownedSchedules += item.schedule.id
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(title))
        awaitText(title)
        capture("activity-flow.png")
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Completar").performScrollTo().performClick()
        awaitText("DESHACER")
        compose.onNodeWithText("DESHACER").performClick()
        compose.waitUntil(10_000) { runBlocking { app.repository.dao.occurrence(item.occurrence.id)?.status == Status.PENDING } }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Posponer / reprogramar").performScrollTo().performClick()
        compose.onNodeWithText("Posponer").performClick()
        compose.waitUntil(10_000) { runBlocking { app.repository.dao.occurrence(item.occurrence.id)?.moved == true } }
        val moved = runBlocking { app.repository.dao.occurrence(item.occurrence.id)!! }
        assertEquals(LocalDate.now().plusDays(1).toEpochDay(), moved.day)
        assertEquals(LocalDate.now().toEpochDay(), moved.originalDay)
        assertEquals(1, runBlocking { app.repository.dao.history(moved.id).first().size })
    }

    @Test fun createRoutineAndAssignWeeklyFromUi() {
        val name = "Rutina UI ${System.nanoTime()}"
        compose.onNodeWithText("Entreno").performClick()
        compose.onNodeWithText("+ Rutina").performClick()
        compose.onNodeWithText("Nombre de la rutina").performTextInput(name)
        compose.onNodeWithText("Ejercicio").performScrollTo().performTextInput("Remo con mochila")
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        awaitText(name)
        val routine = runBlocking { app.repository.routines.first().first { it.routine.name == name } }
        ownedRoutines += routine.routine.id
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar").fetchSemanticsNodes().isEmpty() }
        awaitText("Rutina guardada")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Rutina guardada").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Asignar a días").performScrollTo().performClick()
        try { awaitText("Nueva actividad") } catch (error: Exception) {
            throw AssertionError(compose.onRoot().printToString(), error)
        }
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        val item = awaitActivity(name)
        ownedSchedules += item.schedule.id
        assertEquals("WEEKLY", item.schedule.repeat)
        assertEquals(routine.routine.id, item.schedule.routineId)
        assertEquals("Remo con mochila", routine.exercises.single().name)
    }

    @Test fun themePersistsAndCapturePhoneLayouts() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        listOf(
            Schedule(title = "Programación II", category = Categories.STUDY, startDay = today, startMinute = 960, durationMinutes = 30, reminderMinutes = -1),
            Schedule(title = "Torso A", category = Categories.WORKOUT, startDay = today, startMinute = 1080, durationMinutes = 50, reminderMinutes = -1),
            Schedule(title = "Cálculo y Álgebra Lineal", category = Categories.UNIVERSITY, startDay = today, startMinute = 1020, durationMinutes = 230, reminderMinutes = -1),
        ).forEach { ownedSchedules += app.repository.saveSchedule(it) }
        app.preferences.setAppearance(Appearance.LIGHT)
        awaitText("Programación II")
        capture("home-light.png")
        compose.onNodeWithContentDescription("Configuración").performClick()
        compose.onNodeWithContentDescription("Color dinámico").assertIsToggleable()
        compose.onNodeWithText("Tema", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Oscuro").performClick()
        compose.waitUntil(10_000) { runBlocking { app.preferences.flow.first().appearance == Appearance.DARK } }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Oscuro").assertExists()
        compose.onNodeWithText("Volver").performClick()
        awaitText("Programación II")
        capture("home-dark.png")
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithText("Tu semana").assertExists()
        compose.onNodeWithContentDescription("${LocalDate.now().pretty()}, con actividades").assertIsSelected().assertHasClickAction()
        capture("schedule-dark.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CampusFlowVerification")
        }
        val uri = app.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> app.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
