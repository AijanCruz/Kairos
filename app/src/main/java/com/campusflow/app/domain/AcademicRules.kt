package com.campusflow.app.domain

import com.campusflow.app.data.*

enum class AcademicUrgency { GREEN, YELLOW, RED }

object AcademicRules {
    fun urgency(date: Long, today: Long): AcademicUrgency = when {
        date - today > 14 -> AcademicUrgency.GREEN
        date - today > 7 -> AcademicUrgency.YELLOW
        else -> AcademicUrgency.RED
    }

    fun prepared(item: EvaluationWithTopics): Boolean = item.evaluation.type == EvaluationType.EXAMEN.name &&
        item.topics.isNotEmpty() && item.topics.all { it.status == TopicStatus.DOMINADO.name }

    fun upcoming(items: List<EvaluationWithTopics>, today: Long): List<EvaluationWithTopics> = items
        .filter { !it.evaluation.completed && it.evaluation.date >= today }
        .sortedWith(compareBy<EvaluationWithTopics> { it.evaluation.date }.thenBy { it.evaluation.id })

    fun suggestion(items: List<EvaluationWithTopics>, subjectId: Long?, today: Long): ExamTopic? {
        if (subjectId == null) return null
        val exam = upcoming(items, today).firstOrNull {
            it.evaluation.subjectId == subjectId && it.evaluation.type == EvaluationType.EXAMEN.name
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
