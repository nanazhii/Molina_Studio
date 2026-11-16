// molina-patch27b: disalin dari :terminal (FileDrawerView.kt), sumber file manager sekarang di :filemanager
// molina-patch19
package com.molina.filemanager

import com.molina.core.ui.BlueBackDrawable
import com.molina.core.ui.ExtLabel
import com.molina.core.ui.TreeIconView
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.ui.FileKind
import com.molina.core.ui.MolinaDialog // molina-27c
import com.molina.core.ui.R as UiR
import com.molina.log.MolinaEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Drawer berkas Terminal (diadopsi dari FileDrawer repo Molina-Studio): tree folder dari kiri.
 * Ketuk folder = buka/tutup, ketuk berkas = edit di nano, titik tiga (⋮) di kanan baris = menu opsi.
 * Teks putih polos. Ditutup dengan geser ke kiri, ketuk latar, atau Back (lewat [Host.onOpenChanged]).
 */
class FileDrawerView(
    context: Context,
    private val state: FileDrawerState,
    private val scope: CoroutineScope,
    private val host: Host,
    private val embedded: Boolean = false, // molina-27b
    private val actions: FileManagerActions = FileManagerActions.None, // molina-27d
    private val onSelectionChanged: (Boolean) -> Unit = {}, // molina-27e
) : FrameLayout(context) {

    interface Host {
        fun send(text: String)
        fun hasSession(): Boolean
        fun isProotSession(): Boolean
        fun isFullScreenProgram(): Boolean
        fun runInNewSession(line: (proot: Boolean) -> String?): Boolean
        /** Pilih tab Debian yang folder aktifnya [guestDir]; false bila tidak ada. */
        fun activateSessionAt(guestDir: String): Boolean
        /** [CWD-SILENT] Pindah folder di sesi aktif. [shellPath] belum dikutip; Terminal yang memutuskan caranya (hook prompt Debian atau mengetik cd). */
        fun changeDirectory(shellPath: String)
        /** Buka baris tombol nano (drop-up) agar tombol RUN terlihat. */
        fun showNanoKeys()
        /** [RUN-AUTO] Tunggu nano terbuka di sesi aktif, lalu kirim RUN (Alt+R) otomatis. */
        fun autoRunNano()
        fun runMode(): RunMode
        /** [RUN-REMEMBER] Simpan pilihan Jalankan ke Setelan (kotak "Ingat pilihan"). */
        fun rememberRunMode(mode: RunMode)
        fun runLogEnabled(): Boolean
        fun onRootChanged(path: String)
        fun onOpenChanged(open: Boolean)
    }

    private val white = Color.WHITE
    private val dim = ColorUtils.setAlphaComponent(Color.WHITE, 0x99)
    private val hairline = ColorUtils.setAlphaComponent(Color.WHITE, 0x4D)
    private val accent = ContextCompat.getColor(context, UiR.color.molina_terminal_accent)
    private val danger = ContextCompat.getColor(context, UiR.color.molina_key_red)
    private val runGreen = ContextCompat.getColor(context, UiR.color.molina_key_green)
    private var vols: List<File> = listOf(File(DrawerFs.INTERNAL_ROOT))

    private val panel = DrawerPanel(
        context,
        ContextCompat.getColor(context, UiR.color.molina_surface),
        ContextCompat.getColor(context, UiR.color.molina_header_frame),
        if (embedded) 0 else dp(16),
        dp(1),
    ) { close() }
    private val back = TextView(context)
    private val backArt = BlueBackDrawable() // molina-27j: tombol kembali bulat biru (hanya mode Berkas)
    private val title = TextView(context)
    private val pathText = TextView(context)
    private val chips = LinearLayout(context)
    private val chipScroll = HorizontalScrollView(context)
    private val empty = TextView(context)
    private val list = RecyclerView(context)
    private var favPaths: Set<String> = emptySet() // molina-27d2: path folder Favorit, untuk bintang di baris
    private val selection = EmbeddedSelection() // molina-27e: mode pilih (hanya mode tertanam)
    private val clip = EmbeddedClipboard.state // molina-27e: papan klip Salin/Pindah, bertahan antar kunjungan tab
    private var pasteDest: File? = null // molina-27g0: folder tujuan Tempel yang sedang berjalan, dibuka saat berhasil
    private var sort: DrawerSort = if (embedded) loadSort() else DrawerSort.DEFAULT // molina-27g2
    private var flashPath: String? = null // molina-27g2: baris yang sedang berkilat
    private var flashClear: Runnable? = null // molina-27g2
    private var headerDots: HeaderDotsView? = null // molina-27g3: titik aksen di ⋮ bila urutan/pencarian bukan bawaan
    private var searchOn = false // molina-27g3
    private var searchDeep = false
    private var searchQuery = ""
    private var searchEntries: List<DrawerEntry>? = null // hasil Cari; null = tampilan pohon
    private var searchRel: Map<String, String> = emptyMap() // path -> folder induk relatif (hasil sampai subfolder)
    private var searchInfoText = ""
    private var searchJob: kotlinx.coroutines.Job? = null
    private val searchBar = LinearLayout(context)
    private val searchField = EditText(context)
    private val searchDeepBtn = TextView(context)
    private val searchInfo = TextView(context)
    private val clipBar = LinearLayout(context)
    private val clipText = TextView(context)
    private val clipPaste = TextView(context)
    private val clipClear = TextView(context)
    private val adapter = RowAdapter()
    private val fab = FrameLayout(context) // molina-27h: tombol melayang pojok kanan bawah (menu terpusat)
    private val fabGlyph = TextView(context) // molina-27i
    private val bentoArt = BentoGlyphDrawable() // molina-27n: ikon bento 4 kotak
    private val fabBadge = TextView(context) // molina-27i: jumlah item terpilih

    init {
        visibility = if (embedded) View.VISIBLE else View.GONE
        val scrim = View(context)
        scrim.setBackgroundColor(Color.argb(0x8C, 0, 0, 0))
        scrim.setOnClickListener { close() }
        addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        buildPanel()
        addView(panel, FrameLayout.LayoutParams(if (embedded) MATCH else dp(MIN_WIDTH_DP), MATCH))
        if (embedded) setupFab() // molina-27h
    }

    val isOpen: Boolean get() = visibility == View.VISIBLE

    /**
     * [molina-27d2][molina-27e] Satu menu titik tiga mode tertanam: isi dari [EmbeddedMenu]. Menu baris (target = baris),
     * menu header (target = null, berlaku untuk folder aktif), dan menu header saat mode pilih (target = item terpilih).
     */
    private fun showCentralMenu(anchor: View, target: File?, targetIsDir: Boolean, fromHeader: Boolean) {
        val base = activeDir() // molina-27o: menu header bekerja pada folder yang tampil di header, bukan root
        val picking = selection.active && fromHeader
        val targets: List<File> = when {
            picking -> EmbeddedSelection.topLevel(selection.paths).map { File(it) }
            target != null -> listOf(target)
            else -> emptyList()
        }
        // molina-27h: satu item terpilih berperilaku seperti menu baris lama (Ubah nama, Favorit, Lihat isi, ...)
        val tgt: File? = if (picking) targets.singleOrNull() else target
        val tgtIsDir = if (picking) tgt?.isDirectory == true else targetIsDir
        val dir = when {
            tgt == null -> base
            picking -> tgt.parentFile ?: base
            tgtIsDir -> tgt
            else -> tgt.parentFile ?: base
        }
        val favTarget = if (picking) tgt?.takeIf { tgtIsDir } else if (tgt == null) base else tgt.takeIf { tgtIsDir }
        val hasActions = actions !== FileManagerActions.None
        val managed = hasActions && targets.isNotEmpty() && targets.all { actions.canManage(it) }
        // molina-27g: Properti memakai dialog kaya milik implementasi; Bagikan hanya untuk berkas di root Molina
        val propsTargets: List<File> = if (picking) targets else listOf(target ?: base)
        val entries = EmbeddedMenu.build(
            EmbeddedMenu.State(
                targetCount = targets.size,
                hasActions = hasActions,
                favoriteAvailable = hasActions && favTarget != null && actions.canManage(favTarget),
                isFavorite = hasActions && favTarget != null && actions.isFavorite(favTarget),
                trashAvailable = managed,
                global = fromHeader,
                selecting = picking,
                transferAvailable = managed,
                clipCount = clip.count,
                pasteAvailable = hasActions && actions.canPasteInto(dir),
                archiveAvailable = managed, // molina-27f
                allArchives = hasActions && targets.isNotEmpty() && targets.all { actions.isArchive(it) },
                archiveClipCount = if (hasActions) actions.archiveClipCount() else 0,
                shareAvailable = managed && (if (picking) targets.any { !it.isDirectory } else !targetIsDir), // molina-27g
                propsAvailable = if (picking) managed else hasActions, // molina-27g
                sortLabel = if (embedded && fromHeader) sort.label else "", // molina-27g2
                searchAvailable = embedded && fromHeader, // molina-27g3/27l
                searchActive = searchOn,
            ),
        )
        val dispatch: (EmbeddedMenu.Action) -> Unit = { picked -> // molina-27k: aksi dipanggil dari menu melayang
            when (picked) {
                EmbeddedMenu.Action.REFRESH -> refresh()
                EmbeddedMenu.Action.NEW_DIR -> promptCreateEmbedded(true, dir)
                EmbeddedMenu.Action.NEW_FILE -> promptCreateEmbedded(false, dir)
                EmbeddedMenu.Action.RENAME -> tgt?.let { // molina-27h
                    if (picking) { selection.exit(); selectionChanged() } // path lama tidak boleh tersisa di pilihan
                    renameDialog(it, tgtIsDir)
                }
                EmbeddedMenu.Action.SELECT -> enterSelection(target?.path)
                EmbeddedMenu.Action.SELECT_ALL -> selectAllRows()
                EmbeddedMenu.Action.EXIT_SELECT -> cancelSelection()
                EmbeddedMenu.Action.COPY -> setClip(TransferMode.COPY, targets)
                EmbeddedMenu.Action.MOVE -> setClip(TransferMode.MOVE, targets)
                EmbeddedMenu.Action.PASTE -> pasteInto(dir)
                EmbeddedMenu.Action.FAVORITE -> favTarget?.let { actions.toggleFavorite(it); reloadFavs() }
                EmbeddedMenu.Action.COPY_PATH -> copyPath(tgt ?: base) // molina-27h
                // molina-27g: dialog Properti kaya dari implementasi; bawaan Terminal hanya bila ditolak
                EmbeddedMenu.Action.INFO ->
                    if (!(hasActions && actions.properties(propsTargets))) showInfo(tgt ?: base, if (tgt == null) true else tgtIsDir) // molina-27h
                EmbeddedMenu.Action.SHARE -> actions.share(targets) // molina-27g
                EmbeddedMenu.Action.SORT -> showSortMenu(anchor) // molina-27g2
                EmbeddedMenu.Action.SEARCH -> if (searchOn) closeSearch() else openSearch() // molina-27g3
                EmbeddedMenu.Action.TRASH -> if (picking) trashMany(targets) else target?.let { trashFlow(it) }
                EmbeddedMenu.Action.DELETE_PERMANENT -> if (picking) deleteManyDialog(targets) else target?.let { deleteDialog(it) }
                EmbeddedMenu.Action.FAVORITES_LIST -> showFavorites()
                EmbeddedMenu.Action.OPEN_TRASH -> actions.openTrash()
                EmbeddedMenu.Action.GO_INTERNAL -> setRoot(File(DrawerFs.INTERNAL_ROOT)) // molina-27i
                EmbeddedMenu.Action.GO_DEBIAN -> openDebian() // molina-27i
                // molina-27f: arsip
                EmbeddedMenu.Action.COMPRESS -> runArchive(ArchiveAction.COMPRESS, targets, dir)
                EmbeddedMenu.Action.EXTRACT_HERE -> runArchive(ArchiveAction.EXTRACT_HERE, targets, dir)
                EmbeddedMenu.Action.EXTRACT_FOLDER -> runArchive(ArchiveAction.EXTRACT_FOLDER, targets, dir)
                EmbeddedMenu.Action.EXTRACT_SUBS -> runArchive(ArchiveAction.EXTRACT_SUBS, targets, dir)
                EmbeddedMenu.Action.EXTRACT_TO -> runArchive(ArchiveAction.EXTRACT_TO, targets, dir)
                EmbeddedMenu.Action.VIEW_ARCHIVE -> runArchive(ArchiveAction.VIEW, targets, dir)
                EmbeddedMenu.Action.TEST_ARCHIVE -> runArchive(ArchiveAction.TEST, targets, dir)
                EmbeddedMenu.Action.EXTRACT_PASTE -> extractClipHere(dir)
            }
            Unit
        }
        showMenuRoot(anchor, entries, dispatch) // molina-27m: akar \u22EE Aksi / \u2630 Lokasi
    }

    private val navActionSet = setOf(
        EmbeddedMenu.Action.FAVORITES_LIST, EmbeddedMenu.Action.GO_INTERNAL,
        EmbeddedMenu.Action.GO_DEBIAN, EmbeddedMenu.Action.OPEN_TRASH,
    )

    /**
     * [molina-27m][molina-27n] Menu melayang akar dari tombol bento: dua tombol ikon vertikal, \u22EE Aksi (menu titik tiga lengkap) dan
     * \u2630 Lokasi (Favorit, Internal, Debian, Sampah). Tanpa kelompok Lokasi (mode pilih, menu baris) langsung ke daftar aksi.
     */
    private fun showMenuRoot(anchor: View, entries: List<EmbeddedMenu.Entry>, onPick: (EmbeddedMenu.Action) -> Unit) {
        val nav = entries.filter { it.action in navActionSet }
        val acts = entries.filter { it.action !in navActionSet }
        if (nav.isEmpty() || acts.isEmpty()) { showFlyout(anchor, entries, onPick); return }
        lateinit var popup: PopupWindow
        val col = flyoutColumn()
        fun rootRow(glyph: String, label: String, open: () -> Unit) {
            val r = LinearLayout(context)
            r.gravity = Gravity.CENTER
            val g = TextView(context)
            g.text = glyph
            g.textSize = 24f
            g.gravity = Gravity.CENTER
            g.setTextColor(white)
            r.addView(g, LinearLayout.LayoutParams(MATCH, MATCH))
            r.contentDescription = label
            r.background = flyoutRipple()
            r.setOnClickListener {
                popup.dismiss()
                open()
            }
            col.addView(r, LinearLayout.LayoutParams(MATCH, dp(48)))
        }
        rootRow("\u22EE", context.getString(R.string.menu_group_actions)) { showFlyout(anchor, acts, onPick) }
        rootRow("\u2630", context.getString(R.string.menu_group_locations)) { showFlyout(anchor, nav, onPick) }
        popup = presentFlyout(anchor, col, dp(60)) // molina-27n: tombol ikon vertikal di atas FAB
    }

    /** [molina-27t] Pemetaan aksi ke ikon garis; null = tanpa ikon (aksi lokasi memakai FlyoutIconDrawable). */
    private fun menuIconKind(a: EmbeddedMenu.Action): MenuIconDrawable.Kind? = when (a) {
        EmbeddedMenu.Action.REFRESH -> MenuIconDrawable.Kind.REFRESH
        EmbeddedMenu.Action.NEW_DIR -> MenuIconDrawable.Kind.NEW_DIR
        EmbeddedMenu.Action.NEW_FILE -> MenuIconDrawable.Kind.NEW_FILE
        EmbeddedMenu.Action.RENAME -> MenuIconDrawable.Kind.RENAME
        EmbeddedMenu.Action.SELECT -> MenuIconDrawable.Kind.SELECT
        EmbeddedMenu.Action.SELECT_ALL -> MenuIconDrawable.Kind.SELECT_ALL
        EmbeddedMenu.Action.EXIT_SELECT -> MenuIconDrawable.Kind.EXIT
        EmbeddedMenu.Action.COPY -> MenuIconDrawable.Kind.COPY
        EmbeddedMenu.Action.MOVE -> MenuIconDrawable.Kind.MOVE
        EmbeddedMenu.Action.PASTE, EmbeddedMenu.Action.EXTRACT_PASTE -> MenuIconDrawable.Kind.PASTE
        EmbeddedMenu.Action.COMPRESS -> MenuIconDrawable.Kind.COMPRESS
        EmbeddedMenu.Action.EXTRACT_HERE, EmbeddedMenu.Action.EXTRACT_FOLDER, EmbeddedMenu.Action.EXTRACT_SUBS,
        EmbeddedMenu.Action.EXTRACT_TO -> MenuIconDrawable.Kind.EXTRACT
        EmbeddedMenu.Action.VIEW_ARCHIVE -> MenuIconDrawable.Kind.VIEW
        EmbeddedMenu.Action.TEST_ARCHIVE -> MenuIconDrawable.Kind.TEST
        EmbeddedMenu.Action.SHARE -> MenuIconDrawable.Kind.SHARE
        EmbeddedMenu.Action.SORT -> MenuIconDrawable.Kind.SORT
        EmbeddedMenu.Action.SEARCH -> MenuIconDrawable.Kind.SEARCH
        EmbeddedMenu.Action.FAVORITE -> MenuIconDrawable.Kind.FAVORITE
        EmbeddedMenu.Action.COPY_PATH -> MenuIconDrawable.Kind.LINK
        EmbeddedMenu.Action.INFO -> MenuIconDrawable.Kind.INFO
        EmbeddedMenu.Action.TRASH -> MenuIconDrawable.Kind.TRASH
        EmbeddedMenu.Action.DELETE_PERMANENT -> MenuIconDrawable.Kind.DELETE_FOREVER
        else -> null
    }

    private fun flyoutColumn(): LinearLayout {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(6), dp(6), dp(6), dp(6))
        return col
    }

    private fun flyoutRipple() =
        RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x44)), null, ColorDrawable(Color.WHITE))

    /**
     * [molina-27k][molina-27m] Daftar menu melayang gaya Windows: kartu gelap bersudut membulat, baris dengan efek sentuh.
     * Baris lokasi (Favorit, Internal, Debian, Sampah) berikon gambar.
     */
    private fun showFlyout(anchor: View, entries: List<EmbeddedMenu.Entry>, onPick: (EmbeddedMenu.Action) -> Unit) {
        lateinit var popup: PopupWindow
        val col = flyoutColumn()
        entries.forEach { en ->
            val r = LinearLayout(context)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(10), 0, dp(14), 0)
            val kind: FlyoutIconDrawable.Kind? = when (en.action) {
                EmbeddedMenu.Action.FAVORITES_LIST -> FlyoutIconDrawable.Kind.STAR
                EmbeddedMenu.Action.GO_INTERNAL -> FlyoutIconDrawable.Kind.DRIVE
                EmbeddedMenu.Action.GO_DEBIAN -> FlyoutIconDrawable.Kind.DEBIAN
                EmbeddedMenu.Action.OPEN_TRASH -> FlyoutIconDrawable.Kind.TRASH
                else -> null
            }
            val plain = menuIconKind(en.action)
            val tint = if (en.danger) danger else plain?.let { MenuIconDrawable.colorFor(it) } ?: white // molina-27v: ikon berwarna per kelompok, teks tetap putih/merah
            if (kind != null || plain != null) {
                val iv = ImageView(context)
                iv.setImageDrawable(if (kind != null) FlyoutIconDrawable(kind) else MenuIconDrawable(plain!!, tint))
                r.addView(iv, LinearLayout.LayoutParams(dp(20), dp(20)))
            }
            val t = TextView(context)
            t.text = context.getString(en.labelRes, *en.labelArgs.toTypedArray()) // molina-27l
            t.textSize = 14f
            t.setTextColor(if (en.danger) danger else white)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            val tlp = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (kind != null || plain != null) tlp.marginStart = dp(12)
            r.addView(t, tlp)
            r.alpha = if (en.enabled) 1f else 0.38f
            if (en.enabled) {
                r.background = flyoutRipple()
                r.setOnClickListener {
                    popup.dismiss()
                    onPick(en.action)
                }
            }
            col.addView(r, LinearLayout.LayoutParams(MATCH, dp(42)))
        }
        popup = presentFlyout(anchor, col, dp(210), fit = true) // molina-27t: lebar menyesuaikan isi
    }

    /** [molina-27m] Tinggi kartu = seluruh ruang di atas tombol (bukan 70% layar), bilah gulir aktif agar baris bawah terlihat. */
    private fun presentFlyout(anchor: View, col: LinearLayout, maxWidth: Int, fit: Boolean = false): PopupWindow {
        val cap = minOf(maxWidth, resources.displayMetrics.widthPixels - dp(24))
        col.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val w = if (fit) col.measuredWidth.coerceIn(minOf(dp(150), cap), cap) else cap // molina-27t
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = true
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        val card = FrameLayout(context)
        val cardBg = GradientDrawable()
        cardBg.setColor(ContextCompat.getColor(context, UiR.color.molina_surface_variant))
        cardBg.cornerRadius = dp(10).toFloat()
        cardBg.setStroke(dp(1), ColorUtils.setAlphaComponent(ContextCompat.getColor(context, UiR.color.molina_header_frame), 0x88))
        card.background = cardBg
        card.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val avail = maxOf(loc[1] - dp(16), dp(120))
        val h = minOf(col.measuredHeight, avail)
        val popup = PopupWindow(card, w, h, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.elevation = dp(10).toFloat()
        popup.showAsDropDown(anchor, 0, -(anchor.height + h + dp(8)), Gravity.END) // rata kanan, membuka ke atas dari tombol melayang
        scroll.isScrollbarFadingEnabled = col.measuredHeight <= avail // molina-27o: awakenScrollBars() protected; bilah tetap tampak bila isi meluap
        return popup
    }

    // ---------- [molina-27h] tombol melayang + geser pilih ----------

    /** [molina-27h] Tombol melayang pojok kanan bawah: satu-satunya pintu menu titik tiga mode tertanam. */
    private fun setupFab() {
        fabGlyph.background = bentoArt // molina-27n: ikon bento; menu akar berisi \u22EE dan \u2630
        fabGlyph.gravity = Gravity.CENTER
        fab.addView(fabGlyph, FrameLayout.LayoutParams(MATCH, MATCH))
        // molina-27i: lencana jumlah item terpilih di sudut kanan atas tombol
        fabBadge.textSize = 10f
        fabBadge.typeface = Typeface.DEFAULT_BOLD
        fabBadge.gravity = Gravity.CENTER
        fabBadge.setTextColor(Color.BLACK)
        val badgeBg = GradientDrawable()
        badgeBg.shape = GradientDrawable.OVAL
        badgeBg.setColor(Color.WHITE)
        fabBadge.background = badgeBg
        fabBadge.visibility = View.GONE
        fab.addView(fabBadge, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END))
        fab.isClickable = true
        fab.contentDescription = context.getString(R.string.drawer_more)
        fab.elevation = dp(6).toFloat()
        fab.setOnClickListener { showCentralMenu(fab, null, false, true) }
        updateFab()
        val lp = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END)
        lp.setMargins(0, 0, dp(16), dp(20))
        addView(fab, lp)
    }

    /** [molina-27i] Tombol aksen penuh + lencana jumlah saat mode pilih aktif; keadaan biasa: isi redup, bingkai aksen. */
    private fun updateFab() {
        val picking = selection.active
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(if (picking) accent else ColorUtils.blendARGB(ContextCompat.getColor(context, UiR.color.molina_surface), accent, 0.18f))
        bg.setStroke(dp(2), accent)
        fab.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 0x55)), bg, null)
        bentoArt.setColor(if (picking) Color.BLACK else white) // molina-27n
        fabBadge.visibility = if (picking && selection.count > 0) View.VISIBLE else View.GONE
        fabBadge.text = if (selection.count > 99) "99+" else selection.count.toString()
    }

    /** [molina-27i] Beranda Debian PRoot (filesDir/rootfs/debian/root) sebagai folder utama; pola w7DebianPlaces repo lama. */
    private fun openDebian() {
        val home = File(context.filesDir, "rootfs/debian/root")
        if (!home.isDirectory) { toastText("Debian belum terpasang"); return }
        setRoot(home)
    }

    /**
     * [molina-27h][molina-27i] Ruang di bawah daftar untuk tombol melayang + geser baris (pola W7-SWIPE1 repo lama):
     * baris hanya bergeser sedikit (maks 28dp) dan kembali; lewat 64dp saat dilepas = aksi jalan.
     * Baris belum terpilih hanya bisa digeser kanan (pilih); baris terpilih hanya kiri (batal pilih).
     */
    private fun setupEmbeddedList() {
        list.clipToPadding = false
        list.setPadding(0, 0, 0, dp(88))
        if (actions === FileManagerActions.None) return
        val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cap = dp(28).toFloat()
        val thr = dp(64).toFloat()
        val cb = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            private fun pathOf(vh: RecyclerView.ViewHolder): String? =
                adapter.currentList.getOrNull(vh.bindingAdapterPosition)?.entry?.file?.path

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val path = pathOf(vh) ?: return 0
                return if (selection.active && selection.contains(path)) ItemTouchHelper.LEFT else ItemTouchHelper.RIGHT
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = thr / vh.itemView.width.coerceAtLeast(1)

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE // hanya jarak, bukan lemparan

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val path = pathOf(vh)
                if (path != null) swipeSelect(path, direction == ItemTouchHelper.RIGHT)
                vh.itemView.translationX = 0f
                adapter.notifyDataSetChanged() // itemAnimator null: bind ulang memastikan baris kembali
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean,
            ) {
                val shown = (dX * 0.4f).coerceIn(-cap, cap)
                val v = vh.itemView
                if (shown != 0f) {
                    val armed = abs(dX) > thr
                    swipePaint.color = ColorUtils.setAlphaComponent(if (shown > 0f) accent else danger, if (armed) 0x88 else 0x33)
                    if (shown > 0f) c.drawRect(v.left.toFloat(), v.top.toFloat(), v.left + shown, v.bottom.toFloat(), swipePaint)
                    else c.drawRect(v.right + shown, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat(), swipePaint)
                }
                super.onChildDraw(c, rv, vh, shown, dY, state, active)
            }
        }
        ItemTouchHelper(cb).attachToRecyclerView(list)
    }

    /** [molina-27h] Geser kanan = pilih (mode pilih aktif otomatis); geser kiri = batal pilih (hanya bila terpilih). */
    private fun swipeSelect(path: String, on: Boolean) {
        if (on) {
            if (!selection.active) list.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            selection.select(path)
        } else {
            if (!selection.active || !selection.contains(path)) return
            selection.deselect(path)
        }
        selectionChanged()
    }

    // ---------- [molina-27e] pilih banyak dan Salin/Pindah/Tempel ----------

    private fun enterSelection(first: String?) {
        selection.enter(first)
        selectionChanged()
    }

    private fun toggleSelected(path: String) {
        selection.toggle(path)
        selectionChanged()
    }

    private fun selectAllRows() {
        selection.selectAll(adapter.currentList.map { it.entry.file.path })
        selectionChanged()
    }

    /** Tutup mode pilih (tombol Back / ✕ di header). true bila ada mode pilih yang ditutup. */
    fun cancelSelection(): Boolean {
        if (!selection.active) { // molina-27g3: tanpa mode pilih, Back menutup pencarian bila terbuka
            if (!searchOn) return false
            closeSearch()
            return true
        }
        selection.exit()
        selectionChanged()
        return true
    }

    /** [molina-27u] Back luar dibutuhkan bila mode pilih, pencarian, atau folder bisa naik. */
    private fun notifyBack() {
        if (embedded) onSelectionChanged(selection.active || searchOn || canGoUp(currentRoot()))
    }

    private fun canGoUp(root: File): Boolean =
        (root.parentFile != null && vols.none { it.path == root.path }) || (embedded && focusOrRoot(root).path != root.path)

    /** [molina-27u] Tombol Back sistem: tutup mode pilih/pencarian dulu, lalu naik satu folder. false = tidak ada yang ditangani. */
    fun navigateUp(): Boolean {
        if (cancelSelection()) return true
        if (!canGoUp(currentRoot())) return false
        goUp()
        return true
    }

    /** Header, kotak centang baris, dan Back luar diperbarui. Baris tidak berubah isi, jadi bind dipaksa ulang. */
    private fun selectionChanged() {
        rebuild()
        adapter.notifyDataSetChanged()
        notifyBack() // molina-27g3/27u: Back menutup pilih, pencarian, atau naik folder
    }

    private fun setClip(mode: TransferMode, files: List<File>) {
        if (files.isEmpty()) return
        actions.clearArchiveClip() // molina-27f: klip Salin/Pindah dan klip Ekstrak tidak aktif bersamaan
        clip.put(mode, files.map { it.path })
        selection.exit()
        selectionChanged()
    }

    /** Tempel isi papan klip ke [dir]. Hasil dilaporkan lewat [onTransferFinished]. */
    private fun pasteInto(dir: File) {
        val mode = clip.mode ?: return
        val files = clip.paths.map { File(it) }
        if (files.isEmpty()) return
        if (!actions.canPasteInto(dir) || !files.all { actions.canManage(it) }) {
            toastText("Sumber atau tujuan di luar penyimpanan Internal") // molina-27j
            return
        }
        if (actions.transfer(mode, files, dir)) pasteDest = dir else toastText("Operasi lain masih berjalan") // molina-27g0
    }

    /** Dipanggil dari luar saat salin/pindah selesai: klip dikosongkan bila [clearClip], lalu tree disegarkan. */
    fun onTransferFinished(clearClip: Boolean) {
        if (clearClip) clip.clear()
        refresh()
        val dest = pasteDest // molina-27g0: buka folder tujuan hanya bila Tempel berhasil
        pasteDest = null
        if (clearClip && dest != null) openDirectory(dest)
    }

    /**
     * [molina-27g0] Buka [dir]: leluhur dari folder utama dimuat di IO dan dibentangkan, lalu baris digulir ke [dir].
     * [dir] di luar folder utama menjadi folder utama baru. Dipakai Tempel dan aksi Buka setelah Ekstrak.
     */
    fun openDirectory(dir: File, reveal: File = dir) { // molina-27g3: [reveal] = baris yang digulir dan dikilatkan
        scope.launch {
            if (!withContext(Dispatchers.IO) { dir.isDirectory }) return@launch
            if (searchOn) closeSearch() // molina-27g3
            val root = currentRoot()
            val chain = DrawerFs.chainTo(root, dir)
            if (chain == null) { setRoot(dir); return@launch }
            if (chain.isEmpty()) { if (reveal == dir) refresh() else rebuild(Runnable { revealRow(reveal) }); return@launch } // molina-27g3
            val loaded = withContext(Dispatchers.IO) {
                (listOf(root) + chain).associate { it.path to (DrawerFs.list(it) ?: emptyList()) }
            }
            state.children.putAll(loaded)
            chain.forEach { state.expanded.add(it.path) }
            rebuild(Runnable { revealRow(reveal) }) // molina-27g2: gulir + kilatan setelah daftar baru terpasang
        }
    }

    // ---------- [molina-27g3] Cari ----------

    private fun buildSearchBar() {
        searchBar.orientation = LinearLayout.VERTICAL
        searchBar.visibility = View.GONE
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(4), dp(4), dp(4))
        searchField.hint = "Cari nama\u2026"
        searchField.setHintTextColor(dim)
        searchField.setTextColor(white)
        searchField.textSize = 13f
        searchField.typeface = Typeface.MONOSPACE
        searchField.isSingleLine = true
        searchField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        searchField.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        searchField.background = null
        searchField.setPadding(dp(4), 0, dp(4), 0)
        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString().orEmpty().trim()
                scheduleSearch(SEARCH_DEBOUNCE_MS)
            }
        })
        searchField.setOnEditorActionListener { v, _, _ -> hideSearchKeyboard(v); true }
        row.addView(searchField, LinearLayout.LayoutParams(0, dp(36), 1f))

        searchDeepBtn.text = "Sub"
        searchDeepBtn.textSize = 12f
        searchDeepBtn.typeface = Typeface.DEFAULT_BOLD
        searchDeepBtn.gravity = Gravity.CENTER
        searchDeepBtn.setPadding(dp(10), 0, dp(10), 0)
        searchDeepBtn.contentDescription = "Cari sampai subfolder"
        searchDeepBtn.setOnClickListener {
            searchDeep = !searchDeep
            renderSearchBar()
            scheduleSearch(0L)
        }
        row.addView(searchDeepBtn, LinearLayout.LayoutParams(WRAP, dp(36)))

        val close = TextView(context)
        close.text = "\u2715"
        close.textSize = 16f
        close.setTextColor(white)
        close.gravity = Gravity.CENTER
        close.contentDescription = "Tutup pencarian"
        close.setOnClickListener { closeSearch() }
        row.addView(close, LinearLayout.LayoutParams(dp(40), dp(36)))
        searchBar.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))

        searchInfo.textSize = 11f
        searchInfo.setTextColor(dim)
        searchInfo.setPadding(dp(14), 0, dp(10), dp(4))
        searchInfo.visibility = View.GONE
        searchBar.addView(searchInfo, LinearLayout.LayoutParams(MATCH, WRAP))
        searchBar.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)))
        renderSearchBar()
    }

    private fun renderSearchBar() {
        searchDeepBtn.setTextColor(if (searchDeep) accent else dim)
        searchDeepBtn.background = if (searchDeep) ColorDrawable(ColorUtils.setAlphaComponent(accent, MATCH_ALPHA)) else null
        searchInfo.text = searchInfoText
        searchInfo.visibility = if (searchInfoText.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun hideSearchKeyboard(v: View) {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun openSearch() {
        searchOn = true
        searchBar.visibility = View.VISIBLE
        renderSearchBar()
        rebuild()
        notifyBack() // molina-27u: pencarian dibuka
        searchField.post {
            searchField.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        searchOn = false // lebih dulu, agar perubahan teks di bawah tidak memicu pencarian
        searchJob?.cancel()
        searchEntries = null
        searchRel = emptyMap()
        searchQuery = ""
        searchInfoText = ""
        hideSearchKeyboard(searchField)
        searchField.setText("")
        searchBar.visibility = View.GONE
        renderSearchBar()
        rebuild() // molina-27u: pencarian ditutup; rebuild memanggil notifyBack
    }

    private fun scheduleSearch(delayMs: Long) {
        if (!searchOn) return
        searchJob?.cancel()
        searchJob = scope.launch {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            runSearch(searchQuery, searchDeep)
        }
    }

    /** Folder Molina: lewat [FileManagerActions.search] (EntryLister, bisa sampai subfolder). Lainnya: saring isi folder utama yang sudah dimuat. */
    private suspend fun runSearch(q: String, deep: Boolean) {
        if (q.isEmpty()) {
            searchEntries = null
            searchRel = emptyMap()
            searchInfoText = ""
            renderSearchBar()
            rebuild()
            return
        }
        val root = currentRoot()
        searchInfoText = "Mencari\u2026"
        renderSearchBar()
        val outcome = if (actions !== FileManagerActions.None) actions.search(root, q, deep) else null
        if (!searchOn || q != searchQuery) return // kueri berubah atau pencarian ditutup saat menunggu
        if (outcome != null) {
            searchEntries = outcome.hits.map { DrawerEntry(it.file, it.isDir, it.size, it.modified) }
            searchRel = outcome.hits.mapNotNull { h -> h.relDir?.let { h.file.path to it } }.toMap()
            searchInfoText = SearchMatch.info(outcome.hits.size, outcome.truncated, deep)
        } else {
            val local = state.children[root.path].orEmpty().filter { SearchMatch.matches(it.name, q) }
            searchEntries = local
            searchRel = emptyMap()
            val base = SearchMatch.info(local.size, false, false)
            searchInfoText = if (deep) "Subfolder hanya di folder Molina. $base" else base
        }
        renderSearchBar()
        rebuild()
    }

    /** Nama hasil dengan teks cocok berlatar aksen (alpha sama dengan seleksi/klip/kilatan) dan tebal; folder induk relatif redup di belakang nama. */
    private fun searchLabel(e: DrawerEntry): CharSequence {
        val rel = searchRel[e.file.path]
        val s = SpannableString(if (rel == null) e.name else e.name + "  " + rel)
        for (r in SearchMatch.ranges(e.name, searchQuery)) {
            s.setSpan(android.text.style.BackgroundColorSpan(ColorUtils.setAlphaComponent(accent, MATCH_ALPHA)), r.first, r.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(android.text.style.StyleSpan(Typeface.BOLD), r.first, r.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (rel != null) s.setSpan(ForegroundColorSpan(dim), e.name.length, s.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** Ketuk hasil: tutup pencarian, buka folder (hasil folder) atau folder induk (hasil berkas) dan kilatkan barisnya. */
    private fun openFromSearch(e: DrawerEntry) {
        if (e.isDir) openDirectory(e.file) else e.file.parentFile?.let { openDirectory(it, e.file) }
    }

    // ---------- [molina-27g2] Urut dan kilatan folder hasil ----------

    private fun loadSort(): DrawerSort = runCatching {
        DrawerSort.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SORT, null))
    }.getOrDefault(DrawerSort.DEFAULT)

    private fun applySort(s: DrawerSort) {
        sort = s
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SORT, s.encode()).apply() }
        rebuild()
    }

    /** Pilih kunci urut; mengetuk kunci aktif membalik arah (penanda ▲/▼). */
    private fun showSortMenu(anchor: View) {
        anchor.post {
            val keys = DrawerSortKey.values()
            val menu = PopupMenu(context, anchor)
            keys.forEachIndexed { i, k ->
                val on = k == sort.key
                menu.menu.add(0, i, i, if (on) "${k.label} ${if (sort.ascending) "\u25B2" else "\u25BC"}" else k.label).also {
                    it.isCheckable = true
                    it.isChecked = on
                }
            }
            menu.setOnMenuItemClickListener { item -> applySort(sort.choose(keys[item.itemId])); true }
            menu.show()
        }
    }

    /** Gulir ke baris [dir] lalu beri kilatan latar aksen sekitar [FLASH_MS] ms supaya mudah ditemukan di daftar panjang. */
    private fun revealRow(dir: File) {
        val idx = adapter.currentList.indexOfFirst { it.entry.file.path == dir.path }
        if (idx < 0) return
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(idx, dp(48))
        flashClear?.let { list.removeCallbacks(it) }
        val prev = flashPath
        flashPath = dir.path
        if (prev != null && prev != dir.path) {
            val pi = adapter.currentList.indexOfFirst { it.entry.file.path == prev }
            if (pi >= 0) adapter.notifyItemChanged(pi)
        }
        adapter.notifyItemChanged(idx)
        val clear = Runnable {
            val p = flashPath ?: return@Runnable
            flashPath = null
            val i = adapter.currentList.indexOfFirst { it.entry.file.path == p }
            if (i >= 0) adapter.notifyItemChanged(i)
        }
        flashClear = clear
        list.postDelayed(clear, FLASH_MS)
    }

    /** [molina-27f] Jalankan aksi arsip; mode pilih ditutup bila diterima. Hasil dilaporkan implementasi (panel progres + Snackbar). */
    private fun runArchive(action: ArchiveAction, files: List<File>, dir: File) {
        if (files.isEmpty()) return
        if (!actions.archive(action, files, dir)) {
            toastText("Operasi lain masih berjalan")
            return
        }
        if (selection.active) {
            selection.exit()
            selectionChanged()
        }
    }

    private fun extractClipHere(dir: File) {
        if (!actions.canPasteInto(dir)) { toastText("Tujuan di luar penyimpanan Internal"); return } // molina-27j
        if (!actions.extractClipInto(dir)) toastText("Operasi lain masih berjalan")
    }

    /** [molina-27f] Klip Ekstrak berubah di luar View: klip Salin/Pindah dikosongkan, bilah klip digambar ulang. */
    fun onArchiveClipChanged() {
        if (actions.archiveClipCount() > 0) clip.clear()
        renderClip()
    }

    private fun trashMany(files: List<File>) {
        if (files.isEmpty()) return
        actions.trash(files) { ok ->
            if (ok) {
                selection.exit()
                selectionChanged()
                refresh()
            }
        }
    }

    private fun deleteManyDialog(files: List<File>) {
        if (files.isEmpty()) return
        AlertDialog.Builder(context)
            .setTitle("Hapus permanen ${files.size} item?")
            .setMessage("Item terpilih dan seluruh isinya dihapus permanen.")
            .setNegativeButton(R.string.drawer_cancel, null)
            .setPositiveButton(R.string.drawer_delete) { _, _ ->
                io("delete-many", {
                    when {
                        files.any { DrawerFs.isProtected(it, vols) } -> R.string.drawer_err_protected
                        files.map { DrawerFs.deleteSafe(it) }.all { it } -> null
                        else -> R.string.drawer_err_generic
                    }
                }) {
                    toast(R.string.drawer_done_delete)
                    selection.exit()
                    selectionChanged()
                    refresh()
                }
            }
            .show()
    }

    /** Bilah klip di bawah header: jumlah item, Tempel ke folder utama, dan ✕ untuk mengosongkan. */
    private fun renderClip() {
        if (!embedded) return
        val an = actions.archiveClipCount() // molina-27f: klip Ekstrak lebih diutamakan bila ada
        val n = if (an > 0) an else clip.count
        clipBar.visibility = if (n > 0) View.VISIBLE else View.GONE
        if (n == 0) return
        clipPaste.text = if (an > 0) "Ekstrak" else "Tempel"
        clipText.text = if (an > 0) "Ekstrak $an arsip ke\u2026" else (if (clip.mode == TransferMode.MOVE) "Pindah " else "Salin ") + "$n item"
        val ok = actions.canPasteInto(currentRoot())
        clipPaste.isEnabled = true // molina-27j: tetap bisa diketuk; penolakan dijelaskan lewat toast
        clipPaste.alpha = if (ok) 1f else 0.38f
    }

    /** [molina-27d] Muat ulang tree dari luar (mis. setelah Sampah dipulihkan). */
    fun refresh() { rebuild(); refreshAll(); reloadFavs(); if (searchOn) scheduleSearch(0L) } // molina-27g3

    /** [molina-27d2] Muat daftar Favorit di IO lalu gambar ulang baris agar bintang akurat. */
    private fun reloadFavs() {
        if (actions === FileManagerActions.None) return
        scope.launch {
            favPaths = actions.favoriteDirs().map { it.path }.toSet()
            adapter.notifyDataSetChanged()
        }
    }

    /** [molina-27d] Daftar Favorit lewat [FileManagerActions]; ketuk = jadikan folder utama. */
    private fun showFavorites() {
        scope.launch {
            val dirs = actions.favoriteDirs()
            if (dirs.isEmpty()) { toastText("Belum ada Favorit"); return@launch }
            val labels = dirs.map { DrawerFs.displayPath(it.path) }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Favorit")
                .setItems(labels) { _, i -> setRoot(dirs[i]) }
                .setNegativeButton(R.string.drawer_cancel, null)
                .show()
        }
    }

    /** [molina-27d] Mode tertanam: Hapus = pindah ke Sampah (Urungkan ditampilkan oleh [FileManagerActions]). */
    private fun trashFlow(f: File) {
        actions.trash(listOf(f)) { ok -> if (ok) afterChange(f.parentFile, f.path) }
    }

    private fun toastText(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

    /** [molina-27c] Dialog nama polos seperti promptCreate di Penjelajah; berkas baru tidak dibuka di nano. */
    private fun promptCreateEmbedded(folder: Boolean, parent: File = activeDir()) { // molina-27o
        MolinaDialog.input(
            context,
            title = context.getString(if (folder) R.string.drawer_new_dir else R.string.drawer_new_file),
            initial = "",
            positive = context.getString(R.string.drawer_create),
            selectBase = false,
        ) { raw ->
            val name = raw.trim()
            val target = File(parent, name)
            io(if (folder) "mkdir" else "mkfile", {
                when {
                    !DrawerFs.validName(name) -> R.string.drawer_err_name
                    target.exists() -> R.string.drawer_err_exists
                    !(if (folder) target.mkdir() else target.createNewFile()) -> R.string.drawer_err_generic
                    else -> null
                }
            }) {
                if (parent.path != currentRoot().path) state.expanded.add(parent.path)
                toast(if (folder) R.string.drawer_done_dir else R.string.drawer_done_file)
                load(parent)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (embedded) { rebuild(); refreshAll(); reloadFavs() }
    }

    fun toggle() { if (isOpen) close() else open() }

    fun open() {
        if (isOpen) return
        val screenDp = resources.configuration.screenWidthDp
        val width = max(dp(MIN_WIDTH_DP), min(dp(MAX_WIDTH_DP), (screenDp * FRACTION * resources.displayMetrics.density).toInt()))
        panel.layoutParams = FrameLayout.LayoutParams(width, MATCH)
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
        visibility = View.VISIBLE
        bringToFront()
        MolinaEvents.behavior("term", "drawer.open")
        rebuild()
        refreshAll()
        host.onOpenChanged(true)
    }

    fun close() {
        if (!isOpen || embedded) return
        visibility = View.GONE
        host.onOpenChanged(false)
    }

    // ---------- susunan tampilan ----------

    private fun buildPanel() {
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(2), dp(2), dp(2), dp(2))
        // molina-27j/27w: tanpa bingkai luar; header Terminal dan Berkas memakai tampilan yang sama

        back.text = "\u2039"
        back.textSize = 22f
        back.gravity = Gravity.CENTER
        back.setTextColor(white)
        back.contentDescription = context.getString(R.string.drawer_up)
        back.text = ""
        back.background = backArt // molina-27j/27w: tombol kembali bulat biru di Terminal dan Berkas
        back.setOnClickListener {
            if (embedded && selection.active) cancelSelection() else goUp() // molina-27e/27o
        }
        header.addView(back, LinearLayout.LayoutParams(dp(30), dp(30))) // molina-27j/27n: header tipis

        // [HEADER-FILL] kotak path melebar (bobot 1) sampai tombol titik tiga
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(10), dp(2), dp(10), dp(2))
        val boxBg = GradientDrawable()
        boxBg.setColor(ContextCompat.getColor(context, UiR.color.molina_bg))
        boxBg.cornerRadius = dp(2).toFloat() // molina-27j/27w
        boxBg.setStroke(dp(1), ColorUtils.setAlphaComponent(ContextCompat.getColor(context, UiR.color.molina_header_frame), 0xAA)) // molina-27j/27w
        box.background = boxBg
        title.setTextColor(white)
        title.textSize = 14f
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        pathText.setTextColor(dim)
        pathText.textSize = 10f
        pathText.typeface = Typeface.MONOSPACE
        pathText.maxLines = 1
        pathText.ellipsize = TextUtils.TruncateAt.MIDDLE
        box.addView(title)
        box.addView(pathText)
        run { // molina-27j/27w: satu baris breadcrumb "Internal \u203A Molina \u203A ...", ujung kanan selalu terlihat
            title.typeface = Typeface.DEFAULT
            title.textSize = 13f // molina-27n
            title.ellipsize = TextUtils.TruncateAt.START
            pathText.visibility = View.GONE
            box.setPadding(dp(12), 0, dp(12), 0)
        }
        val boxLp = LinearLayout.LayoutParams(0, dp(30), 1f) // molina-27j/27n/27w
        boxLp.marginStart = dp(4)
        boxLp.marginEnd = dp(4)
        header.addView(box, boxLp)

        val headerMenu = HeaderDotsView(context)
        headerDots = headerMenu // molina-27g3
        headerMenu.contentDescription = context.getString(R.string.drawer_more)
        headerMenu.setOnClickListener { showHeaderMenu(it) }
        if (embedded) headerMenu.visibility = View.GONE // molina-27h: menu pindah ke tombol melayang
        header.addView(headerMenu, LinearLayout.LayoutParams(dp(34), dp(34)))

        val headerLp = LinearLayout.LayoutParams(MATCH, WRAP)
        headerLp.setMargins(dp(6), dp(3), dp(6), dp(3)) // molina-27n/27w
        panel.addView(header, headerLp)
        panel.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1))) // molina-27j/27w: garis pemisah di bawah header

        chips.orientation = LinearLayout.HORIZONTAL
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.setPadding(dp(8), dp(2), dp(8), dp(2))
        chipScroll.visibility = View.GONE
        chipScroll.addView(chips)
        panel.addView(chipScroll, LinearLayout.LayoutParams(MATCH, WRAP))

        panel.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)))

        // molina-27e: bilah klip (hanya tampil bila papan klip berisi), aksen Terminal
        clipBar.orientation = LinearLayout.HORIZONTAL
        clipBar.gravity = Gravity.CENTER_VERTICAL
        clipBar.setPadding(dp(12), 0, dp(4), 0)
        clipBar.setBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x22))
        clipBar.visibility = View.GONE
        clipText.setTextColor(white)
        clipText.textSize = 12f
        clipText.maxLines = 1
        clipText.ellipsize = TextUtils.TruncateAt.END
        clipBar.addView(clipText, LinearLayout.LayoutParams(0, WRAP, 1f))
        clipPaste.text = "Tempel"
        clipPaste.textSize = 12f
        clipPaste.typeface = Typeface.DEFAULT_BOLD
        clipPaste.setTextColor(accent)
        clipPaste.gravity = Gravity.CENTER
        clipPaste.setPadding(dp(12), 0, dp(12), 0)
        clipPaste.setOnClickListener { if (actions.archiveClipCount() > 0) extractClipHere(activeDir()) else pasteInto(activeDir()) } // molina-27f/27o
        clipBar.addView(clipPaste, LinearLayout.LayoutParams(WRAP, MATCH))
        clipClear.text = "\u2715"
        clipClear.textSize = 14f
        clipClear.setTextColor(white)
        clipClear.gravity = Gravity.CENTER
        clipClear.contentDescription = "Kosongkan papan klip"
        clipClear.setOnClickListener { clip.clear(); actions.clearArchiveClip(); renderClip() } // molina-27f
        clipBar.addView(clipClear, LinearLayout.LayoutParams(dp(40), MATCH))
        if (embedded) { buildSearchBar(); panel.addView(searchBar, LinearLayout.LayoutParams(MATCH, WRAP)) } // molina-27g3
        if (embedded) panel.addView(clipBar, LinearLayout.LayoutParams(MATCH, dp(40)))

        empty.text = context.getString(R.string.drawer_empty)
        empty.setTextColor(dim)
        empty.textSize = 12f
        empty.setPadding(dp(20), dp(20), dp(20), dp(8))
        empty.visibility = View.GONE
        panel.addView(empty, LinearLayout.LayoutParams(MATCH, WRAP))

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        list.overScrollMode = View.OVER_SCROLL_NEVER
        if (embedded) setupEmbeddedList() // molina-27h
        panel.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))

        if (!embedded) panel.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)))
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(barButton(true, R.string.drawer_new_dir) { newDirDialog(currentRoot()) }, LinearLayout.LayoutParams(0, MATCH, 1f))
        val sep = View(context)
        sep.setBackgroundColor(hairline)
        bar.addView(sep, LinearLayout.LayoutParams(dp(1), dp(20)))
        bar.addView(barButton(false, R.string.drawer_new_file) { newFileDialog(currentRoot()) }, LinearLayout.LayoutParams(0, MATCH, 1f))
        if (!embedded) panel.addView(bar, LinearLayout.LayoutParams(MATCH, dp(40)))
    }

    private fun divider(): View {
        val v = View(context)
        v.setBackgroundColor(hairline)
        return v
    }

    private fun barButton(dir: Boolean, desc: Int, onClick: () -> Unit): FrameLayout {
        val box = FrameLayout(context)
        box.contentDescription = context.getString(desc)
        box.isClickable = true
        box.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x55)), null, ColorDrawable(Color.WHITE))
        box.setOnClickListener { onClick() }
        val icon = TreeIconView(context)
        icon.isDir = dir
        box.addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        val plus = TextView(context)
        plus.text = "+"
        plus.setTextColor(white)
        plus.textSize = 13f
        plus.typeface = Typeface.DEFAULT_BOLD
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
        lp.leftMargin = dp(14)
        lp.topMargin = dp(10)
        box.addView(plus, lp)
        return box
    }

    // ---------- keadaan dan muat ulang ----------

    private fun currentRoot(): File {
        state.root?.let { return it }
        val start = File(DrawerFs.DEFAULT_ROOT).takeIf { it.isDirectory } ?: File(DrawerFs.INTERNAL_ROOT)
        state.root = start
        return start
    }

    private fun setRoot(dir: File) {
        if (searchOn) closeSearch() // molina-27g3
        state.root = dir
        focusPath = null // molina-27n
        state.expanded.clear()
        rebuild()
        load(dir)
    }

    private fun load(dir: File) {
        scope.launch {
            val entries = withContext(Dispatchers.IO) { DrawerFs.list(dir) ?: emptyList() }
            state.children[dir.path] = entries
            rebuild()
        }
    }

    /** Segarkan folder utama dan folder yang masih terbuka; buang yang sudah tidak ada. */
    private fun refreshAll() {
        val paths = listOf(currentRoot().path) + state.expanded.toList()
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                val map = HashMap<String, List<DrawerEntry>?>()
                for (p in paths) {
                    val d = File(p)
                    map[p] = if (d.isDirectory) (DrawerFs.list(d) ?: emptyList()) else null
                }
                Pair(map, DrawerFs.volumes())
            }
            vols = fresh.second
            for ((p, entries) in fresh.first) {
                if (entries != null) state.children[p] = entries else { state.children.remove(p); state.expanded.remove(p) }
            }
            rebuild()
        }
    }

    private fun afterChange(parent: File?, oldPath: String) {
        if (searchOn) scheduleSearch(150L) // molina-27g3
        state.expanded.removeAll { it == oldPath || it.startsWith("$oldPath/") }
        if (focusPath?.let { it == oldPath || it.startsWith("$oldPath/") } == true) focusPath = null // molina-27n
        state.children.keys.filter { it == oldPath || it.startsWith("$oldPath/") }.forEach { state.children.remove(it) }
        if (parent != null) load(parent) else rebuild()
    }

    private fun rebuild(onCommitted: Runnable? = null) { // molina-27g2
        val root = currentRoot()
        val found = searchEntries // molina-27g3: hasil Cari menggantikan pohon, memakai urutan aktif
        val rows = if (found != null) DrawerSort.sorted(found, sort).map { DrawerRow(it, 0, false) } else DrawerFs.flatten(state, root, sort)
        adapter.submitList(rows, onCommitted)
        headerDots?.statusColor = if (embedded && (searchOn || sort != DrawerSort.DEFAULT)) accent else null
        title.text = crumbText(if (embedded) focusOrRoot(root) else root) // molina-27j/27n/27w: breadcrumb di Terminal juga
        pathText.text = DrawerFs.displayPath(root.path)
        host.onRootChanged(root.path)
        val canUp = canGoUp(root) // molina-27o/27u
        notifyBack()
        val picking = embedded && selection.active // molina-27e: ‹ jadi ✕ dan judul menghitung item terpilih
        back.isEnabled = canUp || picking
        back.alpha = if (canUp || picking) 1f else 0.38f
        if (embedded) {
            backArt.cross = picking // molina-27j: panah kembali berubah jadi \u2715 saat mode pilih
            if (picking) title.text = if (selection.count == 0) "Mode pilih" else "${selection.count} dipilih"
        }
        empty.text = if (found != null) "Tidak ada hasil" else context.getString(R.string.drawer_empty) // molina-27g3
        empty.visibility = if (rows.isEmpty() && (found != null || state.children[root.path] != null)) View.VISIBLE else View.GONE
        renderChips(root)
        renderClip() // molina-27e
        if (embedded) updateFab() // molina-27i
    }

    /** [molina-27n] Item terakhir yang diketuk di pohon; header menampilkan path-nya. Null = root aktif. */
    private var focusPath: String? = null

    private fun focusOrRoot(root: File): File = File(FocusNav.effective(root.path, focusPath)) // molina-27o

    /** [molina-27o] Folder yang dipakai Folder baru, Tempel, Properti: item di header bila folder, jika berkas maka induknya. */
    private fun activeDir(): File {
        val root = currentRoot()
        if (!embedded) return root
        return File(FocusNav.activeDir(root.path, focusPath) { File(it).isDirectory })
    }

    /** [molina-27o] Tombol \u2039: naik dari item di header; baru setelah header di root, root berpindah ke folder induknya. */
    private fun goUp() {
        val root = currentRoot()
        if (embedded) {
            val t = FocusNav.up(root.path, focusPath)
            if (t != FocusNav.UP_AT_ROOT) {
                focusPath = if (t == FocusNav.ROOT_FOCUS) null else t
                rebuild()
                return
            }
        }
        root.parentFile?.takeIf { vols.none { v -> v.path == root.path } }?.let { setRoot(it) }
    }

    /** [molina-27j] Breadcrumb "Internal \u203A Molina \u203A Film": pemisah redup, segmen putih. */
    private fun crumbText(root: File): CharSequence {
        val sb = SpannableStringBuilder()
        DrawerFs.crumbs(root.path).forEachIndexed { i, seg ->
            if (i > 0) {
                val a = sb.length
                sb.append(" \u203A ")
                sb.setSpan(ForegroundColorSpan(dim), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(seg)
        }
        return sb
    }

    private fun renderChips(root: File) {
        chips.removeAllViews()
        chipScroll.visibility = if (vols.size > 1) View.VISIBLE else View.GONE
        if (vols.size <= 1) return
        for (v in vols) {
            val on = root.path == v.path || root.path.startsWith(v.path + "/")
            val chip = TextView(context)
            chip.text = DrawerFs.volumeLabel(v)
            chip.textSize = 12f
            chip.typeface = Typeface.DEFAULT_BOLD
            chip.setTextColor(white)
            chip.setPadding(dp(10), dp(5), dp(10), dp(5))
            val bg = GradientDrawable()
            bg.cornerRadius = dp(6).toFloat()
            bg.setColor(if (on) ColorUtils.setAlphaComponent(accent, 0x55) else ColorUtils.setAlphaComponent(Color.WHITE, 0x1F))
            chip.background = bg
            chip.setOnClickListener { if (root.path != v.path) setRoot(v) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.marginEnd = dp(6)
            chips.addView(chip, lp)
        }
    }

    // ---------- daftar ----------

    private fun onRowTap(row: DrawerRow) {
        val f = row.entry.file
        if (embedded && searchEntries != null) { // molina-27g3: pada hasil Cari, ketuk = buka lokasi (atau ganti pilih)
            if (selection.active) toggleSelected(f.path) else openFromSearch(row.entry)
            return
        }
        if (embedded && !(selection.active && !row.entry.isDir)) focusPath = f.path // molina-27n: header mengikuti ketukan
        if (row.entry.isDir) {
            if (row.expanded) {
                state.expanded.remove(f.path)
                rebuild()
            } else if (embedded && !selection.active) { // molina-27s: folder kosong dibuka sebagai folder utama
                scope.launch {
                    val entries = state.children[f.path] ?: withContext(Dispatchers.IO) { DrawerFs.list(f) ?: emptyList() }
                    if (entries.isEmpty()) {
                        setRoot(f)
                    } else {
                        state.children[f.path] = entries
                        state.expanded.add(f.path)
                        rebuild()
                    }
                }
            } else {
                state.expanded.add(f.path)
                if (state.children[f.path] == null) load(f) else rebuild()
            }
        } else if (!embedded && editInNano(f)) {
            close()
        } else if (embedded && selection.active) { // molina-27e: berkas = ganti status pilih; folder tetap buka/tutup
            toggleSelected(f.path)
        } else if (embedded) {
            rebuild() // molina-27n: berkas diketuk, perbarui path header
        }
    }

    private class RowHolder(
        val row: LinearLayout,
        val arrow: TextView,
        val icon: TreeIconView,
        val name: TextView,
        val star: TextView,
        val check: CheckBox, // molina-27e
        val more: TextView,
    ) : RecyclerView.ViewHolder(row)

    private object RowDiff : DiffUtil.ItemCallback<DrawerRow>() {
        override fun areItemsTheSame(a: DrawerRow, b: DrawerRow): Boolean = a.entry.file.path == b.entry.file.path
        override fun areContentsTheSame(a: DrawerRow, b: DrawerRow): Boolean = a == b
    }

    private inner class RowAdapter : ListAdapter<DrawerRow, RowHolder>(RowDiff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val ctx = parent.context
            val ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x55))
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = RecyclerView.LayoutParams(MATCH, dp(if (embedded) ROW_EMBEDDED_DP else ROW_DP)) // molina-27t
            row.isClickable = true
            row.background = RippleDrawable(ripple, null, ColorDrawable(Color.WHITE))

            val arrow = TextView(ctx)
            arrow.setTextColor(white)
            arrow.textSize = 12f
            arrow.gravity = Gravity.CENTER
            row.addView(arrow, LinearLayout.LayoutParams(dp(14), WRAP))

            val icon = TreeIconView(ctx)
            val iconSize = if (embedded) 30 else 20 // molina-27o/27t: ikon Berkas lebih besar
            val iconLp = LinearLayout.LayoutParams(dp(iconSize), dp(iconSize))
            iconLp.marginStart = dp(4)
            iconLp.marginEnd = dp(8)
            row.addView(icon, iconLp)

            val name = TextView(ctx)
            name.setTextColor(white)
            name.textSize = if (embedded) 15f else 13f // molina-27t
            name.typeface = Typeface.MONOSPACE
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            row.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))

            val star = TextView(ctx) // molina-27d2: penanda folder Favorit, warna aksen Terminal
            star.text = "\u2605"
            star.textSize = 12f
            star.setTextColor(accent)
            star.gravity = Gravity.CENTER
            star.contentDescription = "Favorit"
            star.visibility = View.GONE
            row.addView(star, LinearLayout.LayoutParams(dp(18), WRAP))

            val check = CheckBox(ctx) // molina-27e: pengganti titik tiga saat mode pilih
            check.gravity = Gravity.CENTER
            check.buttonTintList = ColorStateList.valueOf(accent)
            check.contentDescription = "Pilih item"
            check.visibility = View.GONE
            row.addView(check, LinearLayout.LayoutParams(dp(40), MATCH))

            val more = TextView(ctx)
            more.text = "\u22EE"
            more.textSize = 20f
            more.setTextColor(white)
            more.gravity = Gravity.CENTER
            more.contentDescription = ctx.getString(R.string.drawer_more)
            more.background = RippleDrawable(ripple, null, ColorDrawable(Color.WHITE))
            row.addView(more, LinearLayout.LayoutParams(dp(40), MATCH))
            return RowHolder(row, arrow, icon, name, star, check, more)
        }

        override fun onBindViewHolder(h: RowHolder, position: Int) {
            val item = getItem(position)
            val e = item.entry
            h.row.setPadding(dp(6 + item.depth * 14), 0, 0, 0)
            h.row.alpha = if (e.hidden) 0.6f else 1f
            h.arrow.text = if (e.isDir) (if (item.expanded) "\u25BE" else "\u25B8") else ""
            h.icon.isDir = e.isDir
            val kind = if (e.isDir) FileKind.OTHER else FileKind.of(e.name)
            h.icon.kind = kind
            h.icon.label = if (embedded && !e.isDir) ExtLabel.of(e.name) else "" // molina-27o
            h.name.setTextColor(kind.nameColor)
            h.name.text = if (searchEntries != null) searchLabel(e) else e.name // molina-27g3
            h.star.visibility = if (e.isDir && e.file.path in favPaths) View.VISIBLE else View.GONE // molina-27d2
            h.row.setOnClickListener { onRowTap(item) }
            h.more.setOnClickListener { showRowMenu(it, e) }
            // molina-27e: mode pilih menukar titik tiga dengan kotak centang; tekan lama = pilih
            val picking = embedded && selection.active
            h.more.visibility = if (picking || embedded) View.GONE else View.VISIBLE // molina-27h: titik tiga baris dihapus di mode tertanam
            h.check.visibility = if (picking) View.VISIBLE else View.GONE
            h.check.isChecked = picking && selection.contains(e.file.path)
            // molina-27f: baris terpilih berlatar aksen (alpha 0x22, sama dengan bilah klip), ripple tetap
            val selected = picking && selection.contains(e.file.path)
            val flash = e.file.path == flashPath // molina-27g2
            h.row.background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x55)),
                if (selected || flash) ColorDrawable(ColorUtils.setAlphaComponent(accent, 0x22)) else null,
                ColorDrawable(Color.WHITE),
            )
            h.check.setOnClickListener { toggleSelected(e.file.path) }
            if (embedded) {
                h.row.setOnLongClickListener {
                    if (actions === FileManagerActions.None) return@setOnLongClickListener false
                    if (selection.active) {
                        toggleSelected(e.file.path)
                    } else {
                        h.row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        enterSelection(e.file.path)
                    }
                    true
                }
            }
        }
    }

    // ---------- menu opsi (titik tiga) ----------

    private fun showRowMenu(anchor: View, e: DrawerEntry) {
        if (embedded) { showCentralMenu(anchor, e.file, e.isDir, false); return } // molina-27d2
        val f = e.file
        val menu = PopupMenu(context, anchor)
        val m = menu.menu
        if (e.isDir) {
            if (!embedded) m.add(0, ID_CD_ROOT, 0, context.getString(R.string.drawer_cd_root))
            m.add(0, ID_NEW_DIR, 2, context.getString(R.string.drawer_new_dir_here))
            m.add(0, ID_NEW_FILE, 3, context.getString(R.string.drawer_new_file_here))
        } else {
            if (!embedded) m.add(0, ID_NANO, 0, context.getString(R.string.drawer_nano))
            if (!embedded && RunnableFile.interpreter(e.name) != null) {
                val run = SpannableString("\u25B6 " + context.getString(R.string.drawer_run))
                run.setSpan(ForegroundColorSpan(runGreen), 0, 1, 0)
                m.add(0, ID_RUN, 1, run)
            }
        }
        m.add(0, ID_RENAME, 10, context.getString(R.string.drawer_rename))
        m.add(0, ID_COPY, 11, context.getString(R.string.drawer_copy_path))
        m.add(0, ID_INFO, 12, context.getString(R.string.drawer_info))
        val del = SpannableString(context.getString(R.string.drawer_delete))
        del.setSpan(ForegroundColorSpan(danger), 0, del.length, 0)
        m.add(0, ID_DELETE, 13, del)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_CD_ROOT -> cdAndSetRoot(f)
                ID_NEW_DIR -> if (embedded) promptCreateEmbedded(true, f) else newDirDialog(f)
                ID_NEW_FILE -> if (embedded) promptCreateEmbedded(false, f) else newFileDialog(f)
                ID_NANO -> if (editInNano(f)) close()
                ID_RUN -> runFlow(f)
                ID_RENAME -> renameDialog(f, e.isDir)
                ID_COPY -> copyPath(f)
                ID_INFO -> showInfo(f, e.isDir)
                ID_DELETE -> deleteDialog(f)
            }
            true
        }
        menu.show()
    }

    /** Titik tiga di header drawer: satu aksi, cd terminal ke folder utama (Folder baru/Berkas baru ada di bilah bawah). */
    private fun showHeaderMenu(anchor: View) {
        if (embedded) { showCentralMenu(anchor, null, false, true); return } // molina-27d2
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, ID_CD_ROOT, 0, context.getString(R.string.drawer_cd_root))
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == ID_CD_ROOT) cdAndSetRoot(currentRoot())
            true
        }
        menu.show()
    }

    /** cd terminal ke [dir] lalu jadikan folder utama drawer; drawer ditutup bila cd berhasil dikirim. */
    private fun cdAndSetRoot(dir: File) {
        if (currentRoot().path != dir.path) setRoot(dir)
        if (cdHere(dir)) close()
    }

    // ---------- aksi shell ----------

    /** Path aman untuk dikirim ke shell sesi aktif, atau null (dan Toast alasannya). */
    private fun shellTarget(f: File): String? {
        if (!host.hasSession()) { toast(R.string.drawer_no_session); return null }
        if (host.isFullScreenProgram()) { toast(R.string.drawer_fullscreen); return null }
        val p = DrawerFs.shellPath(f, host.isProotSession())
        if (p == null) { toast(R.string.drawer_not_visible); return null }
        if (DrawerFs.hasControlChars(p)) { toast(R.string.drawer_ctrl_chars); return null }
        return p
    }

    private fun cdHere(dir: File): Boolean {
        val p = shellTarget(dir) ?: return false
        host.changeDirectory(p)
        return true
    }

    private fun editInNano(f: File): Boolean {
        val p = shellTarget(f) ?: return false
        host.send("nano " + DrawerFs.quote(p) + "\r")
        MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "nano"))
        return true
    }

    /** Tombol Jalankan: ikut setelan (tanya lewat dialog = bawaan, selalu sesi aktif, atau selalu sesi baru). */ // molina-patch31
    private fun runFlow(f: File) = when (host.runMode()) {
        RunMode.ACTIVE -> runActiveAuto(f)
        RunMode.NEW -> runNewAuto(f)
        RunMode.ASK -> runDialog(f)
    }

    /**
     * [RUN-AUTO] Jalankan = alur sama seperti Edit di nano, tetapi RUN (Alt+R) dikirim otomatis begitu nano siap.
     * Sesi aktif: nano dibuka dengan path berkas, folder kerja sesi tidak diubah. Sesi shell Android tidak punya
     * nano Molina, jadi berkas dijalankan langsung seperti sebelumnya.
     */
    private fun runActiveAuto(f: File) {
        if (!host.isProotSession()) { runInActive(f); return }
        if (editInNano(f)) {
            host.showNanoKeys()
            host.autoRunNano()
            MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "run_nano", "tab" to "active", "auto" to "1"))
            close()
        }
    }

    /** [RUN-AUTO] Sesi baru: tab baru, cd ke folder berkas, nano, lalu RUN otomatis (tab non-Debian: jalankan langsung). */
    private fun runNewAuto(f: File) {
        if (nanoLine(f, true) == null && nanoLine(f, false) == null) { toast(R.string.drawer_not_visible); return }
        var proot = false
        if (host.runInNewSession { p -> proot = p; nanoLine(f, p) }) {
            if (proot) {
                host.showNanoKeys()
                host.autoRunNano()
            } else if (host.runLogEnabled()) {
                toast(R.string.drawer_run_log_note)
            }
            MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "run_nano", "tab" to "new", "auto" to if (proot) "1" else "0"))
            close()
        }
    }

    /** Baris untuk tab baru: Debian = cd ke folder berkas lalu nano; selain itu jalankan langsung. Null bila path tidak aman. */
    private fun nanoLine(f: File, proot: Boolean): String? {
        if (!proot) return runLine(f, false)
        val dir = f.parentFile ?: return null
        val p = DrawerFs.shellPath(f, true) ?: return null
        val d = DrawerFs.shellPath(dir, true) ?: return null
        if (DrawerFs.hasControlChars(p) || DrawerFs.hasControlChars(d)) return null
        return "cd " + DrawerFs.quote(d) + " && nano " + DrawerFs.quote(p) + "\r"
    }

    /** Dialog opsi Jalankan: sesi baru, sesi aktif (ikut path aktif), atau batal; kotak "Ingat pilihan" menyimpannya ke Setelan. */
    private fun runDialog(f: File) {
        val builder = AlertDialog.Builder(context)
        val ctx = builder.context
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        val textColor = if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
        val remember = CheckBox(ctx).apply { setText(R.string.drawer_run_remember) }
        var dlg: AlertDialog? = null
        fun row(label: Int, mode: RunMode, go: () -> Unit): TextView = TextView(ctx).apply {
            setText(label)
            textSize = 16f
            setTextColor(textColor)
            setPadding(dp(24), dp(14), dp(24), dp(14))
            isClickable = true
            val out = TypedValue()
            if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)) setBackgroundResource(out.resourceId)
            setOnClickListener {
                if (remember.isChecked) {
                    host.rememberRunMode(mode)
                    Toast.makeText(ctx, R.string.drawer_run_remembered, Toast.LENGTH_LONG).show()
                    MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "run_remember", "mode" to if (mode == RunMode.NEW) "new" else "active"))
                }
                dlg?.dismiss()
                go()
            }
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(row(R.string.drawer_run_new, RunMode.NEW) { runNewAuto(f) })
            addView(row(R.string.drawer_run_active, RunMode.ACTIVE) { runActiveAuto(f) })
            addView(remember, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(20), dp(8), dp(20), 0) })
        }
        dlg = builder
            .setTitle(context.getString(R.string.drawer_run_title, f.name))
            .setView(body)
            .setNegativeButton(R.string.drawer_cancel, null)
            .show()
    }

    /** Baris shell untuk berkas [f]; path folder dan berkas dikutip. Null bila tidak terlihat di sesi itu. */
    private fun runLine(f: File, proot: Boolean): String? {
        val dir = f.parentFile ?: return null
        val p = DrawerFs.shellPath(f, proot) ?: return null
        val d = DrawerFs.shellPath(dir, proot) ?: return null
        if (DrawerFs.hasControlChars(p) || DrawerFs.hasControlChars(d)) return null
        val log = if (host.runLogEnabled()) {
            val logDir = DrawerFs.shellPath(File(DrawerFs.INTERNAL_ROOT + "/Molina/run-logs"), proot) ?: return null
            RunLog(DrawerFs.quote(logDir), DrawerFs.quote(logDir + "/" + NewFileName.split(f.name).first))
        } else {
            null
        }
        return RunnableFile.shellLine(f.name, DrawerFs.quote(d), DrawerFs.quote(p), proot, log)
    }

    private fun runInActive(f: File) {
        if (shellTarget(f) == null) return
        val line = runLine(f, host.isProotSession())
        if (line == null) { toast(R.string.drawer_not_visible); return }
        host.send(line)
        MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "run"))
        if (host.runLogEnabled()) toast(R.string.drawer_run_log_note)
        close()
    }

    // ---------- operasi berkas ----------

    /** Jalankan [work] di IO. Hasil null = sukses; selain itu id string galat. Log hanya memuat nama operasi dan kelas galat, tanpa path. */
    private fun io(op: String, work: () -> Int?, done: () -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    Pair<Int?, String>(work(), "")
                } catch (e: Exception) {
                    Pair<Int?, String>(R.string.drawer_err_generic, e.javaClass.simpleName)
                }
            }
            val err = r.first
            if (err == null) {
                MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to op))
                done()
            } else {
                MolinaEvents.bug("term", "drawer.op-failed", "operasi drawer gagal", fields = mapOf("op" to op, "err" to r.second.ifEmpty { "rejected" }))
                toast(err)
            }
        }
    }

    private fun renameDialog(f: File, isDir: Boolean) {
        val stemEnd = if (isDir) f.name.length else f.name.lastIndexOf('.').let { if (it > 0) it else f.name.length }
        nameDialog(
            if (isDir) R.string.drawer_rename_dir_title else R.string.drawer_rename_file_title,
            f.name, stemEnd, R.string.drawer_ok, null, null, null,
        ) { name ->
            io("rename", {
                val dest = File(f.parentFile, name)
                when {
                    !DrawerFs.validName(name) -> R.string.drawer_err_name
                    dest.exists() -> R.string.drawer_err_exists
                    !f.renameTo(dest) -> R.string.drawer_err_generic
                    else -> null
                }
            }) { toast(R.string.drawer_done_rename); afterChange(f.parentFile, f.path) }
        }
    }

    private fun newDirDialog(parent: File) {
        val initial = context.getString(R.string.drawer_new_dir)
        nameDialog(R.string.drawer_new_dir, initial, initial.length, R.string.drawer_create, context.getString(R.string.drawer_in_folder, parent.path), null, null) { name ->
            io("mkdir", {
                val d = File(parent, name)
                when {
                    !DrawerFs.validName(name) -> R.string.drawer_err_name
                    d.exists() -> R.string.drawer_err_exists
                    !d.mkdir() -> R.string.drawer_err_generic
                    else -> null
                }
            }) {
                if (parent.path != currentRoot().path) state.expanded.add(parent.path)
                toast(R.string.drawer_done_dir)
                load(parent)
            }
        }
    }

    private fun newFileDialog(parent: File) {
        fun create(name: String, openAfter: Boolean) {
            val target = File(parent, name)
            io("mkfile", {
                when {
                    !DrawerFs.validName(name) -> R.string.drawer_err_name
                    target.exists() -> R.string.drawer_err_exists
                    !target.createNewFile() -> R.string.drawer_err_generic
                    else -> null
                }
            }) {
                if (parent.path != currentRoot().path) state.expanded.add(parent.path)
                toast(R.string.drawer_done_file)
                load(parent)
                if (openAfter && editInNano(target)) close()
            }
        }
        showNewFileDialog(
            context,
            DrawerFs.shellPath(parent, host.isProotSession()) ?: parent.path,
            onNano = { name -> create(name, true) },
        )
    }

    private fun deleteDialog(f: File) {
        AlertDialog.Builder(context)
            .setTitle(R.string.drawer_delete_title)
            .setMessage(context.getString(R.string.drawer_delete_body, f.name))
            .setNegativeButton(R.string.drawer_cancel, null)
            .setPositiveButton(R.string.drawer_delete) { _, _ ->
                io("delete", {
                    when {
                        DrawerFs.isProtected(f, vols) -> R.string.drawer_err_protected
                        DrawerFs.deleteSafe(f) -> null
                        else -> R.string.drawer_err_generic
                    }
                }) { toast(R.string.drawer_done_delete); afterChange(f.parentFile, f.path) }
            }
            .show()
    }

    private fun copyPath(f: File) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Molina path", f.path))
        toast(R.string.drawer_path_copied)
    }

    private fun showInfo(f: File, isDir: Boolean) {
        val shell = DrawerFs.shellPath(f, host.isProotSession()) ?: context.getString(R.string.drawer_info_not_visible)
        scope.launch {
            val body = withContext(Dispatchers.IO) {
                val size = if (isDir) context.getString(R.string.drawer_info_items, f.list()?.size ?: 0) else Formatter.formatFileSize(context, f.length())
                val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(f.lastModified()))
                context.getString(
                    R.string.drawer_info_body,
                    f.name,
                    context.getString(if (isDir) R.string.drawer_kind_dir else R.string.drawer_kind_file),
                    f.path, size, whenText, shell,
                    context.getString(if (f.canWrite()) R.string.drawer_yes else R.string.drawer_no),
                )
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.drawer_info)
                .setMessage(body)
                .setPositiveButton(R.string.drawer_ok, null)
                .show()
        }
    }

    private fun nameDialog(
        titleRes: Int,
        initial: String,
        selectEnd: Int,
        okRes: Int,
        message: String?,
        neutralRes: Int?,
        onNeutral: ((String) -> Unit)?,
        onOk: (String) -> Unit,
    ) {
        val input = EditText(context)
        input.setText(initial)
        input.setSingleLine()
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.setSelection(0, selectEnd.coerceIn(0, initial.length))
        val box = FrameLayout(context)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
        val b = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(box)
            .setNegativeButton(R.string.drawer_cancel, null)
            .setPositiveButton(okRes) { _, _ -> onOk(input.text.toString().trim()) }
        if (message != null) b.setMessage(message)
        if (neutralRes != null && onNeutral != null) b.setNeutralButton(neutralRes) { _, _ -> onNeutral(input.text.toString().trim()) }
        val dialog = b.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val ROW_DP = 36
        const val ROW_EMBEDDED_DP = 44 // molina-27t
        const val MIN_WIDTH_DP = 248
        const val PREFS = "molina_filemanager" // molina-27g2
        const val KEY_SORT = "sort"
        const val FLASH_MS = 1200L
        const val SEARCH_DEBOUNCE_MS = 250L // molina-27g3
        const val MATCH_ALPHA = 0x22 // molina-27g3: sama dengan seleksi, bilah klip, dan kilatan
        const val MAX_WIDTH_DP = 288
        const val FRACTION = 0.40f
        const val ID_CD = 1
        const val ID_ROOT = 2
        const val ID_CD_ROOT = 3
        const val ID_NEW_DIR = 3
        const val ID_NEW_FILE = 4
        const val ID_NANO = 5
        const val ID_RENAME = 6
        const val ID_COPY = 7
        const val ID_INFO = 8
        const val ID_DELETE = 9
        const val ID_RUN = 10
        const val ID_REFRESH = 20
        const val ID_HEAD_NEW_DIR = 21
        const val ID_HEAD_NEW_FILE = 22
    }
}

