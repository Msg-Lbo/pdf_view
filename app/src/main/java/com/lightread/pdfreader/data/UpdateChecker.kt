package com.lightread.pdfreader.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {
    fun checkLatest(): UpdateResult {
        return try {
            val release = runCatching { parseManifest(openText(UPDATE_MANIFEST_URL)) }
                .getOrElse { parseRelease(openText(RELEASE_API_URL)) }
            val currentVersion = currentVersionName()
            if (isNewerVersion(release.versionName, currentVersion)) {
                UpdateResult.UpdateAvailable(release.copy(currentVersionName = currentVersion))
            } else {
                UpdateResult.NoUpdate(currentVersion)
            }
        } catch (error: Exception) {
            UpdateResult.Error(error.message ?: "检查更新失败")
        }
    }

    private fun parseManifest(json: String): UpdateInfo {
        val root = JSONObject(json)
        val versionName = root.getString("versionName")
        val tagName = root.optString("tagName", "v$versionName")
        val releaseUrl = root.optString("releaseUrl")
        return UpdateInfo(
            tagName = tagName,
            versionName = versionName,
            currentVersionName = currentVersionName(),
            releaseUrl = releaseUrl,
            apkUrl = root.getString("apkUrl"),
            body = root.optString("body")
        )
    }

    private fun parseRelease(json: String): UpdateInfo {
        val root = JSONObject(json)
        val tagName = root.optString("tag_name")
        val htmlUrl = root.optString("html_url")
        val body = root.optString("body")
        val assets = root.optJSONArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }
        return UpdateInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v"),
            currentVersionName = currentVersionName(),
            releaseUrl = htmlUrl,
            apkUrl = apkUrl.ifBlank { htmlUrl },
            body = body
        )
    }

    private fun openText(url: String): String {
        return URL(url).openConnection().let { connection ->
            (connection as HttpURLConnection).run {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "QingyuePdfReader")
                if (responseCode !in 200..299) error("更新服务返回 HTTP $responseCode")
                inputStream.bufferedReader().use { reader -> reader.readText() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.toVersionParts()
        val localParts = local.toVersionParts()
        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }

    private fun String.toVersionParts(): List<Int> {
        return removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { part -> part.toIntOrNull() ?: 0 }
    }

    companion object {
        const val UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/Msg-Lbo/pdf_view/main/app-update.json"
        const val RELEASE_API_URL = "https://api.github.com/repos/Msg-Lbo/pdf_view/releases/latest"

        fun uriForDownload(updateInfo: UpdateInfo): Uri {
            return Uri.parse(updateInfo.apkUrl.ifBlank { updateInfo.releaseUrl })
        }
    }
}

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val currentVersionName: String,
    val releaseUrl: String,
    val apkUrl: String,
    val body: String
)

sealed class UpdateResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateResult()
    data class NoUpdate(val currentVersionName: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
