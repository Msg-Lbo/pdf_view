package com.lightread.pdfreader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInstaller(private val context: Context) {
    fun downloadApk(updateInfo: UpdateInfo, onProgress: (UpdateDownloadProgress) -> Unit): File {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, "qingyue-${updateInfo.tagName}.apk")
        val connection = URL(updateInfo.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "QingyuePdfReader")
        if (connection.responseCode !in 200..299) error("APK 下载失败：HTTP ${connection.responseCode}")
        val totalBytes = connection.contentLengthLong.takeIf { size -> size > 0 } ?: -1L
        var downloadedBytes = 0L
        connection.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    onProgress(UpdateDownloadProgress(downloadedBytes, totalBytes))
                }
            }
        }
        if (apkFile.length() <= 0L) error("APK 下载为空文件")
        return apkFile
    }

    fun installApk(apkFile: File): Intent {
        val apkUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long
)
