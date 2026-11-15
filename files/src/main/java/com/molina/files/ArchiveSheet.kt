// molina-patch26k5c
package com.molina.files

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface // 26k5g:e05
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.ArchiveListing
import com.molina.core.storage.ArchiveTree
import com.molina.core.storage.CancelToken
import com.molina.core.storage.ConflictPolicy // 26k5d:e12
import com.molina.core.storage.ExtractTarget
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix // 26k5g:e04
import android.widget.FrameLayout
import android.widget.ImageView
import com.molina.core.storage.TreeNode
import com.molina.core.storage.TreeSort
import com.molina.core.ui.MolinaDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.molina.core.ui.R as UiR

/**
 * Layar "Lihat isi" arsip (lembar bawah) milik [FilesFragment]: folder virtual, breadcrumb, pencarian nama,
 * pilih entri/folder, ekstrak terpilih/semua, dan buka satu entri lewat cache. Kata sandi hanya disimpan di
 * memori instans ini (tidak masuk Bundle, log, atau state). Tidak mencatat nama entri di log.
 */
internal class ArchiveSheet : BottomSheetDialogFragment() {
    private val host: FilesFragment? get() = parentFragment as? FilesFragment
    private val archivePath: String get() = requireArguments().getString(ARG_PATH).orEmpty()

