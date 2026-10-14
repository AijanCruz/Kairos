package com.campusflow.app.domain

import com.campusflow.app.data.Schedule
import org.junit.Assert.*
import org.junit.Test

class WeeklyTimetableTest {
    private fun rule(id: Long = 1, start: Int = 480, duration: Int = 60, days: String = "1,3") =
        Schedule(id, "Clase $id", "UNIVERSITY", 20000, start, duration, repeat = "WEEKLY", weekdays = days)

    @Test fun onlyActiveRecurringRulesAppear() {
        val blocks = WeeklyTimetable.blocks(listOf(rule(), rule(2).copy(repeat = "ONCE"), rule(3).copy(active = false)))
        assertEquals(listOf(1, 3), blocks.map { it.weekday })
        assertTrue(blocks.all { it.schedule.id == 1L })
    }
    @Test fun baseIsIndependentOfDatesAndOccurrenceHorizon() {
        val old = rule().copy(endDay = 20010)
        val future = old.copy(startDay = 30000, endDay = 30010)
        assertEquals(WeeklyTimetable.blocks(listOf(old)).map { it.weekday to it.startMinute }, WeeklyTimetable.blocks(listOf(future)).map { it.weekday to it.startMinute })
    }
    @Test fun dailyRuleCoversMondayThroughSunday() {
        assertEquals((1..7).toList(), WeeklyTimetable.blocks(listOf(rule().copy(repeat = "DAILY"))).map { it.weekday })
    }
    @Test fun overnightSundayContinuesOnMondayWithoutChangingSource() {
        val value = rule(start = 1410, duration = 90, days = "7")
        val blocks = WeeklyTimetable.blocks(listOf(value))
        val monday = blocks.first { it.weekday == 1 }
        val sunday = blocks.first { it.weekday == 7 }
        assertEquals(0, monday.startMinute)
        assertEquals(60, monday.endMinute)
        assertTrue(monday.continuation)
        assertEquals(1410, sunday.startMinute)
        assertEquals(1440, sunday.endMinute)
        assertEquals(value, monday.schedule)
    }
    @Test fun overlappingAndNestedBlocksUseDistinctLanes() {
        val blocks = WeeklyTimetable.blocks(listOf(rule(1, 480, 180, "1"), rule(2, 510, 60, "1"), rule(3, 540, 30, "1")))
        assertEquals(setOf(0, 1, 2), blocks.map { it.lane }.toSet())
        assertTrue(blocks.all { it.lanes == 3 })
    }
    @Test fun adjacentBlocksAndAllDayContinuationsDoNotOverlap() {
        val adjacent = WeeklyTimetable.blocks(listOf(rule(1, 480, 60, "1"), rule(2, 540, 60, "1")))
        assertTrue(adjacent.all { it.lane == 0 && it.lanes == 1 })
        val daily = WeeklyTimetable.blocks(listOf(rule(duration = 1440).copy(repeat = "DAILY")))
        assertEquals(14, daily.size)
        assertTrue(daily.all { it.lanes == 1 && it.endMinute > it.startMinute && it.endMinute <= 1440 })
    }
    @Test fun shortBlocksGainReadableHeightWithoutOverlappingAdjacentBlocks() {
        val blocks = WeeklyTimetable.blocks(listOf(rule(1, 480, 1, "1"), rule(2, 481, 5, "1"), rule(3, 480, 30, "2")))
        val axis = WeeklyTimetable.readableAxis(blocks)
        blocks.forEach { assertTrue(axis.offset(it.endMinute) - axis.offset(it.startMinute) >= 55.99f) }
        assertEquals(axis.offset(blocks[0].endMinute), axis.offset(blocks[1].startMinute), 0f)
        assertEquals(480, blocks[0].startMinute)
        assertEquals(481, blocks[0].endMinute)
        assertTrue((0 until 1440).all { axis.offset(it + 1) > axis.offset(it) })
    }
    @Test fun emptyAxisRetainsHoursAndLargerFontStillFitsShortBlocks() {
        assertEquals(2304f, WeeklyTimetable.readableAxis(emptyList()).height, 0f)
        val blocks = WeeklyTimetable.blocks(listOf(rule(start = 1439, duration = 2, days = "7")))
        val axis = WeeklyTimetable.readableAxis(blocks, minimumBlockHeight = 80f)
        blocks.forEach { assertTrue(axis.offset(it.endMinute) - axis.offset(it.startMinute) >= 79.99f) }
    }
}
