package com.campusflow.app.ocr

import org.junit.Assert.*
import org.junit.Test

class InterpreterTest {
    private val parser = HeuristicScheduleInterpreter()
    @Test fun recognizesMultilineSpanishSchedule() {
        val result = parser.interpret(RecognizedDocument("Programación II\nViernes\n08:00 - 11:40\nInglés III\nSábado\n08:00 - 11:40", emptyList()))
        assertEquals(2, result.size)
        assertEquals("Programación II", result[0].title)
        assertEquals(5, result[0].weekday)
        assertEquals(480, result[0].startMinute)
        assertEquals(700, result[0].endMinute)
        assertEquals("Inglés III", result[1].title)
        assertEquals(6, result[1].weekday)
    }
    @Test fun convertsAmPmAndRejectsInvalidTimes() {
        assertEquals(17 * 60 to 20 * 60 + 50, parser.times("5:00 PM - 8:50 PM"))
        assertEquals(0 to 60, parser.times("12:00 AM - 1:00 AM"))
        assertNull(parser.times("25:00 - 28:00"))
        assertNull(parser.times("11:00 - 08:00"))
    }
    @Test fun unknownDayIsNotInvented() {
        val result = parser.interpret(RecognizedDocument("Álgebra\n17:00 - 20:50", emptyList())).first()
        assertNull(result.weekday)
        assertTrue(result.uncertain)
    }
    @Test fun decomposedAccentsDoNotShiftSubjectName() {
        val result = parser.interpret(RecognizedDocument("Fi\u0301sica mie\u0301rcoles 08:00 - 09:00", emptyList())).single()
        assertEquals("Física", result.title)
        assertEquals(3, result.weekday)
        assertEquals(480, result.startMinute)
    }
    @Test fun malformedMinutesAreNotPartiallyAccepted() {
        assertNull(parser.times("08:00 - 09:300"))
        assertNull(parser.times("08:60 - 09:30"))
    }
    @Test fun usesColumnGeometry() {
        val lines = listOf(TextRegion("Lunes",100,0,200,20), TextRegion("Martes",300,0,400,20), TextRegion("08:00 - 09:30",0,60,80,80), TextRegion("Cálculo",100,60,200,80), TextRegion("Física",300,60,400,80))
        val result = parser.interpret(RecognizedDocument("", lines))
        assertEquals(listOf(1,2), result.map { it.weekday })
        assertEquals(listOf(480,480), result.map { it.startMinute })
    }
}
