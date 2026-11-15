#!/bin/bash
# Molina patch 27h+27i (v3): FAB menu terpusat + hamburger di dalam menu FAB, geser pilih pola repo lama. Jalankan dari dalam folder repo.
python3 << 'PYEOF'
import os, sys, subprocess

try:
    ROOT = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
except Exception:
    sys.exit("GAGAL: jalankan dari dalam folder repo Molina_Studio (git rev-parse gagal)")
if not os.path.isdir(os.path.join(ROOT, "filemanager")):
    sys.exit("GAGAL: %s bukan repo Molina_Studio (folder filemanager tidak ada)" % ROOT)
print("Repo:", ROOT)
FM = os.path.join(ROOT, "filemanager/src/main/java/com/molina/filemanager")
TEST = os.path.join(ROOT, "filemanager/src/test/java/com/molina/filemanager")
changed, skipped = 0, 0

def patch(path, old, new, label):
    global changed, skipped
    s = open(path, encoding="utf-8").read()
    if new in s:
        skipped += 1
        print("  = sudah ada :", label)
        return
    if s.count(old) != 1:
        sys.exit("GAGAL (anchor %d kali): %s di %s" % (s.count(old), label, path))
    open(path, "w", encoding="utf-8").write(s.replace(old, new))
    changed += 1
    print("  + diterapkan:", label)

FAB_FIELD = r'''    private val fab = FrameLayout(context) // molina-27h: tombol melayang pojok kanan bawah (menu terpusat)
    private val fabGlyph = TextView(context) // molina-27i
    private val fabBadge = TextView(context) // molina-27i: jumlah item terpilih
'''

HELPERS_HEAD = "    // ---------- [molina-27h] tombol melayang + geser pilih ----------\n"
E_MARK = "    // ---------- [molina-27e] pilih banyak dan Salin/Pindah/Tempel ----------\n"

HELPERS = HELPERS_HEAD + r'''
    /** [molina-27h] Tombol melayang pojok kanan bawah: satu-satunya pintu menu titik tiga mode tertanam. */
    private fun setupFab() {
        fabGlyph.text = "\u22EE"
        fabGlyph.textSize = 24f
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
        fabGlyph.setTextColor(if (picking) Color.BLACK else white)
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

'''

BURGER_START = "        if (embedded) { // molina-27i: hamburger Favorit + Internal/Debian/Sampah\n"
BURGER_END = "            header.addView(burger, LinearLayout.LayoutParams(dp(34), dp(34)))\n        }\n"

def migrate():
    """Versi skrip sebelumnya: fab TextView, ikon hamburger di header, menu navigasi terpisah -> bentuk terbaru."""
    global changed
    path = os.path.join(FM, "FileDrawerView.kt")
    t = open(path, encoding="utf-8").read()
    o = t
    v1 = "    private val fab = TextView(context) // molina-27h: tombol melayang pojok kanan bawah (menu terpusat)\n"
    if v1 in t:
        t = t.replace(v1, FAB_FIELD)
    a = t.find(HELPERS_HEAD)
    b = t.find(E_MARK)
    if 0 <= a < b and t[a:b] != HELPERS:
        t = t[:a] + HELPERS + t[b:]
    i = t.find(BURGER_START)
    if i >= 0:
        j = t.find(BURGER_END, i)
        if j > i:
            t = t[:i] + t[j + len(BURGER_END):]
    if t != o:
        open(path, "w", encoding="utf-8").write(t)
        changed += 1
        print("  ~ migrasi dari versi lama: hamburger header -> menu FAB, geser pola repo lama")

migrate()

# ---------- EmbeddedSelection ----------
p = os.path.join(FM, "EmbeddedSelection.kt")
patch(p,
r'''    fun contains(path: String): Boolean = path in items
''',
r'''    fun contains(path: String): Boolean = path in items

    /** [molina-27h] Geser kanan: pilih [path] (mode pilih otomatis aktif). */
    fun select(path: String) {
        active = true
        items.add(path)
    }

    /** [molina-27h] Geser kiri: batal pilih [path]. Mode pilih tetap aktif walau kosong. */
    fun deselect(path: String) {
        items.remove(path)
    }
''', "EmbeddedSelection.select/deselect")

