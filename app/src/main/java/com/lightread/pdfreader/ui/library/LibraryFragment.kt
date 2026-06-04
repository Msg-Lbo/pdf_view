package com.lightread.pdfreader.ui.library

import android.Manifest
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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
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
}
