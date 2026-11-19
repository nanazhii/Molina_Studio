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
import com.molina.core.common.diag.Diag
import com.molina.core.common.stream.UrlNormalizer
import com.molina.player.home.HomeController
import com.molina.player.home.HomeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Beranda Molina Cinema (Home Theater): satu kolom untuk tautan ATAU judul (dicari di YouTube),
 * empat kartu (Berkas, Saluran, Setelan, Segarkan); Berkas membuka satu pemilih untuk berkas dan folder
 * (subfolder ikut) atau pemilih sistem.
 * Pencarian dan pemutaran tetap ditangani MPVActivity lewat [StreamLoader.searchRequest].
 */
class MainScreenFragment : Fragment(R.layout.fragment_main_screen), HomeHost {
    private lateinit var binding: FragmentMainScreenBinding
    private lateinit var home: HomeController

    private lateinit var documentTreeOpener: ActivityResultLauncher<Uri?>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var playerLauncher: ActivityResultLauncher<Intent>
    private lateinit var channelFileOpener: ActivityResultLauncher<Array<String>>
    private var onChannelFile: ((Uri) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var firstRun = false
    private var returningFromPlayer = false

    private var prev = ""
    private var prevData: String? = null
    private var lastPath = ""

    private sealed interface Parsed {
        data class Url(val value: String) : Parsed
        data class Query(val text: String) : Parsed
        data object Invalid : Parsed
        data object Empty : Parsed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun = savedInstanceState == null

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
        // */*: banyak pemilih Android 8 memberi .m3u tipe octet-stream; isi divalidasi oleh M3uParser, bukan tipe
        channelFileOpener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val cb = onChannelFile
            onChannelFile = null
            if (uri != null) cb?.invoke(uri)
        }
        playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // we don't care about the result but remember that we've been here
            returningFromPlayer = true
            Diag.behavior("player", "home-return", "kembali dari pemutar", mapOf("result" to it.resultCode.toString()))
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
        home = HomeController(this, scope)
        home.attach(binding)

        if (BuildConfig.DEBUG) {
            binding.homeLogo.setOnLongClickListener { showDebugMenu(); true }
        }

        refreshInputState()
    }

    override fun onDestroyView() {
        if (::home.isInitialized) home.detach()
        super.onDestroyView()
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
                home.recordPlayed(p.value)
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
                OpenChooserSheet.Choice.LOCAL -> launchPicker()
                OpenChooserSheet.Choice.SYSTEM -> launchDocTree()
            }
        }
    }

    /** Satu pemilih untuk berkas dan folder: berkas diputar langsung, folder (tekan lama) jadi playlist beserta subfolder. */
    private fun launchPicker() {
        saveChoice(KEY_OPEN)
        Diag.behavior("player", "open-local", "pemilih berkas dibuka")
        showPickHintOnce()
        val i = Intent(context, FilePickerActivity::class.java)
        i.putExtra("skip", FilePickerActivity.FILE_PICKER)
        i.putExtra("allow_dir", true)
        if (lastPath != "")
            i.putExtra("default_path", lastPath)
        filePickerLauncher.launch(i)
    }

    /** Petunjuk tekan-lama folder tampil sekali saja, bukan setiap kali pemilih dibuka. */
    private fun showPickHintOnce() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean(KEY_PICK_HINT_SEEN, false)) return
        prefs.edit().putBoolean(KEY_PICK_HINT_SEEN, true).apply()
        Toast.makeText(requireContext(), R.string.hint_pick_folder_longpress, Toast.LENGTH_LONG).show()
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
        if (f.isDirectory) {
            Diag.behavior("player", "open-local-folder", "folder dipilih (subfolder ikut)")
            playFolder(f, deep = true)
        } else {
            playFile(path)
            if (::home.isInitialized) home.recordPlayed(path)
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
            KEY_OPEN, "file", "folder", "deep" -> launchPicker() // tiga nilai terakhir: simpanan lama sebelum digabung
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

    // --- HomeHost: kontrak ke paket home (menu, Saluran, Terakhir diputar) ---

    override fun hostContext(): Context = requireContext()

    override fun openFromDevice() = showOpenChooser()

    override fun openSettings() {
        saveChoice("") // will reset
        startActivity(Intent(context, PreferenceActivity::class.java))
    }

    override fun play(target: String) = playFile(target)

    override fun pickChannelFile(onPicked: (Uri) -> Unit) {
        onChannelFile = onPicked
        channelFileOpener.launch(arrayOf("*/*"))
    }

    companion object {
        private const val KEY_OPEN = "open"
        private const val KEY_PICK_HINT_SEEN = "molina_pick_hint_seen"
        private const val MAX_CONTENT_DP = 560
        private const val CONTENT_MARGIN_DP = 32

        // list of debug or testing activities that can be launched
        private val DEBUG_ACTIVITIES = arrayOf(
            "IntentTestActivity",
            "CodecInfoActivity"
        )
    }
}
