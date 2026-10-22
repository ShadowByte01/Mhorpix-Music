package com.music.mhorpix.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Looks at the repo's releases on GitHub once per launch and says whether it is newer
 * than the running build. The installer is not run from here: the dialog opens the asset in the
 * browser, so the user installs it the way they installed the first one.
 */
internal object DesktopUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        /** The installer for this platform, or null when the release has none. */
        val downloadUrl: String?,
        val notes: String?,
    )

    private val RELEASE_URLS = listOf(
        "https://api.github.com/repos/ShadowByte01/Mhorpix/releases",
        "https://api.github.com/repos/ShadowByte01/Mhorpix-Music/releases",
        "https://api.github.com/repos/ShadowByte01/Mhorpix/releases/latest",
    )

    val currentVersion: String = System.getProperty("mhorpix.version") ?: "1.8-beta1"

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun check(): UpdateInfo? = runCatching {
        for (url in RELEASE_URLS) {
            val response = runCatching {
                http.get(url) {
                    header("Accept", "application/vnd.github+json")
                    header("User-Agent", "Mhorpix-Desktop")
                }
            }.getOrNull() ?: continue

            if (!response.status.isSuccess()) continue
            val parsed = json.parseToJsonElement(response.bodyAsText())
            val release = when (parsed) {
                is JsonArray -> parsed
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
                is JsonObject -> parsed.takeUnless {
                    it["draft"]?.jsonPrimitive?.booleanOrNull == true
                }
                else -> null
            } ?: continue

            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue
            val releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull ?: continue
            val latest = tag.trimStart('v', 'V')
            if (!isNewer(latest, currentVersion)) continue
            return@runCatching UpdateInfo(
                version = latest,
                releaseUrl = releaseUrl,
                downloadUrl = installerUrl(release),
                notes = release["body"]?.jsonPrimitive?.contentOrNull,
            )
        }
        null
    }.getOrNull()

    /**
     * The release names its files `Mhorpix-<version>-windows-x64-setup.exe` and
     * `Mhorpix-<version>-linux-x86_64.AppImage` (see .github/workflows/release.yml); match on the
     * platform part so a version change does not matter.
     */
    private fun installerUrl(release: JsonObject): String? {
        val suffixes = when {
            DesktopPlatform.isWindows -> listOf("-windows-x64-setup.exe")
            DesktopPlatform.isLinux -> listOf(".AppImage", "-linux-amd64.deb")
            else -> return null
        }
        val assets = release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        for (suffix in suffixes) {
            val hit = assets.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(suffix, ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            hit?.get("browser_download_url")?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        return null
    }

    private class Parsed(val parts: List<Int>, val preRelease: Boolean)

    private fun parse(raw: String): Parsed {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return Parsed(base.split('.').map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Numeric comparison, with a `-betaN` build counted as older than the plain release it leads up to. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.preRelease && !l.preRelease
    }
}
