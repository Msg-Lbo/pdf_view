package com.lightread.pdfreader.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.lightread.pdfreader.data.model.PdfFile
import java.io.File
import java.security.MessageDigest

class PdfScanner(private val context: Context) {
    private val resolver = context.contentResolver

    fun scanDevicePdfs(): List<PdfFile> {
        val files = scanMediaStore() + scanCommonDirectories()
        return files
            .distinctBy { file -> "${file.fileName.lowercase()}|${file.sizeBytes}|${file.pageCount}" }
            .sortedNaturally()
    }

    fun scanMediaStore(): List<PdfFile> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Files.FileColumns.DATA)
            }
        }.toTypedArray()
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%.pdf")
        val files = mutableListOf<PdfFile>()
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLongOrDefault(MediaStore.Files.FileColumns._ID, -1L)
                if (id < 0L) continue
                val name = cursor.getStringOrDefault(MediaStore.Files.FileColumns.DISPLAY_NAME, "untitled.pdf")
                if (!name.endsWith(".pdf", ignoreCase = true)) continue
                val uri = ContentUris.withAppendedId(collection, id)
                val displayPath = cursor.readDisplayPath(name)
                val sizeBytes = cursor.getLongOrDefault(MediaStore.Files.FileColumns.SIZE, 0L)
                files += PdfFile(
                    id = stableId(displayPath, sizeBytes),
                    fileName = name,
                    filePath = uri.toString(),
                    pageCount = readPageCount(uri),
                    addedTime = cursor.getLongOrDefault(MediaStore.Files.FileColumns.DATE_ADDED, System.currentTimeMillis() / 1000L),
                    sizeBytes = sizeBytes
                )
            }
        }
        return files.sortedNaturally()
    }

    private fun scanCommonDirectories(): List<PdfFile> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) return emptyList()
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "Tencent/QQfile_recv"),
            File(Environment.getExternalStorageDirectory(), "tencent/QQfile_recv"),
            File(Environment.getExternalStorageDirectory(), "QQfile_recv")
        )
        return roots.asSequence()
            .filter { root -> root.exists() && root.canRead() }
            .flatMap { root -> root.walkTopDown().onEnter { directory -> directory.canRead() } }
            .filter { file -> file.isFile && file.extension.equals("pdf", ignoreCase = true) }
            .take(MAX_FILE_SCAN_COUNT)
            .map { file ->
                PdfFile(
                    id = stableId(file.absolutePath, file.length()),
                    fileName = file.name,
                    filePath = Uri.fromFile(file).toString(),
                    pageCount = readPageCount(file),
                    addedTime = file.lastModified() / 1000L,
                    sizeBytes = file.length()
                )
            }
            .toList()
            .sortedNaturally()
    }

    fun scanPickedUris(uris: List<Uri>): List<PdfFile> {
        return uris.mapNotNull { uri ->
            val metadata = readOpenableMetadata(uri) ?: return@mapNotNull null
            if (!metadata.name.endsWith(".pdf", ignoreCase = true)) return@mapNotNull null
            PdfFile(
                id = stableId(uri.toString(), metadata.sizeBytes),
                fileName = metadata.name,
                filePath = uri.toString(),
                pageCount = readPageCount(uri),
                addedTime = System.currentTimeMillis() / 1000L,
                sizeBytes = metadata.sizeBytes
            )
        }.sortedNaturally()
    }

    private fun readOpenableMetadata(uri: Uri): OpenableMetadata? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return OpenableMetadata(
                    name = cursor.getStringOrDefault(OpenableColumns.DISPLAY_NAME, uri.lastPathSegment ?: "untitled.pdf"),
                    sizeBytes = cursor.getLongOrDefault(OpenableColumns.SIZE, 0L)
                )
            }
        }
        return OpenableMetadata(uri.lastPathSegment ?: "untitled.pdf", 0L)
    }

    private fun readPageCount(uri: Uri): Int {
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
            } ?: 0
        } catch (_: Exception) {
            0
        }
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

    private fun stableId(path: String, sizeBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$path|$sizeBytes".toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun Cursor.readDisplayPath(name: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = getStringOrDefault(MediaStore.Files.FileColumns.RELATIVE_PATH, "")
            relativePath + name
        } else {
            @Suppress("DEPRECATION")
            getStringOrDefault(MediaStore.Files.FileColumns.DATA, name)
        }
    }

    private fun Cursor.getStringOrDefault(columnName: String, defaultValue: String): String {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return defaultValue
        return getString(index) ?: defaultValue
    }

    private fun Cursor.getLongOrDefault(columnName: String, defaultValue: Long): Long {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return defaultValue
        return getLong(index)
    }

    private data class OpenableMetadata(
        val name: String,
        val sizeBytes: Long
    )

    private fun List<PdfFile>.sortedNaturally(): List<PdfFile> {
        return sortedWith { left, right -> NaturalFileNameComparator.compare(left.fileName, right.fileName) }
    }

    private companion object {
        const val MAX_FILE_SCAN_COUNT = 5000
    }
}
