package com.example.filmlab

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Keeps each photo's edits on disk, keyed by a fingerprint of the photo's contents, so reopening
 * the same photo brings its edits back however it was opened. Mirrors EditStore.swift.
 */
object EditStore {
    fun key(context: Context, uri: Uri): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun load(context: Context, key: String): EditState? = runCatching {
        val file = file(context, key)
        if (!file.exists()) null else fromJson(JSONObject(file.readText()).getJSONObject("edit"))
    }.getOrNull()

    /** Saves the edit, or removes the saved file when the photo is back to its original state. */
    fun save(context: Context, key: String, edit: EditState) {
        val file = file(context, key)
        if (edit == EditState()) {
            file.delete()
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val json = JSONObject().put("version", 1).put("edit", toJson(edit))
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.toString())
            temp.renameTo(file)
        }
    }

    private fun file(context: Context, key: String) = File(File(context.filesDir, "edits"), "$key.json")

    internal fun toJson(e: EditState) = JSONObject().apply {
        put("lutId", e.lutId ?: JSONObject.NULL)
        put("intensity", e.intensity.toDouble())
        Adjustment.entries.forEach { put(it.name, it.get(e).toDouble()) }
        put("leak", e.leak ?: JSONObject.NULL)
        put("leakAmount", e.leakAmount.toDouble())
        put("leakPlacement", e.leakPlacement)
        put("crop", JSONObject().apply {
            put("quarterTurns", e.crop.quarterTurns)
            put("flipped", e.crop.flipped)
            put("straighten", e.crop.straighten.toDouble())
            put("left", e.crop.rect.left.toDouble())
            put("top", e.crop.rect.top.toDouble())
            put("right", e.crop.rect.right.toDouble())
            put("bottom", e.crop.rect.bottom.toDouble())
            put("aspect", e.crop.aspect.name)
        })
    }

    internal fun fromJson(j: JSONObject): EditState {
        var edit = EditState(
            lutId = if (j.isNull("lutId")) null else j.getString("lutId"),
            intensity = j.optDouble("intensity", 1.0).toFloat(),
            leak = if (j.isNull("leak")) null else j.getInt("leak"),
            leakAmount = j.optDouble("leakAmount", 0.8).toFloat(),
            leakPlacement = j.optInt("leakPlacement", 0),
        )
        Adjustment.entries.forEach { edit = it.set(edit, j.optDouble(it.name, 0.0).toFloat()) }
        j.optJSONObject("crop")?.let { c ->
            edit = edit.copy(crop = CropState(
                quarterTurns = c.optInt("quarterTurns", 0),
                flipped = c.optBoolean("flipped", false),
                straighten = c.optDouble("straighten", 0.0).toFloat(),
                rect = NRect(
                    c.optDouble("left", 0.0).toFloat(),
                    c.optDouble("top", 0.0).toFloat(),
                    c.optDouble("right", 1.0).toFloat(),
                    c.optDouble("bottom", 1.0).toFloat(),
                ),
                aspect = runCatching { CropAspect.valueOf(c.getString("aspect")) }.getOrDefault(CropAspect.Free),
            ))
        }
        return edit
    }
}
