package com.toplevel.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CalEvent(
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val calendar: String,
) {
    fun timeLabel(): String {
        if (allDay) return "All day"
        val f = DateTimeFormatter.ofPattern("HH:mm")
        val z = ZoneId.systemDefault()
        val s = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), z)
        val e = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), z)
        return "${s.format(f)}–${e.format(f)}"
    }
}

/**
 * Reads/writes the calendars synced on the phone (your Google Calendar included),
 * via Android's CalendarContract. No Google sign-in needed inside the app.
 */
object CalendarRepo {

    fun canRead(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun eventsFor(ctx: Context, day: LocalDate, days: Int = 1): List<CalEvent> {
        if (!canRead(ctx)) return emptyList()
        val z = ZoneId.systemDefault()
        val begin = day.atStartOfDay(z).toInstant().toEpochMilli()
        val end = day.plusDays(days.toLong()).atStartOfDay(z).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, begin)
        ContentUris.appendId(builder, end)
        val proj = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val out = mutableListOf<CalEvent>()
        try {
            ctx.contentResolver.query(builder.build(), proj, null, null,
                CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    out += CalEvent(
                        title = c.getString(0) ?: "(no title)",
                        start = c.getLong(1),
                        end = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                        calendar = c.getString(4) ?: "",
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    /** Picks the primary Google calendar if present, otherwise any writable one. */
    private fun writableCalendarId(ctx: Context): Long? {
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        var fallback: Long? = null
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val type = c.getString(1) ?: ""
                val primary = c.getInt(2) == 1
                val access = c.getInt(3)
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (type == "com.google" && primary) return id
                if (fallback == null) fallback = id
            }
        }
        return fallback
    }

    /** Returns true if the event was created. */
    fun addEvent(ctx: Context, title: String, start: LocalDateTime, minutes: Int, description: String = ""): Boolean {
        if (!canWrite(ctx)) return false
        val calId = try { writableCalendarId(ctx) } catch (_: Exception) { null } ?: return false
        val z = ZoneId.systemDefault()
        val s = start.atZone(z).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, s)
            put(CalendarContract.Events.DTEND, s + minutes * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, z.id)
        }
        return try {
            ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
        } catch (_: Exception) { false }
    }
}
