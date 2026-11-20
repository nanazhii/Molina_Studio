package com.molina.player.home

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.molina.core.common.home.Channel
import com.molina.core.common.home.ChannelListModel
import com.molina.player.databinding.DialogChannelsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyambungkan input daftar Saluran (tombol ala remote Prev/Next/Cari/Muat/Hapus/Tutup, kolom cari, Enter,
 * ketuk baris) ke [ChannelListModel], lalu menyegarkan tampilan, penghitung 001/026 dan jam.
 * Tidak tahu tentang dialog, sumber playlist, maupun mpv: semua keputusan dilaporkan lewat callback.
 */
internal class ChannelsController(
    private val binding: DialogChannelsBinding,
    private val onChoose: (Channel) -> Unit,
    private val onReload: () -> Unit,
    private val onRemoveSource: () -> Unit,
    private val onClose: () -> Unit,
) {
    private var channels: List<Channel> = emptyList()
    private var model = ChannelListModel(channels)
    private val layoutManager = LinearLayoutManager(binding.root.context)
    private val adapter = ChannelsAdapter({ model }) { row -> onChoose(row.channel) }
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var searchOpen = false

    private val tick = object : Runnable {
        override fun run() {
            binding.channelClock.text = clockFormat.format(Date())
            binding.root.postDelayed(this, CLOCK_MS)
        }
    }

    fun attach() {
        binding.channelList.layoutManager = layoutManager
        binding.channelList.adapter = adapter

        binding.keyPrev.setOnClickListener { move(-ChannelListModel.PAGE_SIZE) }
        binding.keyNext.setOnClickListener { move(ChannelListModel.PAGE_SIZE) }
        binding.keySearch.setOnClickListener { toggleSearch() }
        binding.keyReload.setOnClickListener { onReload() }
        binding.keyRemove.setOnClickListener { onRemoveSource() }
        binding.keyClose.setOnClickListener { onClose() }

        binding.searchInput.addTextChangedListener { onQueryChanged(binding.searchInput.text.toString()) }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                model.pick()?.let { onChoose(channels[it]) }
                true
            } else {
                false
            }
        }

        tick.run()
        refreshCounter()
    }

    fun detach() {
        binding.root.removeCallbacks(tick)
    }

    /** Saluran yang sedang dimuat (urutan asli), untuk dijadikan antrean putar. */
    fun items(): List<Channel> = channels

    fun setChannels(list: List<Channel>) {
        channels = list
        model = ChannelListModel(list)
        if (binding.searchInput.text.isNotEmpty()) binding.searchInput.setText("")
        adapter.notifyDataSetChanged()
        refreshCounter()
        layoutManager.scrollToPositionWithOffset(0, 0)
    }

    /** [text] null/kosong menyembunyikan baris status; [loading] menampilkan bilah kemajuan. */
    fun setStatus(text: String?, loading: Boolean) {
        binding.channelProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.channelStatus.text = text.orEmpty()
        binding.channelStatus.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setEmpty(show: Boolean) {
        binding.channelEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun onQueryChanged(text: String) {
        model.setQuery(text)
        adapter.notifyDataSetChanged()
        refreshCounter()
        val idx = model.indexOfSelected()
        if (idx >= 0) layoutManager.scrollToPositionWithOffset(idx, 0)
    }

    private fun move(delta: Int) {
        val old = model.indexOfSelected()
        val idx = model.page(delta)
        if (idx < 0) return
        if (old >= 0) adapter.notifyItemChanged(old)
        adapter.notifyItemChanged(idx)
        refreshCounter()
        layoutManager.scrollToPositionWithOffset(idx, 0)
    }

    private fun toggleSearch() {
        searchOpen = !searchOpen
        binding.keySearch.isActivated = searchOpen
        binding.searchInput.visibility = if (searchOpen) View.VISIBLE else View.GONE
        if (searchOpen) {
            binding.searchInput.requestFocus()
            val imm = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.searchInput.setText("")
        }
    }

    private fun refreshCounter() {
        binding.channelCounter.text = if (model.total > 0) model.counter() else ""
    }

    private companion object {
        const val CLOCK_MS = 15_000L
    }
}
