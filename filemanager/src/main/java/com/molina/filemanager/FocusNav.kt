// molina-27o
package com.molina.filemanager

/**
 * [molina-27o] Navigasi header tab Berkas, murni (tanpa Android) agar bisa diuji JVM.
 * Semua path berupa String; [rootPath] = folder utama, [focusPath] = item terakhir yang diketuk (boleh null).
 */
internal object FocusNav {
    /** Fokus efektif: [focusPath] bila berada di dalam (atau sama dengan) root, selain itu root. */
    fun effective(rootPath: String, focusPath: String?): String {
        val f = focusPath ?: return rootPath
        return if (f == rootPath || f.startsWith("$rootPath/")) f else rootPath
    }

    /** Folder yang dipakai Folder baru / Tempel / Properti: fokus bila folder, jika berkas maka induknya. */
    fun activeDir(rootPath: String, focusPath: String?, isDir: (String) -> Boolean): String {
        val f = effective(rootPath, focusPath)
        if (f == rootPath || isDir(f)) return f
        val parent = f.substringBeforeLast('/', rootPath)
        return if (parent == rootPath || parent.startsWith("$rootPath/")) parent else rootPath
    }

    /**
     * Tujuan fokus setelah tombol \u2039 ditekan: induk dari item yang tampil di header.
     * Hasil [UP_AT_ROOT] = header sudah di root, pemanggil naik ke folder induk root.
     * Hasil [ROOT_FOCUS] = kembali ke root (fokus dikosongkan).
     */
    fun up(rootPath: String, focusPath: String?): String {
        val f = effective(rootPath, focusPath)
        if (f == rootPath) return UP_AT_ROOT
        val parent = f.substringBeforeLast('/', rootPath)
        return if (parent == rootPath || !parent.startsWith("$rootPath/")) ROOT_FOCUS else parent
    }

    const val UP_AT_ROOT = "\u0000root-up"
    const val ROOT_FOCUS = "\u0000root-focus"
}
