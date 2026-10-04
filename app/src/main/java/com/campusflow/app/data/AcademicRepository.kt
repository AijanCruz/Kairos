package com.campusflow.app.data

import androidx.room.withTransaction
import java.time.LocalDate

class AcademicRepository(private val db: CampusDatabase) {
    private val dao = db.academicDao()
    val evaluations = dao.evaluations()

    suspend fun saveEvaluation(value: AcademicEvaluation): Long = db.withTransaction {
        require(value.title.isNotBlank()) { "Escribe el título de la evaluación." }
        require(value.type in EvaluationType.entries.map { it.name }) { "Tipo de evaluación inválido." }
        LocalDate.ofEpochDay(value.date)
        if (value.id == 0L) dao.insertEvaluation(value.copy(title = value.title.trim()))
        else {
            val current = requireNotNull(dao.evaluation(value.id)) { "La evaluación ya no existe." }
            require(value.type == EvaluationType.EXAMEN.name || current.topics.isEmpty()) { "Elimina los temas antes de cambiar el tipo del examen." }
            dao.updateEvaluation(value.copy(title = value.title.trim(), completed = current.evaluation.completed))
            value.id
        }
    }

    suspend fun setCompleted(id: Long, completed: Boolean) = dao.setCompleted(id, completed)
    suspend fun deleteEvaluation(id: Long) = dao.deleteEvaluation(id)

    suspend fun saveTopic(value: ExamTopic) = db.withTransaction {
        require(value.name.isNotBlank()) { "Escribe el nombre del tema." }
        require(value.status in TopicStatus.entries.map { it.name }) { "Estado de tema inválido." }
        val evaluation = requireNotNull(dao.evaluation(value.evaluationId)) { "El examen ya no existe." }
        require(evaluation.evaluation.type == EvaluationType.EXAMEN.name) { "Solo los exámenes admiten temas." }
        if (value.id == 0L) dao.insertTopic(value.copy(name = value.name.trim()))
        else {
            val current = requireNotNull(dao.topic(value.id)) { "El tema ya no existe." }
            require(current.evaluationId == value.evaluationId)
            dao.updateTopic(current.copy(name = value.name.trim()))
        }
    }

    suspend fun setTopicStatus(id: Long, status: String) {
        require(status in TopicStatus.entries.map { it.name }) { "Estado de tema inválido." }
        dao.setTopicStatus(id, status)
    }
    suspend fun deleteTopic(id: Long) = dao.deleteTopic(id)
}
