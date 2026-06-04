package com.lightread.pdfreader.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.PdfGroupWithFiles
import com.lightread.pdfreader.data.model.ReadingProgress

class ReaderActivity : FragmentActivity() {
    private lateinit var title: TextView
    private lateinit var pageList: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: ReaderPageAdapter
    private var groupId: String = ""
    private var pages: List<ReaderPage> = emptyList()
    private var pendingProgress: ReadingProgress? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        groupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
        val group = AppGraph.store.getGroupWithFiles(groupId)
        if (group == null || group.files.isEmpty()) {
            finish()
            return
        }

        setContentView(R.layout.activity_reader)
        title = findViewById(R.id.text_reader_title)
        pageList = findViewById(R.id.list_reader_pages)
        layoutManager = LinearLayoutManager(this)
        adapter = ReaderPageAdapter(PdfPageRenderer(applicationContext))
        pageList.layoutManager = layoutManager
        pageList.adapter = adapter

        pages = buildPages(group)
        adapter.submitPages(pages)
        title.text = group.group.title
        restoreProgress()
        pageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                updateCurrentProgress()
                if (newState == RecyclerView.SCROLL_STATE_IDLE) persistCurrentProgress()
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateCurrentProgress()
            }
        })
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.shutdown()
        updateCurrentProgress()
        persistCurrentProgress()
        super.onDestroy()
    }

    private fun buildPages(group: PdfGroupWithFiles): List<ReaderPage> {
        val totalPages = group.files.sumOf { file -> file.pageCount.coerceAtLeast(1) }
        var globalPage = 1
        return buildList {
            group.files.forEach { file ->
                val pageCount = file.pageCount.coerceAtLeast(1)
                repeat(pageCount) { pageIndex ->
                    add(
                        ReaderPage(
                            pdfFile = file,
                            pageIndex = pageIndex,
                            globalPageNumber = globalPage,
                            globalPageCount = totalPages,
                            isFirstPageOfPdf = pageIndex == 0,
                            isRenderable = file.pageCount > 0
                        )
                    )
                    globalPage++
                }
            }
        }
    }

    private fun restoreProgress() {
        val progress = AppGraph.store.getProgress(groupId) ?: return
        val index = pages.indexOfFirst { page ->
            page.pdfFile.id == progress.currentPdfId && page.pageIndex == progress.currentPage
        }
        if (index >= 0) {
            layoutManager.scrollToPositionWithOffset(index, 0)
        }
    }

    private fun updateCurrentProgress() {
        if (pages.isEmpty() || !::layoutManager.isInitialized) return
        val index = layoutManager.findFirstVisibleItemPosition().takeIf { it >= 0 } ?: return
        val page = pages.getOrNull(index) ?: return
        val child = layoutManager.findViewByPosition(index)
        val offset = if (child != null && child.height > 0) {
            (-child.top).coerceAtLeast(0) / child.height.toFloat()
        } else {
            0f
        }
        pendingProgress = ReadingProgress(
            groupId = groupId,
            currentPdfId = page.pdfFile.id,
            currentPage = page.pageIndex,
            pageScrollOffset = offset.coerceIn(0f, 1f),
            updateTime = System.currentTimeMillis()
        )
        title.text = "${page.pdfFile.fileName} · ${page.globalPageNumber}/${page.globalPageCount}"
    }

    private fun persistCurrentProgress() {
        pendingProgress?.let(AppGraph.store::saveProgress)
    }

    companion object {
        private const val EXTRA_GROUP_ID = "groupId"

        fun open(context: Context, groupId: String) {
            context.startActivity(Intent(context, ReaderActivity::class.java).putExtra(EXTRA_GROUP_ID, groupId))
        }
    }
}
