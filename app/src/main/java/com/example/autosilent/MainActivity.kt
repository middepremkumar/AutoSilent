package com.example.autosilent

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.Window
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.autosilent.databinding.ActivityMainBinding
import com.example.autosilent.databinding.DialogEditScheduleBinding
import com.google.android.material.color.MaterialColors
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: ScheduleAdapter

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
            showEditDialog(null)
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