    private var tree: ArchiveTree? = null
    private var listing: ArchiveListing? = null
    private var browse = ArchiveBrowse()
    private var password: String? = null
    private var bannerDismissed = false
    private var opening = false
    private var openCancel: CancelToken? = null
    private var loadJob: Job? = null
    private var passwordPrompting = false
    private var lastFolder = "\u0000"
    private var suppressWatch = false
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private var titleView: TextView? = null
    private var infoView: TextView? = null
    private var cacheView: TextView? = null // 26k5d:e13
    private var previewDialog: android.app.Dialog? = null // 26k5e:e04
    private var crumbBox: LinearLayout? = null
    private var crumbScroll: HorizontalScrollView? = null
    private var searchBox: EditText? = null
    private var bannerView: TextView? = null
    private var busyView: TextView? = null
    private var statusView: TextView? = null
    private var recycler: RecyclerView? = null
    private var actionBar: LinearLayout? = null
    private var actionCount: TextView? = null
    private val adapter = RowAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val sort = runCatching { TreeSort.valueOf(savedInstanceState.getString(KEY_SORT).orEmpty()) }.getOrDefault(TreeSort.NAME)
            browse = ArchiveBrowse(
                folder = savedInstanceState.getString(KEY_FOLDER).orEmpty(),
                sort = sort,
                query = savedInstanceState.getString(KEY_QUERY).orEmpty(),
                selected = savedInstanceState.getStringArrayList(KEY_SEL).orEmpty().toSet(),
            )
            bannerDismissed = savedInstanceState.getBoolean(KEY_BANNER, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_FOLDER, browse.folder)
        outState.putString(KEY_SORT, browse.sort.name)
        outState.putString(KEY_QUERY, browse.query)
        outState.putBoolean(KEY_BANNER, bannerDismissed)
        if (browse.selected.size <= ArchiveBrowse.MAX_SAVED_SELECTION) {
            outState.putStringArrayList(KEY_SEL, ArrayList(browse.selected))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val white = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
        val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
        val frame = ContextCompat.getColor(ctx, UiR.color.molina_header_frame)
        val green = ContextCompat.getColor(ctx, UiR.color.molina_key_green)
        val amber = ContextCompat.getColor(ctx, UiR.color.molina_cinema_accent)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_bg))
            setPadding(dp(16), dp(12), dp(16), dp(8))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (ctx.resources.displayMetrics.heightPixels * 0.88f).toInt())
        }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titleView = TextView(ctx).apply {
            text = archivePath.substringAfterLast('/')
            textSize = 17f
            setTextColor(white)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        infoView = TextView(ctx).apply {
            text = "Membaca isi arsip\u2026"
            textSize = 12f
            setTextColor(dim)
        }
        titles.addView(titleView)
        titles.addView(infoView)
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(
            TextView(ctx).apply {
                text = "\u22EE"
                textSize = 22f
                setTextColor(white)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(6), dp(6))
                setOnClickListener { showMenu(it) }
            },
        )
        root.addView(head)
        // 26k5d:e15 kuota cache
        val cacheRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val cacheText = TextView(ctx).apply {
            text = ArchiveViewText.cacheLine(0L, com.molina.core.storage.ArchiveEngineLimits.CACHE_MAX_BYTES)
            textSize = 12f
            setTextColor(dim)
        }
        cacheView = cacheText
        cacheRow.addView(cacheText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cacheRow.addView(
            TextView(ctx).apply {
                text = "Bersihkan"
                textSize = 12f
                setTextColor(green)
                setPadding(dp(12), dp(6), 0, dp(6))
                setOnClickListener { confirmClearCache() }
            },
        )
        root.addView(cacheRow)

        crumbBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        crumbScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(crumbBox)
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(crumbScroll)

        searchBox = EditText(ctx).apply {
            hint = "Cari nama di arsip"
            textSize = 14f
            setTextColor(white)
            setHintTextColor(dim)
            isSingleLine = true
            setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_surface_variant))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (suppressWatch) return
                    scheduleSearch(s?.toString().orEmpty())
                }
            })
        }
        root.addView(
            searchBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) },
        )

        bannerView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(amber)
            setPadding(0, dp(6), 0, dp(2))
            visibility = View.GONE
            setOnClickListener {
                bannerDismissed = true
                render()
            }
        }
        root.addView(bannerView)

        busyView = TextView(ctx).apply {
            text = ArchiveViewText.opening()
            textSize = 13f
            setTextColor(green)
            setPadding(0, dp(6), 0, dp(2))
            visibility = View.GONE
            setOnClickListener { openCancel?.cancel() }
        }
        root.addView(busyView)

        statusView = TextView(ctx).apply {
            textSize = 14f
            setTextColor(dim)
            setPadding(0, dp(24), 0, dp(24))
            visibility = View.GONE
        }
        root.addView(statusView)

        recycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@ArchiveSheet.adapter
        }
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_surface_variant))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            visibility = View.GONE
        }
        actionCount = TextView(ctx).apply {
            textSize = 14f
            setTextColor(frame)
        }
        bar.addView(actionCount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(
            TextView(ctx).apply {
                text = "Ekstrak"
                textSize = 14f
                setTextColor(green)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { extract(all = false) }
            },
        )
        bar.addView(
            TextView(ctx).apply {
                text = "Batal"
                textSize = 14f
                setTextColor(white)
                setPadding(dp(14), dp(10), dp(6), dp(10))
                setOnClickListener {
                    browse = browse.clearSelection()
                    render()
                }
            },
        )
        actionBar = bar
        root.addView(bar)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                val next = browse.back()
                if (next != null) {
                    browse = next
                    render()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        load()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
        openCancel?.cancel()
        openCancel = null
        loadJob?.cancel()
        loadJob = null
        recycler?.adapter = null
        titleView = null
        infoView = null
        previewDialog?.dismiss() // 26k5e:e07
        previewDialog = null
        cacheView = null // 26k5d:e14
        crumbBox = null
        crumbScroll = null
        searchBox = null
        bannerView = null
        busyView = null
        statusView = null
        recycler = null
        actionBar = null
        actionCount = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        password = null
        super.onDestroy()
    }

    // --- muat

    private fun load() {
        val h = host
        if (h == null) {
            dismissAllowingStateLoss()
            return
        }
        loadJob?.cancel()
        statusView?.text = "Membaca isi arsip\u2026"
        statusView?.visibility = View.VISIBLE
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            when (val r = h.archiveStorage.listArchive(archivePath, password)) {
                is MolinaResult.Success -> onListed(r.value)
                is MolinaResult.Failure -> onListFailed(r.error)
            }
        }
    }

    private suspend fun onListed(l: ArchiveListing) {
        val built = withContext(Dispatchers.Default) { ArchiveTree.build(l.entries) }
        tree = built
        listing = l
        FilesEvents.archiveView(formatLabel(l), l.fileCount, l.folderCount, l.encrypted, l.truncated, built.skipped)
        if (!built.isFolder(browse.folder)) browse = browse.copy(folder = "")
        infoView?.text = ArchiveViewText.counts(l.fileCount, l.folderCount, l.totalBytes)
        refreshCache() // 26k5d:e16
        render()
    }

    private fun onListFailed(error: MolinaError) {
        val reason = ArchiveText.reasonOf(error)
        if (reason == ArchiveFailure.NEED_PASSWORD || reason == ArchiveFailure.WRONG_PASSWORD) {
            promptPassword(wrong = reason == ArchiveFailure.WRONG_PASSWORD, onCancel = { dismissAllowingStateLoss() }) { load() }
            return
        }
        FilesEvents.archiveFailed("list", error, BrowserPath.parent(archivePath))
        infoView?.text = ""
        statusView?.text = if (reason != null) ArchiveText.failure(reason) else FilesErrorText.forOp(error.kind)
        statusView?.visibility = View.VISIBLE
    }

    private fun promptPassword(wrong: Boolean, onCancel: () -> Unit, onReady: () -> Unit) {
        val ctx = context ?: return
        if (passwordPrompting) return
        passwordPrompting = true
        ArchiveDialogs.password(
            ctx,
            title = if (wrong) "Kata sandi salah" else "Arsip bersandi",
            message = if (wrong) "Coba lagi dengan kata sandi yang benar." else "Masukkan kata sandi untuk membuka arsip ini.",
            onCancel = {
                passwordPrompting = false
                onCancel()
            },
            onSubmit = { pw ->
                passwordPrompting = false
                password = pw
                onReady()
            },
        )
    }

    // --- tampilan

    private fun render() {
        val ctx = context ?: return
        val t = tree ?: return
        val rows = browse.rows(t)
        adapter.submit(rows.nodes, browse.selected, browse.searching)
        if (browse.folder != lastFolder) {
            lastFolder = browse.folder
            recycler?.scrollToPosition(0)
        }
        statusView?.let {
            if (rows.nodes.isEmpty()) {
                it.text = if (browse.searching) ArchiveViewText.noMatch() else ArchiveViewText.emptyFolder()
                it.visibility = View.VISIBLE
            } else {
                it.visibility = View.GONE
            }
        }
        val notes = ArrayList<String>()
        val l = listing
        if (l != null && l.truncated && !bannerDismissed) notes.add(ArchiveViewText.truncatedBanner(l.entries.size))
        if (rows.capped) notes.add(ArchiveViewText.searchCapped(ArchiveBrowse.SEARCH_LIMIT))
        bannerView?.let {
            it.text = notes.joinToString("\n")
            it.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        }
        searchBox?.let {
            if (it.text.toString() != browse.query) {
                suppressWatch = true
                it.setText(browse.query)
                suppressWatch = false
            }
        }
        renderCrumbs(ctx)
        actionBar?.visibility = if (browse.selecting) View.VISIBLE else View.GONE
        actionCount?.text = ArchiveViewText.selectionLine(browse.selected.size)
    }

    private fun renderCrumbs(ctx: android.content.Context) {
        val box = crumbBox ?: return
        val d = ctx.resources.displayMetrics.density
        val green = ContextCompat.getColor(ctx, UiR.color.molina_key_green)
        val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
        box.removeAllViews()
        if (browse.searching) {
            box.addView(TextView(ctx).apply {
                text = "Hasil pencarian"
                textSize = 13f
                setTextColor(dim)
            })
            return
        }
        val list = tree?.breadcrumbs(browse.folder).orEmpty()
        for ((i, c) in list.withIndex()) {
            if (i > 0) {
                box.addView(TextView(ctx).apply {
                    text = " \u203A "
                    textSize = 13f
                    setTextColor(dim)
                })
            }
            val last = i == list.lastIndex
            box.addView(TextView(ctx).apply {
                text = if (c.path.isEmpty()) "Isi arsip" else c.name
                textSize = 13f
                setTextColor(if (last) green else dim)
                setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
                if (!last) setOnClickListener {
                    browse = browse.goTo(c.path)
                    render()
                }
            })
        }
        crumbScroll?.post { crumbScroll?.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun scheduleSearch(text: String) {
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        val run = Runnable {
            if (text != browse.query) {
                browse = browse.withQuery(text)
                render()
            }
        }
        pendingSearch = run
        searchHandler.postDelayed(run, SEARCH_DEBOUNCE_MS)
    }

    // --- aksi

    private fun onRowClick(n: TreeNode) {
        if (browse.selecting) {
            browse = browse.toggle(n.path)
            render()
            return
        }
        if (n.isDirectory) {
            browse = browse.open(n)
            render()
        } else if (ImagePreview.isImage(n.name)) {
            openEntry(n, preview = true) // 26k5d:e23
        } else {
            openEntry(n)
        }
    }

    private fun onRowLong(n: TreeNode) {
        browse = browse.toggle(n.path)
        render()
    }

    private fun openEntry(n: TreeNode, preview: Boolean = false) { // 26k5d:e18
        val h = host ?: return
        if (opening) return
        if (h.archiveBusy()) {
            toast("Operasi lain masih berjalan")
            return
        }
        opening = true
        val cancel = CancelToken()
        openCancel = cancel
        busyView?.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val r = h.archiveStorage.openArchiveEntry(archivePath, n.path, password, cancel)) {
                    is MolinaResult.Success -> {
                        val o = r.value
                        FilesEvents.archiveOpen(listing?.let { formatLabel(it) } ?: "unknown", o.cached, o.bytes, o.cancelled)
                        if (!o.cancelled) {
                            if (preview) showPreview(o.relative) else h.viewArchiveEntry(o.relative)?.let { toast(it) } // 26k5d:e19
                        }
                        refreshCache()
                    }
                    is MolinaResult.Failure -> onOpenFailed(n, r.error, preview) // 26k5d:e20
                }
            } finally {
                opening = false
                openCancel = null
                busyView?.visibility = View.GONE
            }
        }
    }

    private fun onOpenFailed(n: TreeNode, error: MolinaError, preview: Boolean = false) { // 26k5d:e21
        val reason = ArchiveText.reasonOf(error)
        if (reason == ArchiveFailure.TOO_LARGE) {
            FilesEvents.archiveFailed("open", error, BrowserPath.parent(archivePath))
            askExtractLarge(n)
            return
        }
        if (reason == ArchiveFailure.NEED_PASSWORD || reason == ArchiveFailure.WRONG_PASSWORD) {
            promptPassword(wrong = reason == ArchiveFailure.WRONG_PASSWORD, onCancel = {}) { openEntry(n, preview) } // 26k5d:e22
            return
        }
        FilesEvents.archiveFailed("open", error, BrowserPath.parent(archivePath))
        toast(if (reason != null) ArchiveText.failure(reason) else FilesErrorText.forOp(error.kind))
    }

    // 26k5d:e24
    private fun askExtractLarge(n: TreeNode) {
        val ctx = context ?: return
        MolinaDialog.confirm(ctx, "Berkas terlalu besar", ArchiveViewText.tooLargeAsk(), "Ekstrak ke folder") {
            val h = host ?: return@confirm
            if (h.startSheetExtract(archivePath, setOf(n.path), ExtractTarget.HERE, ConflictPolicy.RENAME, password)) {
                dismissAllowingStateLoss()
            } else {
                toast("Operasi lain masih berjalan")
            }
        }
    }

    private fun showPreview(relative: String) {
        val h = host ?: return
        val ctx = context ?: return
        val file = h.archiveFile(relative)
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeScaled(file) }
            if (bmp == null) {
                toast("Gambar tidak bisa ditampilkan")
                h.viewArchiveEntry(relative)?.let { toast(it) }
                return@launch
            }
            val d = ctx.resources.displayMetrics.density
            val green = ContextCompat.getColor(ctx, UiR.color.molina_key_green)
            val box = FrameLayout(ctx)
            val iv = ImageView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bmp)
            }
            box.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val dlg = android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            previewDialog?.dismiss()
            previewDialog = dlg // 26k5e:e05
            iv.setOnClickListener { dlg.dismiss() }
            val openOther = TextView(ctx).apply {
                text = "Buka di aplikasi lain"
                textSize = 14f
                setTextColor(green)
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                setOnClickListener {
                    dlg.dismiss()
                    h.viewArchiveEntry(relative)?.let { toast(it) }
                }
            }
            box.addView(openOther, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
            dlg.setContentView(box)
            dlg.setOnDismissListener {
                iv.setImageDrawable(null)
                bmp.recycle()
                if (previewDialog === dlg) previewDialog = null // 26k5e:e06
            }
            dlg.show()
        }
    }

    private fun decodeScaled(file: java.io.File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = ImagePreview.sampleSize(bounds.outWidth, bounds.outHeight, ImagePreview.MAX_EDGE)
            }
            upright(BitmapFactory.decodeFile(file.path, opts), file) // 26k5g:e06
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    /**
     * Putar/cermin hasil decode sesuai tag orientasi EXIF (foto kamera tidak miring lagi). Gagal baca tag atau
     * kehabisan memori = bitmap apa adanya. 26k5g:e08
     */
    private fun upright(src: Bitmap?, file: java.io.File): Bitmap? {
        if (src == null) return null
        val fix = try {
            ExifOrientation.fix(ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifOrientation.NORMAL))
        } catch (e: Exception) {
            ExifOrientation.IDENTITY
        }
        if (fix.isIdentity) return src
        return try {
            val m = Matrix()
            m.setRotate(fix.degrees.toFloat())
            if (fix.flipX) m.postScale(-1f, 1f)
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (out !== src) src.recycle()
            out
        } catch (e: OutOfMemoryError) {
            src
        }
    }

    private fun extract(all: Boolean) {
        val h = host ?: return
        val ctx = context ?: return
        val only = if (all) null else browse.onlyKeys()
        ArchiveDialogs.extractOptions(ctx, 1) { target, policy ->
            if (h.startSheetExtract(archivePath, only, target, policy, password)) {
                dismissAllowingStateLoss()
            } else {
                toast("Operasi lain masih berjalan")
            }
        }
    }

    // 26k5d:e25
    private fun refreshCache() {
        val h = host ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val r = h.archiveStorage.archiveCacheBytes()
            if (r is MolinaResult.Success) {
                cacheView?.text = ArchiveViewText.cacheLine(r.value, com.molina.core.storage.ArchiveEngineLimits.CACHE_MAX_BYTES)
            }
        }
    }

    private fun extractTo() {
        val h = host ?: return
        if (h.setSheetArchiveClip(archivePath, browse.onlyKeys())) {
            toast("Buka folder tujuan, lalu ketuk Ekstrak di sini")
            dismissAllowingStateLoss()
        } else {
            toast("Pilihan terlalu banyak untuk Ekstrak ke\u2026; pakai Ekstrak")
        }
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(Menu.NONE, M_EXTRACT_ALL, 0, "Ekstrak semua\u2026")
        popup.menu.add(Menu.NONE, M_EXTRACT_TO, 0, ArchiveViewText.extractToLabel(browse.selecting)) // 26k5f:e01
        popup.menu.add(Menu.NONE, M_SELECT_ALL, 1, "Pilih semua di sini")
        popup.menu.add(Menu.NONE, M_SORT_NAME, 2, "Urut nama")
        popup.menu.add(Menu.NONE, M_SORT_SIZE, 2, "Urut ukuran")
        popup.menu.add(Menu.NONE, M_CLEAR_CACHE, 3, "Bersihkan cache")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                M_EXTRACT_ALL -> extract(all = true)
                M_EXTRACT_TO -> extractTo() // 26k5d:e27
                M_SELECT_ALL -> tree?.let { t ->
                    browse = browse.selectAll(t)
                    render()
                }
                M_SORT_NAME -> {
                    browse = browse.withSort(TreeSort.NAME)
                    render()
                }
                M_SORT_SIZE -> {
                    browse = browse.withSort(TreeSort.SIZE)
                    render()
                }
                M_CLEAR_CACHE -> confirmClearCache()
            }
            true
        }
        popup.show()
    }

    private fun confirmClearCache() {
        val h = host ?: return
        if (opening) {
            toast("Operasi lain masih berjalan")
            return
        }
        MolinaDialog.confirm(
            requireContext(),
            "Bersihkan cache arsip?",
            "Berkas yang dibuka dari arsip akan dihapus dari cache. Berkas asli di arsip tidak berubah.",
            "Bersihkan",
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = h.archiveStorage.clearArchiveCache()) {
                    is MolinaResult.Success -> {
                        FilesEvents.archiveCache("manual", r.value)
                        toast(ArchiveViewText.cacheCleared(r.value))
                        refreshCache() // 26k5d:e17
                    }
                    is MolinaResult.Failure -> {
                        FilesEvents.failed("archive-cache", r.error, "")
                        toast(FilesErrorText.forOp(r.error.kind))
                    }
                }
            }
        }
    }

    private fun formatLabel(l: ArchiveListing): String = l.format.label.lowercase(Locale.ROOT)

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
    }

    // --- daftar

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.Holder>() {
        private var nodes: List<TreeNode> = emptyList()
        private var selected: Set<String> = emptySet()
        private var searching = false

        inner class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

        fun submit(list: List<TreeNode>, sel: Set<String>, search: Boolean) {
            nodes = list
            selected = sel
            searching = search
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            val tv = TextView(ctx).apply {
                textSize = 15f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            return Holder(tv)
        }

        override fun getItemCount(): Int = nodes.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val n = nodes[position]
            val ctx = holder.text.context
            val white = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
            val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
            val green = ContextCompat.getColor(ctx, UiR.color.molina_key_green)
            val picked = n.path in selected
            val iconColor = ArchiveIcons.iconColor(n, green, dim) // 26k5e:e01
            val lead = (if (picked) "\u2713 " else "") + ArchiveIcons.glyph(n) + "  "
            val name = lead + n.name + (if (n.isDirectory) "/" else "")
            val line2 = "\n" + if (searching) ArchiveViewText.hitLine(n) else ArchiveViewText.nodeLine(n)
            val span = SpannableString(name + line2)
            span.setSpan(ForegroundColorSpan(iconColor), 0, lead.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // 26k5e:e02
            span.setSpan(ForegroundColorSpan(ArchiveIcons.nameColor(n, green, white)), lead.length, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // 26k5e:e03
            span.setSpan(ForegroundColorSpan(dim), name.length, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(RelativeSizeSpan(0.85f), name.length, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.text.text = span
            holder.text.setBackgroundColor(
                if (picked) ContextCompat.getColor(ctx, UiR.color.molina_outline) else android.graphics.Color.TRANSPARENT,
            )
            holder.text.setOnClickListener { onRowClick(n) }
            holder.text.setOnLongClickListener {
                onRowLong(n)
                true
            }
        }
    }

    companion object {
        private const val ARG_PATH = "path"
        private const val KEY_FOLDER = "folder"
        private const val KEY_SORT = "sort"
        private const val KEY_QUERY = "query"
        private const val KEY_SEL = "sel"
        private const val KEY_BANNER = "banner"
        private const val SEARCH_DEBOUNCE_MS = 250L
        private const val M_EXTRACT_ALL = 1
        private const val M_SELECT_ALL = 2
        private const val M_SORT_NAME = 3
        private const val M_SORT_SIZE = 4
        private const val M_CLEAR_CACHE = 5
        private const val M_EXTRACT_TO = 6 // 26k5d:e28

        fun newInstance(path: String): ArchiveSheet = ArchiveSheet().apply {
            arguments = Bundle().apply { putString(ARG_PATH, path) }
        }
    }
}
