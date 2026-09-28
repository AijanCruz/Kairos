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
}
