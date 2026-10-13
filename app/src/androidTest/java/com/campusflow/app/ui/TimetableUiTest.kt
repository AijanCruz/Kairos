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
import androidx.compose.ui.unit.dp

@RunWith(AndroidJUnit4::class)
class TimetableUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as CampusApp
    private val owned = mutableSetOf<Long>()
    @After fun clean() = runBlocking {
        val application = app
        owned.forEach { application.repository.stopSeries(it) }
        application.reminders.reconcile()
        withContext(Dispatchers.IO) {
            owned.forEach { application.database.openHelper.writableDatabase.execSQL("DELETE FROM schedules WHERE id = ?", arrayOf(it)) }
        }
    }
    private fun awaitRule(title: String): Schedule {
        var value: Schedule? = null
        compose.waitUntil(30_000) {
            value = runBlocking { app.repository.recurringSchedules.first().firstOrNull { it.title == title } }
            value != null
        }
        owned += value!!.id
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar").fetchSemanticsNodes().isEmpty() }
        return value!!
    }
    @Test fun addEditAndRemoveWeeklyBlockFromGrid() {
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithTag("timetable-slot-1-480").performScrollTo().performClick()
        compose.onNodeWithText("Nuevo bloque semanal").assertExists()
        val title = "Clase semanal ${System.nanoTime()}"
        compose.onNodeWithText("Nombre").performTextInput(title)
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        val first = awaitRule(title)
        assertEquals("WEEKLY", first.repeat)
        assertEquals("1", first.weekdays)
        assertEquals(480, first.startMinute)
        compose.onNodeWithTag("timetable-block-${first.id}-1-false").performScrollTo().performClick()
        compose.onNodeWithText("Editar bloque semanal").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("$title editada")
        compose.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        val changed = awaitRule("$title editada")
        assertNotEquals(first.id, changed.id)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable-block-${changed.id}-1-false").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("timetable-block-${changed.id}-1-false").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar del horario base").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable-block-${changed.id}-1-false").fetchSemanticsNodes().isEmpty() }
        assertFalse(runBlocking { app.repository.dao.schedule(changed.id)!!.active })
    }

    @Test fun datesStayInAgendaWhileBaseSurvivesWeekNavigation() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val baseId = app.repository.saveSchedule(Schedule(title = "Base permanente UI", category = Categories.UNIVERSITY, startDay = today, startMinute = 480, durationMinutes = 60, repeat = "WEEKLY", weekdays = "1", reminderMinutes = -1))
        val onceId = app.repository.saveSchedule(Schedule(title = "Cita puntual UI", category = Categories.PERSONAL, startDay = today, startMinute = 600, durationMinutes = 30, reminderMinutes = -1))
        owned += baseId; owned += onceId
        compose.onNodeWithText("Horario").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable-block-$baseId-1-false").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cita puntual UI").assertDoesNotExist()
        compose.onNodeWithText("Agenda").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Cita puntual UI"))
        compose.onNodeWithText("Cita puntual UI").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Semana siguiente"))
        compose.onNodeWithContentDescription("Semana siguiente").performClick()
        compose.onNodeWithText("Cita puntual UI").assertDoesNotExist()
        compose.onNodeWithText("Semana").performClick()
        compose.onNodeWithTag("timetable-block-$baseId-1-false").assertExists()
        assertEquals(today, app.repository.dao.schedule(onceId)!!.startDay)
        assertEquals("ONCE", app.repository.dao.schedule(onceId)!!.repeat)
    }
    @Test fun todayAndActivityJumpKeepShortOverlappingBlocksReadableAndEditable() = runBlocking {
        val today = LocalDate.now()
        val shortId = app.repository.saveSchedule(Schedule(title = "Bloque breve UI", category = Categories.UNIVERSITY, startDay = today.toEpochDay(), startMinute = 435, durationMinutes = 5, repeat = "WEEKLY", weekdays = "7", reminderMinutes = -1))
        val overlapId = app.repository.saveSchedule(Schedule(title = "Estudio superpuesto UI", category = Categories.STUDY, startDay = today.toEpochDay(), startMinute = 437, durationMinutes = 3, repeat = "WEEKLY", weekdays = "7", reminderMinutes = -1))
        owned += shortId; owned += overlapId
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithText("Ir a hoy").performClick()
        compose.onNodeWithTag("timetable-day-${today.dayOfWeek.value}").assertIsSelected()
        compose.onNodeWithText("Ir a actividad").performClick()
        compose.onNode(hasText("Bloque breve UI", substring = true) and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("timetable-day-7").assertIsSelected()
        compose.onNodeWithTag("timetable-block-$shortId-7-false").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("timetable-block-$overlapId-7-false").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("timetable-block-$shortId-7-false").performClick()
        compose.onNodeWithText("Editar bloque semanal").assertExists()
        Unit
    }
}
