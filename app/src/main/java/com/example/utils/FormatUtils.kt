package com.example.utils

/**
 * Utility helper functions untuk formatting teks, nama episode, dan badge tampilan.
 */
object FormatUtils {

    /**
     * Menyederhanakan judul episode yang panjang atau kotor dari scraper menjadi format yang bersih dan rapi.
     * Contoh:
     * - "Episode 12 Subtitle Indonesia" -> "Episode 12"
     * - "Doupo Cangqiong Episode 118 Sub Indo [SVIP]" -> "Episode 118"
     * - "Ep 08" -> "Episode 8"
     * - "12" -> "Episode 12"
     */
    fun formatEpisodeTitle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Episode 1"

        // 1. Ekstrak pola Episode / Ep / Eps diikuti nomor (misal Episode 12, Eps 118, Ep. 05, Episode 12.5)
        val match = Regex("""(?i)(?:episode|eps|ep\.?|e)\s*(\d+(?:\.\d+)?)""").find(trimmed)
        if (match != null) {
            val numStr = match.groupValues[1]
            // Format angka tanpa leading zero yang tidak perlu jika lebih dari 1 digit (misal 05 -> 5)
            val numInt = numStr.toIntOrNull()
            val formattedNum = if (numInt != null) numInt.toString() else numStr
            return "Episode $formattedNum"
        }

        // 2. Jika seluruh string adalah angka murni
        if (trimmed.all { it.isDigit() }) {
            return "Episode $trimmed"
        }

        // 3. Bersihkan noise umum seperti Subtitle Indonesia, Sub Indo, [SVIP], dll
        var cleaned = trimmed
            .replace(Regex("""(?i)\s*subtitle\s+indonesia"""), "")
            .replace(Regex("""(?i)\s*sub\s*indo"""), "")
            .replace(Regex("""(?i)\s*end\b"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .trim()

        return cleaned.ifEmpty { "Episode 1" }
    }

    /**
     * Versi ringkas untuk pill/chip label (misal: "Eps 12")
     */
    fun formatEpisodeShort(raw: String): String {
        val full = formatEpisodeTitle(raw)
        return full.replace("Episode ", "Eps ")
    }
}
