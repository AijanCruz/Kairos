package com.campusflow.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademicDao {
    @Transaction
    @Query("SELECT * FROM academic_evaluations ORDER BY date, id")
    fun evaluations(): Flow<List<EvaluationWithTopics>>

    @Transaction
    @Query("SELECT * FROM academic_evaluations WHERE id = :id")
    suspend fun evaluation(id: Long): EvaluationWithTopics?

    @Insert suspend fun insertEvaluation(value: AcademicEvaluation): Long
    @Update suspend fun updateEvaluation(value: AcademicEvaluation)
    @Query("UPDATE academic_evaluations SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)
    @Query("DELETE FROM academic_evaluations WHERE id = :id") suspend fun deleteEvaluation(id: Long)

    @Query("SELECT * FROM exam_topics WHERE id = :id") suspend fun topic(id: Long): ExamTopic?
    @Insert suspend fun insertTopic(value: ExamTopic): Long
    @Update suspend fun updateTopic(value: ExamTopic)
    @Query("UPDATE exam_topics SET status = :status WHERE id = :id") suspend fun setTopicStatus(id: Long, status: String)
    @Query("DELETE FROM exam_topics WHERE id = :id") suspend fun deleteTopic(id: Long)
}
