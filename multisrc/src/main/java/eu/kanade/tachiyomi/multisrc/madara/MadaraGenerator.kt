package eu.kanade.tachiyomi.multisrc.madara

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MadaraGenerator : ThemeSourceGenerator {

    override val themePkg = "madara"

    override val themeClass = "Madara"

    override val baseVersionCode: Int = 30

    override val sources = listOf(
        MultiLang("Reaper Scans", "https://reaperscans.com", listOf("fr", "tr"), className = "ReaperScansFactory", pkgName = "reaperscans", overrideVersionCode = 12),
        SingleLang("1st Kiss Manga.love", "https://1stkissmanga.love", "en", className = "FirstKissMangaLove", overrideVersionCode = 2),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MadaraGenerator().createAll()
        }
    }
}
