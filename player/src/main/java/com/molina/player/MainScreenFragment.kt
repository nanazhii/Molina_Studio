package com.molina.player

import com.molina.player.filepicker.DocumentPickerFragment
import com.molina.player.preferences.PreferenceActivity
import com.molina.player.databinding.FragmentMainScreenBinding
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.molina.core.common.stream.UrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Beranda Molina Player (gaya panel Linux Mint): satu kolom untuk tautan ATAU judul (dicari di YouTube),
 * satu tombol "Buka dari perangkat" (berkas, folder, folder dalam folder, pemilih sistem), dan Setelan.
 * Pencarian dan pemutaran tetap ditangani MPVActivity lewat [StreamLoader.searchRequest].
 */
class MainScreenFragment : Fragment(R.layout.fragment_main_screen) {
    private lateinit var binding: FragmentMainScreenBinding

    private lateinit var documentTreeOpener: ActivityResultLauncher<Uri?>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var playerLauncher: ActivityResultLauncher<Intent>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var firstRun = false
    private var returningFromPlayer = false

    private var prev = ""
    private var prevData: String? = null
    private var lastPath = ""

    /** Mode pemilih berkas yang sedang terbuka; menentukan arti folder yang dipilih. */
    private var pickMode = PickMode.FILE

    private enum class PickMode(val key: String) {
        FILE("file"), FOLDER("folder"), DEEP("deep");

        companion object {
            fun of(key: String?): PickMode? = values().firstOrNull { it.key == key }
        }
    }

    private sealed interface Parsed {
        data class Url(val value: String) : Parsed
        data class Query(val text: String) : Parsed
        data object Invalid : Parsed
        data object Empty : Parsed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun = savedInstanceState == null
        pickMode = PickMode.of(savedInstanceState?.getString(STATE_PICK_MODE)) ?: PickMode.FILE

        documentTreeOpener = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let { root ->
                requireContext().contentResolver.takePersistableUriPermission(
                    root, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                saveChoice("doc", root.toString())

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", root.toString())
                i.putExtra("allow_dir", true)
                filePickerLauncher.launch(i)
            }
        }
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            it.data?.getStringExtra("last_path")?.let { path ->
                lastPath = path
            }
            it.data?.getStringExtra("path")?.let { path ->
                onPathPicked(path)
                return@registerForActivityResult
            }
            it.data?.getStringArrayExtra("paths")?.let { paths ->
                // pemilih dokumen (SAF): isi folder langsung, perlu dijadikan playlist
                if (paths.size == 1) {
                    playFile(paths[0])
                } else {
                    val memoryUri = Utils.makeMemoryPlaylist(paths.asIterable())
                    playFile(memoryUri)
                }
            }
        }
        playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // we don't care about the result but remember that we've been here
            returningFromPlayer = true
            Log.v(TAG, "returned from player ($it)")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentMainScreenBinding.bind(view)

        Utils.handleInsetsAsPadding(binding.root)
        capContentWidth()