# ---------- EmbeddedMenu ----------
p = os.path.join(FM, "EmbeddedMenu.kt")
patch(p,
r'''        l += Entry(Action.SELECT_ALL, "Pilih semua")
''',
r'''        l += Entry(Action.SELECT_ALL, "Pilih semua")
        if (s.targetCount == 1) { // molina-27h: satu item terpilih = isi menu baris lama
            l += Entry(Action.RENAME, "Ubah nama")
            if (s.favoriteAvailable) l += Entry(Action.FAVORITE, if (s.isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit")
            l += Entry(Action.COPY_PATH, "Salin path")
        }
''', "EmbeddedMenu.buildSelecting single")
patch(p,
r'''        val single = s.targetCount == 1 && !s.selecting
''',
r'''        val single = s.targetCount == 1 // molina-27h: satu arsip terpilih juga dapat Lihat isi/Uji
''', "EmbeddedMenu.archiveEntries single")
patch(p,
r'''FAVORITES_LIST, OPEN_TRASH,
    }''',
r'''FAVORITES_LIST, OPEN_TRASH,
        GO_INTERNAL, GO_DEBIAN, // molina-27i: kelompok hamburger di bawah menu titik tiga
    }''', "EmbeddedMenu enum GO_*")
patch(p,
r'''        if (s.global && s.hasActions) {
            l += Entry(Action.FAVORITES_LIST, "Daftar Favorit")
            l += Entry(Action.OPEN_TRASH, "Sampah")
        }
''',
r'''        if (s.global) { // molina-27i: kelompok hamburger di bawah (pola DROP-HAMBURGER repo lama): Favorit, Internal, Debian, Sampah
            if (s.hasActions) l += Entry(Action.FAVORITES_LIST, "\u2630 Favorit")
            l += Entry(Action.GO_INTERNAL, "\u2630 Internal")
            l += Entry(Action.GO_DEBIAN, "\u2630 Debian")
            if (s.hasActions) l += Entry(Action.OPEN_TRASH, "\u2630 Sampah")
        }
''', "EmbeddedMenu nav group")

# ---------- FileDrawerView ----------
p = os.path.join(FM, "FileDrawerView.kt")
patch(p,
r'''import androidx.recyclerview.widget.DiffUtil
''',
r'''import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
''', "import ItemTouchHelper")
patch(p,
r'''    private val adapter = RowAdapter()

    init {''',
r'''    private val adapter = RowAdapter()
''' + FAB_FIELD + r'''
    init {''', "field fab")
