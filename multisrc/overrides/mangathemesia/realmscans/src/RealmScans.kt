package eu.kanade.tachiyomi.extension.en.realmscans

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.TimeUnit

class RealmScans : MangaThemesia("Realm Scans", "https://realmscans.xyz", "en", "/series") {

    private val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::urlChangeInterceptor)
        .addInterceptor(uaIntercept)
        .rateLimit(1, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document)
            .distinctBy { it.imageUrl }
            .mapIndexed { i, page -> Page(i, imageUrl = page.imageUrl) }
    }

    // Permanent Url for Manga/Chapter End
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return super.fetchPopularManga(page).tempUrlToPermIfNeeded()
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return super.fetchLatestUpdates(page).tempUrlToPermIfNeeded()
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters).tempUrlToPermIfNeeded()
    }

    // Temp Url for manga/chapter
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val newManga = manga.titleToUrlFrag()

        return super.fetchChapterList(newManga)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val newManga = manga.titleToUrlFrag()

        return super.fetchMangaDetails(newManga)
    }

    override fun getMangaUrl(manga: SManga): String {
        val dbSlug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")

        val storedSlug = getSlugMap()[dbSlug] ?: dbSlug

        return "$baseUrl$mangaUrlDirectory/$storedSlug/"
    }

    private fun Observable<MangasPage>.tempUrlToPermIfNeeded(): Observable<MangasPage> {
        return this.map { mangasPage ->
            MangasPage(
                mangasPage.mangas.map { it.tempUrlToPermIfNeeded() },
                mangasPage.hasNextPage,
            )
        }
    }

    private fun SManga.tempUrlToPermIfNeeded(): SManga {
        if (!preferences.permaUrlPref) return this

        val slugMap = getSlugMap().toMutableMap()

        val sMangaTitleFirstWord = this.title.split(" ")[0]
        if (!this.url.contains("/$sMangaTitleFirstWord", ignoreCase = true)) {
            val currentSlug = this.url
                .removeSuffix("/")
                .substringAfterLast("/")

            val permaSlug = currentSlug.replaceFirst(TEMP_TO_PERM_REGEX, "")

            slugMap[permaSlug] = currentSlug

            this.url = "$mangaUrlDirectory/$permaSlug/"
        }
        putSlugMap(slugMap)
        return this
    }

    private fun urlChangeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val frag = request.url.fragment

        if (frag.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val dbSlug = request.url.toString()
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")

        val slugMap = getSlugMap().toMutableMap()

        val storedSlug = slugMap[dbSlug] ?: dbSlug

        val response = chain.proceed(
            request.newBuilder()
                .url("$baseUrl$mangaUrlDirectory/$storedSlug/")
                .build(),
        )

        if (!response.isSuccessful && response.code == 404) {
            response.close()

            val newSlug = getNewSlug(storedSlug, frag)
                ?: throw IOException("Migrate from Realm to Realm")

            slugMap[dbSlug] = newSlug
            putSlugMap(slugMap)

            return chain.proceed(
                request.newBuilder()
                    .url("$baseUrl$mangaUrlDirectory/$newSlug/")
                    .build(),
            )
        }

        return response
    }

    private fun getNewSlug(existingSlug: String, search: String): String? {
        val permaSlug = existingSlug
            .replaceFirst(TEMP_TO_PERM_REGEX, "")

        val mangas = client.newCall(searchMangaRequest(1, search, FilterList()))
            .execute()
            .use {
                searchMangaParse(it)
            }

        return mangas.mangas.firstOrNull { newManga ->
            newManga.url.contains(permaSlug, true)
        }
            ?.url
            ?.removeSuffix("/")
            ?.substringAfterLast("/")
    }

    private fun putSlugMap(slugMap: MutableMap<String, String>) {
        val serialized = json.encodeToString(slugMap)

        preferences.edit().putString(PREF_URL_MAP, serialized).commit()
    }

    private fun getSlugMap(): Map<String, String> {
        val serialized = preferences.getString(PREF_URL_MAP, null) ?: return emptyMap()

        return try {
            json.decodeFromString(serialized)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun String.toSearchQuery(): String {
        return this.trim()
            .lowercase()
            .replace(titleSpecialCharactersRegex, "+")
            .replace(trailingPlusRegex, "")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_PERM_MANGA_URL_KEY_PREFIX + lang
            title = PREF_PERM_MANGA_URL_TITLE
            summary = PREF_PERM_MANGA_URL_SUMMARY
            setDefaultValue(true)
        }.also(screen::addPreference)

        addRandomAndCustomUserAgentPreferences(screen)
    }

    private val SharedPreferences.permaUrlPref
        get() = getBoolean(PREF_PERM_MANGA_URL_KEY_PREFIX + lang, true)

    companion object {
        private const val PREF_PERM_MANGA_URL_KEY_PREFIX = "pref_permanent_manga_url_2_"
        private const val PREF_PERM_MANGA_URL_TITLE = "Permanent Manga URL"
        private const val PREF_PERM_MANGA_URL_SUMMARY = "Turns all manga urls into permanent ones."
        private const val PREF_URL_MAP = "pref_url_map"
        private val TEMP_TO_PERM_REGEX = Regex("""^\d+-""")
        private val titleSpecialCharactersRegex = Regex("""[^a-z0-9]+""")
        private val trailingPlusRegex = Regex("""\++$""")
    }
}
