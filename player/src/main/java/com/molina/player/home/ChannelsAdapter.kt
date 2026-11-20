package com.molina.player.home

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.common.home.ChannelListModel
import com.molina.player.R
import com.molina.player.databinding.ItemChannelRowBinding
import java.util.Locale

/** Menggambar baris saluran dari [ChannelListModel]. Tidak menyimpan keadaan sendiri dan tidak memutuskan apa pun. */
internal class ChannelsAdapter(
    private val model: () -> ChannelListModel,
    private val onRowClick: (ChannelListModel.Row) -> Unit,
) : RecyclerView.Adapter<ChannelsAdapter.Holder>() {

    class Holder(val binding: ItemChannelRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemChannelRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = model().rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = model()
        val row = m.rows[position]
        val selected = row.number == m.selected
        with(holder.binding) {
            number.text = String.format(Locale.ROOT, "%03d", row.number)
            name.text = row.channel.name
            name.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            group.text = row.channel.group
            group.visibility = if (row.channel.group.isEmpty()) View.GONE else View.VISIBLE
            root.setBackgroundResource(if (selected) R.drawable.bg_channel_row_selected else R.drawable.bg_channel_row)
            root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRowClick(model().rows[pos])
            }
        }
    }
}
