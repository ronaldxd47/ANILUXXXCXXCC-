package com.example.data

/**
 * Enum sumber konten terpusat (DRY) untuk routing URL, badge UI, dan filter kategori.
 */
enum class AnimeSource(val displayName: String, val isDonghuaDefault: Boolean) {
    SAMEHADAKU("Samehadaku", false),
    ANICHIN("Anichin", true),
    DONGHUB("Donghive", true),
    MAL("MyAnimeList", false),
    UNKNOWN("Sumber", false);

    companion object {
        fun fromUrl(url: String): AnimeSource {
            val lower = url.lowercase()
            return when {
                lower.contains("anichin") -> ANICHIN
                lower.contains("donghub") || lower.contains("donghive") -> DONGHUB
                lower.contains("samehadaku") -> SAMEHADAKU
                lower.contains("myanimelist.net") -> MAL
                else -> UNKNOWN
            }
        }

        fun isDonghua(anime: ScrapedAnime): Boolean {
            val source = fromUrl(anime.link)
            return source.isDonghuaDefault || anime.type.equals("Donghua", ignoreCase = true)
        }

        fun isDonghuaUrl(url: String): Boolean {
            return fromUrl(url).isDonghuaDefault
        }
    }
}
