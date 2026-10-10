package com.chaskifood.app.feature.business.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class StoreStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
}

enum class OperationalStatus {
    OPEN,
    PAUSED,
    CLOSED,
}

enum class DayOfWeekEnum {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

data class DayOperatingHours(
    val dayOfWeek: DayOfWeekEnum,
    val openTime: String = "08:00",
    val closeTime: String = "22:00",
    val enabled: Boolean = true,
)

/** Día al que corresponde el cierre; una jornada nocturna continúa al día siguiente. */
fun DayOperatingHours.closingDay(): DayOfWeekEnum? {
    if (!enabled) return null
    val opening = timeInMinutes(openTime) ?: return null
    val closing = timeInMinutes(closeTime) ?: return null
    if (opening == closing) return null
    return if (closing < opening) DayOfWeekEnum.entries[(dayOfWeek.ordinal + 1) % 7] else dayOfWeek
}

fun defaultWeeklyOperatingHours(): List<DayOperatingHours> {
    return DayOfWeekEnum.entries.map { day ->
        DayOperatingHours(dayOfWeek = day)
    }
}

data class BusinessStore(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phone: String = "",
    val status: StoreStatus = StoreStatus.ACTIVE,
    val operationalStatus: OperationalStatus = OperationalStatus.OPEN,
    val pauseReason: String? = null,
    val operatingHours: List<DayOperatingHours> = defaultWeeklyOperatingHours(),
    val createdAt: Long = System.currentTimeMillis(),
    val suspensionReason: String? = null,
) {
    fun isAvailableForOrders(
        currentTime: String = getCurrentFormattedTime(),
        currentDay: DayOfWeekEnum = getCurrentDayOfWeek(),
    ): Boolean {
        if (status != StoreStatus.ACTIVE) return false
        if (operationalStatus != OperationalStatus.OPEN) return false

        val target = timeInMinutes(currentTime) ?: return false
        val today = operatingHours.singleOrNull { it.dayOfWeek == currentDay }
        val previousDay = DayOfWeekEnum.entries[(currentDay.ordinal + 6) % 7]
        val previous = operatingHours.singleOrNull { it.dayOfWeek == previousDay }
        // Una jornada nocturna pertenece al día en el que abre.
        fun covers(hours: DayOperatingHours?, previousDay: Boolean): Boolean {
            if (hours == null || !hours.enabled) return false
            val start = timeInMinutes(hours.openTime) ?: return false
            val end = timeInMinutes(hours.closeTime) ?: return false
            return if (previousDay) start > end && target < end
            else if (start < end) target >= start && target < end
            else start > end && target >= start
        }
        return covers(today, false) || covers(previous, true)
    }
}

data class StoreManager(
    val id: String = "",
    val businessId: String = "",
    val email: String = "",
    val fullName: String = "",
    val phone: String = "",
    val assignedStoreIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

fun getCurrentFormattedTime(): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Lima"))
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}

fun getCurrentDayOfWeek(): DayOfWeekEnum {
    return when (Calendar.getInstance(TimeZone.getTimeZone("America/Lima")).get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> DayOfWeekEnum.MONDAY
        Calendar.TUESDAY -> DayOfWeekEnum.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeekEnum.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeekEnum.THURSDAY
        Calendar.FRIDAY -> DayOfWeekEnum.FRIDAY
        Calendar.SATURDAY -> DayOfWeekEnum.SATURDAY
        Calendar.SUNDAY -> DayOfWeekEnum.SUNDAY
        else -> DayOfWeekEnum.MONDAY
    }
}

fun isTimeBetween(targetTime: String, startTime: String, endTime: String): Boolean {
    val target = timeInMinutes(targetTime) ?: return false
    val start = timeInMinutes(startTime) ?: return false
    val end = timeInMinutes(endTime) ?: return false
    return when {
        start < end -> target >= start && target < end
        start > end -> target >= start || target < end
        else -> false
    }
}

internal fun timeInMinutes(value: String): Int? {
    if (!Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(value)) return null
    return value.substring(0, 2).toInt() * 60 + value.substring(3, 5).toInt()
}

fun validOperatingHours(hours: List<DayOperatingHours>): Boolean =
    hours.size == 7 && hours.map { it.dayOfWeek }.toSet().size == 7 && hours.all {
        timeInMinutes(it.openTime) != null && timeInMinutes(it.closeTime) != null &&
            (!it.enabled || it.openTime != it.closeTime)
    }
