package com.lightread.pdfreader

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.lightread.pdfreader.ui.groups.GroupsFragment
import com.lightread.pdfreader.ui.library.LibraryFragment

class MainActivity : FragmentActivity() {
    private lateinit var libraryTab: TextView
    private lateinit var groupsTab: TextView
    private var currentTab: Tab = Tab.Library

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        currentTab = savedInstanceState?.getString(KEY_TAB)?.let(Tab::valueOf) ?: Tab.Library
        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        libraryTab = findViewById(R.id.tab_library)
        groupsTab = findViewById(R.id.tab_groups)

        libraryTab.setOnClickListener { showTab(Tab.Library) }
        groupsTab.setOnClickListener { showTab(Tab.Groups) }

        if (savedInstanceState == null) {
            showTab(Tab.Library)
        } else {
            showTab(currentTab)
        }
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
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        updateTabState(tab)
    }

    private fun updateTabState(selectedTab: Tab) {
        libraryTab.setTextColor(if (selectedTab == Tab.Library) 0xFF111111.toInt() else 0xFF777777.toInt())
        groupsTab.setTextColor(if (selectedTab == Tab.Groups) 0xFF111111.toInt() else 0xFF777777.toInt())
        libraryTab.setTypeface(null, if (selectedTab == Tab.Library) Typeface.BOLD else Typeface.NORMAL)
        groupsTab.setTypeface(null, if (selectedTab == Tab.Groups) Typeface.BOLD else Typeface.NORMAL)
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
        Groups
    }

    private companion object {
        const val KEY_TAB = "selectedTab"
    }
}
