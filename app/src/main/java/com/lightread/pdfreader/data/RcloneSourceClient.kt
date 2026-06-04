package com.lightread.pdfreader.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lightread.pdfreader.data.model.PdfFile
import com.lightread.pdfreader.data.model.RemoteEntry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest

class RcloneSourceClient(private val context: Context) {
    fun list(url: String): List<RemoteEntry> {
        val baseUrl = URL(url.ensureSlash())
        val html = openText(baseUrl)
        return LINK_REGEX.findAll(html)
            .mapNotNull { match ->
                val href = match.groupValues[1]
                if (href == ".." || href.startsWith("?") || href.startsWith("#")) return@mapNotNull null
                val resolved = URL(baseUrl, href).toString()
                val pathName = href.substringBefore('?').trimEnd('/').substringAfterLast('/')
                val name = decodeName(pathName).ifBlank { decodeName(href.trimEnd('/')) }
                if (name.isBlank()) return@mapNotNull null
                RemoteEntry(
                    name = name,
                    url = resolved,
                    directory = href.substringBefore('?').endsWith("/")
                )
            }
            .distinctBy { entry -> entry.url }
            .sortedWith { left, right -> NaturalFileNameComparator.compare(left.name, right.name) }
            .toList()
    }

    fun downloadDirectory(sourceUrl: String, directoryUrl: String, groupName: String): DownloadResult {
        val pdfEntries = collectPdfEntries(directoryUrl.ensureSlash(), depth = 0)
        val files = pdfEntries.mapNotNull { entry -> downloadPdf(sourceUrl, groupName, entry) }
        return DownloadResult(groupName = groupName, files = files)
    }

    private fun collectPdfEntries(url: String, depth: Int): List<RemoteEntry> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()
        return list(url).flatMap { entry ->
            when {
                entry.directory -> collectPdfEntries(entry.url.ensureSlash(), depth + 1)
                entry.name.endsWith(".pdf", ignoreCase = true) -> listOf(entry)
                else -> emptyList()
            }
        }
    }

    private fun downloadPdf(sourceUrl: String, groupName: String, entry: RemoteEntry): PdfFile? {
        return try {
            val targetDir = File(context.getExternalFilesDir(null), "RemotePdfs/${safeFileName(groupName)}").apply { mkdirs() }
            val targetFile = uniqueFile(targetDir, safeFileName(entry.name).ifBlank { "remote.pdf" })
            val connection = URL(entry.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "QingyuePdfReader")
            connection.inputStream.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            PdfFile(
                id = stableId("$sourceUrl|${entry.url}", targetFile.length()),
                fileName = targetFile.name,
                filePath = Uri.fromFile(targetFile).toString(),
                pageCount = readPageCount(targetFile),
                addedTime = System.currentTimeMillis() / 1000L,
                sizeBytes = targetFile.length()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun openText(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "QingyuePdfReader")
        return connection.inputStream.bufferedReader().use { reader -> reader.readText() }
    }

    private fun readPageCount(file: File): Int {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        var file = File(directory, cleanName)
        var index = 1
        while (file.exists()) {
            val base = cleanName.substringBeforeLast('.', cleanName)
            file = File(directory, "$base-$index.pdf")
            index++
        }
        return file
    }

    private fun stableId(path: String, sizeBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$path|$sizeBytes".toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun decodeName(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrElse { value }
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun safeFileName(value: String): String {
        return value.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    }

    private fun String.ensureSlash(): String = if (endsWith('/')) this else "$this/"

    companion object {
        private const val MAX_RECURSION_DEPTH = 3
        private val LINK_REGEX = Regex("<a\\s+[^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    }
}

data class DownloadResult(
    val groupName: String,
    val files: List<PdfFile>
)
