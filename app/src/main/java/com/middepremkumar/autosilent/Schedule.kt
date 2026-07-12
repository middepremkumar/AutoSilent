package com.middepremkumar.autosilent

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    var label: String? = null,
    var startTimeMinutes: Int,
    var endTimeMinutes: Int,
    var days: Set<Int>,
    var mode: RingerChoice,
    var enabled: Boolean = true,
    var volumePercent: Int = 0
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("label", label ?: "")
        json.put("start", startTimeMinutes)
        json.put("end", endTimeMinutes)
        json.put("days", JSONArray(days))
        json.put("mode", mode.name)
        json.put("enabled", enabled)
        json.put("volume", volumePercent)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): Schedule {
            val daysArray = json.getJSONArray("days")
            val daysSet = mutableSetOf<Int>()
            for (i in 0 until daysArray.length()) {
                daysSet.add(daysArray.getInt(i))
            }
            return Schedule(
                id = json.getString("id"),
                label = json.optString("label").takeIf { it.isNotEmpty() },
                startTimeMinutes = json.getInt("start"),
                endTimeMinutes = json.getInt("end"),
                days = daysSet,
                mode = RingerChoice.valueOf(json.getString("mode")),
                enabled = json.optBoolean("enabled", true),
                volumePercent = json.optInt("volume", 0)
            )
        }
    }
}
