package com.middepremkumar.autosilent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_SET_MODE = "com.middepremkumar.autosilent.ACTION_SET_MODE"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_TYPE = "extra_type"               // "START" or "END"
    const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"

    fun scheduleAll(context: Context) {
        val prefs = Prefs(context)
        
        Log.d("AutoSilent", "scheduleAll: enabled=${prefs.isEnabled()}")

        val oldIds = prefs.getScheduledRequestCodes()
        Log.d("AutoSilent", "Cancelling ${oldIds.size} old alarms")
        cancelSpecificAlarms(context, oldIds)

        if (!prefs.isEnabled()) {
            // If main switch is off, make sure we return to normal mode
            AlarmReceiver.applyRingerMode(context, RingerChoice.RING, -1)
            return
        }

        val schedules = prefs.getSchedules().filter { it.enabled }.toMutableList()
        if (prefs.isJntuaEnabled()) {
            schedules.addAll(prefs.getJntuaSchedules())
        }
        
        Log.d("AutoSilent", "Processing ${schedules.size} total enabled schedules")
        
        val newRequestCodes = mutableSetOf<Int>()
        
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var activeMode: RingerChoice? = null

        for (schedule in schedules) {
            Log.d("AutoSilent", "Schedule: ID=${schedule.id}, Mode=${schedule.mode}, Start=${schedule.startTimeMinutes}, End=${schedule.endTimeMinutes}, Days=${schedule.days}")
            for (day in schedule.days) {
                val startCode = scheduleAlarm(context, schedule, day, "START")
                val endCode = scheduleAlarm(context, schedule, day, "END")
                newRequestCodes.add(startCode)
                newRequestCodes.add(endCode)

                if (day == currentDay && currentMinutes >= schedule.startTimeMinutes && currentMinutes < schedule.endTimeMinutes) {
                    activeMode = schedule.mode
                    Log.d("AutoSilent", "Found active schedule right now! Mode=$activeMode")
                }
            }
        }
        
        prefs.saveScheduledRequestCodes(newRequestCodes)

        // Apply mode for current time immediately
        if (activeMode != null) {
            val currentSchedule = schedules.find { 
                currentDay in it.days && currentMinutes >= it.startTimeMinutes && currentMinutes < it.endTimeMinutes 
            }
            logAnalyticsEvent(context, "time_immediate", "START", activeMode.name)
            AlarmReceiver.applyRingerMode(context, activeMode, currentSchedule?.volumePercent ?: 0)
        } else {
            AlarmReceiver.applyRingerMode(context, RingerChoice.RING, -1)
        }
    }

    private fun logAnalyticsEvent(context: Context, ruleType: String, action: String, mode: String) {
        val bundle = Bundle().apply {
            putString("rule_type", ruleType)
            putString("action", action)
            putString("mode", mode)
        }
        FirebaseAnalytics.getInstance(context).logEvent("silence_rule_triggered", bundle)
    }

    private fun scheduleAlarm(context: Context, schedule: Schedule, dayOfWeek: Int, type: String): Int {
        val minutesOfDay = if (type == "START") schedule.startTimeMinutes else schedule.endTimeMinutes
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = nextTriggerTime(dayOfWeek, minutesOfDay)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SET_MODE
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_DAY_OF_WEEK, dayOfWeek)
        }

        val requestCode = (schedule.id.hashCode() % 100000) * 20 + dayOfWeek * 2 + (if (type == "START") 0 else 1)

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
            )
        } catch (e: SecurityException) {
            Log.e("AutoSilent", "SecurityException scheduling alarm", e)
        }
        
        return requestCode
    }

    private fun cancelSpecificAlarms(context: Context, codes: Set<Int>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (code in codes) {
            val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_SET_MODE }
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun cancelAll(context: Context) {
        val prefs = Prefs(context)
        cancelSpecificAlarms(context, prefs.getScheduledRequestCodes())
        prefs.saveScheduledRequestCodes(emptySet())
    }

    private fun nextTriggerTime(dayOfWeek: Int, minutesOfDay: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        cal.set(Calendar.MINUTE, minutesOfDay % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 7)
        }
        return cal.timeInMillis
    }
}
