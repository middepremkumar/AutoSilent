package com.example.autosilent

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.autosilent.databinding.ActivityMainBinding
import com.example.autosilent.databinding.DialogEditScheduleBinding
import com.google.android.material.color.MaterialColors
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: ScheduleAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatusLabels()
            handler.postDelayed(this, 1000)
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatusLabels()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        
        // Apply theme before setContentView
        if (prefs.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateUiForTheme()

        setupRecyclerView()

        binding.tvModeLight.setOnClickListener {
            if (prefs.isDarkMode()) {
                prefs.setDarkMode(false)
                recreate()
            }
        }

        binding.tvModeDark.setOnClickListener {
            if (!prefs.isDarkMode()) {
                prefs.setDarkMode(true)
                recreate()
            }
        }

        binding.switchEnabled.isChecked = prefs.isEnabled()
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.setEnabled(isChecked)
            if (isChecked) {
                maybeRequestDndAccess()
                requestBatteryExemption()
            }
            AlarmScheduler.scheduleAll(this)
        }

        binding.switchJntua.isChecked = prefs.isJntuaEnabled()
        binding.switchJntua.setOnCheckedChangeListener { _, isChecked ->
            prefs.setJntuaEnabled(isChecked)
            AlarmScheduler.scheduleAll(this)
        }

        binding.layoutJntuaInfo.setOnClickListener {
            startActivity(Intent(this, JntuaActivity::class.java))
        }
        
        AlarmScheduler.scheduleAll(this)

        binding.fabAdd.setOnClickListener {
            showAddOptions()
        }

        binding.btnGrantDnd.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        binding.btnGrantBattery.setOnClickListener {
            requestBatteryExemption()
        }
    }

    private fun updateUiForTheme() {
        val isDark = prefs.isDarkMode()
        
        if (isDark) {
            binding.tvModeDark.background = ContextCompat.getDrawable(this, R.drawable.bg_segmented_thumb)
            binding.tvModeLight.background = null
            binding.tvModeDark.alpha = 1.0f
            binding.tvModeLight.alpha = 0.5f
        } else {
            binding.tvModeLight.background = ContextCompat.getDrawable(this, R.drawable.bg_segmented_thumb)
            binding.tvModeDark.background = null
            binding.tvModeLight.alpha = 1.0f
            binding.tvModeDark.alpha = 0.5f
        }
    }

    private fun setupRecyclerView() {
        binding.rvSchedules.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        adapter = ScheduleAdapter(
            schedules = prefs.getSchedules(),
            onEdit = { showEditDialog(it) },
            onDelete = { deleteSchedule(it) },
            onToggle = { schedule, isEnabled -> toggleSchedule(schedule, isEnabled) }
        )
        binding.rvSchedules.adapter = adapter
    }

    private fun toggleSchedule(schedule: Schedule, isEnabled: Boolean) {
        val schedules = prefs.getSchedules().toMutableList()
        val index = schedules.indexOfFirst { it.id == schedule.id }
        if (index != -1) {
            schedules[index] = schedule.copy(enabled = isEnabled)
            prefs.saveSchedules(schedules)
            adapter.updateData(schedules)
            AlarmScheduler.scheduleAll(this)
        }
    }

    private fun deleteSchedule(schedule: Schedule) {
        val schedules = prefs.getSchedules().toMutableList()
        schedules.removeAll { it.id == schedule.id }
        prefs.saveSchedules(schedules)
        adapter.updateData(schedules)
        AlarmScheduler.scheduleAll(this)
    }

    private fun showEditDialog(schedule: Schedule?) {
        val dialogBinding = DialogEditScheduleBinding.inflate(layoutInflater)
        val isNew = schedule == null
        
        var startMin = schedule?.startTimeMinutes ?: (9 * 60)
        var endMin = schedule?.endTimeMinutes ?: (17 * 60)
        var volume = schedule?.volumePercent ?: 0
        val selectedDays = schedule?.days?.toMutableSet() ?: mutableSetOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )

        dialogBinding.tvDialogStartTime.text = formatMinutes(startMin)
        dialogBinding.tvDialogEndTime.text = formatMinutes(endMin)
        dialogBinding.etDialogLabel.setText(schedule?.label ?: "")
        dialogBinding.sliderVolume.value = volume.toFloat()
        dialogBinding.tvVolumeLabel.text = "Media Volume: $volume%"
        
        dialogBinding.sliderVolume.addOnChangeListener { _, value, _ ->
            volume = value.toInt()
            dialogBinding.tvVolumeLabel.text = "Media Volume: $volume%"
        }

        dialogBinding.tvDialogStartTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startMin = h * 60 + m
                dialogBinding.tvDialogStartTime.text = formatMinutes(startMin)
            }, startMin / 60, startMin % 60, false).show()
        }
        dialogBinding.btnStartTime.setOnClickListener { dialogBinding.tvDialogStartTime.performClick() }
        
        dialogBinding.tvDialogEndTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endMin = h * 60 + m
                dialogBinding.tvDialogEndTime.text = formatMinutes(endMin)
            }, endMin / 60, endMin % 60, false).show()
        }
        dialogBinding.btnEndTime.setOnClickListener { dialogBinding.tvDialogEndTime.performClick() }

        when (schedule?.mode) {
            RingerChoice.SILENT -> dialogBinding.rgDialogMode.check(R.id.rbDialogSilent)
            RingerChoice.DND -> dialogBinding.rgDialogMode.check(R.id.rbDialogDnd)
            else -> dialogBinding.rgDialogMode.check(R.id.rbDialogVibrate)
        }

        val dayViews = mapOf(
            Calendar.SUNDAY to dialogBinding.cbDialogSun,
            Calendar.MONDAY to dialogBinding.cbDialogMon,
            Calendar.TUESDAY to dialogBinding.cbDialogTue,
            Calendar.WEDNESDAY to dialogBinding.cbDialogWed,
            Calendar.THURSDAY to dialogBinding.cbDialogThu,
            Calendar.FRIDAY to dialogBinding.cbDialogFri,
            Calendar.SATURDAY to dialogBinding.cbDialogSat
        )
        for ((day, cb) in dayViews) {
            cb.isChecked = selectedDays.contains(day)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isNew) "Add Schedule" else "Edit Schedule")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val mode = when (dialogBinding.rgDialogMode.checkedRadioButtonId) {
                    R.id.rbDialogSilent -> RingerChoice.SILENT
                    R.id.rbDialogDnd -> RingerChoice.DND
                    else -> RingerChoice.VIBRATE
                }
                
                val finalDays = dayViews.filter { it.value.isChecked }.keys
                val label = dialogBinding.etDialogLabel.text.toString().trim().takeIf { it.isNotEmpty() }
                
                val newSchedule = if (isNew) {
                    Schedule(label = label, startTimeMinutes = startMin, endTimeMinutes = endMin, days = finalDays, mode = mode, volumePercent = volume)
                } else {
                    schedule!!.copy(label = label, startTimeMinutes = startMin, endTimeMinutes = endMin, days = finalDays, mode = mode, volumePercent = volume)
                }

                val schedules = prefs.getSchedules().toMutableList()
                if (isNew) {
                    schedules.add(newSchedule)
                } else {
                    val index = schedules.indexOfFirst { it.id == schedule!!.id }
                    if (index != -1) schedules[index] = newSchedule
                }
                prefs.saveSchedules(schedules)
                adapter.updateData(schedules)
                AlarmScheduler.scheduleAll(this)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        
        // Make the dialog wider
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showAddOptions() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.cardAddSchedule).setOnClickListener {
            dialog.dismiss()
            showEditDialog(null)
        }

        dialogView.findViewById<View>(R.id.cardQuickSilence).setOnClickListener {
            dialog.dismiss()
            showQuickSilenceDialog()
        }

        dialog.show()
    }

    private fun showQuickSilenceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quick_silence, null)
        val etDuration = dialogView.findViewById<EditText>(R.id.etQuickDuration)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Start") { _, _ ->
                val mins = etDuration.text.toString().toIntOrNull() ?: 15
                startQuickSilence(mins)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startQuickSilence(minutes: Int) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            maybeRequestDndAccess()
            return
        }

        val endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        prefs.setQuickSilenceEnd(endTime)
        
        // Apply Silence
        AlarmReceiver.applyRingerMode(this, RingerChoice.SILENT, 0)
        
        // Schedule restoration
        val intent = Intent(this, QuickSilenceReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pendingIntent)
        
        updateStatusLabels()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            refreshReceiver,
            IntentFilter("com.example.autosilent.REFRESH_UI"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.post(updateRunnable)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(refreshReceiver)
        handler.removeCallbacks(updateRunnable)
    }

    override fun onResume() {
        super.onResume()
        updateStatusLabels()
    }

    private fun maybeRequestDndAccess() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun updateStatusLabels() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val modeText = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }
        val modeIcon = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> android.R.drawable.ic_lock_silent_mode
            AudioManager.RINGER_MODE_VIBRATE -> android.R.drawable.ic_lock_silent_mode
            else -> android.R.drawable.ic_menu_call
        }
        
        binding.tvCurrentMode.text = modeText
        binding.ivCurrentModeIcon.setImageResource(modeIcon)
        binding.ivCurrentModeIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, when(audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> R.color.ios_blue
                AudioManager.RINGER_MODE_VIBRATE -> R.color.ios_orange
                else -> R.color.ios_red
            })
        )

        val quickEnd = prefs.getQuickSilenceEnd()
        if (quickEnd > System.currentTimeMillis()) {
            val remaining = quickEnd - System.currentTimeMillis()
            val min = TimeUnit.MILLISECONDS.toMinutes(remaining)
            val sec = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
            binding.tvQuickSilenceTimer.text = String.format("Ends in: %02d:%02d", min, sec)
            binding.tvQuickSilenceTimer.visibility = View.VISIBLE
        } else {
            binding.tvQuickSilenceTimer.visibility = View.GONE
            if (quickEnd != 0L) prefs.setQuickSilenceEnd(0L)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val dndGranted = notificationManager.isNotificationPolicyAccessGranted
        binding.tvDndStatus.text = if (dndGranted) "DND Access Granted" else "DND Access Missing"
        binding.ivDndDot.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, if (dndGranted) R.color.ios_green else R.color.ios_orange))
        binding.btnGrantDnd.visibility = if (dndGranted) View.GONE else View.VISIBLE

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val exempted = powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text = if (exempted) "Battery Optimized" else "Battery Restricted"
        binding.ivBatteryDot.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, if (exempted) R.color.ios_green else R.color.ios_orange))
        binding.btnGrantBattery.visibility = if (exempted) View.GONE else View.VISIBLE
    }

    private fun requestBatteryExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%d:%02d %s", h12, m, amPm)
    }
}
