package com.lightread.pdfreader.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lightread.pdfreader.R
import com.lightread.pdfreader.data.model.PdfFile

class PdfFileAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<PdfFileAdapter.PdfFileViewHolder>() {
    private val selectedIds = linkedSetOf<String>()
    private var files: List<PdfFile> = emptyList()

    fun submitFiles(nextFiles: List<PdfFile>) {
        files = nextFiles
        selectedIds.retainAll(nextFiles.mapTo(mutableSetOf()) { file -> file.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun selectedPdfIds(): List<String> = selectedIds.toList()

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfFileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_pdf_file, parent, false)
        return PdfFileViewHolder(view)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: PdfFileViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = file.fileName
        holder.meta.text = buildString {
            append(if (file.pageCount > 0) "${file.pageCount} 页" else "页数未知")
            if (file.sizeBytes > 0L) append(" · ${formatSize(file.sizeBytes)}")
        }
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = file.id in selectedIds
        holder.itemView.setOnClickListener { toggle(file.id) }
        holder.checkBox.setOnClickListener { toggle(file.id) }
    }

    private fun toggle(pdfId: String) {
        if (!selectedIds.add(pdfId)) selectedIds.remove(pdfId)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    private fun formatSize(sizeBytes: Long): String {
        val mb = sizeBytes / 1024f / 1024f
        return if (mb >= 1f) "%.1f MB".format(mb) else "${sizeBytes / 1024L} KB"
    }

    class PdfFileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.check_pdf)
        val name: TextView = view.findViewById(R.id.text_pdf_name)
        val meta: TextView = view.findViewById(R.id.text_pdf_meta)
    }
}
