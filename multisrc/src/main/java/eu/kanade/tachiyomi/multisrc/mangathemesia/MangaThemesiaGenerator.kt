package eu.kanade.tachiyomi.multisrc.mangathemesia

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

// Formerly WPMangaStream & WPMangaReader -> MangaThemesia
class MangaThemesiaGenerator : ThemeSourceGenerator {

    override val themePkg = "mangathemesia"

    override val themeClass = "MangaThemesia"

    override val baseVersionCode: Int = 25

    override val sources = listOf(
        MultiLang("Asura Scans", "https://www.asurascans.com", listOf("en", "tr"), className = "AsuraScansFactory", pkgName = "asurascans", overrideVersionCode = 23),
        MultiLang("Flame Scans", "https://flamescans.org", listOf("en"), className = "FlameScansFactory", pkgName = "flamescans", overrideVersionCode = 4),
        SingleLang("Animated Glitched Scans", "https://anigliscans.com", "en"),
        SingleLang("Surya Scans", "https://suryascans.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaThemesiaGenerator().createAll()
        }
    }
}
