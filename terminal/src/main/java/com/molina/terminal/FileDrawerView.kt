// molina-patch19
package com.molina.terminal

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
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.ui.R as UiR
import com.molina.log.MolinaEvents
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.ShellPrompt
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
internal class FileDrawerView(
    context: Context,
    private val state: FileDrawerState,
    private val scope: CoroutineScope,
    private val host: Host,
) : FrameLayout(context) {

    interface Host {
        fun send(text: String)
        fun hasSession(): Boolean
        fun isProotSession(): Boolean
        fun isFullScreenProgram(): Boolean
        fun runInNewSession(line: (proot: Boolean) -> String?): Boolean
        /** Pilih tab Debian yang folder aktifnya [guestDir]; false bila tidak ada. */
        fun activateSessionAt(guestDir: String): Boolean
        /** Buka baris tombol nano (drop-up) agar tombol RUN terlihat. */
        fun showNanoKeys()
        /** [RUN-AUTO] Tunggu nano terbuka di sesi aktif, lalu kirim RUN (Alt+R) otomatis. */
        fun autoRunNano()
        fun runMode(): RunMode
        fun runLogEnabled(): Boolean
        fun onRootChanged(path: String)
        fun onOpenChanged(open: Boolean)
    }

    private val white = Color.WHITE
    private val dim = ColorUtils.setAlphaComponent(Color.WHITE, 0x99)
    private val hairline = ColorUtils.setAlphaComponent(Color.WHITE, 0x4D)
    private val accent = ContextCompat.getColor(context, UiR.color.molina_terminal_accent)
    private val danger = ContextCompat.getColor(context, UiR.color.molina_key_red)
    private var vols: List<File> = listOf(File(DrawerFs.INTERNAL_ROOT))

    private val panel = DrawerPanel(
        context,
        ContextCompat.getColor(context, UiR.color.molina_surface),
        ColorUtils.setAlphaComponent(ContextCompat.getColor(context, UiR.color.molina_key_green), 0x99),
        dp(16),
        dp(1),
    ) { close() }
    private val back = TextView(context)
    private val title = TextView(context)
    private val pathText = TextView(context)
    private val chips = LinearLayout(context)
    private val chipScroll = HorizontalScrollView(context)
    private val empty = TextView(context)
    private val list = RecyclerView(context)
    private val adapter = RowAdapter()

    init {
        visibility = View.GONE
        val scrim = View(context)
        scrim.setBackgroundColor(Color.argb(0x8C, 0, 0, 0))
        scrim.setOnClickListener { close() }
        addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        buildPanel()
        addView(panel, FrameLayout.LayoutParams(dp(MIN_WIDTH_DP), MATCH))
    }

    val isOpen: Boolean get() = visibility == View.VISIBLE

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
        if (!isOpen) return
        visibility = View.GONE
        host.onOpenChanged(false)
    }

    // ---------- susunan tampilan ----------

    private fun buildPanel() {
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(2), dp(2), dp(2), dp(2))
        val headerFrame = GradientDrawable()
        headerFrame.setColor(Color.TRANSPARENT)
        headerFrame.cornerRadius = dp(8).toFloat()
        headerFrame.setStroke(dp(2), ContextCompat.getColor(context, UiR.color.molina_key_green))
        header.background = headerFrame

        back.text = "\u2039"
        back.textSize = 26f
        back.gravity = Gravity.CENTER
        back.setTextColor(white)
        back.contentDescription = context.getString(R.string.drawer_up)
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setColor(Color.TRANSPARENT)
        ring.setStroke(dp(1), dim)
        back.background = ring
        back.setOnClickListener { currentRoot().parentFile?.let { setRoot(it) } }
        header.addView(back, LinearLayout.LayoutParams(dp(36), dp(36)))

        // [HEADER-FILL] kotak path melebar (bobot 1) sampai tombol titik tiga
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(10), dp(2), dp(10), dp(2))
        val boxBg = GradientDrawable()
        boxBg.setColor(ContextCompat.getColor(context, UiR.color.molina_bg))
        boxBg.cornerRadius = dp(4).toFloat()
        boxBg.setStroke(dp(1), ContextCompat.getColor(context, UiR.color.molina_outline))
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
        val boxLp = LinearLayout.LayoutParams(0, dp(40), 1f)
        boxLp.marginStart = dp(4)
        boxLp.marginEnd = dp(4)
        header.addView(box, boxLp)

        val headerMenu = HeaderDotsView(context)
        headerMenu.contentDescription = context.getString(R.string.drawer_more)
        headerMenu.setOnClickListener { showHeaderMenu(it) }
        header.addView(headerMenu, LinearLayout.LayoutParams(dp(40), dp(40)))

        val headerLp = LinearLayout.LayoutParams(MATCH, WRAP)
        headerLp.setMargins(dp(6), dp(6), dp(6), dp(4))
        panel.addView(header, headerLp)

        chips.orientation = LinearLayout.HORIZONTAL
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.setPadding(dp(8), dp(2), dp(8), dp(2))
        chipScroll.visibility = View.GONE
        chipScroll.addView(chips)
        panel.addView(chipScroll, LinearLayout.LayoutParams(MATCH, WRAP))

        panel.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)))

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
        panel.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))

        panel.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)))
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(barButton(true, R.string.drawer_new_dir) { newDirDialog(currentRoot()) }, LinearLayout.LayoutParams(0, MATCH, 1f))
        val sep = View(context)
        sep.setBackgroundColor(hairline)
        bar.addView(sep, LinearLayout.LayoutParams(dp(1), dp(20)))
        bar.addView(barButton(false, R.string.drawer_new_file) { newFileDialog(currentRoot()) }, LinearLayout.LayoutParams(0, MATCH, 1f))
        panel.addView(bar, LinearLayout.LayoutParams(MATCH, dp(40)))
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
        state.root = dir
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
        state.expanded.removeAll { it == oldPath || it.startsWith("$oldPath/") }
        state.children.keys.filter { it == oldPath || it.startsWith("$oldPath/") }.forEach { state.children.remove(it) }
        if (parent != null) load(parent) else rebuild()
    }

    private fun rebuild() {
        val root = currentRoot()
        val rows = DrawerFs.flatten(state, root)
        adapter.submitList(rows)
        title.text = root.name.ifEmpty { "/" }
        pathText.text = DrawerFs.displayPath(root.path)
        host.onRootChanged(root.path)
        val canUp = root.parentFile != null && vols.none { it.path == root.path }
        back.isEnabled = canUp
        back.alpha = if (canUp) 1f else 0.38f
        empty.visibility = if (rows.isEmpty() && state.children[root.path] != null) View.VISIBLE else View.GONE
        renderChips(root)
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
        if (row.entry.isDir) {
            if (row.expanded) {
                state.expanded.remove(f.path)
                rebuild()
            } else {
                state.expanded.add(f.path)
                if (state.children[f.path] == null) load(f) else rebuild()
            }
        } else if (editInNano(f)) {
            close()
        }
    }

    private class RowHolder(
        val row: LinearLayout,
        val arrow: TextView,
        val icon: TreeIconView,
        val name: TextView,
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
            row.layoutParams = RecyclerView.LayoutParams(MATCH, dp(ROW_DP))
            row.isClickable = true
            row.background = RippleDrawable(ripple, null, ColorDrawable(Color.WHITE))

            val arrow = TextView(ctx)
            arrow.setTextColor(white)
            arrow.textSize = 12f
            arrow.gravity = Gravity.CENTER
            row.addView(arrow, LinearLayout.LayoutParams(dp(14), WRAP))

            val icon = TreeIconView(ctx)
            val iconLp = LinearLayout.LayoutParams(dp(20), dp(20))
            iconLp.marginStart = dp(4)
            iconLp.marginEnd = dp(8)
            row.addView(icon, iconLp)

            val name = TextView(ctx)
            name.setTextColor(white)
            name.textSize = 13f
            name.typeface = Typeface.MONOSPACE
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            row.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))

            val more = TextView(ctx)
            more.text = "\u22EE"
            more.textSize = 20f
            more.setTextColor(white)
            more.gravity = Gravity.CENTER
            more.contentDescription = ctx.getString(R.string.drawer_more)
            more.background = RippleDrawable(ripple, null, ColorDrawable(Color.WHITE))
            row.addView(more, LinearLayout.LayoutParams(dp(40), MATCH))
            return RowHolder(row, arrow, icon, name, more)
        }

        override fun onBindViewHolder(h: RowHolder, position: Int) {
            val item = getItem(position)
            val e = item.entry
            h.row.setPadding(dp(6 + item.depth * 14), 0, 0, 0)
            h.row.alpha = if (e.hidden) 0.6f else 1f
            h.arrow.text = if (e.isDir) (if (item.expanded) "\u25BE" else "\u25B8") else ""
            h.icon.isDir = e.isDir
            h.name.text = e.name
            h.row.setOnClickListener { onRowTap(item) }
            h.more.setOnClickListener { showRowMenu(it, e) }
        }
    }

    // ---------- menu opsi (titik tiga) ----------

    private fun showRowMenu(anchor: View, e: DrawerEntry) {
        val f = e.file
        val menu = PopupMenu(context, anchor)
        val m = menu.menu
        if (e.isDir) {
            m.add(0, ID_CD_ROOT, 0, context.getString(R.string.drawer_cd_root))
            m.add(0, ID_NEW_DIR, 2, context.getString(R.string.drawer_new_dir_here))
            m.add(0, ID_NEW_FILE, 3, context.getString(R.string.drawer_new_file_here))
        } else {
            m.add(0, ID_NANO, 0, context.getString(R.string.drawer_nano))
            if (RunnableFile.interpreter(e.name) != null) m.add(0, ID_RUN, 1, context.getString(R.string.drawer_run))
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
                ID_NEW_DIR -> newDirDialog(f)
                ID_NEW_FILE -> newFileDialog(f)
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
        if (host.isProotSession()) {
            // [CWD-SILENT] sesi Debian: hook prompt yang pindah folder, jadi baris "cd '...'" tidak tampil
            val rootfs = DebianLayout(context.applicationContext.filesDir).rootfsDir
            scope.launch {
                val silent = withContext(Dispatchers.IO) { ShellPrompt.requestCd(rootfs, p) }
                host.send(if (silent) ShellPrompt.CD_KEYS else "cd " + DrawerFs.quote(p) + "\r")
                MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "cd", "mode" to if (silent) "silent" else "typed"))
            }
            return true
        }
        host.send("cd " + DrawerFs.quote(p) + "\r")
        MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "cd", "mode" to "typed"))
        return true
    }

    private fun editInNano(f: File): Boolean {
        val p = shellTarget(f) ?: return false
        host.send("nano " + DrawerFs.quote(p) + "\r")
        MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "nano"))
        return true
    }

    /** Tombol Jalankan: selalu tanya dulu (sesi baru / sesi aktif / batal). */ // molina-patch30
    private fun runFlow(f: File) = runDialog(f)

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

    /** Dialog opsi Jalankan: sesi baru, sesi aktif (ikut path aktif), atau batal. */
    private fun runDialog(f: File) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.drawer_run_title, f.name))
            .setItems(
                arrayOf(context.getString(R.string.drawer_run_new), context.getString(R.string.drawer_run_active)),
            ) { _, which -> if (which == 0) runNewAuto(f) else runActiveAuto(f) }
            .setNegativeButton(R.string.terminal_tab_cancel, null)
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
            .setNegativeButton(R.string.terminal_tab_cancel, null)
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
            .setNegativeButton(R.string.terminal_tab_cancel, null)
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
        const val MIN_WIDTH_DP = 248
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

