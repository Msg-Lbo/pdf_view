package com.lightread.pdfreader.ui.about

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.UpdateChecker
import com.lightread.pdfreader.data.UpdateInfo
import com.lightread.pdfreader.data.UpdateResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AboutFragment : Fragment() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var versionText: TextView
    private lateinit var statusText: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_about, container, false)
        versionText = view.findViewById(R.id.text_about_version)
        statusText = view.findViewById(R.id.text_update_status)
        versionText.text = "当前版本：${currentVersionName()}"
        statusText.text = "可手动检查 GitHub Releases 是否有新版本。"
        view.findViewById<Button>(R.id.button_check_update).setOnClickListener { checkUpdate() }
        return view
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun checkUpdate() {
        statusText.text = "正在检查更新..."
        ioExecutor.execute {
            val result = AppGraph.updateChecker.checkLatest()
            mainHandler.post updateResult@{
                if (!isAdded) return@updateResult
                handleUpdateResult(result)
            }
        }
    }

    private fun handleUpdateResult(result: UpdateResult) {
        when (result) {
            is UpdateResult.UpdateAvailable -> {
                statusText.text = "发现新版本：${result.updateInfo.tagName}"
                showUpdateDialog(result.updateInfo)
            }
            is UpdateResult.NoUpdate -> {
                statusText.text = "当前已是最新版本：${result.currentVersionName}"
            }
            is UpdateResult.Error -> {
                statusText.text = result.message
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("发现新版本 ${updateInfo.tagName}")
            .setMessage("当前版本：${updateInfo.currentVersionName}\n最新版本：${updateInfo.versionName}\n\n是否打开下载页面？")
            .setNegativeButton("稍后", null)
            .setPositiveButton("下载") { _, _ -> openDownload(updateInfo) }
            .show()
    }

    private fun openDownload(updateInfo: UpdateInfo) {
        startActivity(Intent(Intent.ACTION_VIEW, UpdateChecker.uriForDownload(updateInfo)))
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String {
        return requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "0.0.0"
    }
}
