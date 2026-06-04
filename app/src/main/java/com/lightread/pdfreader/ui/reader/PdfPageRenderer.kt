package com.lightread.pdfreader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lightread.pdfreader.data.model.PdfFile
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class PdfPageRenderer(private val context: Context) {
    fun render(file: PdfFile, pageIndex: Int, targetWidth: Int): Bitmap? {
        if (targetWidth <= 0) return null
        val uri = Uri.parse(file.filePath)
        return try {
            openDescriptor(uri)?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth / page.width.toFloat()
                        val targetHeight = max(1, (page.height * scale).roundToInt())
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor? {
        return if (uri.scheme == "file") {
            ParcelFileDescriptor.open(File(uri.path.orEmpty()), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        }
    }
}
