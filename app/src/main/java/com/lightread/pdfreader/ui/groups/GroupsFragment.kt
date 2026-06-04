package com.lightread.pdfreader.ui.groups

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.AppGraph
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.PdfGroupWithFiles
import com.lightread.pdfreader.ui.reader.ReaderActivity

class GroupsFragment : Fragment() {
    private lateinit var status: TextView
    private lateinit var adapter: GroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_groups, container, false)
        status = view.findViewById(R.id.text_groups_status)
        adapter = GroupAdapter(
            onOpenGroup = { groupId -> ReaderActivity.open(requireContext(), groupId) },
            onEditGroup = ::showEditDialog,
            onDeleteGroup = ::showDeleteDialog
        )
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

    private fun showEditDialog(groupWithFiles: PdfGroupWithFiles) {
        val allFiles = AppGraph.store.getPdfFiles()
        val selectedIds = groupWithFiles.files.mapTo(linkedSetOf()) { file -> file.id }
        val input = EditText(requireContext()).apply {
            setSingleLine(true)
            setText(groupWithFiles.group.title)
            setSelection(text.length)
        }
        val density = resources.displayMetrics.density
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
            addView(input)
            addView(TextView(requireContext()).apply {
                text = "分组内 PDF"
                textSize = 14f
                setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
            })
            allFiles.forEach { file ->
                addView(CheckBox(requireContext()).apply {
                    text = file.fileName
                    isChecked = file.id in selectedIds
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedIds.add(file.id) else selectedIds.remove(file.id)
                    }
                })
            }
        }
        val scrollView = ScrollView(requireContext()).apply { addView(content) }
        AlertDialog.Builder(requireContext())
            .setTitle("编辑分组")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                if (AppGraph.store.updateGroup(groupWithFiles.group.groupId, input.text.toString(), selectedIds.toList())) {
                    refresh()
                } else {
                    status.text = "分组名称不能为空，且至少保留一个 PDF。"
                }
            }
            .show()
    }

    private fun showDeleteDialog(groupWithFiles: PdfGroupWithFiles) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除分组")
            .setMessage("确定删除“${groupWithFiles.group.title}”？只删除分组，不删除原 PDF 文件。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                AppGraph.store.deleteGroup(groupWithFiles.group.groupId)
                refresh()
            }
            .show()
    }
}
