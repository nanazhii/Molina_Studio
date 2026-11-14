package com.molina.files

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.storage.FileEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.molina.core.ui.FileKind
import com.molina.core.ui.R as UiR

private object FileEntryDiff : DiffUtil.ItemCallback<FileEntry>() {
    override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem.path == newItem.path
    override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem == newItem
}

/**
 * molina-patch26b: ikon per jenis, nama putih polos, titik tiga per baris.
 * molina-patch26c: Mode Pilih (kotak centang menggantikan titik tiga, baris terpilih disorot).
 * [keyOf] memberi kunci unik item (path relatif) untuk mencocokkan pilihan.
 */
internal class FilesAdapter(
    private val keyOf: (FileEntry) -> String,
    private val onClick: (FileEntry) -> Unit,
    private val onMenu: (FileEntry, View) -> Unit,
) : ListAdapter<FileEntry, FilesAdapter.Holder>(FileEntryDiff) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private var selectionActive = false
    private var selectedKeys: Set<String> = emptySet()

    fun isSelectedAt(position: Int): Boolean {
        val entry = currentList.getOrNull(position) ?: return false
        return selectionActive && keyOf(entry) in selectedKeys
    }

    /** Memperbarui tampilan pilihan tanpa mengubah daftar. */
    fun setSelection(active: Boolean, keys: Set<String>) {
        if (active == selectionActive && keys == selectedKeys) return
        selectionActive = active
        selectedKeys = keys
        notifyItemRangeChanged(0, itemCount)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.file_icon)
        val name: TextView = view.findViewById(R.id.file_name)
        val info: TextView = view.findViewById(R.id.file_info)
        val more: ImageButton = view.findViewById(R.id.file_more)
        val check: ImageView = view.findViewById(R.id.file_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        val holder = Holder(view)
        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
        }
        holder.more.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMenu(getItem(pos), holder.more)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        val ctx = holder.itemView.context
        val date = dateFormat.format(Date(entry.lastModifiedMs))
        holder.name.text = if (entry.isDirectory) entry.name + "/" else entry.name
        val where = if (entry.relDir.isNullOrEmpty()) "" else entry.relDir + " \u00B7 "
        holder.info.text = where +
            if (entry.isDirectory) "Folder \u00B7 $date" else FileSizeFormat.format(entry.sizeBytes) + " \u00B7 $date"
        holder.icon.setImageResource(if (entry.isDirectory) R.drawable.ic_files_folder else R.drawable.ic_files_file)
        val kind = if (entry.isDirectory) FileKind.OTHER else FileKind.of(entry.name)
        val tint = when {
            entry.isDirectory -> ContextCompat.getColor(ctx, UiR.color.molina_tab_brand)
            kind != FileKind.OTHER -> kind.color
            else -> ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
        }
        ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(tint))
        holder.name.setTextColor(if (entry.isDirectory) Color.WHITE else kind.nameColor)
        holder.itemView.alpha = if (entry.isHidden) 0.6f else 1f
        val picked = selectionActive && keyOf(entry) in selectedKeys
        holder.itemView.isActivated = picked
        holder.more.visibility = if (selectionActive) View.GONE else View.VISIBLE
        holder.check.visibility = if (selectionActive) View.VISIBLE else View.GONE
        holder.check.setImageResource(if (picked) R.drawable.ic_files_check_on else R.drawable.ic_files_check_off)
        holder.check.contentDescription = if (picked) "Terpilih" else "Tidak terpilih"
    }

    private companion object {
        val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "flv", "wmv", "3gp", "mpg", "mpeg")
    }
}
