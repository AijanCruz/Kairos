package com.campusflow.app.domain

import org.junit.Assert.*
import org.junit.Test

class TimerMathTest {
    @Test fun runningTimerReconstructsAfterProcessDeath() { assertEquals(20_000, TimerMath.remaining(30_000, 1000, 11_000)) }
    @Test fun pausedTimerDoesNotConsumeTime() { assertEquals(30_000, TimerMath.remaining(30_000, null, 999_999)) }
    @Test fun elapsedAndBackwardsClockAreBounded() {
        assertEquals(0, TimerMath.remaining(30_000, 1000, 99_999))
        assertEquals(30_000, TimerMath.remaining(30_000, 10_000, 1000))
    }
}
