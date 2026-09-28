package com.campusflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Category::class, Subject::class, WorkoutRoutine::class, Exercise::class, Schedule::class, Occurrence::class, RescheduleHistory::class, StudyRecord::class, WorkoutRecord::class, StudyTimer::class, ReminderState::class], version = 2, exportSchema = true)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun dao(): CampusDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_states (occurrenceId INTEGER NOT NULL, triggerAt INTEGER NOT NULL, delivered INTEGER NOT NULL, scheduled INTEGER NOT NULL, PRIMARY KEY(occurrenceId), FOREIGN KEY(occurrenceId) REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        fun create(context: Context): CampusDatabase = Room.databaseBuilder(context, CampusDatabase::class.java, "campusflow.db").addMigrations(MIGRATION_1_2).build()
    }
}
