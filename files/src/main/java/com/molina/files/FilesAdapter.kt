package com.molina.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.storage.FileEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object FileEntryDiff : DiffUtil.ItemCallback<FileEntry>() {
    override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem.path == newItem.path
    override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem == newItem
}

internal class FilesAdapter(
    private val onClick: (FileEntry) -> Unit,
) : ListAdapter<FileEntry, FilesAdapter.Holder>(FileEntryDiff) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.file_name)
        val info: TextView = view.findViewById(R.id.file_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        val holder = Holder(view)
        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        val date = dateFormat.format(Date(entry.lastModifiedMs))
        holder.name.text = if (entry.isDirectory) entry.name + "/" else entry.name
        holder.info.text = if (entry.isDirectory) "Folder \u00B7 $date" else FileSizeFormat.format(entry.sizeBytes) + " \u00B7 " + date
    }
}
