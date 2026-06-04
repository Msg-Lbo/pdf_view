package com.lightread.pdfreader.ui.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
import com.lightread.pdfreader.ui.reader.ReaderActivity

class GroupsFragment : Fragment() {
    private lateinit var status: TextView
    private lateinit var adapter: GroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_groups, container, false)
        status = view.findViewById(R.id.text_groups_status)
        adapter = GroupAdapter { groupId -> ReaderActivity.open(requireContext(), groupId) }
        view.findViewById<RecyclerView>(R.id.list_groups).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GroupsFragment.adapter
        }
        refresh()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refresh()
    }

    private fun refresh() {
        val groups = AppGraph.store.getGroupsWithFiles()
        adapter.submitGroups(groups)
        status.text = if (groups.isEmpty()) getString(R.string.empty_groups) else "共 ${groups.size} 个分组，点击进入连续阅读。"
    }
}
