package com.chaskifood.app.feature.business.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperatingHoursClosingDayTest {
    @Test fun daytimeClosesOnOpeningDay() {
        assertEquals(DayOfWeekEnum.MONDAY, DayOperatingHours(DayOfWeekEnum.MONDAY).closingDay())
    }

    @Test fun overnightClosesOnFollowingDay() {
        val hours = DayOperatingHours(DayOfWeekEnum.MONDAY, "22:00", "02:00")
        assertEquals(DayOfWeekEnum.TUESDAY, hours.closingDay())
        assertEquals(DayOfWeekEnum.TUESDAY, hours.copy(closeTime = "00:00").closingDay())
    }

    @Test fun overnightSundayClosesOnMonday() {
        assertEquals(DayOfWeekEnum.MONDAY, DayOperatingHours(DayOfWeekEnum.SUNDAY, "23:00", "03:00").closingDay())
    }

    @Test fun disabledAndInvalidHoursDoNotDescribeAClosingDay() {
        assertNull(DayOperatingHours(DayOfWeekEnum.MONDAY, enabled = false).closingDay())
        assertNull(DayOperatingHours(DayOfWeekEnum.MONDAY, "08:00", "08:00").closingDay())
        assertNull(DayOperatingHours(DayOfWeekEnum.MONDAY, "24:00", "02:00").closingDay())
        assertNull(DayOperatingHours(DayOfWeekEnum.MONDAY, "08:00", "xx:xx").closingDay())
    }
}
