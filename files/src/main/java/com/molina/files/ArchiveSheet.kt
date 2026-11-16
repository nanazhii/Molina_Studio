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
import android.view.GestureDetector // 26k5h:e03
import android.view.MotionEvent // 26k5h:e04
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
import com.molina.core.ui.ExtLabel
import com.molina.core.ui.FileKind
import com.molina.core.ui.MolinaDialog
import com.molina.core.ui.TreeIconView
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
    // molina-27f: pemilik bisa FilesFragment (lama, lewat pembungkus) atau BerkasFragment
    private val host: ArchiveSheetHost?
        get() = when (val p = parentFragment) {
            is ArchiveSheetHost -> p
            is FilesFragment -> FilesFragmentArchiveHost(p)
            else -> null
        }
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
    private var pager: ImagePager? = null // 26k5h:e02
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
        val green = ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent) // molina-27x: aksen teal Molina untuk yang bisa diketuk
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
            // molina-27x: bingkai tipis di sekeliling daftar + garis pemisah antarbaris (dimulai setelah kolom ikon)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            clipToPadding = true
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(frame, 0xAA))
            }
            addItemDecoration(rowDivider(frame, dp(58)))
        }
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

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

    /** [molina-27x] Garis pemisah tipis antarbaris, dimulai [startPx] dari kiri (sejajar teks, setelah kolom ikon). */
    private fun rowDivider(lineColor: Int, startPx: Int) = object : RecyclerView.ItemDecoration() {
        private val paint = android.graphics.Paint().apply {
            color = androidx.core.graphics.ColorUtils.setAlphaComponent(lineColor, 0x44)
            strokeWidth = 1f
        }

        override fun onDrawOver(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount - 1) {
                val v = parent.getChildAt(i)
                val y = v.bottom.toFloat()
                c.drawLine(parent.paddingLeft + startPx.toFloat(), y, (parent.width - parent.paddingRight).toFloat(), y, paint)
            }
        }
    }

    private fun renderCrumbs(ctx: android.content.Context) {
        val box = crumbBox ?: return
        val d = ctx.resources.displayMetrics.density
        val green = ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent) // molina-27x
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
            openImage(n) // 26k5h:e01
        } else {
            openEntry(n)
        }
    }

    // 26k5h:e05
    /** Ketuk gambar: kumpulkan gambar yang sedang tampil di daftar (urutan sama) sebagai bahan geser, lalu buka. */
    private fun openImage(n: TreeNode) {
        val t = tree
        val paths = if (t == null) {
            emptyList()
        } else {
            browse.rows(t).nodes.filter { !it.isDirectory && ImagePreview.isImage(it.name) }.map { it.path }
        }
        pager = ImagePager.of(paths, n.path)
        openEntry(n, preview = true)
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

    // 26k5h:e06
    @Suppress("ClickableViewAccessibility")
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
            // Keadaan milik dialog ini saja (bukan field): dialog lama ditutup async dan tidak boleh menyentuh yang baru.
            var cur: Bitmap? = bmp
            var curRel = relative
            var pg: ImagePager? = pager
            var loading = false
            val box = FrameLayout(ctx)
            val iv = ImageView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bmp)
            }
            box.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val dlg = android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            previewDialog?.dismiss()
            previewDialog = dlg
            val counter = TextView(ctx).apply {
                textSize = 13f
                setTextColor(0xFFB0B0B0.toInt())
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            }
            box.addView(counter, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            fun refreshCounter(busy: Boolean) {
                val p = pg
                if (p == null || p.size < 2) {
                    counter.visibility = View.GONE
                } else {
                    counter.visibility = View.VISIBLE
                    counter.text = if (busy) p.label() + " \u2026" else p.label()
                }
            }
            refreshCounter(false)
            fun go(dir: Int) {
                val p = pg ?: return
                val target = (if (dir > 0) p.next() else p.prev()) ?: return
                if (loading || opening) return
                loading = true
                opening = true
                val cancel = CancelToken()
                openCancel = cancel
                refreshCounter(true)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        when (val r = h.archiveStorage.openArchiveEntry(archivePath, target.current, password, cancel)) {
                            is MolinaResult.Success -> {
                                val o = r.value
                                FilesEvents.archiveOpen(listing?.let { formatLabel(it) } ?: "unknown", o.cached, o.bytes, o.cancelled)
                                if (!o.cancelled) {
                                    val nb = withContext(Dispatchers.IO) { decodeScaled(h.archiveFile(o.relative)) }
                                    if (nb == null) {
                                        toast("Gambar tidak bisa ditampilkan")
                                    } else if (!dlg.isShowing || cur == null) {
                                        nb.recycle()
                                    } else {
                                        iv.setImageBitmap(nb)
                                        cur?.recycle()
                                        cur = nb
                                        curRel = o.relative
                                        pg = target
                                    }
                                }
                                refreshCache()
                            }
                            is MolinaResult.Failure -> {
                                val reason = ArchiveText.reasonOf(r.error)
                                FilesEvents.archiveFailed("open", r.error, BrowserPath.parent(archivePath))
                                toast(if (reason != null) ArchiveText.failure(reason) else FilesErrorText.forOp(r.error.kind))
                            }
                        }
                    } finally {
                        loading = false
                        opening = false
                        openCancel = null
                        if (dlg.isShowing) refreshCounter(false)
                    }
                }
            }
            val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    dlg.dismiss()
                    return true
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    val start = e1 ?: return false
                    val dir = ImagePager.swipe(e2.x - start.x, e2.y - start.y, 64f * d)
                    if (dir == 0) return false
                    go(dir)
                    return true
                }
            })
            iv.setOnTouchListener { _, e -> gd.onTouchEvent(e) }
            val openOther = TextView(ctx).apply {
                text = "Buka di aplikasi lain"
                textSize = 14f
                setTextColor(green)
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                setOnClickListener {
                    dlg.dismiss()
                    h.viewArchiveEntry(curRel)?.let { toast(it) }
                }
            }
            box.addView(openOther, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
            dlg.setContentView(box)
            dlg.setOnDismissListener {
                if (loading) openCancel?.cancel()
                iv.setImageDrawable(null)
                cur?.recycle()
                cur = null
                if (previewDialog === dlg) previewDialog = null
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

        inner class Holder(
            val row: LinearLayout,
            val icon: TreeIconView,
            val name: TextView,
            val info: TextView,
            val tick: TextView,
        ) : RecyclerView.ViewHolder(row)

        fun submit(list: List<TreeNode>, sel: Set<String>, search: Boolean) {
            nodes = list
            selected = sel
            searching = search
            notifyDataSetChanged()
        }

        // molina-27x: baris bergaya Berkas: ikon Mint (TreeIconView) di kolom tetap, nama putih, baris kedua redup, centang di kanan
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val white = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
            val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
            val accent = ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(10), dp(6), dp(12), dp(6))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val icon = TreeIconView(ctx)
            row.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(ctx).apply {
                textSize = 15f
                setTextColor(white)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            val info = TextView(ctx).apply {
                textSize = 12f
                setTextColor(dim)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            col.addView(name)
            col.addView(info)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val tick = TextView(ctx).apply {
                text = "\u2713"
                textSize = 18f
                setTextColor(accent)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                visibility = View.GONE
            }
            row.addView(tick)
            return Holder(row, icon, name, info, tick)
        }

        override fun getItemCount(): Int = nodes.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val n = nodes[position]
            val ctx = holder.row.context
            val picked = n.path in selected
            holder.icon.isDir = n.isDirectory
            holder.icon.kind = if (n.isDirectory) FileKind.OTHER else FileKind.of(n.name)
            holder.icon.label = if (n.isDirectory) "" else ExtLabel.of(n.name)
            holder.name.text = n.name
            holder.info.text = ArchiveRowText.second(n, searching)
            holder.tick.visibility = if (picked) View.VISIBLE else View.GONE
            holder.row.setBackgroundColor(
                if (picked) androidx.core.graphics.ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent), 0x2A) else android.graphics.Color.TRANSPARENT,
            )
            holder.row.setOnClickListener { onRowClick(n) }
            holder.row.setOnLongClickListener {
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
