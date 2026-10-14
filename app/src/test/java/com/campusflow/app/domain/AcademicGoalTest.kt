package com.campusflow.app.domain

import com.campusflow.app.data.*
import org.junit.Assert.*
import org.junit.Test

class AcademicGoalTest {
    private fun evaluation(weight: Double, grade: Double?, subjectId: Long = 1) =
        AcademicEvaluation(subjectId = subjectId, title = "Evaluación", date = 21000, percentage = weight, grade = grade)

    @Test fun securedGoalNeedsNoFurtherGrade() {
        val result = AcademicRules.goalResult(listOf(evaluation(80.0, 100.0), evaluation(20.0, null)), 1, 80.0)
        assertEquals(GradeGoalStatus.SECURED, result.status)
        assertNull(result.requiredAverage)
    }
    @Test fun reachableGoalUsesAccumulatedPointsNotCurrentAverage() {
        val rows = listOf(evaluation(40.0, 80.0), evaluation(60.0, null))
        val summary = AcademicRules.gradeSummary(rows, 1)
        assertEquals(32.0, summary.accumulatedPoints, 0.0)
        assertEquals(80.0, summary.weightedAverage!!, 0.0)
        val result = AcademicRules.goalResult(rows, 1, 80.0)
        assertEquals(GradeGoalStatus.REACHABLE, result.status)
        assertEquals(80.0, result.requiredAverage!!, 0.0)
    }
    @Test fun goalAboveMaximumPossibleIsImpossible() {
        val result = AcademicRules.goalResult(listOf(evaluation(80.0, 20.0), evaluation(20.0, null)), 1, 80.0)
        assertEquals(GradeGoalStatus.IMPOSSIBLE, result.status)
        assertTrue(result.requiredAverage!! > 100)
    }
    @Test fun fullyGradedCourseDoesNotDivideByZero() {
        val rows = listOf(evaluation(100.0, 75.0))
        assertEquals(GradeGoalStatus.SECURED, AcademicRules.goalResult(rows, 1, 75.0).status)
        val impossible = AcademicRules.goalResult(rows, 1, 80.0)
        assertEquals(GradeGoalStatus.IMPOSSIBLE, impossible.status)
        assertNull(impossible.requiredAverage)
    }
    @Test fun unknownPercentagesAreExplicitAndOtherSubjectsExcluded() {
        val result = AcademicRules.goalResult(listOf(evaluation(20.0, 100.0), evaluation(20.0, null), evaluation(100.0, 0.0, 2)), 1, 80.0)
        assertEquals(60.0, result.unassignedPercentage, 0.0)
        assertEquals(75.0, result.requiredAverage!!, 0.0)
        assertEquals(GradeGoalStatus.REACHABLE, result.status)
        assertEquals(80.0, AcademicRules.goalResult(emptyList(), 1, 80.0).requiredAverage!!, 0.0)
    }
    @Test fun requiredDisplayedGradeIsRoundedUpAndBoundaryOneHundredIsReachable() {
        assertEquals("83,34", AcademicRules.requiredGradeLabel(83.333333))
        assertEquals(GradeGoalStatus.REACHABLE, AcademicRules.goalResult(listOf(evaluation(20.0, 0.0)), 1, 80.0).status)
        assertEquals(GradeGoalStatus.SECURED, AcademicRules.goalResult(emptyList(), 1, 0.0).status)
    }
    @Test fun topicSummaryCountsAllExamAndQuizStatesForTheSelectedSubject() {
        fun item(id: Long, subject: Long, type: String, states: List<String>) = EvaluationWithTopics(
            evaluation(0.0, null, subject).copy(id = id, type = type), Subject(subject, "Materia"),
            states.mapIndexed { index, state -> ExamTopic(index + 1L, id, "Tema", state) },
        )
        val items = listOf(item(1, 1, "EXAMEN", listOf("PENDIENTE", "DOMINADO")), item(2, 1, "QUIZ", listOf("PRACTICANDO", "PENDIENTE")), item(3, 2, "EXAMEN", listOf("PENDIENTE")))
        assertEquals(TopicSummary(2, 1, 1), AcademicRules.topicSummary(items, 1))
        assertEquals(TopicSummary(0, 0, 0), AcademicRules.topicSummary(items, 7))
    }
}
