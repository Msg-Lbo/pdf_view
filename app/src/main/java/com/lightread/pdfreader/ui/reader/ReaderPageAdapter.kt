package com.lightread.pdfreader.ui.reader

import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReaderPageAdapter(
    private val renderer: PdfPageRenderer
) : RecyclerView.Adapter<ReaderPageAdapter.ReaderPageViewHolder>() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private var pages: List<ReaderPage> = emptyList()

    fun submitPages(nextPages: List<ReaderPage>) {
        pages = nextPages
        notifyDataSetChanged()
    }

    fun shutdown() {
        renderExecutor.shutdownNow()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReaderPageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_reader_page, parent, false)
        return ReaderPageViewHolder(view)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: ReaderPageViewHolder, position: Int) {
        val page = pages[position]
        val key = "${page.pdfFile.id}:${page.pageIndex}:${page.globalPageNumber}"
        holder.boundKey = key
        holder.chapter.text = if (page.isFirstPageOfPdf) page.pdfFile.fileName else ""
        holder.pageNumber.text = "${page.globalPageNumber} / ${page.globalPageCount}"
        holder.error.visibility = View.GONE
        holder.clearImage()

        if (!page.isRenderable) {
            holder.error.text = "该 PDF 暂无法用系统 PdfRenderer 预览，可后续接入 Pdfium 兼容。"
            holder.error.visibility = View.VISIBLE
            return
        }

        holder.image.post {
            val width = holder.image.width.takeIf { it > 0 } ?: holder.itemView.width
            if (width > 0 && holder.boundKey == key) {
                renderExecutor.execute {
                    val bitmap = renderer.render(page.pdfFile, page.pageIndex, width)
                    mainHandler.post renderResult@{
                        if (holder.boundKey != key) {
                            bitmap?.recycle()
                            return@renderResult
                        }
                        if (bitmap == null) {
                            holder.error.text = "页面渲染失败"
                            holder.error.visibility = View.VISIBLE
                        } else {
                            holder.error.visibility = View.GONE
                            holder.image.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: ReaderPageViewHolder) {
        super.onViewRecycled(holder)
        holder.boundKey = null
        holder.clearImage()
    }

    class ReaderPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chapter: TextView = view.findViewById(R.id.text_chapter)
        val image: ImageView = view.findViewById(R.id.image_page)
        val error: TextView = view.findViewById(R.id.text_render_error)
        val pageNumber: TextView = view.findViewById(R.id.text_page_number)
        var boundKey: String? = null

        fun clearImage() {
            val bitmap = (image.drawable as? BitmapDrawable)?.bitmap
            image.setImageDrawable(null)
            if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
        }
    }
}
