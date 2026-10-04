package com.example.filmlab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A saved look: the look, its strength, adjustments and effects. Crop belongs to each photo, so it is left out. */
data class Preset(val id: String = UUID.randomUUID().toString(), val name: String, val edit: EditState)

object PresetStore {
    fun load(context: Context): List<Preset> = runCatching {
        val file = file(context)
        if (!file.exists()) return emptyList()
        val array = JSONObject(file.readText()).getJSONArray("presets")
        (0 until array.length()).map { i ->
            val p = array.getJSONObject(i)
            Preset(p.getString("id"), p.getString("name"), EditStore.fromJson(p.getJSONObject("edit")))
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, presets: List<Preset>) {
        runCatching {
            val array = JSONArray()
            presets.forEach {
                array.put(JSONObject().put("id", it.id).put("name", it.name).put("edit", EditStore.toJson(it.edit)))
            }
            val file = file(context)
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSONObject().put("version", 1).put("presets", array).toString())
            temp.renameTo(file)
        }
    }

    private fun file(context: Context) = File(context.filesDir, "presets.json")
}
