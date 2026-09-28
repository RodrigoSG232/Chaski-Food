package com.chaskifood.app.feature.business.domain

import java.util.Calendar
import java.util.Locale

enum class StoreStatus {
    ACTIVE,
    INACTIVE,
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
) {
    fun isAvailableForOrders(
        currentTime: String = getCurrentFormattedTime(),
        currentDay: DayOfWeekEnum = getCurrentDayOfWeek(),
    ): Boolean {
        if (status != StoreStatus.ACTIVE) return false
        if (operationalStatus != OperationalStatus.OPEN) return false

        val todayHours = operatingHours.firstOrNull { it.dayOfWeek == currentDay } ?: return false
        if (!todayHours.enabled) return false

        return isTimeBetween(currentTime, todayHours.openTime, todayHours.closeTime)
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
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}

fun getCurrentDayOfWeek(): DayOfWeekEnum {
    return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
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
    return try {
        val target = targetTime.replace(":", "").toInt()
        val start = startTime.replace(":", "").toInt()
        val end = endTime.replace(":", "").toInt()
        target in start..end
    } catch (e: Exception) {
        true
    }
}
