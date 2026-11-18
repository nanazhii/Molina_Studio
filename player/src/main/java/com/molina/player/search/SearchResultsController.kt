package com.molina.player.search

import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import com.molina.core.common.stream.SearchListModel
import com.molina.player.R
import com.molina.player.databinding.DialogSearchResultsBinding

/**
 * Menyambungkan input (kolom cari, Sebelumnya/Berikut, Enter, ketuk baris) ke [SearchListModel] lalu
 * menyegarkan tampilan. Tidak tahu tentang dialog atau Activity: pilihan dilaporkan lewat [onChoose].
 */
internal class SearchResultsController(
    private val binding: DialogSearchResultsBinding,
    private val model: SearchListModel,
    /** Indeks pada daftar asli. */
    private val onChoose: (Int) -> Unit,
) {
    private val layoutManager = LinearLayoutManager(binding.root.context)
    private val adapter = SearchResultsAdapter(model) { row -> onChoose(row.number - 1) }

    fun attach() {
        binding.list.layoutManager = layoutManager
        binding.list.adapter = adapter
        binding.count.text = binding.root.context.getString(R.string.search_count, model.total)
        refreshCounter()

        binding.prevBtn.setOnClickListener { movePage(-SearchListModel.PAGE_SIZE) }
        binding.nextBtn.setOnClickListener { movePage(SearchListModel.PAGE_SIZE) }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onQueryChanged(s?.toString().orEmpty())
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                model.pick()?.let(onChoose)
                true
            } else {
                false
            }
        }
    }

    private fun onQueryChanged(text: String) {
        model.setQuery(text)
        adapter.notifyDataSetChanged()
        refreshCounter()
        val idx = model.indexOfSelected()
        if (idx >= 0) layoutManager.scrollToPositionWithOffset(idx, 0)
    }

    private fun movePage(delta: Int) {
        val old = model.indexOfSelected()
        val idx = model.page(delta)
        if (idx < 0) return
        if (old >= 0) adapter.notifyItemChanged(old)
        adapter.notifyItemChanged(idx)
        refreshCounter()
        layoutManager.scrollToPositionWithOffset(idx, 0)
    }

    private fun refreshCounter() {
        binding.counter.text = model.counter()
    }
}
