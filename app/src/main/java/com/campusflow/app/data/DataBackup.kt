package com.campusflow.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.campusflow.app.domain.CalendarRules
import com.campusflow.app.domain.AcademicRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON with a fixed table/column allowlist; all restores are atomic. */
class DataBackup(private val db: CampusDatabase) {
    private val legacyTables = listOf("categories", "subjects", "routines", "exercises", "schedules", "occurrences", "reschedules", "study_records", "workout_records", "timers", "reminder_states")
    private val academicTables = listOf("academic_evaluations", "exam_topics", "subject_goals")
    private val tables = legacyTables + academicTables

    suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val root = JSONObject().put("format", "CampusFlow").put("version", 2).put("databaseVersion", 5).put("exportedAt", System.currentTimeMillis())
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
            root.put("data", data).toString(2).also {
                require(it.toByteArray(Charsets.UTF_8).size <= 10_000_000) { "La copia supera el límite de 10 MB." }
            }
        }
    }

    suspend fun restore(json: String) = withContext(Dispatchers.IO) {
        require(json.toByteArray(Charsets.UTF_8).size <= 10_000_000) { "La copia supera el límite de 10 MB." }
        val root = JSONObject(json)
        val legacy = root.optInt("version") == 1 && root.optInt("databaseVersion") == 2
        val current = root.optInt("version") == 2 && root.optInt("databaseVersion") == 5
        require(root.optString("format") == "CampusFlow" && (legacy || current)) { "Esta copia no es compatible con esta versión de Kairos." }
        val data = root.getJSONObject("data")
        val includedTables = if (legacy) legacyTables else tables
        require(data.keys().asSequence().toSet() == includedTables.toSet()) { "La copia está incompleta o contiene tablas desconocidas." }
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            val prepared = includedTables.associateWith { table ->
                val columns = mutableMapOf<String, String>()
                val required = mutableSetOf<String>()
                sql.query("PRAGMA table_info($table)").use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        columns[name] = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                        if (cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1) required += name
                    }
                }
                val array = data.getJSONArray(table)
                require(array.length() <= 100_000) { "Demasiados registros." }
                (0 until array.length()).map { index ->
                    val row = array.getJSONObject(index)
                    require(row.keys().asSequence().toSet() == columns.keys) { "Estructura inválida en $table." }
                    ContentValues().apply {
                        columns.forEach { (column, type) ->
                            if (row.isNull(column)) {
                                require(column !in required) { "Falta $column en $table." }
                                putNull(column)
                            } else {
                                val raw = row.get(column)
                                when (type) {
                                    "INTEGER" -> {
                                        require(raw is Int || raw is Long) { "Entero inválido en $table.$column." }
                                        val value = (raw as Number).toLong()
                                        if (column in setOf("active", "completed", "moved", "undone", "delivered", "scheduled")) require(value in 0L..1L) { "Estado inválido en $table." }
                                        if (column == "id") require(value > 0) { "Identificador inválido en $table." }
                                        put(column, value)
                                    }
                                    "REAL" -> {
                                        require(raw is Number && raw.toDouble().isFinite()) { "Número inválido en $table.$column." }
                                        put(column, (raw as Number).toDouble())
                                    }
                                    else -> {
                                        require(raw is String && raw.length <= 100_000) { "Texto inválido en $table.$column." }
                                        put(column, raw as String)
                                    }
                                }
                            }
                        }
                        if (table == "schedules") {
                            require(getAsLong("startMinute") in 0L..1439L && getAsLong("durationMinutes") in 1L..1440L) { "Horas inválidas en la copia." }
                            require(getAsString("title").isNotBlank()) { "Nombre de actividad inválido." }
                            CalendarRules.validateRule(getAsLong("startDay"), getAsLong("endDay"), getAsString("repeat"), getAsString("weekdays"))
                            require(getAsLong("reminderMinutes") in com.campusflow.app.domain.ReminderOptions.map { it.toLong() }) { "Recordatorio inválido." }
                        }
                        if (table == "occurrences") {
                            require(getAsLong("minute") in 0L..1439L && getAsLong("originalMinute") in 0L..1439L) { "Hora inválida." }
                            require(getAsString("status") in listOf(Status.PENDING, Status.DONE, Status.CANCELLED)) { "Estado inválido." }
                            java.time.LocalDate.ofEpochDay(getAsLong("day"))
                            java.time.LocalDate.ofEpochDay(getAsLong("originalDay"))
                        }
                        if (table == "reschedules") {
                            java.time.LocalDate.ofEpochDay(getAsLong("fromDay"))
                            java.time.LocalDate.ofEpochDay(getAsLong("toDay"))
                            require(getAsLong("fromMinute") in 0L..1439L && getAsLong("toMinute") in 0L..1439L) { "Hora inválida en el historial." }
                        }
                        if (table == "timers") {
                            require(getAsLong("id") == 1L && getAsLong("totalMillis") in 1..86_400_000L && getAsLong("remainingMillis") in 0..getAsLong("totalMillis")) { "Temporizador inválido." }
                            putNull("runningSince")
                        }
                        if (table == "reminder_states") put("scheduled", 0)
                        if (table == "academic_evaluations") {
                            require(getAsString("title").isNotBlank() && getAsString("type") in EvaluationType.entries.map { it.name }) { "Evaluación inválida." }
                            java.time.LocalDate.ofEpochDay(getAsLong("date"))
                            AcademicRules.validateGrading(getAsDouble("percentage"), getAsDouble("grade"))
                        }
                        if (table == "exam_topics") require(getAsString("name").isNotBlank() && getAsString("status") in TopicStatus.entries.map { it.name }) { "Tema inválido." }
                        if (table == "subject_goals") AcademicRules.validateGrading(0.0, getAsDouble("targetGrade"))
                    }
                }
            }.toMutableMap()
            if (legacy) preserveAcademicData(prepared)
            val evaluations = prepared.getValue("academic_evaluations")
            evaluations.groupBy { it.getAsLong("subjectId") }.values.forEach { rows ->
                AcademicRules.validatePercentageTotal(0.0, rows.map { it.getAsDouble("percentage") })
            }
            val evaluationTypes = evaluations.associate { it.getAsLong("id") to it.getAsString("type") }
            require(prepared.getValue("exam_topics").all { AcademicRules.supportsTopics(evaluationTypes[it.getAsLong("evaluationId")].orEmpty()) }) { "Un tema no pertenece a un examen o quiz válido." }
            tables.asReversed().forEach { sql.execSQL("DELETE FROM $it") }
            tables.forEach { table -> prepared.getValue(table).forEach { sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, it) } }
            sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "La copia contiene relaciones inválidas." } }
            sql.query("SELECT timers.id FROM timers JOIN occurrences ON occurrences.id = timers.occurrenceId JOIN schedules ON schedules.id = occurrences.scheduleId WHERE occurrences.status != 'PENDING' OR schedules.category != 'STUDY'").use {
                require(!it.moveToFirst()) { "El temporizador no pertenece a una sesión de estudio pendiente." }
            }
            db.dao().insertCategories(Categories.defaults)
        }
    }

    /** Legacy snapshots predate academic tables: preserve them, never silently discard them. */
    private fun preserveAcademicData(prepared: MutableMap<String, List<ContentValues>>) {
        academicTables.forEach { prepared[it] = readRows(it) }
        val subjectIds = prepared.getValue("academic_evaluations").map { it.getAsLong("subjectId") }.toSet() +
            prepared.getValue("subject_goals").map { it.getAsLong("subjectId") }
        val incoming = prepared.getValue("subjects").toMutableList()
        readRows("subjects").filter { it.getAsLong("id") in subjectIds }.forEach { subject ->
            val byId = incoming.find { it.getAsLong("id") == subject.getAsLong("id") }
            val byName = incoming.find { it.getAsString("name") == subject.getAsString("name") }
            require((byId == null && byName == null) || (byId == byName && byId?.getAsString("name") == subject.getAsString("name"))) {
                "La copia antigua contiene materias incompatibles con tus evaluaciones o metas actuales. No se modificó ningún dato."
            }
            if (byId == null) incoming += subject
        }
        prepared["subjects"] = incoming
    }

    private fun readRows(table: String): List<ContentValues> {
        val rows = mutableListOf<ContentValues>()
        db.openHelper.readableDatabase.query("SELECT * FROM $table").use { cursor ->
            while (cursor.moveToNext()) rows += ContentValues().apply {
                cursor.columnNames.forEachIndexed { index, column ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> putNull(column)
                        Cursor.FIELD_TYPE_INTEGER -> put(column, cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> put(column, cursor.getDouble(index))
                        else -> put(column, cursor.getString(index))
                    }
                }
            }
        }
        return rows
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
