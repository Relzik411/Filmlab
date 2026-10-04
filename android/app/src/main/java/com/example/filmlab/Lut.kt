package com.example.filmlab

import android.content.res.AssetManager

/** A 3D colour lookup table from a `.cube` file. RGB triples, red changing fastest. */
class Lut(val id: String, val name: String, val size: Int, val table: FloatArray) {

    companion object {
        /** Display order for the bundled looks. Any other .cube file is listed after these. */
        private val preferredOrder = listOf(
            "Golden", "Portrait", "Mint", "Everyday", "Summer", "Vivid", "Classic", "Warm",
            "Dusk", "Winter", "Instant", "Faded", "Cross", "Mono", "Grit",
        )

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

        /** Loads every `.cube` file in the app's assets. */
        fun loadAll(assets: AssetManager): List<Lut> {
            val files = assets.list("").orEmpty().filter { it.endsWith(".cube") }
            val luts = files.mapNotNull { file ->
                runCatching {
                    val text = assets.open(file).bufferedReader().use { it.readText() }
                    parse(file.removeSuffix(".cube"), text)
                }.getOrNull()
            }
            return luts.sortedWith(
                compareBy<Lut>({ preferredOrder.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.name })
            )
        }
    }
}
