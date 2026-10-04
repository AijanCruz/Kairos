package com.campusflow.app.data

import androidx.room.*

enum class EvaluationType(val label: String) {
    EXAMEN("Examen"), TAREA("Tarea"), PROYECTO("Proyecto"), QUIZ("Quiz"), EXPOSICION("Exposición")
}

enum class TopicStatus(val label: String) {
    PENDIENTE("Pendiente"), PRACTICANDO("Practicando"), DOMINADO("Dominado")
}

@Entity(tableName = "academic_evaluations", foreignKeys = [
    ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("subjectId"), Index("date")])
data class AcademicEvaluation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val type: String = EvaluationType.EXAMEN.name,
    val date: Long,
    val completed: Boolean = false,
)

@Entity(tableName = "exam_topics", foreignKeys = [
    ForeignKey(entity = AcademicEvaluation::class, parentColumns = ["id"], childColumns = ["evaluationId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("evaluationId")])
data class ExamTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val evaluationId: Long,
    val name: String,
    val status: String = TopicStatus.PENDIENTE.name,
)

data class EvaluationWithTopics(
    @Embedded val evaluation: AcademicEvaluation,
    @Relation(parentColumn = "subjectId", entityColumn = "id") val subject: Subject,
    @Relation(parentColumn = "id", entityColumn = "evaluationId") val topics: List<ExamTopic>,
)
