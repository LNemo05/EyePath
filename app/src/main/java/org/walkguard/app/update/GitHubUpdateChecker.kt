package org.walkguard.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Result of comparing the installed app against GitHub Releases. */
sealed class UpdateCheckResult {
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Long
    ) : UpdateCheckResult()

    data class UpdateAvailable(
        val currentVersionName: String,
        val currentVersionCode: Long,
        val remoteVersionName: String,
        val remoteVersionCode: Long?,
        val releasePageUrl: String,
        val notes: String?
    ) : UpdateCheckResult()

    data class Failed(
        val message: String,
        val fallbackUrl: String
    ) : UpdateCheckResult()

    data class NotConfigured(
        val hint: String
    ) : UpdateCheckResult()
}

/**
 * Lightweight GitHub Releases client (no extra HTTP stack).
 * Compares [android.content.pm.PackageInfo] against `GET .../releases/latest`.
 */
object GitHubUpdateChecker {

    fun installedVersion(context: Context): Pair<String, Long> {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        val name = info.versionName ?: "0"
        val code = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return name to code
    }

    /**
     * Blocking network call — invoke on a background dispatcher.
     */
    fun checkForUpdate(context: Context): UpdateCheckResult {
        if (!WalkGuardLinks.isConfigured) {
            return UpdateCheckResult.NotConfigured(
                hint = "Set WalkGuardLinks.GITHUB_OWNER (and GITHUB_REPO) before checking updates."
            )
        }

        val (localName, localCode) = installedVersion(context)
        return runCatching {
            val body = httpGet(WalkGuardLinks.releasesApiUrl)
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").ifBlank {
                error("Release has no tag_name")
            }
            val htmlUrl = json.optString("html_url")
                .ifBlank { WalkGuardLinks.releasesLatestUrl }
            val notes = json.optString("body").takeIf { it.isNotBlank() }
            val remoteName = normalizeVersionLabel(tagName)
            val remoteCodeFromBody = notes?.let { parseVersionCodeFromBody(it) }
            val remoteCodeFromTag = parseVersionCodeFromTag(tagName)
            val remoteCode = remoteCodeFromBody ?: remoteCodeFromTag

            val isNewer = when {
                remoteCode != null -> remoteCode > localCode
                else -> compareVersionNames(remoteName, normalizeVersionLabel(localName)) > 0
            }

            if (isNewer) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersionName = localName,
                    currentVersionCode = localCode,
                    remoteVersionName = remoteName,
                    remoteVersionCode = remoteCode,
                    releasePageUrl = htmlUrl,
                    notes = notes?.lineSequence()?.take(12)?.joinToString("\n")
                )
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersionName = localName,
                    currentVersionCode = localCode
                )
            }
        }.getOrElse { error ->
            UpdateCheckResult.Failed(
                message = error.message ?: error.javaClass.simpleName,
                fallbackUrl = WalkGuardLinks.releasesLatestUrl
            )
        }
    }

    internal fun normalizeVersionLabel(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /** Optional convention: first line or any line `versionCode: 12`. */
    internal fun parseVersionCodeFromBody(body: String): Long? {
        val regex = Regex("""(?im)^\s*versionCode\s*[:=]\s*(\d+)\s*$""")
        return regex.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    /** Tag like `v12` or `12` maps to versionCode; semver tags return null. */
    internal fun parseVersionCodeFromTag(tag: String): Long? {
        val normalized = normalizeVersionLabel(tag)
        if (normalized.matches(Regex("""\d+"""))) {
            return normalized.toLongOrNull()
        }
        return null
    }

    /**
     * Semver-ish compare: `1.2.10` > `1.2.9`. Non-numeric chunks compared lexicographically.
     * @return positive if [a] > [b]
     */
    internal fun compareVersionNames(a: String, b: String): Int {
        val left = a.split('.', '-', '_').filter { it.isNotEmpty() }
        val right = b.split('.', '-', '_').filter { it.isNotEmpty() }
        val n = maxOf(left.size, right.size)
        for (i in 0 until n) {
            val l = left.getOrNull(i) ?: "0"
            val r = right.getOrNull(i) ?: "0"
            val ln = l.toLongOrNull()
            val rn = r.toLongOrNull()
            val cmp = when {
                ln != null && rn != null -> ln.compareTo(rn)
                else -> l.compareTo(r)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "EyePath-UpdateChecker")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                error("GitHub HTTP $code: ${text.take(200)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}
