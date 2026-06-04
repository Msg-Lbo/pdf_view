package com.lightread.pdfreader

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.lightread.pdfreader.data.UpdateChecker
import com.lightread.pdfreader.data.UpdateInfo
import com.lightread.pdfreader.data.UpdateResult
import com.lightread.pdfreader.ui.about.AboutFragment
import com.lightread.pdfreader.ui.groups.GroupsFragment
import com.lightread.pdfreader.ui.library.LibraryFragment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {
    private lateinit var libraryTab: TextView
    private lateinit var groupsTab: TextView
    private lateinit var aboutTab: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentTab: Tab = Tab.Library
    private var updatePromptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        currentTab = savedInstanceState?.getString(KEY_TAB)?.let(Tab::valueOf) ?: Tab.Library
        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        libraryTab = findViewById(R.id.tab_library)
        groupsTab = findViewById(R.id.tab_groups)
        aboutTab = findViewById(R.id.tab_about)

        libraryTab.setOnClickListener { showTab(Tab.Library) }
        groupsTab.setOnClickListener { showTab(Tab.Groups) }
        aboutTab.setOnClickListener { showTab(Tab.About) }

        if (savedInstanceState == null) {
            showTab(Tab.Library)
            checkUpdateOnStart()
        } else {
            showTab(currentTab)
        }
    }

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        val fragment: Fragment = when (tab) {
            Tab.Library -> LibraryFragment()
            Tab.Groups -> GroupsFragment()
            Tab.About -> AboutFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        updateTabState(tab)
    }

    private fun updateTabState(selectedTab: Tab) {
        libraryTab.setTextColor(if (selectedTab == Tab.Library) 0xFF111111.toInt() else 0xFF777777.toInt())
        groupsTab.setTextColor(if (selectedTab == Tab.Groups) 0xFF111111.toInt() else 0xFF777777.toInt())
        aboutTab.setTextColor(if (selectedTab == Tab.About) 0xFF111111.toInt() else 0xFF777777.toInt())
        libraryTab.setTypeface(null, if (selectedTab == Tab.Library) Typeface.BOLD else Typeface.NORMAL)
        groupsTab.setTypeface(null, if (selectedTab == Tab.Groups) Typeface.BOLD else Typeface.NORMAL)
        aboutTab.setTypeface(null, if (selectedTab == Tab.About) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun checkUpdateOnStart() {
        updateExecutor.execute {
            val result = AppGraph.updateChecker.checkLatest()
            mainHandler.post {
                if (!isFinishing && !updatePromptShown && result is UpdateResult.UpdateAvailable) {
                    updatePromptShown = true
                    showUpdateDialog(result.updateInfo)
                }
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("发现新版本 ${updateInfo.tagName}")
            .setMessage("当前版本：${updateInfo.currentVersionName}\n最新版本：${updateInfo.versionName}\n\n是否打开下载页面？")
            .setNegativeButton("稍后", null)
            .setPositiveButton("下载") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, UpdateChecker.uriForDownload(updateInfo)))
            }
            .show()
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.root_main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }
    }

    private enum class Tab {
        Library,
        Groups,
        About
    }

    private companion object {
        const val KEY_TAB = "selectedTab"
    }
}
