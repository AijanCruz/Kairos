package com.campusflow.app.domain

import com.campusflow.app.data.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class AcademicUrgency { GREEN, YELLOW, RED }

data class AcademicGradeSummary(
    val accumulatedPoints: Double,
    val weightedAverage: Double?,
    val gradedPercentage: Double,
    val pendingPercentage: Double,
)

enum class GradeGoalStatus { SECURED, REACHABLE, IMPOSSIBLE }
data class GradeGoalResult(val status: GradeGoalStatus, val requiredAverage: Double?, val unassignedPercentage: Double)
data class TopicSummary(val pending: Int, val practicing: Int, val mastered: Int)

object AcademicRules {
    fun supportsTopics(type: String): Boolean = type == EvaluationType.EXAMEN.name || type == EvaluationType.QUIZ.name

    fun validateGrading(percentage: Double, grade: Double?) {
        require(percentage.isFinite() && percentage in 0.0..100.0) { "El porcentaje debe estar entre 0 y 100." }
        require(grade == null || (grade.isFinite() && grade in 0.0..100.0)) { "La nota debe estar entre 0 y 100, o quedar vacía." }
    }

    fun validatePercentageTotal(percentage: Double, others: List<Double>) {
        validateGrading(percentage, null)
        val total = others.fold(BigDecimal.valueOf(percentage)) { sum, value -> sum + BigDecimal.valueOf(value) }
        require(total <= BigDecimal.valueOf(100)) { "Los porcentajes de esta materia no pueden superar el 100 %." }
    }

    fun contribution(evaluation: AcademicEvaluation): Double? = evaluation.grade?.let {
        (BigDecimal.valueOf(it) * BigDecimal.valueOf(evaluation.percentage)).movePointLeft(2).toDouble()
    }

    fun gradeSummary(evaluations: List<AcademicEvaluation>, subjectId: Long): AcademicGradeSummary {
        var points = BigDecimal.ZERO
        var graded = BigDecimal.ZERO
        evaluations.filter { it.subjectId == subjectId && it.grade != null }.forEach {
            val percentage = BigDecimal.valueOf(it.percentage)
            graded += percentage
            points += (BigDecimal.valueOf(it.grade!!) * percentage).movePointLeft(2)
        }
        val average = if (graded.signum() == 0) null else points.movePointRight(2).divide(graded, MathContext.DECIMAL64).toDouble()
        return AcademicGradeSummary(points.toDouble(), average, graded.toDouble(), (BigDecimal.valueOf(100) - graded).toDouble())
    }

    fun goalResult(evaluations: List<AcademicEvaluation>, subjectId: Long, target: Double): GradeGoalResult {
        validateGrading(0.0, target)
        val summary = gradeSummary(evaluations, subjectId)
        val assigned = evaluations.filter { it.subjectId == subjectId }.fold(BigDecimal.ZERO) { sum, item -> sum + BigDecimal.valueOf(item.percentage) }
        val unassigned = (BigDecimal.valueOf(100) - assigned).toDouble()
        val needed = BigDecimal.valueOf(target) - BigDecimal.valueOf(summary.accumulatedPoints)
        val remaining = BigDecimal.valueOf(summary.pendingPercentage)
        if (needed.signum() <= 0) return GradeGoalResult(GradeGoalStatus.SECURED, null, unassigned)
        if (remaining.signum() == 0) return GradeGoalResult(GradeGoalStatus.IMPOSSIBLE, null, unassigned)
        val required = needed.movePointRight(2).divide(remaining, MathContext.DECIMAL64).toDouble()
        return GradeGoalResult(if (needed > remaining) GradeGoalStatus.IMPOSSIBLE else GradeGoalStatus.REACHABLE, required, unassigned)
    }

    fun requiredGradeLabel(value: Double): String = BigDecimal.valueOf(value).setScale(2, RoundingMode.CEILING)
        .stripTrailingZeros().toPlainString().replace('.', ',')

    fun topicSummary(items: List<EvaluationWithTopics>, subjectId: Long): TopicSummary {
        val topics = items.filter { it.evaluation.subjectId == subjectId && supportsTopics(it.evaluation.type) }.flatMap { it.topics }
        return TopicSummary(topics.count { it.status == TopicStatus.PENDIENTE.name }, topics.count { it.status == TopicStatus.PRACTICANDO.name }, topics.count { it.status == TopicStatus.DOMINADO.name })
    }

    fun parseGradingNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 0.0..100.0 }

    fun numberLabel(value: Double): String = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString().replace('.', ',')

    fun urgency(date: Long, today: Long): AcademicUrgency = when {
        date - today > 14 -> AcademicUrgency.GREEN
        date - today > 7 -> AcademicUrgency.YELLOW
        else -> AcademicUrgency.RED
    }

    fun prepared(item: EvaluationWithTopics): Boolean = supportsTopics(item.evaluation.type) &&
        item.topics.isNotEmpty() && item.topics.all { it.status == TopicStatus.DOMINADO.name }

    fun upcoming(items: List<EvaluationWithTopics>, today: Long): List<EvaluationWithTopics> = items
        .filter { !it.evaluation.completed && it.evaluation.date >= today }
        .sortedWith(compareBy<EvaluationWithTopics> { it.evaluation.date }.thenBy { it.evaluation.id })

    fun suggestion(items: List<EvaluationWithTopics>, subjectId: Long?, today: Long): ExamTopic? {
        if (subjectId == null) return null
        val exam = upcoming(items, today).firstOrNull {
            it.evaluation.subjectId == subjectId && supportsTopics(it.evaluation.type)
        } ?: return null
        val topics = exam.topics.sortedBy { it.id }
        return topics.firstOrNull { it.status == TopicStatus.PENDIENTE.name }
            ?: topics.firstOrNull { it.status == TopicStatus.PRACTICANDO.name }
    }

    fun dateLabel(date: Long, today: Long): String = when (val days = date - today) {
        0L -> "Hoy"
        1L -> "1 día"
        in 2..Long.MAX_VALUE -> "$days días"
        -1L -> "Venció hace 1 día"
        else -> "Venció hace ${-days} días"
    }
}
