package com.example.filmlab

import android.content.res.AssetManager
import org.json.JSONObject

/** A 3D colour lookup table from a `.cube` file. RGB triples, red changing fastest. */
class Lut(val id: String, val name: String, val size: Int, val table: FloatArray) {

    companion object {
        private val whitespace = Regex("\\s+")

        fun parse(id: String, text: String): Lut {
            var size = 0
            var title: String? = null
            var table = FloatArray(0)
            var count = 0

            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.startsWith("TITLE")) {
                    title = line.removePrefix("TITLE").trim().trim('"')
                } else if (line.startsWith("LUT_3D_SIZE")) {
                    size = line.split(whitespace).last().toInt()
                    table = FloatArray(size * size * size * 3)
                } else if (line[0].isLetter()) {
                    continue // DOMAIN_MIN/MAX and other keywords; only 0...1 LUTs are supported
                } else {
                    val parts = line.split(whitespace)
                    require(parts.size == 3 && count + 3 <= table.size) { "$id is not a 3D .cube LUT" }
                    for (part in parts) table[count++] = part.toFloat()
                }
            }
            require(size > 1 && count == table.size) { "$id is not a 3D .cube LUT" }
            return Lut(id, title ?: id, size, table)
        }
    }
}

/** A named group of looks, as listed in looks.json. */
data class LookCategory(val name: String, val lutIds: List<String>)

/**
 * The bundled looks and how they are grouped. looks.json (next to the .cube files, shared with iOS)
 * sets the categories and their order; any .cube file it doesn't list goes under "More".
 */
class LookLibrary(val luts: List<Lut>, val categories: List<LookCategory>) {
    companion object {
        fun load(assets: AssetManager): LookLibrary {
            val byId = assets.list("").orEmpty()
                .filter { it.endsWith(".cube") }
                .mapNotNull { file ->
                    runCatching {
                        val text = assets.open(file).bufferedReader().use { it.readText() }
                        Lut.parse(file.removeSuffix(".cube"), text)
                    }.getOrNull()
                }
                .associateBy { it.id }

            val categories = mutableListOf<LookCategory>()
            runCatching {
                val json = JSONObject(assets.open("looks.json").bufferedReader().use { it.readText() })
                val list = json.getJSONArray("categories")
                for (i in 0 until list.length()) {
                    val c = list.getJSONObject(i)
                    val looks = c.getJSONArray("looks")
                    val ids = (0 until looks.length()).map { looks.getString(it) }.filter { it in byId }
                    categories += LookCategory(c.getString("name"), ids)
                }
            }
            val listed = categories.flatMap { it.lutIds }.toSet()
            val unlisted = byId.values.filter { it.id !in listed }.sortedBy { it.name }.map { it.id }
            if (unlisted.isNotEmpty()) categories += LookCategory("More", unlisted)
            categories.removeAll { it.lutIds.isEmpty() }

            val ordered = categories.flatMap { it.lutIds }.distinct().mapNotNull { byId[it] }
            return LookLibrary(ordered, categories)
        }
    }
}
