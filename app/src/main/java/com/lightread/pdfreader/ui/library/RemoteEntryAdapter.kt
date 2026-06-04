package com.lightread.pdfreader.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.RemoteEntry

class RemoteEntryAdapter(
    private val onEntryClick: (RemoteEntry) -> Unit
) : RecyclerView.Adapter<RemoteEntryAdapter.RemoteEntryViewHolder>() {
    private var entries: List<RemoteEntry> = emptyList()

    fun submitEntries(nextEntries: List<RemoteEntry>) {
        entries = nextEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemoteEntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_remote_entry, parent, false)
        return RemoteEntryViewHolder(view)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: RemoteEntryViewHolder, position: Int) {
        val entry = entries[position]
        holder.name.text = if (entry.directory) "[目录] ${entry.name}" else "[PDF] ${entry.name}"
        holder.hint.text = if (entry.directory) "点击进入目录" else "点击下载它所在目录并自动分组"
        holder.itemView.setOnClickListener { onEntryClick(entry) }
    }

    class RemoteEntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_remote_name)
        val hint: TextView = view.findViewById(R.id.text_remote_hint)
    }
}
