package com.campusflow.app.domain

import com.campusflow.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AcademicRulesTest {
    private val today = LocalDate.of(2026, 12, 28).toEpochDay()
    private fun exam(id: Long = 1, days: Long = 2, subject: Long = 1, completed: Boolean = false, vararg states: String) = EvaluationWithTopics(
        AcademicEvaluation(id, subject, "Examen $id", date = today + days, completed = completed),
        Subject(subject, "Materia $subject"),
        states.mapIndexed { index, state -> ExamTopic(index + 1L, id, "Tema $index", state) },
    )

    @Test fun trafficLightBoundariesAreUnambiguous() {
        assertEquals(AcademicUrgency.GREEN, AcademicRules.urgency(today + 15, today))
        assertEquals(AcademicUrgency.YELLOW, AcademicRules.urgency(today + 14, today))
        assertEquals(AcademicUrgency.YELLOW, AcademicRules.urgency(today + 8, today))
        listOf(7L, 1L, 0L, -1L).forEach { assertEquals(AcademicUrgency.RED, AcademicRules.urgency(today + it, today)) }
    }

    @Test fun preparedRequiresAtLeastOneTopicAndAllMastered() {
        assertFalse(AcademicRules.prepared(exam()))
        assertFalse(AcademicRules.prepared(exam(states = arrayOf("DOMINADO", "PRACTICANDO"))))
        val ready = exam(states = arrayOf("DOMINADO", "DOMINADO"))
        assertTrue(AcademicRules.prepared(ready))
        assertEquals(AcademicUrgency.RED, AcademicRules.urgency(ready.evaluation.date, today))
        assertFalse(AcademicRules.prepared(ready.copy(evaluation = ready.evaluation.copy(type = "TAREA"))))
    }

    @Test fun upcomingSkipsCompletedAndExpiredAndSortsAcrossYear() {
        val items = listOf(exam(3, 15), exam(2, 5), exam(1, 0), exam(4, -1), exam(5, 1, completed = true))
        assertEquals(listOf(1L, 2L, 3L), AcademicRules.upcoming(items, today).map { it.evaluation.id })
        assertTrue(AcademicRules.upcoming(emptyList(), today).isEmpty())
    }

    @Test fun suggestionUsesNearestExamBeforeTopicPriority() {
        val near = exam(1, 2, states = arrayOf("PRACTICANDO"))
        val far = exam(2, 3, states = arrayOf("PENDIENTE"))
        assertEquals(near.topics.single(), AcademicRules.suggestion(listOf(far, near), 1, today))
    }

    @Test fun suggestionPrefersPendingThenPracticingNeverMastered() {
        val item = exam(states = arrayOf("DOMINADO", "PRACTICANDO", "PENDIENTE"))
        assertEquals(item.topics[2], AcademicRules.suggestion(listOf(item), 1, today))
        assertEquals(item.topics[1], AcademicRules.suggestion(listOf(item.copy(topics = item.topics.take(2))), 1, today))
        assertNull(AcademicRules.suggestion(listOf(item.copy(topics = item.topics.take(1))), 1, today))
    }

    @Test fun suggestionIgnoresOtherSubjectsCompletedExpiredAndOtherTypes() {
        val other = exam(1, 0, subject = 2, states = arrayOf("PENDIENTE"))
        val completed = exam(2, 0, completed = true, states = arrayOf("PENDIENTE"))
        val expired = exam(3, -1, states = arrayOf("PENDIENTE"))
        val task = exam(4, 0, states = arrayOf("PENDIENTE")).let { it.copy(evaluation = it.evaluation.copy(type = "TAREA")) }
        val target = exam(5, 4, states = arrayOf("PRACTICANDO"))
        val items = listOf(other, completed, expired, task, target)
        assertEquals(target.topics.single(), AcademicRules.suggestion(items, 1, today))
        assertNull(AcademicRules.suggestion(items, null, today))
        assertNull(AcademicRules.suggestion(items, 9, today))
    }

    @Test fun nearestPreparedExamDoesNotSuggestAMasteredTopicOrSwitchExam() {
        assertNull(AcademicRules.suggestion(listOf(exam(1, 1, states = arrayOf("DOMINADO")), exam(2, 2, states = arrayOf("PENDIENTE"))), 1, today))
        assertNull(AcademicRules.suggestion(listOf(exam()), 1, today))
    }

    @Test fun daysAreCalendarDatesAndUpdateAtMidnight() {
        val deadline = today + 8
        assertEquals("8 días", AcademicRules.dateLabel(deadline, today))
        assertEquals(AcademicUrgency.RED, AcademicRules.urgency(deadline, today + 1))
        assertEquals("Hoy", AcademicRules.dateLabel(deadline, deadline))
        assertEquals("Venció hace 1 día", AcademicRules.dateLabel(deadline, deadline + 1))
    }
}
