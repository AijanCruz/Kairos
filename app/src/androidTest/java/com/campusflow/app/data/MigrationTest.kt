package com.campusflow.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CampusDatabase::class.java)
    @Test fun migrationRetainsSchedulesAndAddsReminderLedger() {
        helper.createDatabase("migration-verification", 1).apply {
            execSQL("INSERT INTO categories(`key`,name,palette) VALUES('STUDY','Estudio',1)")
            execSQL("INSERT INTO schedules(id,title,category,startDay,startMinute,durationMinutes,repeat,weekdays,endDay,reminderMinutes,notes,subjectId,routineId,active) VALUES(1,'Conservar materia','STUDY',20000,960,30,'ONCE','',NULL,15,'',NULL,NULL,1)")
            close()
        }
        helper.runMigrationsAndValidate("migration-verification", 2, true, CampusDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT title FROM schedules WHERE id=1").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("Conservar materia", cursor.getString(0)) }
            db.query("SELECT * FROM reminder_states").use { cursor -> assertEquals(0, cursor.count) }
        }
    }
    @Test fun migration2to3PreservesExistingDataAndAddsEmptyAcademicTables() {
        helper.createDatabase("academic-migration", 2).apply {
            execSQL("INSERT INTO categories(`key`,name,palette) VALUES('STUDY','Estudio',1)")
            execSQL("INSERT INTO subjects(id,name) VALUES(42,'Cálculo')")
            execSQL("INSERT INTO schedules(id,title,category,startDay,startMinute,durationMinutes,repeat,weekdays,endDay,reminderMinutes,notes,subjectId,routineId,active) VALUES(1,'Sesión existente','STUDY',20000,960,40,'ONCE','',NULL,15,'Mis notas',42,NULL,1)")
            execSQL("INSERT INTO occurrences(id,scheduleId,originalDay,originalMinute,day,minute,status,moved,completedAt) VALUES(1,1,20000,960,20000,960,'PENDING',0,NULL)")
            execSQL("INSERT INTO timers(id,occurrenceId,remainingMillis,runningSince,totalMillis) VALUES(1,1,1200000,NULL,2400000)")
            execSQL("INSERT INTO reminder_states(occurrenceId,triggerAt,delivered,scheduled) VALUES(1,123456,0,1)")
            close()
        }
        helper.runMigrationsAndValidate("academic-migration", 3, true, CampusDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT name FROM subjects WHERE id=42").use { assertTrue(it.moveToFirst()); assertEquals("Cálculo", it.getString(0)) }
            db.query("SELECT title,notes,subjectId FROM schedules WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("Sesión existente", it.getString(0)); assertEquals("Mis notas", it.getString(1)); assertEquals(42L, it.getLong(2)) }
            db.query("SELECT status FROM occurrences WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("PENDING", it.getString(0)) }
            db.query("SELECT remainingMillis FROM timers WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals(1200000L, it.getLong(0)) }
            db.query("SELECT triggerAt,scheduled FROM reminder_states WHERE occurrenceId=1").use { assertTrue(it.moveToFirst()); assertEquals(123456L, it.getLong(0)); assertEquals(1, it.getInt(1)) }
            db.query("SELECT * FROM academic_evaluations").use { assertEquals(0, it.count) }
            db.query("SELECT * FROM exam_topics").use { assertEquals(0, it.count) }
            db.execSQL("INSERT INTO academic_evaluations(id,subjectId,title,type,date,completed) VALUES(1,42,'Parcial','EXAMEN',20011,0)")
            db.execSQL("INSERT INTO exam_topics(id,evaluationId,name,status) VALUES(1,1,'Límites','PENDIENTE')")
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }

    @Test fun migration1to3RetainsSubjects() {
        helper.createDatabase("academic-migration-from-1", 1).apply {
            execSQL("INSERT INTO subjects(id,name) VALUES(7,'Álgebra')")
            close()
        }
        helper.runMigrationsAndValidate("academic-migration-from-1", 3, true, CampusDatabase.MIGRATION_1_2, CampusDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT name FROM subjects WHERE id=7").use { assertTrue(it.moveToFirst()); assertEquals("Álgebra", it.getString(0)) }
            db.query("SELECT * FROM academic_evaluations").use { assertEquals(0, it.count) }
        }
    }
}
