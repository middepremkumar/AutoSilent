package com.middepremkumar.autosilent

import android.content.Context
import java.util.Calendar

/** The three ringer modes the user can pick for any time slot. */
enum class RingerChoice { RING, VIBRATE, SILENT, DND }

/** Thin wrapper around SharedPreferences for the work-hours + lunch schedule. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("auto_silent_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULES = "schedules_json"
        private const val KEY_JNTUA_ENABLED = "jntua_enabled"
        private const val KEY_JNTUA_SCHEDULES = "jntua_schedules_json"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_QUICK_SILENCE_END = "quick_silence_end"
    }

    fun getQuickSilenceEnd(): Long = sp.getLong(KEY_QUICK_SILENCE_END, 0L)
    fun setQuickSilenceEnd(timeMs: Long) = sp.edit().putLong(KEY_QUICK_SILENCE_END, timeMs).apply()

    fun isEnabled(): Boolean = sp.getBoolean(KEY_ENABLED, false)
    fun setEnabled(value: Boolean) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    fun isDarkMode(): Boolean = sp.getBoolean(KEY_DARK_MODE, true) // Default to dark
    fun setDarkMode(value: Boolean) = sp.edit().putBoolean(KEY_DARK_MODE, value).apply()

    fun isJntuaEnabled(): Boolean = sp.getBoolean(KEY_JNTUA_ENABLED, false)
    fun setJntuaEnabled(value: Boolean) = sp.edit().putBoolean(KEY_JNTUA_ENABLED, value).apply()

    fun getSchedules(): List<Schedule> {
        val jsonStr = sp.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return parseSchedules(jsonStr)
    }

    fun saveSchedules(schedules: List<Schedule>) {
        sp.edit().putString(KEY_SCHEDULES, serializeSchedules(schedules)).apply()
    }

    fun getJntuaSchedules(): List<Schedule> {
        val jsonStr = sp.getString(KEY_JNTUA_SCHEDULES, null)
        if (jsonStr == null) {
            // Default JNTUA template with stable IDs
            val weekDays = setOf(2, 3, 4, 5, 6) // Mon-Fri
            val defaults = listOf(
                Schedule(id = "jntua_m1", label = "Morning-Alpha", startTimeMinutes = 9 * 60 + 30, endTimeMinutes = 11 * 60 + 30, days = weekDays, mode = RingerChoice.VIBRATE),
                Schedule(id = "jntua_m2", label = "Morning-Beta", startTimeMinutes = 11 * 60 + 45, endTimeMinutes = 12 * 60 + 45, days = weekDays, mode = RingerChoice.VIBRATE),
                Schedule(id = "jntua_a1", label = "Afternoon-Gamma", startTimeMinutes = 13 * 60 + 45, endTimeMinutes = 16 * 60 + 45, days = weekDays, mode = RingerChoice.VIBRATE)
            )
            // Save them immediately so they become persistent
            saveJntuaSchedules(defaults)
            return defaults
        }
        return parseSchedules(jsonStr)
    }

    fun saveJntuaSchedules(schedules: List<Schedule>) {
        sp.edit().putString(KEY_JNTUA_SCHEDULES, serializeSchedules(schedules)).apply()
    }

    private fun parseSchedules(jsonStr: String): List<Schedule> {
        val list = mutableListOf<Schedule>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(Schedule.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun serializeSchedules(schedules: List<Schedule>): String {
        val array = org.json.JSONArray()
        schedules.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    fun getSavedMediaVolume(): Int = sp.getInt("saved_media_volume", -1)
    fun setSavedMediaVolume(index: Int) = sp.edit().putInt("saved_media_volume", index).apply()

    fun getScheduledRequestCodes(): Set<Int> {
        return sp.getStringSet("scheduled_codes", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
    }

    fun saveScheduledRequestCodes(codes: Set<Int>) {
        sp.edit().putStringSet("scheduled_codes", codes.map { it.toString() }.toSet()).apply()
    }
}
