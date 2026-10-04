package com.campusflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Category::class, Subject::class, WorkoutRoutine::class, Exercise::class, Schedule::class, Occurrence::class, RescheduleHistory::class, StudyRecord::class, WorkoutRecord::class, StudyTimer::class, ReminderState::class, AcademicEvaluation::class, ExamTopic::class], version = 3, exportSchema = true)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun dao(): CampusDao
    abstract fun academicDao(): AcademicDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_states (occurrenceId INTEGER NOT NULL, triggerAt INTEGER NOT NULL, delivered INTEGER NOT NULL, scheduled INTEGER NOT NULL, PRIMARY KEY(occurrenceId), FOREIGN KEY(occurrenceId) REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_evaluations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subjectId INTEGER NOT NULL, title TEXT NOT NULL, type TEXT NOT NULL, date INTEGER NOT NULL, completed INTEGER NOT NULL, FOREIGN KEY(subjectId) REFERENCES subjects(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_academic_evaluations_subjectId ON academic_evaluations(subjectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_academic_evaluations_date ON academic_evaluations(date)")
                db.execSQL("CREATE TABLE IF NOT EXISTS exam_topics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, evaluationId INTEGER NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, FOREIGN KEY(evaluationId) REFERENCES academic_evaluations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exam_topics_evaluationId ON exam_topics(evaluationId)")
            }
        }
        fun create(context: Context): CampusDatabase = Room.databaseBuilder(context, CampusDatabase::class.java, "campusflow.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
