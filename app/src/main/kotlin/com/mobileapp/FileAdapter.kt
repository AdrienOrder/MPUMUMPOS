package com.mobileapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mobileapp.data.CsvFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val onDownloadClick: (CsvFile) -> Unit,
    private val onVisualizeClick: (CsvFile) -> Unit,
    private val onDeleteClick: (CsvFile) -> Unit
) : ListAdapter<CsvFile, FileAdapter.ViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
        private val btnDownload: Button = itemView.findViewById(R.id.btnDownload)
        private val btnVisualize: Button = itemView.findViewById(R.id.btnVisualize)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(file: CsvFile) {
            tvName.text = file.fileName
            
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val sizeStr = formatFileSize(file.fileSize)
            tvInfo.text = "${dateFormat.format(Date(file.downloadedAt))}, $sizeStr"

            btnDownload.setOnClickListener { onDownloadClick(file) }
            if (file.fileName.lowercase().endsWith(".txt")) {
                btnVisualize.visibility = View.GONE
            } else {
                btnVisualize.visibility = View.VISIBLE
                btnVisualize.setOnClickListener { onVisualizeClick(file) }
            }
            btnDelete.setOnClickListener { onDeleteClick(file) }
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<CsvFile>() {
        override fun areItemsTheSame(oldItem: CsvFile, newItem: CsvFile) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CsvFile, newItem: CsvFile) = oldItem == newItem
    }
}