patch(p,
r'''        addView(panel, FrameLayout.LayoutParams(if (embedded) MATCH else dp(MIN_WIDTH_DP), MATCH))
    }
''',
r'''        addView(panel, FrameLayout.LayoutParams(if (embedded) MATCH else dp(MIN_WIDTH_DP), MATCH))
        if (embedded) setupFab() // molina-27h
    }
''', "init setupFab")
patch(p,
r'''        headerMenu.setOnClickListener { showHeaderMenu(it) }
''',
r'''        headerMenu.setOnClickListener { showHeaderMenu(it) }
        if (embedded) headerMenu.visibility = View.GONE // molina-27h: menu pindah ke tombol melayang
''', "header dots hidden")
patch(p,
r'''        list.overScrollMode = View.OVER_SCROLL_NEVER
''',
r'''        list.overScrollMode = View.OVER_SCROLL_NEVER
        if (embedded) setupEmbeddedList() // molina-27h
''', "list setup call")
patch(p,
r'''            h.more.visibility = if (picking) View.GONE else View.VISIBLE
''',
r'''            h.more.visibility = if (picking || embedded) View.GONE else View.VISIBLE // molina-27h: titik tiga baris dihapus di mode tertanam
''', "row dots hidden")
patch(p,
r'''        val dir = when {
            target == null -> base
            targetIsDir -> target
            else -> target.parentFile ?: base
        }
        val picking = selection.active && fromHeader
        val targets: List<File> = when {
            picking -> EmbeddedSelection.topLevel(selection.paths).map { File(it) }
            target != null -> listOf(target)
            else -> emptyList()
        }
        val favTarget = if (target == null) base else target.takeIf { targetIsDir }
''',
r'''        val picking = selection.active && fromHeader
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
''', "showCentralMenu tgt/dir/favTarget")
patch(p,
r'''                EmbeddedMenu.Action.RENAME -> target?.let { renameDialog(it, targetIsDir) }
''',
r'''                EmbeddedMenu.Action.RENAME -> tgt?.let { // molina-27h
                    if (picking) { selection.exit(); selectionChanged() } // path lama tidak boleh tersisa di pilihan
                    renameDialog(it, tgtIsDir)
                }
''', "action RENAME")
patch(p,
r'''                EmbeddedMenu.Action.COPY_PATH -> copyPath(target ?: base)
''',
r'''                EmbeddedMenu.Action.COPY_PATH -> copyPath(tgt ?: base) // molina-27h
''', "action COPY_PATH")
patch(p,
r'''showInfo(target ?: base, if (target == null) true else targetIsDir)
''',
r'''showInfo(tgt ?: base, if (tgt == null) true else tgtIsDir) // molina-27h
''', "action INFO fallback")
patch(p,
r'''                EmbeddedMenu.Action.OPEN_TRASH -> actions.openTrash()
''',
r'''                EmbeddedMenu.Action.OPEN_TRASH -> actions.openTrash()
                EmbeddedMenu.Action.GO_INTERNAL -> setRoot(File(DrawerFs.INTERNAL_ROOT)) // molina-27i
                EmbeddedMenu.Action.GO_DEBIAN -> openDebian() // molina-27i
''', "actions GO_INTERNAL/GO_DEBIAN")
patch(p,
r'''        menu.show()
    }

''' + E_MARK,
r'''        menu.gravity = Gravity.END // molina-27h: rata kanan, membuka ke atas dari tombol melayang
        menu.show()
    }

''' + HELPERS + E_MARK, "gravity + helper FAB/geser")
patch(p,
r'''        renderClip() // molina-27e
    }
''',
r'''        renderClip() // molina-27e
        if (embedded) updateFab() // molina-27i
    }
''', "rebuild updateFab")

# ---------- Tes JVM ----------
def make_test(name, body, label):
    global changed, skipped
    tp = os.path.join(TEST, name)
    if os.path.exists(tp):
        skipped += 1
        print("  = sudah ada :", label)
    else:
        open(tp, "w", encoding="utf-8").write(body)
        changed += 1
        print("  + dibuat    :", label)

make_test("Embedded27hTest.kt", r'''// molina-27h
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Embedded27hTest {
    private fun state(
        targetCount: Int,
        favoriteAvailable: Boolean = true,
        archiveAvailable: Boolean = false,
        allArchives: Boolean = false,
    ) = EmbeddedMenu.State(
        targetCount = targetCount, hasActions = true, favoriteAvailable = favoriteAvailable, isFavorite = false,
        trashAvailable = true, global = true, selecting = true, transferAvailable = true,
        archiveAvailable = archiveAvailable, allArchives = allArchives,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun selectAndDeselectKeepModeActive() {
        val s = EmbeddedSelection()
        s.select("/a")
        assertTrue(s.active && s.contains("/a"))
        s.select("/a")
        assertEquals(1, s.count)
        s.deselect("/a")
        assertTrue(s.active)
        assertEquals(0, s.count)
        s.deselect("/tidak-ada")
        assertEquals(0, s.count)
    }

    @Test
    fun singleSelectedItemGetsRowMenuActions() {
        val a = actions(state(1))
        assertTrue(Action.RENAME in a)
        assertTrue(Action.FAVORITE in a)
        assertTrue(Action.COPY_PATH in a)
    }

    @Test
    fun singleSelectedFileHasNoFavorite() {
        val a = actions(state(1, favoriteAvailable = false))
        assertTrue(Action.RENAME in a)
        assertFalse(Action.FAVORITE in a)
    }

    @Test
    fun multipleSelectedItemsKeepBulkOnlyMenu() {
        val a = actions(state(3))
        assertFalse(Action.RENAME in a)
        assertFalse(Action.FAVORITE in a)
        assertFalse(Action.COPY_PATH in a)
    }

    @Test
    fun singleSelectedArchiveOffersViewAndTest() {
        val a = actions(state(1, archiveAvailable = true, allArchives = true))
        assertTrue(Action.VIEW_ARCHIVE in a)
        assertTrue(Action.TEST_ARCHIVE in a)
        val many = actions(state(2, archiveAvailable = true, allArchives = true))
        assertFalse(Action.VIEW_ARCHIVE in many)
    }
}
''', "Embedded27hTest.kt")

