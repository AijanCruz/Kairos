package com.campusflow.app.domain

import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    @Test fun rebootCanRecoverPreviouslyScheduledActivityAlreadyInProgress() {
        assertTrue(ReminderPolicy.shouldQueue(1_000_000, 0, 30, 1_025_000, true))
    }
    @Test fun oldManualEntriesDoNotCreateCatchupNotifications() {
        assertFalse(ReminderPolicy.shouldQueue(1_000_000, 0, 30, 1_025_000, false))
        assertFalse(ReminderPolicy.shouldQueue(1_000_000, -1, 30, 900_000, true))
    }
    @Test fun expiresAtActivityEndOrThreeHoursAfterReminder() {
        assertFalse(ReminderPolicy.shouldQueue(1_000_000, 0, 30, 3_000_000, true))
        assertFalse(ReminderPolicy.shouldQueue(10_000_000, 60, 600, 18_000_000, true))
        assertTrue(ReminderPolicy.shouldQueue(1_000_000, 15, 30, 900_000, false))
    }
}
