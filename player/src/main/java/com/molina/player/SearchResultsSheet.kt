package com.molina.player

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.common.StreamSearchItem
import com.molina.core.common.stream.SearchCard
import com.molina.player.databinding.DialogSearchResultsBinding
import com.molina.player.databinding.ItemSearchResultBinding

/**
 * Hasil pencarian judul sebagai kartu bergaya Home Theater (kartu #2F2F2F, garis #3C3C3C, aksen #87CF3E) dengan
 * chip sumber (YouTube, TikTok, Facebook). Tanpa thumbnail: tidak ada pemuat gambar dan tidak ada RAM tambahan.
 * Isi kartu dirakit di core-common ([SearchCard]) supaya bisa diuji tanpa Android.
 */
internal object SearchResultsSheet {
    private const val MAX_WRAP_ITEMS = 4
    private const val MAX_WIDTH_DP = 560
    private const val MAX_HEIGHT_FRACTION = 0.6f

    /** [onPick] dipanggil dengan item terpilih; [onCancel] bila ditutup tanpa memilih (luar dialog atau Back). */
    fun show(
        activity: AppCompatActivity,
        items: List<StreamSearchItem>,
        onPick: (StreamSearchItem) -> Unit,
        onCancel: () -> Unit,
    ): AlertDialog {
        val binding = DialogSearchResultsBinding.inflate(LayoutInflater.from(activity))
        val cards = items.map { SearchCard.of(it) }
        val metrics = activity.resources.displayMetrics

        binding.list.layoutManager = LinearLayoutManager(activity)
        binding.list.layoutParams = binding.list.layoutParams.apply {
            height = if (cards.size > MAX_WRAP_ITEMS) {
                (metrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setOnCancelListener { onCancel() }
            .create()
        binding.list.adapter = CardAdapter(cards) { index ->
            dialog.dismiss() // dismiss tidak memicu onCancel
            onPick(items[index])
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // lembar bulat milik layout
        dialog.show()
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), (MAX_WIDTH_DP * metrics.density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private class Holder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    private class CardAdapter(
        private val cards: List<SearchCard>,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = cards.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val card = cards[position]
            with(holder.binding) {
                title.text = card.title
                chip.text = card.source
                meta.text = card.meta.orEmpty()
                meta.visibility = if (card.meta == null) View.INVISIBLE else View.VISIBLE // tetap menahan lebar agar durasi di ujung
                duration.text = card.duration.orEmpty()
                duration.visibility = if (card.duration == null) View.GONE else View.VISIBLE
                root.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onClick(pos)
                }
            }
        }
    }
}
