package com.molina.player

// molina-tv-playlist: daftar putar bergaya channel TV digital. Logika filter ada di core-common/playlist.
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.common.playlist.ChannelRow
import com.molina.core.common.playlist.ChannelRows
import com.molina.player.databinding.DialogPlaylistBinding
import com.molina.player.mpv.MpvProperties

internal class PlaylistDialog(private val player: MPVView) {
    private lateinit var binding: DialogPlaylistBinding

    private var playlist = listOf<MPVView.PlaylistItem>()
    private var allRows = listOf<ChannelRow>()
    private var visibleRows = listOf<ChannelRow>()
    private var selectedIndex = -1
    private var query = ""

    interface Listeners {
        fun pickFile()
        fun openUrl()
        fun onItemPicked(item: MPVView.PlaylistItem)

        /** Tombol Exit pada action bar. */
        fun onExit() {}

        /** Tombol Menu pada action bar (buka menu atas). */
        fun onMenu() {}
    }

    var listeners: Listeners? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        binding = DialogPlaylistBinding.inflate(layoutInflater)

        binding.list.adapter = CustomAdapter(this)
        binding.list.setHasFixedSize(true)
        refresh()

        binding.fileBtn.setOnClickListener { listeners?.pickFile() }
        binding.urlBtn.setOnClickListener { listeners?.openUrl() }
        binding.searchBtn.setOnClickListener { toggleSearch() }
        binding.infoBtn.setOnClickListener { showInfo() }
        binding.menuBtn.setOnClickListener { listeners?.onMenu() }
        binding.exitBtn.setOnClickListener { listeners?.onExit() }
        binding.shuffleBtn.setOnClickListener {
            player.changeShuffle(true)
            refresh()
        }
        binding.repeatBtn.setOnClickListener {
            player.cycleRepeat()
            refresh()
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                applyFilter(scrollToPlaying = false)
            }
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                ChannelRows.pickOnEnter(visibleRows, query)?.let { clickRow(it) }
                true
            } else false
        }

        Utils.handleInsetsAsPadding(binding.root)
        return binding.root
    }

    fun refresh() {
        selectedIndex = MpvProperties.getInt("playlist-pos") ?: -1
        playlist = player.loadPlaylist()
        allRows = playlist.mapIndexed { i, item ->
            ChannelRow(i + 1, item.title ?: Utils.fileBasename(item.filename), i)
        }
        applyFilter(scrollToPlaying = true)

        /*
         * At least on api 33 there is in some cases a (reproducible) bug, where the space below the
         * recycler view for the buttons is not taken into account and they go out-of-bounds of the
         * alert dialog. This fixes it.
         */
        binding.list.post { binding.list.parent.requestLayout() }

        val ctx = binding.root.context
        val accent = ContextCompat.getColor(ctx, R.color.molina_tv_active)
        val normal = ContextCompat.getColor(ctx, R.color.molina_tv_text)
        val disabled = ContextCompat.getColor(ctx, R.color.alpha_disabled)

        binding.countText.text = ChannelRows.counter(playingNumber(), allRows.size)

        binding.shuffleBtn.apply {
            isEnabled = playlist.size > 1
            tint(this, if (!isEnabled) disabled else if (player.getShuffle()) accent else normal)
        }
        val repeatState = player.getRepeat()
        binding.repeatBtn.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, if (repeatState == 2) R.drawable.ic_repeat_one_24dp else R.drawable.ic_repeat_24dp, 0, 0)
            tint(this, if (repeatState > 0) accent else normal)
        }
        tint(binding.searchBtn, if (query.isNotEmpty()) accent else normal)
    }

    private fun tint(button: android.widget.Button, color: Int) {
        button.setTextColor(color)
        button.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    private fun playingNumber(): Int =
        allRows.firstOrNull { playlist[it.position].index == selectedIndex }?.number ?: 0

    private fun applyFilter(scrollToPlaying: Boolean) {
        visibleRows = ChannelRows.filter(allRows, query)
        binding.list.adapter!!.notifyDataSetChanged()
        if (scrollToPlaying) {
            val pos = visibleRows.indexOfFirst { playlist[it.position].index == selectedIndex }
            if (pos >= 0) binding.list.scrollToPosition(pos)
        }
    }

    private fun toggleSearch() {
        val input = binding.searchInput
        val ctx = binding.root.context
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (input.visibility == View.VISIBLE) {
            input.setText("")
            input.visibility = View.GONE
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        } else {
            input.visibility = View.VISIBLE
            input.requestFocus()
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        refresh()
    }

    private fun showInfo() {
        val ctx = binding.root.context
        val on = ctx.getString(R.string.tv_on)
        val off = ctx.getString(R.string.tv_off)
        val text = ctx.getString(
            R.string.tv_info_format, playingNumber(), allRows.size,
            if (player.getShuffle()) on else off, if (player.getRepeat() > 0) on else off,
        )
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    private fun clickRow(row: ChannelRow) {
        listeners?.onItemPicked(playlist[row.position])
    }

    class CustomAdapter(private val parent: PlaylistDialog) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        class ViewHolder(private val parent: PlaylistDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: TextView = view.findViewById(android.R.id.text1)
            private val numberView: TextView = view.findViewById(R.id.channelNumber)

            init {
                view.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) parent.clickRow(parent.visibleRows[pos])
                }
            }

            fun bind(row: ChannelRow, selected: Boolean) {
                val ctx = textView.context
                numberView.text = ChannelRows.number(row.number)
                textView.text = row.title
                itemView.setBackgroundResource(
                    if (selected) R.drawable.bg_channel_selected else R.drawable.bg_channel_row)
                textView.setTextColor(ContextCompat.getColor(ctx, R.color.molina_tv_text))
                numberView.setTextColor(ContextCompat.getColor(
                    ctx, if (selected) R.color.molina_tv_text else R.color.molina_tv_number))
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.dialog_playlist_item, viewGroup, false)
            return ViewHolder(parent, view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val row = parent.visibleRows[position]
            viewHolder.bind(row, parent.playlist[row.position].index == parent.selectedIndex)
        }

        override fun getItemCount() = parent.visibleRows.size
    }
}
