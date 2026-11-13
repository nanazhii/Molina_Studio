package com.molina.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaResult
import com.molina.core.storage.FileEntry
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Penjelajah folder Molina (hanya baca). Seluruh akses berkas lewat :core-storage. */
class FilesFragment : Fragment(R.layout.fragment_files) {
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val adapter = FilesAdapter { entry -> onEntryClick(entry) }

    private var views: Views? = null
    private var backCallback: OnBackPressedCallback? = null
    private var loadJob: Job? = null
    private var currentPath = ""

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            FilesEvents.permissionResult(granted)
            if (views != null) load(currentPath)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentPath = savedInstanceState?.getString(KEY_PATH).orEmpty()
        val v = Views(view)
        views = v
        v.list.layoutManager = LinearLayoutManager(requireContext())
        v.list.adapter = adapter
        v.up.setOnClickListener { goUp() }
        v.retry.setOnClickListener { onRetry() }
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
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        backCallback = null
        views?.let { it.list.adapter = null }
        views = null
        super.onDestroyView()
    }

    private fun onEntryClick(entry: FileEntry) {
        if (entry.isDirectory) load(BrowserPath.child(currentPath, entry.name))
    }

    private fun goUp() {
        if (!BrowserPath.isRoot(currentPath)) load(BrowserPath.parent(currentPath))
    }

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
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (BrowserPath.isRoot(path)) {
                val layout = storage.ensureLayout()
                if (layout is MolinaResult.Failure) FilesEvents.failed("layout", layout.error, path)
            }
            when (val result = storage.list(path)) {
                is MolinaResult.Success -> {
                    FilesEvents.listed(path, result.value.size)
                    showContent(result.value)
                }
                is MolinaResult.Failure -> {
                    FilesEvents.failed("list", result.error, path)
                    showError(result.error.kind)
                }
            }
        }
    }

    private fun showHeader(path: String) {
        val v = views ?: return
        val root = BrowserPath.isRoot(path)
        v.title.text = BrowserPath.title(path)
        v.up.visibility = if (root) View.GONE else View.VISIBLE
        backCallback?.isEnabled = !root
    }

    private fun showContent(entries: List<FileEntry>) {
        val v = views ?: return
        adapter.submitList(entries)
        v.retry.visibility = View.GONE
        if (entries.isEmpty()) {
            v.message.text = "Folder kosong"
            v.message.visibility = View.VISIBLE
        } else {
            v.message.visibility = View.GONE
        }
    }

    private fun showError(kind: ErrorKind) {
        val v = views ?: return
        adapter.submitList(emptyList<FileEntry>())
        v.message.text = FilesErrorText.of(kind)
        v.message.visibility = View.VISIBLE
        v.retry.text = if (kind == ErrorKind.PERMISSION_DENIED) "Beri izin" else "Coba lagi"
        v.retry.visibility = View.VISIBLE
    }

    private class Views(root: View) {
        val title: TextView = root.findViewById(R.id.files_title)
        val up: MaterialButton = root.findViewById(R.id.files_up)
        val message: TextView = root.findViewById(R.id.files_message)
        val retry: MaterialButton = root.findViewById(R.id.files_retry)
        val list: RecyclerView = root.findViewById(R.id.files_list)
    }

    private companion object {
        const val KEY_PATH = "path"
    }
}
