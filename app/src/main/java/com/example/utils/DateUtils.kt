package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val monthNameFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    private val time24Format = SimpleDateFormat("HH:mm", Locale.US)

    fun todayIso(): String {
        return isoFormat.format(Date())
    }

    fun currentMonthKey(): String {
        return monthFormat.format(Date())
    }

    fun formatDisplayDate(iso: String): String {
        return try {
            val date = isoFormat.parse(iso)
            if (date != null) displayFormat.format(date) else iso
        } catch (_: Exception) {
            iso
        }
    }

    fun formatMonthName(monthKey: String): String {
        return try {
            val date = monthFormat.parse(monthKey)
            if (date != null) monthNameFormat.format(date) else monthKey
        } catch (_: Exception) {
            monthKey
        }
    }

    fun formatTimeDisplay(time24: String): String {
        return try {
            val date = time24Format.parse(time24)
            if (date != null) timeFormat.format(date) else time24
        } catch (_: Exception) {
            time24
        }
    }

    fun parseIsoDate(iso: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val date = isoFormat.parse(iso)
            if (date != null) {
                cal.time = date
            }
        } catch (_: Exception) {}
        return cal
    }

    fun formatIso(calendar: Calendar): String {
        return isoFormat.format(calendar.time)
    }

    fun formatIso(date: Date): String {
        return isoFormat.format(date)
    }

    fun getDayOfWeek(iso: String): Int {
        val cal = parseIsoDate(iso)
        // Calendar: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
        // Convert to 0=Sunday, 1=Monday, ..., 6=Saturday
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    fun weekdayName(weekday: Int): String {
        return when (weekday) {
            0 -> "Sunday"
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> ""
        }
    }

    fun weekdayShortName(weekday: Int): String {
        return when (weekday) {
            0 -> "Sun"
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            else -> ""
        }
    }

    /**
     * Generates all months from startMonth (yyyy-MM) to endMonth (yyyy-MM) inclusive
     */
    fun getMonthSequence(startMonthKey: String, endMonthKey: String): List<String> {
        val result = mutableListOf<String>()
        val startStr = if (startMonthKey.length == 7) "$startMonthKey-01" else startMonthKey
        val endStr = if (endMonthKey.length == 7) "$endMonthKey-01" else endMonthKey
        val startCal = parseIsoDate(startStr)
        startCal.set(Calendar.DAY_OF_MONTH, 1)

        val endCal = parseIsoDate(endStr)
        endCal.set(Calendar.DAY_OF_MONTH, 1)

        val cur = startCal.clone() as Calendar
        while (!cur.after(endCal)) {
            result.add(monthFormat.format(cur.time))
            cur.add(Calendar.MONTH, 1)
        }
        return result
    }

    /**
     * Clamps a billing day (1..28) safely for a given month
     */
    fun clampDueDate(monthKey: String, billingDay: Int): String {
        val cal = Calendar.getInstance()
        try {
            val date = monthFormat.parse(monthKey)
            if (date != null) cal.time = date
        } catch (_: Exception) {}

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = billingDay.coerceIn(1, maxDay)
        return String.format(Locale.US, "%s-%02d", monthKey, day)
    }

    fun isDateBefore(date1: String, date2: String): Boolean {
        return date1 < date2
    }

    fun getNext7DaysDates(): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        for (i in 0 until 7) {
            list.add(isoFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return list
    }
}
