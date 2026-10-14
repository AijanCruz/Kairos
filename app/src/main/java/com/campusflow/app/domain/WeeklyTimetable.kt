package com.campusflow.app.domain

import com.campusflow.app.data.Schedule

data class TimetableBlock(
    val schedule: Schedule,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val continuation: Boolean = false,
    val lane: Int = 0,
    val lanes: Int = 1,
)

/** A weekly template, independent of dated occurrences and their completion state. */
object WeeklyTimetable {
    /** Display-only scale: expand very short intervals equally across all seven days. */
    fun readableAxis(blocks: List<TimetableBlock>, hourHeight: Float = 96f, minimumBlockHeight: Float = 56f): TimetableAxis {
        val marks = ((0..24).map { it * 60 } + blocks.flatMap { listOf(it.startMinute, it.endMinute) }).distinct().sorted()
        val heights = marks.zipWithNext { start, end -> (end - start) * hourHeight / 60f }.toMutableList()
        blocks.sortedBy { it.endMinute - it.startMinute }.forEach { block ->
            val start = marks.binarySearch(block.startMinute)
            val end = marks.binarySearch(block.endMinute)
            val height = (start until end).sumOf { heights[it].toDouble() }.toFloat()
            if (height < minimumBlockHeight) {
                val extra = minimumBlockHeight - height
                for (index in start until end) heights[index] += extra * (marks[index + 1] - marks[index]) / (block.endMinute - block.startMinute)
            }
        }
        return TimetableAxis(marks, heights.runningFold(0f) { sum, value -> sum + value })
    }

    fun blocks(schedules: List<Schedule>): List<TimetableBlock> {
        val segments = schedules.filter { it.active }.flatMap { schedule ->
            val days = when (schedule.repeat) {
                Repeat.DAILY.name -> (1..7).toList()
                Repeat.WEEKLY.name -> schedule.weekdays.split(',').mapNotNull(String::toIntOrNull).filter { it in 1..7 }.distinct()
                else -> emptyList()
            }
            days.flatMap { day ->
                val end = schedule.startMinute + schedule.durationMinutes
                listOf(TimetableBlock(schedule, day, schedule.startMinute, minOf(end, 1440))) +
                    if (end > 1440) listOf(TimetableBlock(schedule, day % 7 + 1, 0, end - 1440, continuation = true)) else emptyList()
            }
        }
        return (1..7).flatMap { day ->
            val ordered = segments.filter { it.weekday == day }
                .sortedWith(compareBy<TimetableBlock> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.schedule.id })
            val result = mutableListOf<TimetableBlock>()
            val group = mutableListOf<TimetableBlock>()
            val laneEnds = mutableListOf<Int>()
            var groupEnd = -1
            fun flush() {
                result += group.map { it.copy(lanes = laneEnds.size) }
                group.clear()
                laneEnds.clear()
            }
            for (block in ordered) {
                if (block.startMinute >= groupEnd) flush()
                val available = laneEnds.indexOfFirst { it <= block.startMinute }
                val lane = if (available < 0) laneEnds.size else available
                if (available < 0) laneEnds += block.endMinute else laneEnds[lane] = block.endMinute
                group += block.copy(lane = lane)
                groupEnd = if (group.size == 1) block.endMinute else maxOf(groupEnd, block.endMinute)
            }
            flush()
            result
        }
    }
}

data class TimetableAxis(private val minutes: List<Int>, private val offsets: List<Float>) {
    val height: Float get() = offsets.last()
    fun offset(minute: Int): Float {
        val value = minute.coerceIn(0, 1440)
        val index = minutes.binarySearch(value)
        if (index >= 0) return offsets[index]
        val next = -index - 1
        val fraction = (value - minutes[next - 1]).toFloat() / (minutes[next] - minutes[next - 1])
        return offsets[next - 1] + (offsets[next] - offsets[next - 1]) * fraction
    }
}
