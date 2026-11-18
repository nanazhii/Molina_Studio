package com.molina.player.search

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.molina.core.common.StreamSearchItem
import com.molina.core.common.stream.SearchCard
import com.molina.core.common.stream.SearchListModel
import com.molina.player.databinding.DialogSearchResultsBinding

/**
 * Pintu masuk tunggal untuk hasil pencarian judul: hanya merakit jendela dialog.
 * Keadaan ada di [SearchListModel] (core-common), input di [SearchResultsController], baris di [SearchResultsAdapter].
 * Gaya Mint: kartu #2F2F2F, garis #3C3C3C, aksen #87CF3E; tanpa thumbnail (hemat RAM dan jaringan).
 */
internal object SearchResultsSheet {
    private const val MAX_WRAP_ITEMS = 4
    private const val MAX_WIDTH_DP = 560
    private const val MAX_HEIGHT_FRACTION = 0.5f

    /** [onPick] dipanggil dengan item terpilih; [onCancel] bila ditutup tanpa memilih (luar dialog atau Back). */
    fun show(
        activity: AppCompatActivity,
        items: List<StreamSearchItem>,
        onPick: (StreamSearchItem) -> Unit,
        onCancel: () -> Unit,
    ): AlertDialog {
        val binding = DialogSearchResultsBinding.inflate(LayoutInflater.from(activity))
        val model = SearchListModel(items.map { SearchCard.of(it) })
        val metrics = activity.resources.displayMetrics

        binding.list.layoutParams = binding.list.layoutParams.apply {
            height = if (model.total > MAX_WRAP_ITEMS) {
                (metrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setOnCancelListener { onCancel() }
            .create()
        SearchResultsController(binding, model) { index ->
            dialog.dismiss() // dismiss tidak memicu onCancel
            onPick(items[index])
        }.attach()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // lembar bulat milik layout
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), (MAX_WIDTH_DP * metrics.density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}
