package com.campusflow.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CampusDao {
    @Query("SELECT * FROM reminder_states") suspend fun reminderStates(): List<ReminderState>
    @Upsert suspend fun saveReminderState(value: ReminderState)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(values: List<Category>)
    @Query("SELECT * FROM categories ORDER BY palette") fun categories(): Flow<List<Category>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSubject(value: Subject): Long
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE") fun subjects(): Flow<List<Subject>>
    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1") suspend fun subjectByName(name: String): Subject?
    @Update suspend fun updateSubject(value: Subject)
    @Delete suspend fun deleteSubject(value: Subject)
    @Upsert suspend fun saveSchedule(value: Schedule): Long
    @Query("SELECT * FROM schedules WHERE id = :id") suspend fun schedule(id: Long): Schedule?
    @Query("SELECT * FROM schedules WHERE active = 1") suspend fun activeSchedules(): List<Schedule>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrences(values: List<Occurrence>)
    @Update suspend fun updateOccurrence(value: Occurrence)
    @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun occurrence(id: Long): Occurrence?
    @Query("SELECT * FROM occurrences WHERE scheduleId = :id") suspend fun seriesOccurrences(id: Long): List<Occurrence>
    @Transaction @Query("SELECT * FROM occurrences WHERE status != 'CANCELLED' ORDER BY day, minute") fun activities(): Flow<List<ActivityItem>>
    @Transaction @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun activity(id: Long): ActivityItem?
    @Transaction @Query("SELECT * FROM occurrences WHERE status = 'PENDING' ORDER BY day, minute") suspend fun pending(): List<ActivityItem>
    @Insert suspend fun insertHistory(value: RescheduleHistory): Long
    @Query("SELECT * FROM reschedules WHERE occurrenceId = :id ORDER BY changedAt DESC") fun history(id: Long): Flow<List<RescheduleHistory>>
    @Query("UPDATE reschedules SET undone = 1 WHERE id = :id") suspend fun undoHistory(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertStudyRecord(value: StudyRecord)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWorkoutRecord(value: WorkoutRecord)
    @Query("DELETE FROM study_records WHERE occurrenceId = :id") suspend fun deleteStudyRecord(id: Long)
    @Query("DELETE FROM workout_records WHERE occurrenceId = :id") suspend fun deleteWorkoutRecord(id: Long)
    @Query("SELECT * FROM study_records ORDER BY completedAt DESC") fun studyRecords(): Flow<List<StudyRecord>>
    @Query("SELECT * FROM workout_records ORDER BY completedAt DESC") fun workoutRecords(): Flow<List<WorkoutRecord>>
    @Upsert suspend fun saveTimer(value: StudyTimer)
    @Query("SELECT * FROM timers LIMIT 1") fun timerFlow(): Flow<StudyTimer?>
    @Query("SELECT * FROM timers LIMIT 1") suspend fun timer(): StudyTimer?
    @Query("DELETE FROM timers") suspend fun clearTimer()
    @Query("DELETE FROM timers WHERE occurrenceId = :id") suspend fun clearTimerFor(id: Long)
    @Transaction @Query("SELECT * FROM routines ORDER BY name") fun routines(): Flow<List<RoutineWithExercises>>
    @Upsert suspend fun saveRoutine(value: WorkoutRoutine): Long
    @Delete suspend fun deleteRoutine(value: WorkoutRoutine)
    @Query("DELETE FROM exercises WHERE routineId = :id") suspend fun clearExercises(id: Long)
    @Insert suspend fun insertExercises(values: List<Exercise>)
}
