package com.campusflow.app.domain

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Repeat(val label: String) { ONCE("Una vez"), DAILY("Cada día"), WEEKLY("Cada semana") }
object CalendarRules {
    fun dates(start: Long, until: Long?, repeat: String, days: String, from: Long, to: Long): List<Long> {
        val first = maxOf(start, from)
        val last = minOf(until ?: to, to)
        if (first > last) return emptyList()
        val weekdays = days.split(',').mapNotNull(String::toIntOrNull).toSet()
        return (first..last).filter { day ->
            when (repeat) {
                Repeat.ONCE.name -> day == start
                Repeat.DAILY.name -> true
                Repeat.WEEKLY.name -> LocalDate.ofEpochDay(day).dayOfWeek.value in weekdays
                else -> false
            }
        }
    }
    fun instant(day: Long, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(day).atTime(LocalTime.ofSecondOfDay(minute * 60L)).atZone(zone).toInstant().toEpochMilli()
    fun weekStart(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)
}

val Spanish: Locale = Locale.forLanguageTag("es")
fun Long.asDate(): LocalDate = LocalDate.ofEpochDay(this)
fun Int.clock(): String = "%02d:%02d".format(this / 60, this % 60)
fun LocalDate.pretty(): String = format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish))
fun LocalDate.shortDate(): String = format(DateTimeFormatter.ofPattern("d MMM", Spanish))
fun reminderLabel(minutes: Int): String = when (minutes) {
    -1 -> "Sin recordatorio"
    0 -> "A la hora"
    60 -> "1 hora antes"
    else -> "$minutes min antes"
}
val ReminderOptions = listOf(-1, 0, 5, 10, 15, 30, 60)
