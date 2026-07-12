package com.example.autosilent

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.autosilent.databinding.ActivityJntuaBinding
import com.example.autosilent.databinding.DialogEditScheduleBinding
import java.util.Calendar

class JntuaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJntuaBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (prefs.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding = ActivityJntuaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            schedules = prefs.getJntuaSchedules(),
            onEdit = { showEditDialog(it) },
            onDelete = { /* Disable delete for JNTUA template to keep it simple */ },
            onToggle = { _, _ -> /* Toggled together in main screen */ }
        )
        binding.rvJntuaSchedules.adapter = adapter
    }

    private fun showEditDialog(schedule: Schedule) {
        val dialogBinding = DialogEditScheduleBinding.inflate(layoutInflater)
        
        var startMin = schedule.startTimeMinutes
        var endMin = schedule.endTimeMinutes
        var volume = schedule.volumePercent
        val selectedDays = schedule.days.toMutableSet()

        dialogBinding.tvDialogStartTime.text = formatMinutes(startMin)
        dialogBinding.tvDialogEndTime.text = formatMinutes(endMin)
        dialogBinding.etDialogLabel.setText(schedule.label ?: "")
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

        when (schedule.mode) {
            RingerChoice.SILENT -> dialogBinding.rgDialogMode.check(R.id.rbDialogSilent)
            RingerChoice.DND -> dialogBinding.rgDialogMode.check(R.id.rbDialogDnd)
            else -> dialogBinding.rgDialogMode.check(R.id.rbDialogVibrate)
        }

        val dayViews = mapOf(
            Calendar.MONDAY to dialogBinding.cbDialogMon,
            Calendar.TUESDAY to dialogBinding.cbDialogTue,
            Calendar.WEDNESDAY to dialogBinding.cbDialogWed,
            Calendar.THURSDAY to dialogBinding.cbDialogThu,
            Calendar.FRIDAY to dialogBinding.cbDialogFri,
            Calendar.SATURDAY to dialogBinding.cbDialogSat,
            Calendar.SUNDAY to dialogBinding.cbDialogSun
        )
        for ((day, cb) in dayViews) {
            cb.isChecked = selectedDays.contains(day)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit JNTUA Session")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val mode = when (dialogBinding.rgDialogMode.checkedRadioButtonId) {
                    R.id.rbDialogSilent -> RingerChoice.SILENT
                    R.id.rbDialogDnd -> RingerChoice.DND
                    else -> RingerChoice.VIBRATE
                }
                
                val finalDays = dayViews.filter { it.value.isChecked }.keys
                val label = dialogBinding.etDialogLabel.text.toString().trim()
                
                val updatedSchedule = schedule.copy(
                    label = label,
                    startTimeMinutes = startMin,
                    endTimeMinutes = endMin,
                    days = finalDays,
                    mode = mode,
                    volumePercent = volume
                )

                val schedules = prefs.getJntuaSchedules().toMutableList()
                val index = schedules.indexOfFirst { it.id == schedule.id }
                if (index != -1) {
                    schedules[index] = updatedSchedule
                    prefs.saveJntuaSchedules(schedules)
                    adapter.updateData(schedules)
                    if (prefs.isJntuaEnabled()) {
                        AlarmScheduler.scheduleAll(this)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
