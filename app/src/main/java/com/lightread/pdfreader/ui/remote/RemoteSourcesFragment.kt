package com.lightread.pdfreader.ui.remote

import android.app.AlertDialog
import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.RemoteEntry
import com.lightread.pdfreader.data.model.RemoteSource
import com.lightread.pdfreader.ui.library.RemoteEntryAdapter
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RemoteSourcesFragment : Fragment() {
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var albumOnlyCheck: CheckBox
    private lateinit var adapter: RemoteEntryAdapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentSource: RemoteSource? = null
    private var currentUrl: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_remote_sources, container, false)
        title = view.findViewById(R.id.text_remote_page_title)
        status = view.findViewById(R.id.text_remote_page_status)
        albumOnlyCheck = view.findViewById(R.id.check_remote_album_only)
        adapter = RemoteEntryAdapter { entry -> openEntry(entry) }

        view.findViewById<RecyclerView>(R.id.list_remote_page_entries).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@RemoteSourcesFragment.adapter
        }
        view.findViewById<Button>(R.id.button_remote_select_source).setOnClickListener { showSourceSettings() }
        view.findViewById<Button>(R.id.button_remote_add_source).setOnClickListener { showAddSourceDialog() }
        view.findViewById<Button>(R.id.button_remote_page_root).setOnClickListener {
            currentSource?.let { source -> browseSource(source, source.baseUrl) }
        }
        view.findViewById<Button>(R.id.button_remote_page_download).setOnClickListener {
            val source = currentSource ?: return@setOnClickListener
            val url = currentUrl ?: return@setOnClickListener
            downloadDirectory(source, url, directoryNameFromUrl(url).ifBlank { source.name }, albumOnlyCheck.isChecked)
        }

        openDefaultSource()
        return view
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun openDefaultSource() {
        val source = AppGraph.store.getSources().firstOrNull()
        if (source == null) {
            title.text = "远端源"
            status.text = "还没有远端源。点击“添加源”，默认地址可使用 https://rclone.ytb.icu/"
        } else {
            browseSource(source, source.baseUrl)
        }
    }

    private fun showSourceSettings() {
        val sources = AppGraph.store.getSources()
        if (sources.isEmpty()) {
            showAddSourceDialog()
            return
        }
        val labels = sources.map { source -> "${source.name}\n${source.baseUrl}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择远端源")
            .setItems(labels) { _, index -> browseSource(sources[index], sources[index].baseUrl) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showAddSourceDialog() {
        val density = resources.displayMetrics.density
        val nameInput = EditText(requireContext()).apply {
            hint = "名称"
            setSingleLine(true)
            setText("rclone")
        }
        val urlInput = EditText(requireContext()).apply {
            hint = "源地址"
            setSingleLine(true)
            setText(DEFAULT_RCLONE_SOURCE)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            addView(nameInput)
            addView(urlInput)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("添加源")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val source = AppGraph.store.addSource(nameInput.text.toString(), urlInput.text.toString())
                if (source == null) {
                    status.text = "源地址不能为空。"
                } else {
                    browseSource(source, source.baseUrl)
                }
            }
            .show()
    }

    private fun browseSource(source: RemoteSource, url: String) {
        currentSource = source
        currentUrl = url
        title.text = "${source.name} / ${shortPath(source, url)}"
        status.text = "正在读取：$url"
        albumOnlyCheck.isChecked = false
        albumOnlyCheck.isEnabled = false
        albumOnlyCheck.text = "只下载整本 PDF（检测中）"
        adapter.submitEntries(emptyList())
        ioExecutor.execute {
            val result = runCatching { AppGraph.rcloneClient.list(url) }
            mainHandler.post sourceList@{
                if (!isAdded) return@sourceList
                result.onSuccess { entries -> showEntries(source, url, entries) }
                    .onFailure { error -> status.text = "读取源失败：${error.message ?: "未知错误"}\n$url" }
            }
        }
    }

    private fun showEntries(source: RemoteSource, url: String, entries: List<RemoteEntry>) {
        val visibleEntries = entries.filter { entry -> entry.directory || entry.name.endsWith(".pdf", ignoreCase = true) }
        title.text = "${source.name} / ${shortPath(source, url)}"
        if (visibleEntries.isEmpty()) {
            status.text = if (entries.isEmpty()) "远端目录为空，或没有解析到目录项：$url" else "该目录有 ${entries.size} 个文件，但没有子目录或 PDF。"
            adapter.submitEntries(emptyList())
            return
        }
        val directoryCount = visibleEntries.count { entry -> entry.directory }
        val pdfCount = visibleEntries.size - directoryCount
        val hasAlbumPdf = visibleEntries.any { entry -> isAlbumPdf(entry) }
        albumOnlyCheck.isEnabled = hasAlbumPdf
        albumOnlyCheck.isChecked = hasAlbumPdf
        albumOnlyCheck.text = if (hasAlbumPdf) {
            "只下载整本 PDF（album_ 开头，已找到）"
        } else {
            "只下载整本 PDF（当前目录没有 album_，分章节目录不可用）"
        }
        status.text = "当前目录：$url\n$directoryCount 个子目录，$pdfCount 个 PDF。"
        adapter.submitEntries(visibleEntries)
    }

    private fun openEntry(entry: RemoteEntry) {
        val source = currentSource ?: return
        if (entry.directory) {
            browseSource(source, entry.url)
        } else {
            downloadDirectory(source, parentUrl(entry.url), entry.name.removeSuffix(".pdf").removeSuffix(".PDF"), albumOnly = false)
        }
    }

    private fun downloadDirectory(source: RemoteSource, directoryUrl: String, groupName: String, albumOnly: Boolean) {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setTitle("正在下载 PDF")
            setMessage(if (albumOnly) "准备下载整本：$groupName" else "准备递归下载：$groupName")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = true
            setCancelable(false)
            setButton(AlertDialog.BUTTON_NEGATIVE, "后台等待") { dialog, _ -> dialog.dismiss() }
            show()
        }
        ioExecutor.execute {
            val result = runCatching {
                AppGraph.rcloneClient.downloadDirectory(source.baseUrl, directoryUrl, groupName, albumOnly) { progress ->
                    mainHandler.post progressUpdate@{
                        if (!isAdded) return@progressUpdate
                        val total = progress.totalFiles
                        progressDialog.isIndeterminate = total <= 0
                        if (total > 0) {
                            progressDialog.max = total
                            progressDialog.progress = progress.completedFiles.coerceAtMost(total)
                        }
                        val current = progress.currentFileName?.let { "\n当前：$it" }.orEmpty()
                        progressDialog.setMessage("${progress.completedFiles}/$total 个 PDF$current")
                        status.text = "正在下载 $groupName：${progress.completedFiles}/$total"
                    }
                }
            }
            mainHandler.post sourceDownload@{
                if (!isAdded) return@sourceDownload
                progressDialog.dismiss()
                result.onSuccess { download ->
                    val group = AppGraph.store.createGroupFromPdfFiles(download.groupName, download.files)
                    status.text = if (group == null) "未下载到 PDF。" else "已下载 ${download.files.size} 个 PDF，并创建分组：${group.title}"
                }.onFailure { error ->
                    status.text = "下载失败：${error.message ?: "未知错误"}"
                }
            }
        }
    }

    private fun directoryNameFromUrl(url: String): String = Uri.parse(url).lastPathSegment.orEmpty()

    private fun parentUrl(url: String): String {
        val cleanUrl = url.substringBefore('?').trimEnd('/')
        return cleanUrl.substringBeforeLast('/', missingDelimiterValue = cleanUrl) + "/"
    }

    private fun shortPath(source: RemoteSource, url: String): String {
        val sourcePath = Uri.parse(source.baseUrl).path.orEmpty().trimEnd('/')
        val path = Uri.parse(url).path.orEmpty().removePrefix(sourcePath).trim('/')
        return decodePath(path).ifBlank { "根目录" }
    }

    private fun decodePath(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrElse { value }
    }

    private fun isAlbumPdf(entry: RemoteEntry): Boolean {
        return !entry.directory && entry.name.startsWith("album_", ignoreCase = true) && entry.name.endsWith(".pdf", ignoreCase = true)
    }

    private companion object {
        const val DEFAULT_RCLONE_SOURCE = "https://rclone.ytb.icu/"
    }
}
