package com.music.mhorpix

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.music.mhorpix.auth.AuthStore
import com.music.mhorpix.data.DebugLog
import com.music.mhorpix.data.lyrics.LyricsTranslation
import androidx.appcompat.app.AppCompatDelegate
import com.music.mhorpix.ui.player.AndroidPlayerHost
import com.music.mhorpix.ui.player.PlayerPlatform
import com.music.mhorpix.data.canvas.CanvasCache
import com.music.mhorpix.data.smb.SmbCoverFetcher
import com.music.mhorpix.data.webdav.WebDavCoilAuth
import com.music.mhorpix.data.canvas.SpotifyToken
import com.music.mhorpix.data.spotify.SpotifyLibrary
import com.music.mhorpix.playback.AudioCache
import com.music.mhorpix.playback.LastPlayed
import com.music.mhorpix.playback.OriginalVersion
import com.music.mhorpix.data.innertube.Innertube
import com.music.mhorpix.data.innertube.AndroidStreamHooks
import com.music.mhorpix.data.listentogether.ListenTogether
import com.music.mhorpix.data.scrobbling.LastFM
import com.music.mhorpix.data.settings.AppSettings
import com.music.mhorpix.data.settings.SearchHistory
import com.music.mhorpix.data.sources.SourceRegistry
import com.music.mhorpix.data.stats.ArtistFacts
import com.music.mhorpix.data.stats.ListeningStats
import com.music.mhorpix.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class MhorpixApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // The player is drawn by the shared UI module; this is what it reads
        // underneath — settings, the Canvas decoder, outputs, the party.
        AndroidStreamHooks.installEarly()
        PlayerPlatform.install(AndroidPlayerHost(this))
        com.music.mhorpix.ui.AppUi.install(com.music.mhorpix.ui.AndroidAppUiHost)
        // The shared data layer (lyrics, the YouTube Music client) logs to
        // logcat on debug builds only, as the app's own DebugLog always has.
        if (BuildConfig.DEBUG) {
            DebugLog.sink = DebugLog.Sink { level, tag, message, error ->
                when (level) {
                    'D' -> android.util.Log.d(tag, message, error)
                    'I' -> android.util.Log.i(tag, message, error)
                    'W' -> android.util.Log.w(tag, message, error)
                    else -> android.util.Log.e(tag, message, error)
                }
            }
        }
        // The Spotify library and import live in the shared module; the tokens are the WebView's.
        SpotifyLibrary.auth = SpotifyLibrary.Auth {
            SpotifyToken.accessToken()?.let { SpotifyLibrary.Tokens(it, SpotifyToken.clientToken()) }
        }
        // The per-app language picker, which YouTube Music's `hl` follows.
        // Full tag (zh-Hant / zh-Hans preserved); Innertube maps to hl/gl.
        Innertube.appLanguage = { AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() }
        migrateScriptOnlyAppLocale()
        LyricsTranslation.cacheDir = cacheDir
        // PlaybackService shares this process, so seeding the cookie here means
        // stream resolution is authenticated from the first play onwards.
        authStore = AuthStore(this)
        // Catch any fatal uncaught exception and log before crash to aid debugging
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            android.util.Log.e("Mhorpix", "Fatal crash on thread ${t.name}: ${e.message}", e)
            defaultHandler?.uncaughtException(t, e)
        }

        // Opened off the main thread, alongside everything below: none of these
        // reads a setting or the session, and between them they are the slowest
        // opens at startup — SourceRegistry's encrypted store most of all.
        // Started only once [AuthStore] exists, because both encrypted stores
        // share one keystore master key and a first launch must not have two
        // threads racing to create it. Joined before onCreate returns with a timeout
        // to prevent startup ANR lockups.
        val backgroundInit = thread(name = "startup-init") {
            runCatching { SourceRegistry.init(this) }
                .onFailure { android.util.Log.e("Mhorpix", "SourceRegistry.init failed", it) }
            runCatching { AndroidStreamHooks.initInnerTubeX(this) }
                .onFailure { android.util.Log.e("Mhorpix", "initInnerTubeX failed", it) }
            runCatching { CanvasCache.init(this) }
                .onFailure { android.util.Log.e("Mhorpix", "CanvasCache.init failed", it) }
        }
        // Migration-safe: an old single cookie becomes the first encrypted
        // session, while newer installs restore the profile the listener chose.
        runCatching {
            val restoredSession = authStore.activeSession
            if (restoredSession != null && authStore.activeAccountId == null) {
                authStore.select(restoredSession.accountId, restoredSession.activeProfileId)
            }
            authStore.cookie = restoredSession?.cookie
            Innertube.cookie = restoredSession?.cookie
            if (restoredSession != null) {
                restoredSession.profiles.firstOrNull { it.profileId == restoredSession.activeProfileId }
                    ?.let { Innertube.selectChannel(it.pageId, it.dataSyncId, it.authUser) }
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { Innertube.ensureSessionScope() }
                }
            }
        }.onFailure { android.util.Log.e("Mhorpix", "AuthStore session restore failed", it) }

        runCatching { AppSettings.init(this, authStore) }
            .onFailure { android.util.Log.e("Mhorpix", "AppSettings.init failed", it) }
        runCatching { com.music.mhorpix.playback.audio.LosslessOutput.init(this) }
        runCatching { ListenTogether.init(this) }
        runCatching { SearchHistory.init(this) }
        runCatching { LastPlayed.init(this) }
        runCatching { com.music.mhorpix.playback.PartyPersonalQueueStash.init(this) }
        runCatching { OriginalVersion.init(this) }
        runCatching { Downloads.init(this) }
        runCatching { ListeningStats.init(this) }
        runCatching { ArtistFacts.init(this) }
        runCatching { AudioCache.init(this) }
            .onFailure { android.util.Log.e("Mhorpix", "AudioCache.init failed", it) }
        runCatching { SpotifyToken.init(this) }

        runCatching {
            if (AppSettings.consumeVersionUpdate(BuildConfig.VERSION_CODE)) {
                AudioCache.clear()
                SingletonImageLoader.get(this).let { loader ->
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
            }
        }.onFailure { android.util.Log.e("Mhorpix", "Version update cache wipe failed", it) }

        runCatching { initLastfm() }
        runCatching { com.music.mhorpix.data.presence.AndroidPresence.install(this) }
            .onFailure { android.util.Log.e("Mhorpix", "AndroidPresence.install failed", it) }

        // Bounded join so startup cannot hang or trigger an ANR if a background open takes longer
        runCatching { backgroundInit.join(3000L) }
    }

    /**
     * Artwork loading, which was previously left entirely on Coil's defaults.
     *
     * The defaults aren't unreasonable, but the disk cache is sized at 2% of
     * free space — which on a full phone is the 10MB floor, a few screens of
     * covers, and covers are exactly the thing worth still having tomorrow.
     * Naming a directory alongside it keeps that cache somewhere identifiable
     * rather than in the process's temp dir.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Covers on the WebDAV server need the credential or every one
            // of them 401s — which reads as "this track has no artwork".
            // Coil's own transport never sees Http.client's interceptor, so
            // the header is attached per request instead. See WebDavCoilAuth.
            .components {
                add(WebDavCoilAuth())
                // Covers filed on the SMB share; anything else falls
                // through to Coil's own fetchers. See SmbCoverFetcher.
                add(SmbCoverFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            // Covers arriving with a hard cut read as the list flickering as
            // it scrolls; a short fade reads as them developing.
            .crossfade(200)
            .build()

    private fun initLastfm() {
        val sessionKey = AppSettings.lastfmSessionKey.value
        if (sessionKey.isBlank()) return
        val endpoint = AppSettings.lastfmEndpoint.value.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
        val apiKey = AppSettings.lastfmApiKey.value.trim()
        val secret = AppSettings.lastfmSecret.value.trim()
        if (apiKey.isBlank() || secret.isBlank()) return
        LastFM.configure(
            endpoint = endpoint,
            apiKey = apiKey,
            secret = secret,
            sessionKey = sessionKey,
        )
    }

    companion object {
        lateinit var authStore: AuthStore
            private set

        /**
         * One-time move from region-less script tags to regional ones.
         *
         * Early builds of the Simplified/Traditional picker stored `zh-Hant` /
         * `zh-Hans`. Android Views resolve those (script qualifiers), but the
         * shared Compose resources only match language + region — so Settings
         * read Traditional while the player read Simplified. Regional tags
         * match on both systems. Bare `zh` is left alone: it historically
         * meant Simplified.
         */
        private fun migrateScriptOnlyAppLocale() {
            val current = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
            val replacement = when (current) {
                "zh-Hant" -> "zh-TW"
                "zh-Hans" -> "zh-CN"
                else -> return
            }
            AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(replacement),
            )
        }
    }
}
