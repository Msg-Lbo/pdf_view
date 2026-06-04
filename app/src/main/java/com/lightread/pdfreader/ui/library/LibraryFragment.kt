package com.lightread.pdfreader.ui.library

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.RemoteEntry
import com.lightread.pdfreader.data.model.RemoteSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LibraryFragment : Fragment() {
    private lateinit var status: TextView
    private lateinit var adapter: PdfFileAdapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val pickPdfDocuments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importPickedUris(uris)
    }

    private val requestReadStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanMediaStore() else status.text = "未授予存储读取权限，可改用系统文件选择器添加 PDF。"
    }

    private val requestManageStorage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            scanMediaStore()
        } else {
            status.text = "未授予完整文件访问权限，已改用 MediaStore 扫描；若仍扫不到，请使用“选择 PDF”。"
            scanMediaStore()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        status = view.findViewById(R.id.text_library_status)
        adapter = PdfFileAdapter { selectedCount -> updateCreateStatus(selectedCount) }

        view.findViewById<RecyclerView>(R.id.list_pdfs).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LibraryFragment.adapter
        }
        view.findViewById<View>(R.id.button_source_settings).setOnClickListener { showSourceSettings() }
        view.findViewById<Button>(R.id.button_scan).setOnClickListener { requestScan() }
        view.findViewById<Button>(R.id.button_pick).setOnClickListener { pickPdfDocuments.launch(arrayOf("application/pdf")) }
        view.findViewById<Button>(R.id.button_create_group).setOnClickListener { createGroup() }
        refresh()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refresh()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun requestScan() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager() -> {
                status.text = "自动扫描需要完整文件访问权限。授权后会扫描 Download、Documents、QQ/Tencent 接收目录。"
                val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                val fallbackSettings = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                requestManageStorage.launch(
                    if (appSettings.resolveActivity(requireContext().packageManager) != null) appSettings else fallbackSettings
                )
            }
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED -> {
                requestReadStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> scanMediaStore()
        }
    }

    private fun scanMediaStore() {
        status.text = "正在扫描本地 PDF..."
        ioExecutor.execute {
            val files = AppGraph.scanner.scanDevicePdfs()
            val changed = AppGraph.store.upsertPdfFiles(files)
            mainHandler.post {
                if (isAdded) refresh("扫描到 ${files.size} 个 PDF，新增或更新 $changed 个。")
            }
        }
    }

    private fun importPickedUris(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        status.text = "正在添加 ${uris.size} 个 PDF..."
        ioExecutor.execute {
            val files = AppGraph.scanner.scanPickedUris(uris)
            val changed = AppGraph.store.upsertPdfFiles(files)
            mainHandler.post {
                if (isAdded) refresh("已添加 ${files.size} 个 PDF，新增或更新 $changed 个。")
            }
        }
    }

    private fun createGroup() {
        val group = AppGraph.store.createGroupFromPdfIds(adapter.selectedPdfIds())
        if (group == null) {
            status.text = "请至少选择一个 PDF。"
            return
        }
        adapter.clearSelection()
        refresh("已创建分组：${group.title}")
    }

    private fun refresh(message: String? = null) {
        val files = AppGraph.store.getPdfFiles()
        adapter.submitFiles(files)
        status.text = message ?: if (files.isEmpty()) getString(R.string.empty_library) else "已导入 ${files.size} 个 PDF。"
    }

    private fun updateCreateStatus(selectedCount: Int) {
        if (selectedCount > 0) status.text = "已选择 $selectedCount 个 PDF，可创建分组。"
    }

    private fun showSourceSettings() {
        val sources = AppGraph.store.getSources()
        val labels = buildList {
            add("添加源")
            addAll(sources.map { source -> source.name })
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("源设置")
            .setItems(labels) { _, index ->
                if (index == 0) showAddSourceDialog() else showSourceActions(sources[index - 1])
            }
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
                status.text = if (source == null) "源地址不能为空。" else "已添加源：${source.name}"
            }
            .show()
    }

    private fun showSourceActions(source: RemoteSource) {
        AlertDialog.Builder(requireContext())
            .setTitle(source.name)
            .setItems(arrayOf("浏览目录", "删除源")) { _, index ->
                when (index) {
                    0 -> browseSource(source, source.baseUrl)
                    1 -> {
                        AppGraph.store.deleteSource(source.id)
                        status.text = "已删除源：${source.name}"
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun browseSource(source: RemoteSource, url: String) {
        status.text = "正在读取远端目录..."
        ioExecutor.execute {
            val result = runCatching { AppGraph.rcloneClient.list(url) }
            mainHandler.post sourceList@{
                if (!isAdded) return@sourceList
                result.onSuccess { entries -> showRemoteEntries(source, url, entries) }
                    .onFailure { error -> status.text = "读取源失败：${error.message ?: "未知错误"}" }
            }
        }
    }

    private fun showRemoteEntries(source: RemoteSource, url: String, entries: List<RemoteEntry>) {
        val visibleEntries = entries.filter { entry -> entry.directory || entry.name.endsWith(".pdf", ignoreCase = true) }
        if (visibleEntries.isEmpty()) {
            status.text = "该目录没有可浏览目录或 PDF。"
            return
        }
        val labels = buildList {
            add("下载当前目录 PDF 并自动分组")
            addAll(visibleEntries.map { entry -> if (entry.directory) "[目录] ${entry.name}" else "[PDF] ${entry.name}" })
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(source.name)
            .setItems(labels) { _, index ->
                if (index == 0) {
                    downloadDirectory(source, url, directoryNameFromUrl(url).ifBlank { source.name })
                } else {
                    val entry = visibleEntries[index - 1]
                    if (entry.directory) {
                        browseSource(source, entry.url)
                    } else {
                        downloadDirectory(source, parentUrl(entry.url), entry.name.removeSuffix(".pdf").removeSuffix(".PDF"))
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun downloadDirectory(source: RemoteSource, directoryUrl: String, groupName: String) {
        status.text = "正在下载并分组：$groupName"
        ioExecutor.execute {
            val result = runCatching { AppGraph.rcloneClient.downloadDirectory(source.baseUrl, directoryUrl, groupName) }
            mainHandler.post sourceDownload@{
                if (!isAdded) return@sourceDownload
                result.onSuccess { download ->
                    val group = AppGraph.store.createGroupFromPdfFiles(download.groupName, download.files)
                    refresh(
                        if (group == null) "未下载到 PDF。" else "已下载 ${download.files.size} 个 PDF，并创建分组：${group.title}"
                    )
                }.onFailure { error ->
                    status.text = "下载失败：${error.message ?: "未知错误"}"
                }
            }
        }
    }

    private fun directoryNameFromUrl(url: String): String {
        return Uri.parse(url).lastPathSegment.orEmpty()
    }

    private fun parentUrl(url: String): String {
        val cleanUrl = url.substringBefore('?').trimEnd('/')
        return cleanUrl.substringBeforeLast('/', missingDelimiterValue = cleanUrl) + "/"
    }

    private companion object {
        const val DEFAULT_RCLONE_SOURCE = "https://rclone.ytb.icu/"
    }
}
