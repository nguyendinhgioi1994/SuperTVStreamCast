package com.tuntech.supertvstreamcast.domain

/**
 * A language the app ships strings for, named in its own language. [aliases] are the legacy codes
 * some platforms still report (Java's `in` for Indonesian, `tl` for Filipino).
 */
enum class AppLanguage(val code: String, val nativeName: String, val aliases: List<String> = emptyList()) {
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    PORTUGUESE("pt", "Português"),
    INDONESIAN("id", "Bahasa Indonesia", listOf("in")),
    FILIPINO("fil", "Filipino", listOf("tl")),
    HINDI("hi", "हिन्दी"),
    THAI("th", "ไทย"),
    VIETNAMESE("vi", "Tiếng Việt"),
    CHINESE("zh", "中文"),
    KOREAN("ko", "한국어"),
    JAPANESE("ja", "日本語"),
    TURKISH("tr", "Türkçe");

    companion object {
        /**
         * The stored choice, else the device language ("vi-VN", "en_US"); a language the app does
         * not ship falls back to English, the base strings.
         */
        fun resolve(saved: String?, system: String): AppLanguage {
            val tag = saved?.takeIf { it.isNotBlank() } ?: system
            val code = tag.substringBefore('-').substringBefore('_').lowercase()
            return entries.firstOrNull { it.code == code || code in it.aliases } ?: ENGLISH
        }
    }
}