make_test("Embedded27iTest.kt", r'''// molina-27i
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Embedded27iTest {
    private fun state(
        hasActions: Boolean = true,
        global: Boolean = true,
        selecting: Boolean = false,
        targetCount: Int = 0,
    ) = EmbeddedMenu.State(
        targetCount = targetCount, hasActions = hasActions, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, transferAvailable = true,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun headerMenuEndsWithHamburgerGroupInOrder() {
        val a = actions(state())
        assertEquals(listOf(Action.FAVORITES_LIST, Action.GO_INTERNAL, Action.GO_DEBIAN, Action.OPEN_TRASH), a.takeLast(4))
    }

    @Test
    fun locationsShownEvenWithoutActionsButNoFavoritesOrTrash() {
        val a = actions(state(hasActions = false))
        assertTrue(Action.GO_INTERNAL in a && Action.GO_DEBIAN in a)
        assertFalse(Action.FAVORITES_LIST in a || Action.OPEN_TRASH in a)
    }

    @Test
    fun rowAndSelectingMenusHaveNoHamburgerGroup() {
        val row = actions(state(global = false, targetCount = 1))
        assertFalse(Action.GO_INTERNAL in row || Action.GO_DEBIAN in row)
        val sel = actions(state(selecting = true, targetCount = 2))
        assertFalse(Action.GO_INTERNAL in sel || Action.GO_DEBIAN in sel)
    }
}
''', "Embedded27iTest.kt")

# ---------- progres.md ----------
pp = os.path.join(ROOT, "progres.md")
s = open(pp, encoding="utf-8").read() if os.path.exists(pp) else ""
add = []
if "[27h]" not in s:
    add.append("- [27h] Tab Berkas: menu titik tiga baris dan header dihapus; semua isi menu dipusatkan di satu tombol melayang (FAB ⋮) pojok kanan bawah (PopupMenu rata kanan). Geser baris ke kanan = pilih (mode pilih aktif otomatis), geser kiri = batal pilih; folder dan berkas. Satu item terpilih mendapat isi menu baris lama (Ubah nama, Favorit, Salin path, Lihat isi/Uji arsip); banyak item tetap menu massal. Tes JVM: Embedded27hTest. Mode Terminal tidak berubah. Menunggu hasil CI.")
if "[27i]" not in s:
    add.append("- [27i] Tab Berkas: FAB aksen penuh + lencana jumlah saat mode pilih. Hamburger digabung ke menu FAB (pola DROP-HAMBURGER repo lama): kelompok ☰ Favorit, Internal, Debian (filesDir/rootfs/debian/root), Sampah di bawah menu titik tiga. Geser baris mengikuti pola W7-SWIPE1 repo lama: baris hanya bergeser maks 28dp lalu kembali, aksi jalan bila lewat 64dp saat dilepas; baris belum terpilih hanya bisa geser kanan, baris terpilih hanya kiri (memperbaiki baris kosong merah setelah geser kiri). Tes JVM: Embedded27iTest. Menunggu hasil CI.")
if add:
    open(pp, "w", encoding="utf-8").write(s.rstrip("\n") + "\n" + "\n".join(add) + "\n")
    changed += 1
    print("  + diperbarui: progres.md (%d baris)" % len(add))
else:
    skipped += 1
    print("  = sudah ada : progres.md [27h][27i]")

print("Selesai: %d diterapkan, %d dilewati" % (changed, skipped))
PYEOF
