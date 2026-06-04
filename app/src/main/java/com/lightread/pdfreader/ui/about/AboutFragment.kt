package com.lightread.pdfreader.ui.about

import android.app.AlertDialog
import android.app.ProgressDialog
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
                statusText.text = "检查更新失败：${result.message}"
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("发现新版本 ${updateInfo.tagName}")
            .setMessage("当前版本：${updateInfo.currentVersionName}\n最新版本：${updateInfo.versionName}\n\n点击更新后会自动下载 APK，并打开系统安装确认页。安装完成后重新打开轻阅即可使用新版本。")
            .setNegativeButton("稍后", null)
            .setNeutralButton("打开网页") { _, _ -> openDownload(updateInfo) }
            .setPositiveButton("下载并安装") { _, _ -> downloadAndInstall(updateInfo) }
            .show()
    }

    private fun downloadAndInstall(updateInfo: UpdateInfo) {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setTitle("正在下载更新")
            setMessage("准备下载 ${updateInfo.tagName}")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = true
            setCancelable(false)
            show()
        }
        ioExecutor.execute {
            val result = runCatching {
                AppGraph.updateInstaller.downloadApk(updateInfo) { progress ->
                    mainHandler.post progressUpdate@{
                        if (!isAdded) return@progressUpdate
                        val total = progress.totalBytes
                        progressDialog.isIndeterminate = total <= 0L
                        if (total > 0L) {
                            progressDialog.max = 100
                            progressDialog.progress = ((progress.downloadedBytes * 100L) / total).toInt().coerceIn(0, 100)
                            progressDialog.setMessage("已下载 ${formatBytes(progress.downloadedBytes)} / ${formatBytes(total)}")
                        } else {
                            progressDialog.setMessage("已下载 ${formatBytes(progress.downloadedBytes)}")
                        }
                    }
                }
            }
            mainHandler.post downloadDone@{
                if (!isAdded) return@downloadDone
                progressDialog.dismiss()
                result.onSuccess { apkFile -> startActivity(AppGraph.updateInstaller.installApk(apkFile)) }
                    .onFailure { error ->
                        statusText.text = "更新下载失败：${error.message ?: "未知错误"}"
                        AlertDialog.Builder(requireContext())
                            .setTitle("更新下载失败")
                            .setMessage(error.message ?: "未知错误")
                            .setPositiveButton("打开网页下载") { _, _ -> openDownload(updateInfo) }
                            .setNegativeButton("关闭", null)
                            .show()
                    }
            }
        }
    }

    private fun openDownload(updateInfo: UpdateInfo) {
        startActivity(Intent(Intent.ACTION_VIEW, UpdateChecker.uriForDownload(updateInfo)))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val mib = bytes / 1024.0 / 1024.0
        return "%.1f MB".format(mib)
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String {
        return requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "0.0.0"
    }
}