        binding.urlInput.addTextChangedListener { refreshInputState() }
        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                submit()
                true
            } else {
                false
            }
        }
        binding.pasteBtn.setOnClickListener { pasteFromClipboard() }
        binding.playBtn.setOnClickListener { submit() }
        binding.openBtn.setOnClickListener { showOpenChooser() }
        binding.settingsBtn.setOnClickListener {
            saveChoice("") // will reset
            startActivity(Intent(context, PreferenceActivity::class.java))
        }

        if (BuildConfig.DEBUG) {
            binding.settingsBtn.setOnLongClickListener { showDebugMenu(); true }
        }

        refreshInputState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PICK_MODE, pickMode.key)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Kolom tautan / judul ---

    private fun parse(raw: String): Parsed {
        val t = raw.trim()
        if (t.isEmpty()) return Parsed.Empty
        val url = Utils.normalizeUrl(t)
        return when {
            Utils.isUrl(url) -> Parsed.Url(url)
            StreamLoader.looksLikeQuery(t) -> Parsed.Query(t)
            else -> Parsed.Invalid
        }
    }

    private fun refreshInputState() {
        val p = parse(binding.urlInput.text.toString())
        binding.urlInput.error = if (p is Parsed.Invalid) getString(R.string.uri_invalid_protocol) else null
        binding.playBtn.isEnabled = p is Parsed.Url || p is Parsed.Query
        binding.playBtn.setText(if (p is Parsed.Query) R.string.home_search_youtube else R.string.home_play)
    }

    private fun submit() {
        when (val p = parse(binding.urlInput.text.toString())) {
            is Parsed.Url -> {
                saveChoice("url")
                playFile(p.value)
            }
            is Parsed.Query -> {
                saveChoice("url")
                playFile(StreamLoader.searchRequest(p.text)) // MPVActivity: cari, tampilkan kartu hasil, lalu putar
            }
            Parsed.Invalid -> binding.urlInput.error = getString(R.string.uri_invalid_protocol)
            Parsed.Empty -> Unit
        }
    }

    private fun pasteFromClipboard() {
        val ctx = requireContext()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx).toString().trim() else ""
        if (text.isEmpty()) return
        binding.urlInput.setText(UrlNormalizer.extractUrl(text) ?: text)
        binding.urlInput.setSelection(binding.urlInput.text.length)
    }

    private fun focusUrlInput() {
        binding.urlInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.urlInput, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Di tablet/TV panel dibatasi 560dp dan di tengah; di ponsel selebar layar. */
    private fun capContentWidth() {
        val density = resources.displayMetrics.density
        val max = (MAX_CONTENT_DP * density).toInt()
        val margin = (CONTENT_MARGIN_DP * density).toInt()
        binding.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val target = if (v.width > max + margin) max else ViewGroup.LayoutParams.MATCH_PARENT
            val lp = binding.content.layoutParams
            if (lp.width != target) {
                lp.width = target
                binding.content.layoutParams = lp
            }
        }
    }

    // --- Buka dari perangkat ---

    private fun showOpenChooser() {
        OpenChooserSheet.show(requireContext()) { choice ->
            when (choice) {
                OpenChooserSheet.Choice.FILE -> launchPicker(PickMode.FILE)
                OpenChooserSheet.Choice.FOLDER -> launchPicker(PickMode.FOLDER)
                OpenChooserSheet.Choice.FOLDER_DEEP -> launchPicker(PickMode.DEEP)
                OpenChooserSheet.Choice.SYSTEM -> launchDocTree()
            }
        }
    }

    private fun launchPicker(mode: PickMode) {
        saveChoice(mode.key)
        pickMode = mode
        if (mode != PickMode.FILE) {
            // pemilih lama memilih folder lewat tekan-lama; beri petunjuk agar tidak membingungkan
            Toast.makeText(requireContext(), R.string.hint_pick_folder_longpress, Toast.LENGTH_LONG).show()
        }
        val i = Intent(context, FilePickerActivity::class.java)
        i.putExtra("skip", FilePickerActivity.FILE_PICKER)
        i.putExtra("allow_dir", mode != PickMode.FILE)
        if (lastPath != "")
            i.putExtra("default_path", lastPath)
        filePickerLauncher.launch(i)
    }

    private fun launchDocTree() {
        try {
            documentTreeOpener.launch(null)
        } catch (e: ActivityNotFoundException) {
            // Android TV doesn't come with a document picker and certain versions just throw
            // instead of handling this gracefully
            Toast.makeText(requireContext(), R.string.notice_saf_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPathPicked(path: String) {
        val f = File(path)
        if (pickMode != PickMode.FILE && f.isDirectory) {
            playFolder(f, deep = pickMode == PickMode.DEEP)
        } else {
            playFile(path)
        }
    }

    private fun playFolder(dir: File, deep: Boolean) {
        val ctx = requireContext().applicationContext
        Toast.makeText(ctx, R.string.notice_scanning, Toast.LENGTH_SHORT).show()
        scope.launch {
            val scan = withContext(Dispatchers.IO) { MediaFolder.scan(dir, deep) }
            val uri = MediaFolder.memoryPlaylist(scan.paths)
            if (!isAdded) return@launch
            if (uri == null) {
                Toast.makeText(ctx, R.string.notice_folder_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val msg = if (scan.truncated) {
                ctx.getString(R.string.notice_folder_truncated, MediaFolder.MAX_ENTRIES)
            } else {
                ctx.getString(R.string.notice_folder_playlist, scan.paths.size)
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            playFile(uri)
        }
    }

    private fun showDebugMenu() {
        assert(BuildConfig.DEBUG)
        val context = requireContext()
        with (AlertDialog.Builder(context)) {
            setItems(DEBUG_ACTIVITIES) { dialog, idx ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName(context, "${context.packageName}.${DEBUG_ACTIVITIES[idx]}")
                startActivity(intent)
            }
            create().show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (firstRun) {
            restoreChoice()
        } else if (returningFromPlayer) {
            restoreChoice(prev, prevData)
        }
        firstRun = false
        returningFromPlayer = false
    }

    private fun saveChoice(type: String, data: String? = null) {
        if (prev != type)
            lastPath = ""
        prev = type
        prevData = data

        if (!binding.switch1.isChecked)
            return
        binding.switch1.isChecked = false
        with (PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()) {
            putString("MainScreenFragment_remember", type)
            if (data == null)
                remove("MainScreenFragment_remember_data")
            else
                putString("MainScreenFragment_remember_data", data)
            commit()
        }
    }

    private fun restoreChoice() {
        with (PreferenceManager.getDefaultSharedPreferences(requireContext())) {
            restoreChoice(
                getString("MainScreenFragment_remember", "") ?: "",
                getString("MainScreenFragment_remember_data", "")
            )
        }
    }

    private fun restoreChoice(type: String, data: String?) {
        when (type) {
            "doc" -> {
                val uri = Uri.parse(data)
                // check that we can still access the folder
                if (!DocumentPickerFragment.isTreeUsable(requireContext(), uri))
                    return

                saveChoice(type, data)

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", uri.toString())
                i.putExtra("allow_dir", true)
                if (lastPath != "")
                    i.putExtra("default_path", lastPath)
                filePickerLauncher.launch(i)
            }
            "url" -> focusUrlInput()
            else -> PickMode.of(type)?.let { launchPicker(it) }
        }
    }

    private fun playFile(filepath: String) {
        val i: Intent
        if (filepath.startsWith("content://")) {
            i = Intent(Intent.ACTION_VIEW, Uri.parse(filepath))
        } else {
            i = Intent()
            // if we pass 'filepath' it will go directly to mpv without any processing
            i.putExtra("filepath", filepath)
        }
        i.setClass(requireContext(), MPVActivity::class.java)
        playerLauncher.launch(i)
    }

    companion object {
        private const val TAG = "mpv"
        private const val STATE_PICK_MODE = "pick_mode"
        private const val MAX_CONTENT_DP = 560
        private const val CONTENT_MARGIN_DP = 32

        // list of debug or testing activities that can be launched
        private val DEBUG_ACTIVITIES = arrayOf(
            "IntentTestActivity",
            "CodecInfoActivity"
        )
    }
}
