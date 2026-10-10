package com.chaskifood.app.feature.business.domain

import org.junit.Assert.*
import org.junit.Test

class StoreAvailabilityTest {
    @Test fun `horas invalidas nunca habilitan compras`() {
        for (invalid in listOf("", "8:00", "24:00", "10:60", "abc", "0800")) {
            assertFalse(isTimeBetween("12:00", invalid, "22:00"))
            assertFalse(isTimeBetween(invalid, "08:00", "22:00"))
        }
        assertFalse(isTimeBetween("12:00", "08:00", "08:00"))
    }
    @Test fun `cierre es exclusivo y apertura inclusiva`() {
        assertTrue(isTimeBetween("08:00", "08:00", "22:00"))
        assertFalse(isTimeBetween("22:00", "08:00", "22:00"))
        assertFalse(isTimeBetween("07:59", "08:00", "22:00"))
    }
    @Test fun `turno nocturno termina el dia siguiente incluso si ese dia esta cerrado`() {
        val store = BusinessStore(operatingHours = listOf(
            DayOperatingHours(DayOfWeekEnum.MONDAY, "22:00", "02:00"),
            DayOperatingHours(DayOfWeekEnum.TUESDAY, enabled = false),
        ))
        assertFalse(store.isAvailableForOrders("01:00", DayOfWeekEnum.MONDAY))
        assertTrue(store.isAvailableForOrders("22:00", DayOfWeekEnum.MONDAY))
        assertTrue(store.isAvailableForOrders("01:59", DayOfWeekEnum.TUESDAY))
        assertFalse(store.isAvailableForOrders("02:00", DayOfWeekEnum.TUESDAY))
    }
    @Test fun `turno domingo continua lunes pero pausa y suspension lo bloquean`() {
        val store = BusinessStore(operatingHours = listOf(DayOperatingHours(DayOfWeekEnum.SUNDAY, "23:00", "03:00")))
        assertTrue(store.isAvailableForOrders("02:00", DayOfWeekEnum.MONDAY))
        assertFalse(store.copy(status = StoreStatus.SUSPENDED).isAvailableForOrders("02:00", DayOfWeekEnum.MONDAY))
        assertFalse(store.copy(operationalStatus = OperationalStatus.PAUSED).isAvailableForOrders("02:00", DayOfWeekEnum.MONDAY))
    }
    @Test fun `horario semanal rechaza dias repetidos y turnos vacios`() {
        val hours = defaultWeeklyOperatingHours()
        assertTrue(validOperatingHours(hours))
        assertFalse(validOperatingHours(hours.drop(1)))
        assertFalse(validOperatingHours(hours.dropLast(1) + hours.first()))
        assertFalse(validOperatingHours(hours.map { it.copy(openTime = "00:00", closeTime = "00:00") }))
    }
}
