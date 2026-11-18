package com.molina.player.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.common.stream.SearchListModel
import com.molina.player.R
import com.molina.player.databinding.ItemSearchResultBinding
import java.util.Locale

/** Menggambar baris hasil dari [SearchListModel]. Tidak menyimpan keadaan sendiri dan tidak memutuskan apa pun. */
internal class SearchResultsAdapter(
    private val model: SearchListModel,
    private val onRowClick: (SearchListModel.Row) -> Unit,
) : RecyclerView.Adapter<SearchResultsAdapter.Holder>() {

    class Holder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = model.rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = model.rows[position]
        val card = row.card
        with(holder.binding) {
            number.text = String.format(Locale.ROOT, "%03d", row.number)
            title.text = card.title
            chip.text = card.source
            meta.text = card.meta.orEmpty()
            meta.visibility = if (card.meta == null) View.INVISIBLE else View.VISIBLE // tetap menahan lebar agar durasi di ujung
            duration.text = card.duration.orEmpty()
            duration.visibility = if (card.duration == null) View.GONE else View.VISIBLE
            root.setBackgroundResource(
                if (row.number == model.selected) R.drawable.bg_search_card_selected else R.drawable.bg_search_card
            )
            root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRowClick(model.rows[pos])
            }
        }
    }
}
