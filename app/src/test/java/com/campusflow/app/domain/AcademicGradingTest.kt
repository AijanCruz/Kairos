package com.campusflow.app.domain

import com.campusflow.app.data.AcademicEvaluation
import org.junit.Assert.*
import org.junit.Test

class AcademicGradingTest {
    private fun evaluation(weight: Double, grade: Double? = null, subject: Long = 1) =
        AcademicEvaluation(subjectId = subject, title = "Evaluación", date = 20000, percentage = weight, grade = grade)

    @Test fun contributionAndWeightedAverageUseOnlyGradedEvaluations() {
        val first = evaluation(60.0, 80.0)
        val summary = AcademicRules.gradeSummary(listOf(first, evaluation(20.0, 90.0), evaluation(20.0)), 1)
        assertEquals(48.0, AcademicRules.contribution(first)!!, 0.000001)
        assertEquals(66.0, summary.accumulatedPoints, 0.000001)
        assertEquals(82.5, summary.weightedAverage!!, 0.000001)
        assertEquals(80.0, summary.gradedPercentage, 0.000001)
        assertEquals(20.0, summary.pendingPercentage, 0.000001)
    }

    @Test fun emptyAndUnweightedGradesHaveNoAverage() {
        listOf(emptyList(), listOf(evaluation(100.0)), listOf(evaluation(0.0, 100.0))).forEach {
            val summary = AcademicRules.gradeSummary(it, 1)
            assertEquals(0.0, summary.accumulatedPoints, 0.0)
            assertEquals(100.0, summary.pendingPercentage, 0.0)
            assertNull(summary.weightedAverage)
        }
    }

    @Test fun zeroIsAGradeButMissingGradeIsNotZero() {
        val zero = AcademicRules.gradeSummary(listOf(evaluation(20.0, 0.0)), 1)
        val missing = AcademicRules.gradeSummary(listOf(evaluation(20.0)), 1)
        assertEquals(0.0, zero.weightedAverage!!, 0.0)
        assertEquals(80.0, zero.pendingPercentage, 0.0)
        assertNull(missing.weightedAverage)
        assertEquals(100.0, missing.pendingPercentage, 0.0)
        assertNull(AcademicRules.contribution(evaluation(20.0)))
    }

    @Test fun calculationsArePerSubjectAndPendingIncludesUnassignedWeight() {
        val summary = AcademicRules.gradeSummary(listOf(evaluation(20.0, 80.0), evaluation(30.0), evaluation(100.0, 100.0, subject = 2)), 1)
        assertEquals(16.0, summary.accumulatedPoints, 0.0)
        assertEquals(80.0, summary.weightedAverage!!, 0.0)
        assertEquals(80.0, summary.pendingPercentage, 0.0)
    }

    @Test fun editingOrClearingGradeRecomputesRatherThanAccumulatingOldResults() {
        val before = evaluation(20.0, 80.0)
        val edited = before.copy(percentage = 40.0, grade = 90.0)
        assertEquals(16.0, AcademicRules.gradeSummary(listOf(before), 1).accumulatedPoints, 0.0)
        assertEquals(36.0, AcademicRules.gradeSummary(listOf(edited), 1).accumulatedPoints, 0.0)
        assertNull(AcademicRules.gradeSummary(listOf(edited.copy(grade = null, completed = true)), 1).weightedAverage)
    }

    @Test fun decimalWeightsAtExactlyOneHundredAreAccepted() {
        AcademicRules.validatePercentageTotal(33.4, listOf(33.3, 33.3))
        AcademicRules.validatePercentageTotal(99.7, listOf(0.1, 0.2))
        val summary = AcademicRules.gradeSummary(listOf(evaluation(33.3, 100.0), evaluation(33.3, 100.0), evaluation(33.4, 100.0)), 1)
        assertEquals(100.0, summary.accumulatedPoints, 0.0)
        assertEquals(0.0, summary.pendingPercentage, 0.0)
        assertThrows(IllegalArgumentException::class.java) { AcademicRules.validatePercentageTotal(33.4001, listOf(33.3, 33.3)) }
    }

    @Test fun invalidValuesAndNonFiniteValuesAreRejected() {
        listOf(-0.01, 100.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { AcademicRules.validateGrading(value, null) }
            assertThrows(IllegalArgumentException::class.java) { AcademicRules.validateGrading(20.0, value) }
        }
        AcademicRules.validateGrading(0.0, 0.0)
        AcademicRules.validateGrading(100.0, 100.0)
        AcademicRules.validateGrading(20.0, null)
    }

    @Test fun decimalCommaAndBlankGradeInputAreHandled() {
        assertEquals(85.5, AcademicRules.parseGradingNumber("85,5")!!, 0.0)
        assertEquals(85.5, AcademicRules.parseGradingNumber("85.5")!!, 0.0)
        listOf("", " ", "NaN", "Infinity", "100,1", "-1", "1,2,3").forEach { assertNull(AcademicRules.parseGradingNumber(it)) }
        assertEquals("16,1", AcademicRules.numberLabel(16.1))
    }
}
