package com.middepremkumar.autosilent

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.middepremkumar.autosilent.databinding.ItemScheduleBinding
import java.util.Calendar

class ScheduleAdapter(
    private var schedules: List<Schedule>,
    private val onEdit: (Schedule) -> Unit,
    private val onDelete: (Schedule) -> Unit,
    private val onToggle: (Schedule, Boolean) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        
        if (!schedule.label.isNullOrEmpty()) {
            holder.binding.tvLabel.text = schedule.label
            holder.binding.tvLabel.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvLabel.visibility = android.view.View.GONE
        }

        holder.binding.tvTimeRange.text = "${formatMinutes(schedule.startTimeMinutes)} - ${formatMinutes(schedule.endTimeMinutes)}"
        holder.binding.tvDays.text = formatDays(schedule.days)
        holder.binding.tvMode.text = "${schedule.mode.name} • ${schedule.volumePercent}% Vol"
        holder.binding.switchScheduleEnabled.isChecked = schedule.enabled

        // Use a flag or remove listener before setting isChecked to avoid triggering toggle during bind
        holder.binding.switchScheduleEnabled.setOnCheckedChangeListener(null)
        holder.binding.switchScheduleEnabled.isChecked = schedule.enabled
        holder.binding.switchScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(schedule, isChecked)
        }

        holder.binding.btnEdit.setOnClickListener { onEdit(schedule) }
        holder.binding.btnDelete.setOnClickListener { onDelete(schedule) }
    }

    override fun getItemCount() = schedules.size

    fun updateData(newSchedules: List<Schedule>) {
        schedules = newSchedules
        notifyDataSetChanged()
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

    private fun formatDays(days: Set<Int>): String {
        if (days.size == 7) return "Every day"
        val dayNames = mapOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun"
        )
        return days.sorted().joinToString(", ") { dayNames[it] ?: "" }
    }
}
