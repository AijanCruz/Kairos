package com.campusflow.app.ocr

import java.io.Serializable
import java.text.Normalizer
import java.util.UUID
import kotlin.math.abs

data class TextRegion(val text: String, val left: Int = 0, val top: Int = 0, val right: Int = 100, val bottom: Int = 20) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
data class RecognizedDocument(val text: String, val lines: List<TextRegion>, val words: List<TextRegion> = emptyList())
data class DetectedClass(
    val id: String = UUID.randomUUID().toString(), val title: String = "",
    val weekday: Int? = null, val startMinute: Int? = null, val endMinute: Int? = null,
    val source: String = "", val uncertain: Boolean = true,
) : Serializable

/** Replace this boundary to introduce another interpreter without changing OCR or storage. */
interface ScheduleInterpreter { fun interpret(document: RecognizedDocument): List<DetectedClass> }

class HeuristicScheduleInterpreter : ScheduleInterpreter {
    private val dayNames = mapOf("lunes" to 1, "lun" to 1, "martes" to 2, "mar" to 2, "miercoles" to 3, "mie" to 3, "jueves" to 4, "jue" to 4, "viernes" to 5, "vie" to 5, "sabado" to 6, "sab" to 6, "domingo" to 7, "dom" to 7)
    private val dayRegex = Regex("\\b(lunes|lun|martes|mar|miercoles|mie|jueves|jue|viernes|vie|sabado|sab|domingo|dom)\\b")
    private val range = Regex("(?<!\\d)(\\d{1,2})[:.](\\d{2})\\s*([ap]\\.?m\\.?)?\\s*(?:[-–—]|\\ba\\b)\\s*(\\d{1,2})[:.](\\d{2})\\s*([ap]\\.?m\\.?)?", RegexOption.IGNORE_CASE)
    private fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    private fun day(value: String): Int? = dayRegex.find(normalized(value))?.value?.let(dayNames::get)
    private fun onlyDay(value: String) = dayRegex.replace(normalized(value), "").trim(' ', '.', ':', '|').isEmpty()
    private fun clock(hour: String, minute: String, suffix: String): Int? {
        var h = hour.toIntOrNull() ?: return null
        val m = minute.toIntOrNull() ?: return null
        if (m !in 0..59 || h !in 0..23) return null
        if (suffix.isNotBlank()) {
            if (h !in 1..12) return null
            h = h % 12 + if (suffix.lowercase().startsWith('p')) 12 else 0
        }
        return h * 60 + m
    }
    fun times(text: String): Pair<Int, Int>? {
        val match = range.find(text) ?: return null
        val endSuffix = match.groupValues[6]
        val firstSuffix = match.groupValues[3].ifBlank { endSuffix }
        val start = clock(match.groupValues[1], match.groupValues[2], firstSuffix) ?: return null
        val end = clock(match.groupValues[4], match.groupValues[5], endSuffix) ?: return null
        return if (end > start) start to end else null
    }
    private fun title(text: String): String {
        var cleaned = range.replace(text, "")
        val matches = dayRegex.findAll(normalized(cleaned)).toList().asReversed()
        matches.forEach { cleaned = cleaned.removeRange(it.range) }
        return cleaned.trim(' ', '|', '-', '–', ':', ',', ';')
    }
    private fun isSubject(text: String): Boolean {
        val clean = title(text)
        return clean.count(Char::isLetter) >= 3 && !onlyDay(text) && normalized(clean) !in setOf("horario", "horario semanal", "hora", "horas", "materia", "materias", "asignatura", "dia", "dias") && !Regex("^\\d{1,2}[:.]\\d{2}$").matches(clean)
    }

    override fun interpret(document: RecognizedDocument): List<DetectedClass> {
        val lines = document.lines.sortedWith(compareBy<TextRegion> { it.top }.thenBy { it.left }).ifEmpty {
            document.text.lines().filter(String::isNotBlank).mapIndexed { index, text -> TextRegion(text, top = index * 30, bottom = index * 30 + 20) }
        }
        val headers = (document.words.ifEmpty { lines }).filter { onlyDay(it.text) && day(it.text) != null }
        val topHeaders = headers.filter { h -> headers.count { abs(it.centerY - h.centerY) < 30 } >= 2 }.distinctBy { day(it.text) }.sortedBy { it.centerX }
        return if (topHeaders.size >= 2) table(lines, topHeaders) else linear(lines)
    }

    private fun linear(lines: List<TextRegion>): List<DetectedClass> {
        val result = mutableListOf<DetectedClass>()
        val usedSubjects = mutableSetOf<Int>()
        lines.forEachIndexed { index, line ->
            if (range.find(line.text) == null) return@forEachIndexed
            val nearby = (maxOf(0, index - 3)..minOf(lines.lastIndex, index + 2)).toList()
            val titleIndex = if (isSubject(line.text)) index else nearby.filter { it != index && it !in usedSubjects && isSubject(lines[it].text) && range.find(lines[it].text) == null }.minByOrNull { abs(it - index) + if (it > index) 1 else 0 }
            val detectedDay = day(line.text) ?: nearby.sortedBy { abs(it - index) }.firstNotNullOfOrNull { day(lines[it].text) }
            val time = times(line.text)
            titleIndex?.let(usedSubjects::add)
            result += DetectedClass(title = titleIndex?.let { title(lines[it].text) } ?: "", weekday = detectedDay, startMinute = time?.first, endMinute = time?.second, source = nearby.joinToString("\n") { lines[it].text }, uncertain = titleIndex == null || detectedDay == null || time == null)
        }
        if (result.isEmpty()) {
            lines.filter { isSubject(it.text) }.take(40).forEach { result += DetectedClass(title = title(it.text), weekday = day(it.text), source = it.text) }
        }
        return result.distinctBy { listOf(it.title, it.weekday, it.startMinute, it.endMinute) }.take(100)
    }

    private fun table(lines: List<TextRegion>, headers: List<TextRegion>): List<DetectedClass> {
        val headerY = headers.maxOf { it.bottom }
        val columnWidth = headers.zipWithNext { a, b -> b.centerX - a.centerX }.minOrNull() ?: 100
        val body = lines.filter { it.top > headerY - 4 && !onlyDay(it.text) }
        val timeRows = body.filter { range.find(it.text) != null }
        val result = mutableListOf<DetectedClass>()
        body.filter { isSubject(it.text) }.forEach { subject ->
            val header = headers.minByOrNull { abs(it.centerX - subject.centerX) } ?: return@forEach
            if (subject.centerX < headers.first().centerX - columnWidth / 2) return@forEach
            val sameColumn = timeRows.filter { abs(it.centerX - header.centerX) < columnWidth / 2 }
            val own = times(subject.text)
            val nearby = sameColumn.minByOrNull { abs(it.centerY - subject.centerY) }?.takeIf { abs(it.centerY - subject.centerY) < 100 }
            val row = timeRows.filter { it.centerX < headers.first().centerX - columnWidth / 2 }.minByOrNull { abs(it.centerY - subject.centerY) }?.takeIf { abs(it.centerY - subject.centerY) < 100 }
            val time = own ?: (nearby ?: row)?.let { times(it.text) }
            result += DetectedClass(title = title(subject.text), weekday = day(header.text), startMinute = time?.first, endMinute = time?.second, source = "${header.text}\n${subject.text}\n${(nearby ?: row)?.text.orEmpty()}", uncertain = true)
        }
        return result.distinctBy { listOf(it.title, it.weekday, it.startMinute, it.endMinute) }.take(100)
    }
}
