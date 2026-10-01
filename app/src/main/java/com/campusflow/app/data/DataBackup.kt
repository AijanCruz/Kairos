package com.campusflow.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.campusflow.app.domain.CalendarRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON with a fixed table/column allowlist; all restores are atomic. */
class DataBackup(private val db: CampusDatabase) {
    private val tables = listOf("categories", "subjects", "routines", "exercises", "schedules", "occurrences", "reschedules", "study_records", "workout_records", "timers", "reminder_states")

    suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val root = JSONObject().put("format", "CampusFlow").put("version", 1).put("databaseVersion", 2).put("exportedAt", System.currentTimeMillis())
            val data = JSONObject()
            for (table in tables) {
                val rows = JSONArray()
                db.openHelper.readableDatabase.query("SELECT * FROM $table").use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        cursor.columnNames.forEachIndexed { index, column ->
                            row.put(column, when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                else -> cursor.getString(index)
                            })
                        }
                        if (table == "timers") {
                            val since = if (row.isNull("runningSince")) null else row.getLong("runningSince")
                            row.put("remainingMillis", com.campusflow.app.domain.TimerMath.remaining(row.getLong("remainingMillis"), since, System.currentTimeMillis()))
                            row.put("runningSince", JSONObject.NULL)
                        }
                        if (table == "reminder_states") row.put("scheduled", 0)
                        rows.put(row)
                    }
                }
                data.put(table, rows)
            }
            root.put("data", data).toString(2)
        }
    }

    suspend fun restore(json: String) = withContext(Dispatchers.IO) {
        require(json.length <= 10_000_000) { "La copia supera el límite de 10 MB." }
        val root = JSONObject(json)
        require(root.optString("format") == "CampusFlow" && root.optInt("version") == 1 && root.optInt("databaseVersion") == 2) { "Esta copia no es compatible con esta versión de Kairos." }
        val data = root.getJSONObject("data")
        require(tables.all { data.has(it) }) { "La copia está incompleta." }
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            val prepared = tables.associateWith { table ->
                val columns = mutableMapOf<String, String>()
                sql.query("PRAGMA table_info($table)").use { cursor ->
                    while (cursor.moveToNext()) columns[cursor.getString(cursor.getColumnIndexOrThrow("name"))] = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                }
                val array = data.getJSONArray(table)
                require(array.length() <= 100_000) { "Demasiados registros." }
                (0 until array.length()).map { index ->
                    val row = array.getJSONObject(index)
                    require(row.keys().asSequence().toSet() == columns.keys) { "Estructura inválida en $table." }
                    ContentValues().apply {
                        columns.forEach { (column, type) ->
                            if (row.isNull(column)) putNull(column)
                            else if (type == "INTEGER") put(column, row.getLong(column))
                            else put(column, row.getString(column).also { require(it.length <= 100_000) { "Texto demasiado largo." } })
                        }
                        if (table == "schedules") {
                            require(getAsInteger("startMinute") in 0..1439 && getAsInteger("durationMinutes") in 1..1440) { "Horas inválidas en la copia." }
                            require(getAsString("title").isNotBlank()) { "Nombre de actividad inválido." }
                            CalendarRules.validateRule(getAsLong("startDay"), getAsLong("endDay"), getAsString("repeat"), getAsString("weekdays"))
                            require(getAsInteger("reminderMinutes") in com.campusflow.app.domain.ReminderOptions) { "Recordatorio inválido." }
                        }
                        if (table == "occurrences") {
                            require(getAsInteger("minute") in 0..1439 && getAsInteger("originalMinute") in 0..1439) { "Hora inválida." }
                            require(getAsString("status") in listOf(Status.PENDING, Status.DONE, Status.CANCELLED)) { "Estado inválido." }
                            java.time.LocalDate.ofEpochDay(getAsLong("day"))
                            java.time.LocalDate.ofEpochDay(getAsLong("originalDay"))
                        }
                        if (table == "reschedules") {
                            java.time.LocalDate.ofEpochDay(getAsLong("fromDay"))
                            java.time.LocalDate.ofEpochDay(getAsLong("toDay"))
                            require(getAsInteger("fromMinute") in 0..1439 && getAsInteger("toMinute") in 0..1439) { "Hora inválida en el historial." }
                        }
                        if (table == "timers") {
                            require(getAsInteger("id") == 1 && getAsLong("totalMillis") in 1..86_400_000L && getAsLong("remainingMillis") in 0..getAsLong("totalMillis")) { "Temporizador inválido." }
                            putNull("runningSince")
                        }
                        if (table == "reminder_states") put("scheduled", 0)
                    }
                }
            }
            tables.asReversed().forEach { sql.execSQL("DELETE FROM $it") }
            tables.forEach { table -> prepared.getValue(table).forEach { sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, it) } }
            sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "La copia contiene relaciones inválidas." } }
            sql.query("SELECT timers.id FROM timers JOIN occurrences ON occurrences.id = timers.occurrenceId JOIN schedules ON schedules.id = occurrences.scheduleId WHERE occurrences.status != 'PENDING' OR schedules.category != 'STUDY'").use {
                require(!it.moveToFirst()) { "El temporizador no pertenece a una sesión de estudio pendiente." }
            }
            db.dao().insertCategories(Categories.defaults)
        }
    }

    suspend fun exportTo(context: Context, uri: Uri) {
        val json = snapshot()
        withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "No se pudo abrir el archivo." }.bufferedWriter().use { it.write(json) } }
    }
    suspend fun readFrom(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openInputStream(uri)) { "No se pudo abrir la copia." }.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 10_000_000) { "La copia supera 10 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }
    suspend fun clear() = withContext(Dispatchers.IO) {
        db.withTransaction {
            tables.asReversed().forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
            db.dao().insertCategories(Categories.defaults)
        }
    }
}
