package com.lightread.pdfreader.ui.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.PdfGroupWithFiles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupAdapter(
    private val onOpenGroup: (String) -> Unit,
    private val onEditGroup: (PdfGroupWithFiles) -> Unit,
    private val onDeleteGroup: (PdfGroupWithFiles) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private var groups: List<PdfGroupWithFiles> = emptyList()

    fun submitGroups(nextGroups: List<PdfGroupWithFiles>) {
        groups = nextGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun getItemCount(): Int = groups.size

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val groupWithFiles = groups[position]
        val group = groupWithFiles.group
        holder.title.text = group.title
        holder.meta.text = buildString {
            append("${groupWithFiles.files.size} 个 PDF")
            val progress = groupWithFiles.progress
            if (progress != null) {
                append(" · 上次阅读 ${dateFormat.format(Date(progress.updateTime))}")
            }
        }
        holder.itemView.setOnClickListener { onOpenGroup(group.groupId) }
        holder.editButton.setOnClickListener { onEditGroup(groupWithFiles) }
        holder.deleteButton.setOnClickListener { onDeleteGroup(groupWithFiles) }
    }

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.text_group_title)
        val meta: TextView = view.findViewById(R.id.text_group_meta)
        val editButton: Button = view.findViewById(R.id.button_edit_group)
        val deleteButton: Button = view.findViewById(R.id.button_delete_group)
    }
}
