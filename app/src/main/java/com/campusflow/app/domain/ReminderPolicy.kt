package com.campusflow.app.domain

object ReminderPolicy {
    fun shouldQueue(startAt: Long, reminderMinutes: Int, durationMinutes: Int, now: Long, previouslyTracked: Boolean): Boolean {
        if (reminderMinutes < 0) return false
        if (startAt >= now) return true
        // Recover an alarm missed during reboot, without notifying newly-created past entries.
        val trigger = startAt - reminderMinutes * 60_000L
        val expires = minOf(startAt + durationMinutes * 60_000L, trigger + 3 * 60 * 60_000L)
        return previouslyTracked && now <= expires
    }
}
