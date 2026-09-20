package com.example.data.stream.detector

import com.example.data.stream.model.ContainerFormat

/**
 * Sniffer byte signature untuk mendeteksi container binary media
 * (MP4 ftyp, MKV/WebM EBML, MPEG-TS sync byte 0x47, FLV header, dll).
 */
object ContainerSniffer {

    /**
     * Menganalisis potongan bytes awal (header chunk) dan URL untuk menentukan container format nyata.
     */
    fun sniff(bytes: ByteArray, url: String = ""): ContainerFormat {
        val lowerUrl = url.lowercase()

        if (bytes.size >= 8) {
            // 1. MP4 / FMP4: Box header [size: 4 bytes][type: 4 bytes] (e.g. 'ftyp', 'moov', 'styp')
            val boxType = String(bytes.sliceArray(4..7), Charsets.US_ASCII)
            if (boxType == "ftyp" || boxType == "moov" || boxType == "isom" || boxType == "mp42") {
                return ContainerFormat.MP4
            }
            if (boxType == "styp" || boxType == "msdh" || boxType == "msix") {
                return ContainerFormat.FMP4
            }
        }

        // 2. EBML Header untuk Matroska (.mkv) dan WebM: 0x1A 0x45 0xDF 0xA3
        if (bytes.size >= 4) {
            if ((bytes[0].toInt() and 0xFF) == 0x1A &&
                (bytes[1].toInt() and 0xFF) == 0x45 &&
                (bytes[2].toInt() and 0xFF) == 0xDF &&
                (bytes[3].toInt() and 0xFF) == 0xA3
            ) {
                // Bedakan WebM vs MKV dari DocType string di dalam EBML header jika terbaca
                val headerSnippet = String(bytes.take(64).toByteArray(), Charsets.US_ASCII)
                return if (headerSnippet.contains("webm", ignoreCase = true) || lowerUrl.contains(".webm")) {
                    ContainerFormat.WEBM
                } else {
                    ContainerFormat.MATROSKA
                }
            }
        }

        // 3. FLV Header: "FLV" (0x46 0x4C 0x56)
        if (bytes.size >= 3) {
            if (bytes[0] == 0x46.toByte() && bytes[1] == 0x4C.toByte() && bytes[2] == 0x56.toByte()) {
                return ContainerFormat.FLV
            }
        }

        // 4. MPEG-TS: Sync byte 0x47 berulang setiap 188 bytes
        if (bytes.size >= 188) {
            if ((bytes[0].toInt() and 0xFF) == 0x47 && (bytes[188].toInt() and 0xFF) == 0x47) {
                return ContainerFormat.MPEG_TS
            }
        } else if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) == 0x47) {
            if (lowerUrl.contains(".ts")) {
                return ContainerFormat.MPEG_TS
            }
        }

        // 5. OGG Header: "OggS"
        if (bytes.size >= 4) {
            val magic = String(bytes.sliceArray(0..3), Charsets.US_ASCII)
            if (magic == "OggS") {
                return ContainerFormat.OGG
            }
        }

        // 6. RIFF / WAV: "RIFF....WAVE"
        if (bytes.size >= 12) {
            val riff = String(bytes.sliceArray(0..3), Charsets.US_ASCII)
            val wave = String(bytes.sliceArray(8..11), Charsets.US_ASCII)
            if (riff == "RIFF" && wave == "WAVE") {
                return ContainerFormat.WAV
            }
        }

        // 7. AAC ADTS: Syncword 0xFFF (0xFF followed by 0xF0..0xFF)
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xF0
            if (b0 == 0xFF && b1 == 0xF0) {
                return ContainerFormat.ADTS
            }
        }

        // 8. Fallback heuristic URL extension
        return when {
            lowerUrl.contains(".mp4") || lowerUrl.contains("videoplayback") -> ContainerFormat.MP4
            lowerUrl.contains(".webm") -> ContainerFormat.WEBM
            lowerUrl.contains(".mkv") -> ContainerFormat.MATROSKA
            lowerUrl.contains(".ts") -> ContainerFormat.MPEG_TS
            lowerUrl.contains(".flv") -> ContainerFormat.FLV
            lowerUrl.contains(".m4s") -> ContainerFormat.FMP4
            lowerUrl.contains(".mp3") -> ContainerFormat.MP3
            lowerUrl.contains(".aac") -> ContainerFormat.ADTS
            else -> ContainerFormat.UNKNOWN
        }
    }
}