/** Panel drawer: sudut kanan membulat, bingkai hijau transparan di atas/kanan/bawah (tepi kiri tanpa garis), geser kiri = tutup. */
private class DrawerPanel(
    context: Context,
    fill: Int,
    private val frame: Int,
    private val radiusPx: Int,
    private val strokePx: Int,
    private val onSwipeClose: () -> Unit,
) : LinearLayout(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private val fling = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityX < -SWIPE_VX && abs(velocityX) > abs(velocityY) * 2f) {
                onSwipeClose()
                return true
            }
            return false
        }
    })

    init {
        setBackgroundColor(fill)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokePx.toFloat()
        paint.color = frame
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(-radiusPx, 0, view.width, view.height, radiusPx.toFloat())
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        fling.onTouchEvent(ev)
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val w = strokePx.toFloat()
        val r = radiusPx.toFloat()
        val y1 = w / 2f
        val y2 = height - w / 2f
        val x2 = width - w / 2f
        path.reset()
        path.moveTo(0f, y1)
        path.lineTo(x2 - r, y1)
        oval.set(x2 - 2 * r, y1, x2, y1 + 2 * r)
        path.arcTo(oval, -90f, 90f, false)
        path.lineTo(x2, y2 - r)
        oval.set(x2 - 2 * r, y2 - 2 * r, x2, y2)
        path.arcTo(oval, 0f, 90f, false)
        path.lineTo(0f, y2)
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val SWIPE_VX = 900f
    }
}
