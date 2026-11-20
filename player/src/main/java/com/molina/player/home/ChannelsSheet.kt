package com.molina.player.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.Channel
import com.molina.player.R
import com.molina.player.databinding.DialogChannelsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pintu masuk tunggal daftar Saluran bergaya TV digital: hanya merakit jendela dan menghubungkan dua bagian.
 * Daftar, sorotan, dan tombol remote ada di [ChannelsController]; chip sumber, tambah tautan, dan berkas folder
 * ada di [ChannelsSources]; data ada di [ChannelsRepository]; aturan daftar ada di core-common (ChannelListModel).
 * [onPlay] menerima saluran terpilih dan seluruh daftar (untuk antrean putar).
 */
internal class ChannelsSheet(
    private val context: Context,
    private val repo: ChannelsRepository,
    private val scope: CoroutineScope,
    private val onPlay: (picked: Channel, all: List<Channel>) -> Unit,
) {
    private val binding = DialogChannelsBinding.inflate(LayoutInflater.from(context))
    private val dialog = Dialog(context)
    private var loadJob: Job? = null
    private lateinit var controller: ChannelsController
    private lateinit var sources: ChannelsSources

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)

        controller = ChannelsController(
            binding,
            onChoose = { channel ->
                dialog.dismiss()
                onPlay(channel, controller.items())
            },
            onReload = { sources.current?.let { load(it, force = true) } },
            onRemoveSource = { sources.removeCurrent() },
            onClose = { dialog.dismiss() },
        )
        sources = ChannelsSources(
            binding, repo, scope,
            onLoad = { ref, force -> load(ref, force) },
            onEmpty = {
                loadJob?.cancel()
                controller.setChannels(emptyList())
                controller.setStatus(null, false)
                controller.setEmpty(true)
            },
            onProblem = { res -> controller.setStatus(context.getString(res), false) },
        )
        dialog.setOnDismissListener {
            loadJob?.cancel()
            controller.detach()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // lembar bulat milik layout
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        controller.attach()
        dialog.show()
        val m = context.resources.displayMetrics
        dialog.window?.setLayout(
            minOf((m.widthPixels * WIDTH_FRACTION).toInt(), (MAX_WIDTH_DP * m.density).toInt()),
            minOf((m.heightPixels * HEIGHT_FRACTION).toInt(), (MAX_HEIGHT_DP * m.density).toInt()),
        )
        Diag.behavior("player", "channels-open", "daftar saluran dibuka")
        sources.attach()
    }

    private fun load(ref: String, force: Boolean) {
        loadJob?.cancel()
        val hit = if (force) null else repo.cached(ref)
        if (hit != null) {
            showLoaded(hit)
            return
        }
        controller.setChannels(emptyList())
        controller.setStatus(context.getString(R.string.channels_loading), true)
        controller.setEmpty(false)
        loadJob = scope.launch {
            val result = repo.loadSource(ref, force)
            if (!dialog.isShowing || ref != sources.current) return@launch
            if (result.failed) {
                controller.setStatus(context.getString(R.string.channels_source_failed), false)
            } else {
                showLoaded(result.channels)
            }
        }
    }

    private fun showLoaded(list: List<Channel>) {
        controller.setChannels(list)
        controller.setEmpty(false)
        controller.setStatus(
            if (list.isEmpty()) context.getString(R.string.channels_source_empty)
            else context.getString(R.string.channels_count, list.size),
            false,
        )
    }

    private companion object {
        const val WIDTH_FRACTION = 0.88f
        const val HEIGHT_FRACTION = 0.95f
        const val MAX_WIDTH_DP = 480
        const val MAX_HEIGHT_DP = 720
    }
}
