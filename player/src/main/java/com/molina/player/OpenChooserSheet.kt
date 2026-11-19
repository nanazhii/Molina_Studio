package com.molina.player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.molina.player.databinding.DialogOpenChooserBinding
import com.molina.player.databinding.ItemOpenOptionBinding

/**
 * Dialog kartu Berkas di Home: satu pemilih untuk berkas dan folder (subfolder ikut),
 * dan pemilih sistem (SAF) untuk penyimpanan tanpa izin berkas. Gaya kartu sama dengan [SearchResultsSheet].
 */
internal object OpenChooserSheet {
    enum class Choice { LOCAL, SYSTEM }

    private const val MAX_WIDTH_DP = 420

    private class Option(
        val choice: Choice,
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val sub: Int,
    )

    private val OPTIONS = listOf(
        Option(Choice.LOCAL, R.drawable.ic_file_open_48dp, R.string.chooser_file, R.string.chooser_file_sub),
        Option(Choice.SYSTEM, R.drawable.ic_sd_card_white_24dp, R.string.chooser_system, R.string.chooser_system_sub),
    )

    /** [onPick] dipanggil setelah dialog tertutup; ditutup tanpa memilih tidak memanggil apa pun. */
    fun show(context: Context, onPick: (Choice) -> Unit): AlertDialog {
        val inflater = LayoutInflater.from(context)
        val binding = DialogOpenChooserBinding.inflate(inflater)
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        for (o in OPTIONS) {
            val row = ItemOpenOptionBinding.inflate(inflater, binding.optionList, false)
            row.icon.setImageResource(o.icon)
            row.title.setText(o.title)
            row.sub.setText(o.sub)
            row.root.setOnClickListener {
                dialog.dismiss()
                onPick(o.choice)
            }
            binding.optionList.addView(row.root)
        }
        binding.cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // lembar bulat milik layout
        dialog.show()
        val metrics = context.resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), (MAX_WIDTH_DP * metrics.density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}
