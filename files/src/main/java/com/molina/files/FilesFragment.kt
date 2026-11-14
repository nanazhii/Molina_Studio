package com.molina.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Penjelajah folder Molina. molina-patch26b: header bingkai hijau, cari nama (opsional sampai subfolder),
 * sortir, berkas tersembunyi, serta Folder baru / Berkas baru / Ganti nama / Hapus lewat titik tiga.
 * Seluruh akses berkas lewat :core-storage.
 */
class FilesFragment : Fragment(R.layout.fragment_files) {
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val adapter = FilesAdapter(
        onClick = { entry -> onEntryClick(entry) },
        onMenu = { entry, anchor -> showEntryMenu(entry, anchor) },
    )

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
        if (savedInstanceState != null) options = restoreOptions(savedInstanceState)
        val v = Views(view)
        views = v
        v.list.layoutManager = LinearLayoutManager(requireContext())
        v.list.adapter = adapter
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
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        queryJob?.cancel()
        queryJob = null
        backCallback = null
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

    // --- navigasi

    private fun onEntryClick(entry: FileEntry) {
        if (entry.isDirectory) navigate(BrowserPath.entryPath(currentPath, entry.relDir, entry.name))
    }

    /** Pindah folder; kueri pencarian dikosongkan agar daftar folder baru tidak terfilter. */
    private fun navigate(path: String) {
        clearQuery()
        load(path)
    }

    private fun goUp() {
        if (options.query.isNotEmpty()) {
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
        m.add(Menu.NONE, MENU_REFRESH, 0, "Segarkan")
        m.add(Menu.NONE, MENU_NEW_FOLDER, 1, "Folder baru")
        m.add(Menu.NONE, MENU_NEW_FILE, 2, "Berkas baru")
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
        FilesDialog.input(
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
        FilesDialog.input(
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
        FilesDialog.confirm(requireContext(), "Hapus?", message, "Hapus") {
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

    // --- tampilan

    private fun showHeader(path: String) {
        val v = views ?: return
        val root = BrowserPath.isRoot(path)
        v.title.text = BrowserPath.title(path)
        v.up.isEnabled = !root
        v.up.alpha = if (root) 0.35f else 1f
        backCallback?.isEnabled = !root || options.query.isNotEmpty()
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
        backCallback?.isEnabled = !BrowserPath.isRoot(currentPath) || opts.query.isNotEmpty()
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
    }

    private companion object {
        const val KEY_PATH = "path"
        const val KEY_QUERY = "query"
        const val KEY_SORT = "sort"
        const val KEY_ASC = "asc"
        const val KEY_FOLDERS_FIRST = "folders_first"
        const val KEY_HIDDEN = "hidden"
        const val KEY_DEEP = "deep"
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
        const val MENU_SORT_BASE = 100
    }
}
