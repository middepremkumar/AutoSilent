package com.middepremkumar.autosilent

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_SET_MODE) return

        val scheduleId = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULE_ID) ?: return
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: return
        val dayOfWeek = intent.getIntExtra(AlarmScheduler.EXTRA_DAY_OF_WEEK, -1)

        val prefs = Prefs(context)

        // If Quick Silence is active, don't let scheduled alarms interrupt it
        if (prefs.getQuickSilenceEnd() > System.currentTimeMillis()) {
            Log.d("AutoSilent", "Ignoring scheduled alarm because Quick Silence is active")
            return
        }

        val schedule = prefs.getSchedules().find { it.id == scheduleId } ?: return

        if (!schedule.enabled || !schedule.days.contains(dayOfWeek)) return

        if (type == "START") {
            logAnalyticsEvent(context, "time", "START", schedule.mode.name)
            applyRingerMode(context, schedule.mode, schedule.volumePercent)
            showNotification(context, "Silence Active", "Mode: ${schedule.mode.name} (Scheduled)")
        } else {
            logAnalyticsEvent(context, "time", "END", "RING")
            applyRingerMode(context, RingerChoice.RING, -1) // -1 to restore
            cancelNotification(context)
        }

        if (prefs.isEnabled()) {
            AlarmScheduler.scheduleAll(context) 
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "silence_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Silence Status", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_tile_silent)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001)
    }

    companion object {
        fun applyRingerMode(context: Context, mode: RingerChoice, volPercent: Int) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = Prefs(context)

            try {
                when (mode) {
                    RingerChoice.RING -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            if (notificationManager.isNotificationPolicyAccessGranted) {
                                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            }
                        }
                        restoreMediaVolume(audioManager, prefs)
                    }
                    RingerChoice.VIBRATE -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        setMediaVolume(audioManager, prefs, volPercent)
                    }
                    RingerChoice.SILENT -> {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        }
                        setMediaVolume(audioManager, prefs, volPercent)
                    }
                    RingerChoice.DND -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            if (notificationManager.isNotificationPolicyAccessGranted) {
                                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                            }
                        }
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        setMediaVolume(audioManager, prefs, volPercent)
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoSilent", "Error applying ringer mode", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }

        private fun setMediaVolume(audioManager: AudioManager, prefs: Prefs, percent: Int) {
            if (percent < 0) return
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (prefs.getSavedMediaVolume() == -1) {
                prefs.setSavedMediaVolume(current)
            }
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * percent) / 100
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }

        private fun restoreMediaVolume(audioManager: AudioManager, prefs: Prefs) {
            val saved = prefs.getSavedMediaVolume()
            if (saved >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
                prefs.setSavedMediaVolume(-1)
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
    }
}
