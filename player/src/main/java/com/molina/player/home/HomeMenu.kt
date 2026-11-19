package com.molina.player.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.molina.player.R
import com.molina.player.databinding.ItemHomeMenuRowBinding
import com.molina.player.databinding.PopupHomeMenuBinding

/** Panel drop dari ikon menu Home. Tampilan murni: tidak tahu apa yang dikerjakan tiap aksi. */
internal object HomeMenu {
    private const val WIDTH_DP = 216 // 200dp isi + 8dp ruang bayangan di tiap sisi
    private const val ELEVATION_DP = 16f
    private const val OFFSET_DP = 2

    private class Item(val action: HomeAction, @DrawableRes val icon: Int, @StringRes val label: Int)

    private val ITEMS = listOf(
        Item(HomeAction.FILES, R.drawable.ic_home_files, R.string.home_menu_files),
        Item(HomeAction.CHANNELS, R.drawable.ic_home_channels, R.string.home_menu_channels),
        Item(HomeAction.SETTINGS, R.drawable.ic_home_settings, R.string.home_menu_settings),
        Item(HomeAction.REFRESH, R.drawable.ic_home_refresh, R.string.home_menu_refresh),
    )

    /** [onPick] dipanggil setelah panel tertutup; menutup tanpa memilih tidak memanggil apa pun. */
    fun show(anchor: View, onPick: (HomeAction) -> Unit): PopupWindow {
        val ctx = anchor.context
        val density = ctx.resources.displayMetrics.density
        val inflater = LayoutInflater.from(ctx)
        val binding = PopupHomeMenuBinding.inflate(inflater)
        val popup = PopupWindow(binding.root, (WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = ELEVATION_DP * density
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // agar sentuhan di luar menutup panel
        popup.animationStyle = R.style.MolinaHomeMenuAnim
        for (item in ITEMS) {
            val row = ItemHomeMenuRowBinding.inflate(inflater, binding.menuList, false)
            row.icon.setImageResource(item.icon)
            row.label.setText(item.label)
            row.root.setOnClickListener {
                popup.dismiss()
                onPick(item.action)
            }
            binding.menuList.addView(row.root)
        }
        popup.showAsDropDown(anchor, 0, (OFFSET_DP * density).toInt(), Gravity.END)
        return popup
    }
}
