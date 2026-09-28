package com.campusflow.app.data

import androidx.room.*

object Categories {
    const val UNIVERSITY = "UNIVERSITY"
    const val STUDY = "STUDY"
    const val WORKOUT = "WORKOUT"
    const val PERSONAL = "PERSONAL"
    val defaults = listOf(
        Category(UNIVERSITY, "Universidad", 0), Category(STUDY, "Estudio", 1),
        Category(WORKOUT, "Entrenamiento", 2), Category(PERSONAL, "Personal", 3),
    )
    fun label(key: String) = defaults.find { it.key == key }?.name ?: key
}
object Status { const val PENDING = "PENDING"; const val DONE = "DONE"; const val CANCELLED = "CANCELLED" }

@Entity(tableName = "categories")
data class Category(@PrimaryKey val key: String, val name: String, val palette: Int)

@Entity(tableName = "subjects", indices = [Index("name", unique = true)])
data class Subject(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "routines")
data class WorkoutRoutine(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val minutes: Int = 50, val notes: String = "")

@Entity(tableName = "exercises", foreignKeys = [ForeignKey(entity = WorkoutRoutine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)], indices = [Index("routineId")])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val routineId: Long,
    val position: Int, val name: String, val sets: Int = 3, val reps: String = "8–12",
    val restSeconds: Int = 60, val weight: String = "", val notes: String = "",
)

@Entity(tableName = "schedules", foreignKeys = [
    ForeignKey(entity = Category::class, parentColumns = ["key"], childColumns = ["category"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.SET_NULL),
    ForeignKey(entity = WorkoutRoutine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index("category"), Index("subjectId"), Index("routineId")])
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val category: String,
    val startDay: Long, val startMinute: Int, val durationMinutes: Int,
    val repeat: String = "ONCE", val weekdays: String = "", val endDay: Long? = null,
    val reminderMinutes: Int = 15, val notes: String = "",
    val subjectId: Long? = null, val routineId: Long? = null, val active: Boolean = true,
) : java.io.Serializable

@Entity(tableName = "occurrences", foreignKeys = [ForeignKey(entity = Schedule::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["scheduleId", "originalDay"], unique = true), Index("day")])
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long, val originalDay: Long, val originalMinute: Int,
    val day: Long, val minute: Int, val status: String = Status.PENDING,
    val moved: Boolean = false, val completedAt: Long? = null,
)

data class ActivityItem(
    @Embedded val occurrence: Occurrence,
    @Relation(parentColumn = "scheduleId", entityColumn = "id") val schedule: Schedule,
)

@Entity(tableName = "reschedules", foreignKeys = [ForeignKey(entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.CASCADE)], indices = [Index("occurrenceId")])
data class RescheduleHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val occurrenceId: Long,
    val fromDay: Long, val fromMinute: Int, val toDay: Long, val toMinute: Int,
    val changedAt: Long, val undone: Boolean = false,
)

@Entity(tableName = "study_records", foreignKeys = [ForeignKey(entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.CASCADE)], indices = [Index("occurrenceId", unique = true)])
data class StudyRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val occurrenceId: Long, val completedAt: Long, val seconds: Long)

@Entity(tableName = "workout_records", foreignKeys = [ForeignKey(entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.CASCADE)], indices = [Index("occurrenceId", unique = true)])
data class WorkoutRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val occurrenceId: Long, val completedAt: Long)

@Entity(tableName = "timers", foreignKeys = [ForeignKey(entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.CASCADE)], indices = [Index("occurrenceId", unique = true)])
data class StudyTimer(@PrimaryKey val id: Int = 1, val occurrenceId: Long, val remainingMillis: Long, val runningSince: Long? = null, val totalMillis: Long)

@Entity(tableName = "reminder_states", foreignKeys = [ForeignKey(entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.CASCADE)])
data class ReminderState(@PrimaryKey val occurrenceId: Long, val triggerAt: Long, val delivered: Boolean = false, val scheduled: Boolean = false)

data class RoutineWithExercises(
    @Embedded val routine: WorkoutRoutine,
    @Relation(parentColumn = "id", entityColumn = "routineId") val exercises: List<Exercise>,
)
