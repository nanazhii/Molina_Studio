package com.molina.player.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.Channel
import com.molina.core.common.home.ChannelFilter
import com.molina.core.security.UrlPolicy
import com.molina.player.PlaybackLog
import com.molina.player.R
import com.molina.player.databinding.DialogChannelsBinding
import com.molina.player.databinding.ItemHomeRowBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Jendela daftar Saluran TV: kolom cari, daftar, tombol impor berkas, tambah/ganti tautan M3U. Hanya tampilan
 * dan input; sumber data ada di [ChannelsRepository], penyaringan di core-common ([ChannelFilter]), pemilih
 * berkas dimiliki layar lewat [pickFile] (kontrak [HomeHost.pickChannelFile]).
 */
internal class ChannelsSheet(
    private val context: Context,
    private val repo: ChannelsRepository,
    private val scope: CoroutineScope,
    private val pickFile: (onPicked: (Uri) -> Unit) -> Unit,
    private val onPlay: (Channel) -> Unit,
) {
    private val binding = DialogChannelsBinding.inflate(LayoutInflater.from(context))
    private val dialog = Dialog(context)
    private val adapter = ChannelsAdapter { channel ->
        dialog.dismiss()
        onPlay(channel)
    }
    private var all: List<Channel> = emptyList()

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        binding.channelList.layoutManager = LinearLayoutManager(context)
        binding.channelList.adapter = adapter
        binding.filterInput.addTextChangedListener { render() }
        binding.importBtn.setOnClickListener { pickFile { uri -> import(uri) } }
        binding.addUrlBtn.setOnClickListener { askUrl() }
        binding.closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // lembar bulat milik layout
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        val metrics = context.resources.displayMetrics
        dialog.window?.setLayout(
            minOf((metrics.widthPixels * 0.94f).toInt(), (MAX_WIDTH_DP * metrics.density).toInt()),
            (metrics.heightPixels * 0.82f).toInt(),
        )
        Diag.behavior("player", "channels-open", "daftar saluran dibuka")
        val cached = repo.cached()
        if (cached != null) apply(cached) else reload(force = false)
    }

    private fun reload(force: Boolean) {
        binding.channelStatus.setText(R.string.channels_loading)
        binding.channelStatus.visibility = View.VISIBLE
        scope.launch {
            val result = repo.load(force)
            if (dialog.isShowing) apply(result)
        }
    }

    private fun import(uri: Uri) {
        if (!dialog.isShowing) return
        binding.channelStatus.setText(R.string.channels_loading)
        binding.channelStatus.visibility = View.VISIBLE
        scope.launch {
            when (val r = repo.import(uri)) {
                is ImportResult.Ok -> {
                    val msg = context.getString(R.string.channels_import_ok, r.fileName, r.count) +
                        if (r.publicFolder) "" else "\n" + context.getString(R.string.channels_import_private)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (dialog.isShowing) reload(force = false)
                }
                ImportResult.Empty -> done(R.string.channels_import_empty)
                ImportResult.Failed -> done(R.string.channels_import_failed)
            }
        }
    }

    private fun done(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
        if (dialog.isShowing) binding.channelStatus.visibility = View.GONE
    }

    private fun apply(load: ChannelsLoad) {
        all = load.channels
        binding.addUrlBtn.setText(if (repo.sourceUrl == null) R.string.channels_add_url else R.string.channels_change_url)
        val status = when {
            load.remoteFailed -> R.string.channels_load_failed
            load.folderBlocked && all.isEmpty() -> R.string.channels_folder_blocked
            else -> 0
        }
        binding.channelStatus.visibility = if (status != 0) View.VISIBLE else View.GONE
        if (status != 0) binding.channelStatus.setText(status)
        render()
    }

    private fun render() {
        val shown = ChannelFilter.apply(all, binding.filterInput.text.toString())
        adapter.submit(shown)
        binding.channelCount.text = context.getString(R.string.channels_count, shown.size)
        binding.channelEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        if (shown.isEmpty()) {
            binding.channelEmpty.text =
                if (all.isEmpty()) context.getString(R.string.channels_empty, repo.folder.path)
                else context.getString(R.string.search_no_results)
        }
    }

    private fun askUrl() {
        val input = EditText(context).apply {
            setSingleLine()
            setText(repo.sourceUrl.orEmpty())
            setSelection(text.length)
            hint = context.getString(R.string.channels_url_hint)
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(R.string.channels_add_url)
            .setView(input)
            .setPositiveButton(R.string.channels_save, null) // diganti di bawah agar dialog tidak tertutup saat tautan salah
            .setNegativeButton(R.string.chooser_cancel, null)
        if (repo.sourceUrl != null) {
            builder.setNeutralButton(R.string.channels_remove_url) { _, _ ->
                repo.sourceUrl = null
                Diag.behavior("player", "channels-source-cleared", "tautan daftar dihapus")
                reload(force = true)
            }
        }
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (!M3uFetcher.isHttp(text) || UrlPolicy.check(text) !is UrlPolicy.Verdict.Allowed) {
                    Toast.makeText(context, R.string.channels_url_invalid, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                repo.sourceUrl = text
                Diag.behavior("player", "channels-source-set", "tautan daftar disimpan",
                    mapOf("target" to PlaybackLog.target(text)))
                dlg.dismiss()
                reload(force = true)
            }
        }
        dlg.show()
    }

    private companion object {
        const val MAX_WIDTH_DP = 480
    }
}

/** Baris saluran. Tanpa logika: hanya menampilkan nama dan grup lalu meneruskan ketukan. */
internal class ChannelsAdapter(private val onClick: (Channel) -> Unit) : RecyclerView.Adapter<ChannelsAdapter.Holder>() {
    private var items: List<Channel> = emptyList()

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemHomeRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemHomeRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = items[position]
        holder.binding.rowTitle.text = channel.name
        holder.binding.rowSub.text = channel.group
        holder.binding.rowSub.visibility = if (channel.group.isEmpty()) View.GONE else View.VISIBLE
        holder.binding.root.setOnClickListener { onClick(channel) }
    }
}
