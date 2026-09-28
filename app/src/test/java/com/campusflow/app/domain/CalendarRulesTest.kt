package com.campusflow.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarRulesTest {
    @Test fun weeklyCrossesYearAndNeverPrecedesStart() {
        val start = LocalDate.of(2026, 12, 29).toEpochDay()
        val dates = CalendarRules.dates(start, null, "WEEKLY", "2,6", start - 8, start + 10).map { LocalDate.ofEpochDay(it) }
        assertEquals(listOf(LocalDate.of(2026,12,29), LocalDate.of(2027,1,2), LocalDate.of(2027,1,5)), dates)
    }
    @Test fun onceDoesNotRepeatOrEscapeRange() {
        assertEquals(listOf(100L), CalendarRules.dates(100, null, "ONCE", "", 90, 110))
        assertTrue(CalendarRules.dates(100, null, "ONCE", "", 101, 110).isEmpty())
    }
    @Test fun respectsEndDate() {
        assertEquals(listOf(100L,101L,102L), CalendarRules.dates(100, 102, "DAILY", "", 90, 200))
    }
    @Test fun localClockSurvivesDaylightSaving() {
        val zone = ZoneId.of("Europe/Madrid")
        val d = LocalDate.of(2026,3,29).toEpochDay()
        val instant = CalendarRules.instant(d, 8 * 60, zone)
        assertEquals(8, Instant.ofEpochMilli(instant).atZone(zone).hour)
        assertEquals(23 * 3600000L, instant - CalendarRules.instant(d - 1, 8 * 60, zone))
    }
}
