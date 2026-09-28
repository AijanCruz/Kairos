package com.campusflow.app.domain

object TimerMath {
    fun remaining(storedMillis: Long, runningSince: Long?, now: Long): Long =
        (storedMillis - if (runningSince == null) 0 else (now - runningSince).coerceAtLeast(0)).coerceAtLeast(0)
    fun display(millis: Long): String {
        val seconds = (millis + 999) / 1000
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}