/** Ikon kecil: folder (kuning) atau berkas (garis putih). Digambar manual agar tanpa aset tambahan. */
internal class TreeIconView(context: Context) : View(context) {
    var isDir: Boolean = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    init {
        fill.style = Paint.Style.FILL
        fill.color = 0xFFFFC107.toInt()
        line.style = Paint.Style.STROKE
        line.color = Color.WHITE
        line.strokeWidth = 1.5f * resources.displayMetrics.density
        line.strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h * 0.1f
        if (isDir) {
            rect.set(0f, h * 0.18f, w * 0.45f, h * 0.5f)
            canvas.drawRoundRect(rect, r, r, fill)
            rect.set(0f, h * 0.3f, w, h * 0.88f)
            canvas.drawRoundRect(rect, r, r, fill)
        } else {
            path.reset()
            path.moveTo(w * 0.2f, h * 0.08f)
            path.lineTo(w * 0.62f, h * 0.08f)
            path.lineTo(w * 0.82f, h * 0.28f)
            path.lineTo(w * 0.82f, h * 0.92f)
            path.lineTo(w * 0.2f, h * 0.92f)
            path.close()
            canvas.drawPath(path, line)
            path.reset()
            path.moveTo(w * 0.62f, h * 0.08f)
            path.lineTo(w * 0.62f, h * 0.28f)
            path.lineTo(w * 0.82f, h * 0.28f)
            canvas.drawPath(path, line)
        }
    }
}
