package com.molina.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.FileEntry
import com.molina.core.storage.ListOptions
import com.molina.core.storage.ListResult
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import com.molina.core.storage.SortKey
import com.molina.core.storage.TransferKind
import com.molina.core.ui.MolinaDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.molina.core.ui.R as UiR

/**
 * Penjelajah folder Molina. molina-patch26b: header bingkai hijau, cari nama (opsional sampai subfolder),
 * sortir, berkas tersembunyi, serta Folder baru / Berkas baru / Ganti nama / Hapus lewat titik tiga.
 * molina-patch26c: Mode Pilih (geser kanan memilih, geser kiri membatalkan, otomatis keluar saat 0
 * terpilih), Salin/Pindah/Hapus massal dengan progres dan batal, serta bilah Tempel.
 * Seluruh akses berkas lewat :core-storage.
 */
class FilesFragment : Fragment(R.layout.fragment_files) {
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val adapter = FilesAdapter(
        keyOf = { entry -> keyOf(entry) },
        onClick = { entry -> onEntryClick(entry) },
        onMenu = { entry, anchor -> showEntryMenu(entry, anchor) },
    )
    private val selection = FilesSelection()
    private var clip: FilesClip? = null
    private var swipeHelper: ItemTouchHelper? = null

    private var views: Views? = null
    private var backCallback: OnBackPressedCallback? = null
    private var loadJob: Job? = null
    private var queryJob: Job? = null
    private var currentPath = ""
    private var options = ListOptions()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            FilesEvents.permissionResult(granted)
            if (views != null) load(currentPath)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentPath = savedInstanceState?.getString(KEY_PATH).orEmpty()
        if (savedInstanceState != null) {
            options = restoreOptions(savedInstanceState)
            restoreSelectionAndClip(savedInstanceState)
        }
        val v = Views(view)
        views = v
        v.list.layoutManager = LinearLayoutManager(requireContext())
        v.list.adapter = adapter
        swipeHelper = ItemTouchHelper(
            FilesSwipeCallback(
                density = resources.displayMetrics.density,
                isSelected = { pos -> adapter.isSelectedAt(pos) },
                onSelect = { pos -> onSwipeSelect(pos) },
                onDeselect = { pos -> onSwipeDeselect(pos) },
            ),
        ).also { it.attachToRecyclerView(v.list) }
        v.clipPaste.setOnClickListener { pasteHere() }
        v.clipCancel.setOnClickListener { clearClip() }
        v.progressCancel.setOnClickListener { FilesTransfer.cancel() }
        v.up.setOnClickListener { goUp() }
        v.menu.setOnClickListener { showHeaderMenu(it) }
        v.retry.setOnClickListener { onRetry() }
        v.search.setText(options.query)
        v.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onQueryEdited(s?.toString().orEmpty())
        })
        v.search.setOnEditorActionListener { tv, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                tv.clearFocus()
                true
            } else {
                false
            }
        }
        val back = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                goUp()
            }
        }
        backCallback = back
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, back)
        applyClip()
        applySelection()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                FilesTransfer.state.collect { renderTransfer(it) }
            }
        }
        load(currentPath)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PATH, currentPath)
        outState.putString(KEY_QUERY, options.query)
        outState.putString(KEY_SORT, options.sortKey.name)
        outState.putBoolean(KEY_ASC, options.ascending)
        outState.putBoolean(KEY_FOLDERS_FIRST, options.foldersFirst)
        outState.putBoolean(KEY_HIDDEN, options.showHidden)
        outState.putBoolean(KEY_DEEP, options.deep)
        outState.putBoolean(KEY_SEL_ACTIVE, selection.active)
        outState.putStringArrayList(KEY_SEL_KEYS, ArrayList(selection.snapshot()))
        clip?.let {
            outState.putString(KEY_CLIP_KIND, it.kind.name)
            outState.putStringArrayList(KEY_CLIP_PATHS, ArrayList(it.paths))
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        queryJob?.cancel()
        queryJob = null
        backCallback = null
        swipeHelper?.attachToRecyclerView(null)
        swipeHelper = null
        views?.let { it.list.adapter = null }
        views = null
        super.onDestroyView()
    }

    private fun restoreOptions(b: Bundle) = ListOptions(
        sortKey = runCatching { SortKey.valueOf(b.getString(KEY_SORT).orEmpty()) }.getOrDefault(SortKey.NAME),
        ascending = b.getBoolean(KEY_ASC, true),
        foldersFirst = b.getBoolean(KEY_FOLDERS_FIRST, true),
        showHidden = b.getBoolean(KEY_HIDDEN, false),
        query = b.getString(KEY_QUERY).orEmpty(),
        deep = b.getBoolean(KEY_DEEP, false),
    )

    private fun restoreSelectionAndClip(b: Bundle) {
        selection.restore(b.getBoolean(KEY_SEL_ACTIVE, false), b.getStringArrayList(KEY_SEL_KEYS).orEmpty())
        val kind = runCatching { TransferKind.valueOf(b.getString(KEY_CLIP_KIND).orEmpty()) }.getOrNull()
        val paths = b.getStringArrayList(KEY_CLIP_PATHS).orEmpty()
        clip = if (kind != null && kind != TransferKind.DELETE && paths.isNotEmpty()) FilesClip(kind, paths) else null
    }

    /** Kunci item = path relatif terhadap root Molina (juga untuk hasil pencarian sampai subfolder). */
    private fun keyOf(entry: FileEntry): String = BrowserPath.entryPath(currentPath, entry.relDir, entry.name)

    // --- navigasi

    private fun onEntryClick(entry: FileEntry) {
        if (selection.active) {
            val picked = selection.toggle(keyOf(entry))
            FilesEvents.selection(
                action = if (picked) "add" else if (selection.active) "remove" else "exit",
                via = "tap",
                count = selection.count,
            )
            applySelection()
            return
        }
        if (entry.isDirectory) navigate(BrowserPath.entryPath(currentPath, entry.relDir, entry.name))
    }

    /** Pindah folder; kueri pencarian dikosongkan agar daftar folder baru tidak terfilter. */
    private fun navigate(path: String) {
        clearQuery()
        load(path)
    }

    private fun goUp() {
        if (selection.active) {
            exitSelection("back")
        } else if (options.query.isNotEmpty()) {
            clearQuery()
            load(currentPath)
        } else if (!BrowserPath.isRoot(currentPath)) {
            load(BrowserPath.parent(currentPath))
        }
    }

    private fun clearQuery() {
        queryJob?.cancel()
        if (options.query.isEmpty()) return
        options = options.copy(query = "")
        views?.search?.setText("")
    }

    // --- pencarian

    private fun onQueryEdited(text: String) {
        if (text.trim() == options.query) return
        queryJob?.cancel()
        queryJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(QUERY_DEBOUNCE_MS)
            options = options.copy(query = text.trim())
            load(currentPath)
        }
    }

    // --- izin dan muat

    private fun onRetry() {
        if (hasStoragePermission()) {
            load(currentPath)
        } else {
            FilesEvents.permissionRequested()
            permissionRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasStoragePermission(): Boolean =
        requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun load(path: String) {
        loadJob?.cancel()
        if (path != currentPath && selection.active) exitSelection("nav")
        currentPath = path
        showHeader(path)
        if (!hasStoragePermission()) {
            FilesEvents.permissionMissing(path)
            showError(ErrorKind.PERMISSION_DENIED)
            return
        }
        val opts = options
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (BrowserPath.isRoot(path)) {
                val layout = storage.ensureLayout()
                if (layout is MolinaResult.Failure) FilesEvents.failed("layout", layout.error, path)
            }
            if (opts.query.isNotEmpty() && opts.deep) showStatus("Mencari\u2026")
            when (val result = storage.browse(path, opts)) {
                is MolinaResult.Success -> {
                    FilesEvents.listed(path, result.value.entries.size)
                    if (opts.query.isNotEmpty()) {
                        FilesEvents.searched(path, result.value.entries.size, opts.deep, result.value.truncated)
                    }
                    showContent(result.value, opts)
                }
                is MolinaResult.Failure -> {
                    FilesEvents.failed("list", result.error, path)
                    showError(result.error.kind)
                }
            }
        }
    }

    // --- menu

    private fun showHeaderMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        val m = popup.menu
        if (selection.active) {
            val canAct = selection.count > 0 && !FilesTransfer.isRunning
            m.add(Menu.NONE, MENU_COPY, 0, "Salin").isEnabled = canAct
            m.add(Menu.NONE, MENU_MOVE, 1, "Pindah").isEnabled = canAct
            m.add(Menu.NONE, MENU_DELETE_SELECTED, 2, "Hapus").isEnabled = canAct
            m.add(Menu.NONE, MENU_SELECT_ALL, 3, "Pilih semua")
            m.add(Menu.NONE, MENU_SELECT_DONE, 4, "Selesai memilih")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_COPY -> setClip(TransferKind.COPY)
                    MENU_MOVE -> setClip(TransferKind.MOVE)
                    MENU_DELETE_SELECTED -> confirmDeleteSelection()
                    MENU_SELECT_ALL -> selectAllVisible()
                    MENU_SELECT_DONE -> exitSelection("menu")
                }
                true
            }
            popup.show()
            return
        }
        m.add(Menu.NONE, MENU_REFRESH, 0, "Segarkan")
        m.add(Menu.NONE, MENU_NEW_FOLDER, 1, "Folder baru")
        m.add(Menu.NONE, MENU_NEW_FILE, 2, "Berkas baru")
        m.add(Menu.NONE, MENU_SELECT, 3, "Pilih")
        val sorts = listOf(
            SortKey.NAME to "Urut: Nama", SortKey.DATE to "Urut: Tanggal",
            SortKey.SIZE to "Urut: Ukuran", SortKey.TYPE to "Urut: Tipe",
        )
        sorts.forEachIndexed { i, (key, label) ->
            m.add(GROUP_SORT, MENU_SORT_BASE + key.ordinal, 10 + i, label)
        }
        m.setGroupCheckable(GROUP_SORT, true, true)
        m.findItem(MENU_SORT_BASE + options.sortKey.ordinal)?.isChecked = true
        m.add(GROUP_FLAGS, MENU_DESC, 20, "Menurun").apply { isCheckable = true; isChecked = !options.ascending }
        m.add(GROUP_FLAGS, MENU_FOLDERS_FIRST, 21, "Folder di atas").apply { isCheckable = true; isChecked = options.foldersFirst }
        m.add(GROUP_FLAGS, MENU_HIDDEN, 22, "Tampilkan tersembunyi").apply { isCheckable = true; isChecked = options.showHidden }
        m.add(GROUP_FLAGS, MENU_DEEP, 23, "Cari sampai subfolder").apply { isCheckable = true; isChecked = options.deep }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_REFRESH -> load(currentPath)
                MENU_NEW_FOLDER -> promptCreate(folder = true)
                MENU_NEW_FILE -> promptCreate(folder = false)
                MENU_SELECT -> enterSelectionFromMenu()
                MENU_DESC -> applyOptions(options.copy(ascending = !options.ascending))
                MENU_FOLDERS_FIRST -> applyOptions(options.copy(foldersFirst = !options.foldersFirst))
                MENU_HIDDEN -> applyOptions(options.copy(showHidden = !options.showHidden))
                MENU_DEEP -> applyOptions(options.copy(deep = !options.deep))
                else -> {
                    val key = SortKey.values().firstOrNull { MENU_SORT_BASE + it.ordinal == item.itemId }
                    if (key != null) applyOptions(options.copy(sortKey = key))
                }
            }
            true
        }
        popup.show()
    }

    private fun applyOptions(next: ListOptions) {
        options = next
        load(currentPath)
    }

    private fun showEntryMenu(entry: FileEntry, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(Menu.NONE, MENU_RENAME, 0, "Ganti nama")
        popup.menu.add(Menu.NONE, MENU_DELETE, 1, "Hapus")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> promptRename(entry)
                MENU_DELETE -> promptDelete(entry)
            }
            true
        }
        popup.show()
    }

    // --- operasi

    private fun promptCreate(folder: Boolean) {
        MolinaDialog.input(
            requireContext(),
            title = if (folder) "Folder baru" else "Berkas baru",
            initial = "",
            positive = "Buat",
            selectBase = false,
        ) { name ->
            runOp(if (folder) "create-folder" else "create-file", folder, currentPath) {
                if (folder) storage.createFolder(currentPath, name) else storage.createFile(currentPath, name)
            }
        }
    }

    private fun promptRename(entry: FileEntry) {
        val path = BrowserPath.entryPath(currentPath, entry.relDir, entry.name)
        MolinaDialog.input(
            requireContext(),
            title = "Ganti nama",
            initial = entry.name,
            positive = "Ganti",
            selectBase = !entry.isDirectory,
        ) { name ->
            runOp("rename", entry.isDirectory, path) { storage.rename(path, name) }
        }
    }

    private fun promptDelete(entry: FileEntry) {
        val path = BrowserPath.entryPath(currentPath, entry.relDir, entry.name)
        val message = if (entry.isDirectory) {
            "\"${entry.name}\" dan seluruh isinya akan dihapus permanen."
        } else {
            "\"${entry.name}\" akan dihapus permanen."
        }
        MolinaDialog.confirm(requireContext(), "Hapus?", message, "Hapus") {
            runOp("delete", entry.isDirectory, path) { storage.delete(path) }
        }
    }

    /** Menjalankan operasi tulis, mencatat peristiwa tanpa path, lalu memuat ulang folder aktif. */
    private fun runOp(op: String, directory: Boolean, path: String, block: suspend () -> MolinaResult<*>) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = block()) {
                is MolinaResult.Success -> {
                    FilesEvents.op(op, directory, path)
                    load(currentPath)
                }
                is MolinaResult.Failure -> {
                    reportOpFailure(op, result.error, path)
                    load(currentPath)
                }
            }
        }
    }

    private fun reportOpFailure(op: String, error: MolinaError, path: String) {
        FilesEvents.failed(op, error, path)
        val root = view ?: return
        Snackbar.make(root, FilesErrorText.forOp(error.kind), Snackbar.LENGTH_LONG).show()
    }

    // --- Mode Pilih dan operasi massal (molina-patch26c)

    private fun enterSelectionFromMenu() {
        selection.enter()
        FilesEvents.selection("enter", "menu", selection.count)
        applySelection()
    }

    private fun exitSelection(via: String) {
        if (!selection.active && selection.count == 0) return
        selection.exit()
        FilesEvents.selection("exit", via, 0)
        applySelection()
    }

    private fun selectAllVisible() {
        selection.selectAll(adapter.currentList.map { keyOf(it) })
        FilesEvents.selection("all", "menu", selection.count)
        applySelection()
    }

    private fun onSwipeSelect(position: Int) {
        val entry = adapter.currentList.getOrNull(position) ?: return
        val wasActive = selection.active
        selection.select(keyOf(entry))
        FilesEvents.selection(if (wasActive) "add" else "enter", "swipe", selection.count)
        views?.list?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        applySelection()
        adapter.notifyItemChanged(position)
    }

    private fun onSwipeDeselect(position: Int) {
        val entry = adapter.currentList.getOrNull(position) ?: return
        selection.deselect(keyOf(entry))
        FilesEvents.selection(if (selection.active) "remove" else "exit", "swipe", selection.count)
        views?.list?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        applySelection()
        adapter.notifyItemChanged(position)
    }

    /** Salin/Pindah: item terpilih masuk papan klip, lalu pengguna menavigasi ke tujuan dan menekan Tempel. */
    private fun setClip(kind: TransferKind) {
        val paths = selection.snapshot()
        if (paths.isEmpty()) return
        clip = FilesClip(kind, paths)
        FilesEvents.clip("set", kind, paths.size)
        exitSelection("op")
        applyClip()
    }

    private fun clearClip() {
        clip?.let { FilesEvents.clip("clear", it.kind, it.paths.size) }
        clip = null
        applyClip()
    }

    private fun pasteHere() {
        val c = clip ?: return
        if (!FilesTransfer.start(c.kind, c.paths, currentPath)) notifyBusy()
    }

    private fun confirmDeleteSelection() {
        val paths = selection.snapshot()
        if (paths.isEmpty()) return
        MolinaDialog.confirm(
            requireContext(),
            title = "Hapus ${paths.size} item?",
            message = "Item terpilih dan seluruh isinya akan dihapus permanen.",
            positive = "Hapus",
        ) {
            if (!FilesTransfer.start(TransferKind.DELETE, paths, currentPath)) notifyBusy()
        }
    }

    private fun notifyBusy() {
        val root = view ?: return
        Snackbar.make(root, "Operasi lain masih berjalan", Snackbar.LENGTH_SHORT).show()
    }

    private fun renderTransfer(state: TransferState) {
        val v = views ?: return
        when (state) {
            TransferState.Idle -> v.progress.visibility = View.GONE
            is TransferState.Running -> {
                v.progress.visibility = View.VISIBLE
                v.progressText.text = TransferText.progressLine(state.kind, state.progress, state.cancelling)
                v.progressBar.setProgressCompat(((state.progress?.fraction ?: 0f) * 100).toInt(), true)
                v.progressCancel.isEnabled = !state.cancelling
            }
            is TransferState.Finished -> {
                v.progress.visibility = View.GONE
                onTransferFinished(state)
                FilesTransfer.acknowledge(state.id)
            }
        }
        applyClip()
    }

    private fun onTransferFinished(state: TransferState.Finished) {
        val text = state.result?.let { TransferText.summary(it) }
            ?: state.error?.let { FilesErrorText.forTransfer(it.kind) }
            ?: "Operasi gagal"
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
        if (state.kind == TransferKind.DELETE) exitSelection("op") else if (state.result != null) clearClip()
        load(currentPath)
    }

    private fun applySelection() {
        adapter.setSelection(selection.active, selection.snapshot().toSet())
        showHeader(currentPath)
    }

    private fun applyClip() {
        val v = views ?: return
        val c = clip
        v.clipBar.visibility = if (c == null) View.GONE else View.VISIBLE
        if (c != null) v.clipText.text = TransferText.clipLabel(c.kind, c.paths.size)
        v.clipPaste.isEnabled = !FilesTransfer.isRunning
    }

    private fun updateBack() {
        backCallback?.isEnabled = selection.active || options.query.isNotEmpty() || !BrowserPath.isRoot(currentPath)
    }

    // --- tampilan

    private fun showHeader(path: String) {
        val v = views ?: return
        val root = BrowserPath.isRoot(path)
        val picking = selection.active
        v.title.text = when {
            !picking -> BrowserPath.title(path)
            selection.count == 0 -> "Mode Pilih"
            else -> "${selection.count} dipilih"
        }
        v.title.setTextColor(
            if (picking) {
                ContextCompat.getColor(requireContext(), UiR.color.molina_key_green)
            } else {
                ContextCompat.getColor(requireContext(), android.R.color.white)
            },
        )
        val canUp = picking || !root
        v.up.isEnabled = canUp
        v.up.alpha = if (canUp) 1f else 0.35f
        updateBack()
    }

    private fun showStatus(text: String?) {
        val v = views ?: return
        v.status.text = text.orEmpty()
        v.status.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun showContent(result: ListResult, opts: ListOptions) {
        val v = views ?: return
        adapter.submitList(result.entries)
        v.retry.visibility = View.GONE
        selection.retainOnly(result.entries.mapTo(HashSet()) { keyOf(it) })
        applySelection()
        val searching = opts.query.isNotEmpty()
        showStatus(
            when {
                !searching -> null
                result.truncated -> "${result.entries.size} hasil (dipotong, maks. ${ListOptions.MAX_RESULTS})"
                else -> "${result.entries.size} hasil"
            },
        )
        if (result.entries.isEmpty()) {
            v.message.text = if (searching) "Tidak ada hasil" else "Folder kosong"
            v.message.visibility = View.VISIBLE
        } else {
            v.message.visibility = View.GONE
        }
    }

    private fun showError(kind: ErrorKind) {
        val v = views ?: return
        adapter.submitList(emptyList<FileEntry>())
        showStatus(null)
        v.message.text = FilesErrorText.of(kind)
        v.message.visibility = View.VISIBLE
        v.retry.text = if (kind == ErrorKind.PERMISSION_DENIED) "Beri izin" else "Coba lagi"
        v.retry.visibility = View.VISIBLE
    }

    private class Views(root: View) {
        val title: TextView = root.findViewById(R.id.files_title)
        val up: ImageButton = root.findViewById(R.id.files_up)
        val menu: ImageButton = root.findViewById(R.id.files_menu)
        val search: EditText = root.findViewById(R.id.files_search)
        val status: TextView = root.findViewById(R.id.files_status)
        val message: TextView = root.findViewById(R.id.files_message)
        val retry: MaterialButton = root.findViewById(R.id.files_retry)
        val list: RecyclerView = root.findViewById(R.id.files_list)
        val clipBar: LinearLayout = root.findViewById(R.id.files_clip_bar)
        val clipText: TextView = root.findViewById(R.id.files_clip_text)
        val clipPaste: MaterialButton = root.findViewById(R.id.files_clip_paste)
        val clipCancel: MaterialButton = root.findViewById(R.id.files_clip_cancel)
        val progress: LinearLayout = root.findViewById(R.id.files_progress)
        val progressText: TextView = root.findViewById(R.id.files_progress_text)
        val progressBar: LinearProgressIndicator = root.findViewById(R.id.files_progress_bar)
        val progressCancel: MaterialButton = root.findViewById(R.id.files_progress_cancel)
    }

    private companion object {
        const val KEY_PATH = "path"
        const val KEY_QUERY = "query"
        const val KEY_SORT = "sort"
        const val KEY_ASC = "asc"
        const val KEY_FOLDERS_FIRST = "folders_first"
        const val KEY_HIDDEN = "hidden"
        const val KEY_DEEP = "deep"
        const val KEY_SEL_ACTIVE = "sel_active"
        const val KEY_SEL_KEYS = "sel_keys"
        const val KEY_CLIP_KIND = "clip_kind"
        const val KEY_CLIP_PATHS = "clip_paths"
        const val QUERY_DEBOUNCE_MS = 250L

        const val GROUP_SORT = 1
        const val GROUP_FLAGS = 2
        const val MENU_REFRESH = 1
        const val MENU_NEW_FOLDER = 2
        const val MENU_NEW_FILE = 3
        const val MENU_RENAME = 4
        const val MENU_DELETE = 5
        const val MENU_DESC = 6
        const val MENU_FOLDERS_FIRST = 7
        const val MENU_HIDDEN = 8
        const val MENU_DEEP = 9
        const val MENU_SELECT = 10
        const val MENU_COPY = 11
        const val MENU_MOVE = 12
        const val MENU_DELETE_SELECTED = 13
        const val MENU_SELECT_ALL = 14
        const val MENU_SELECT_DONE = 15
        const val MENU_SORT_BASE = 100
    }
}
