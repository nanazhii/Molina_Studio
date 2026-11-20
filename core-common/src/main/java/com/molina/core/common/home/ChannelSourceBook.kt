package com.molina.core.common.home

/** Tempat teks buku sumber disimpan (SharedPreferences di Android, memori di tes). */
interface ChannelSourceStore {
    fun read(): String
    fun write(text: String)
}

/**
 * Daftar sumber playlist saluran (maks. [max]): tautan http(s) atau jalur berkas lokal, plus sumber yang
 * terakhir dipakai. Murni tanpa Android. Label sumber tautan hanya host + nama berkas terakhir, tanpa query
 * dan tanpa token milik penyedia (URL penuh tidak pernah ditampilkan).
 */
class ChannelSourceBook(
    private val store: ChannelSourceStore,
    private val max: Int = MAX_SOURCES,
) {
    data class Source(val ref: String, val name: String)

    private data class State(val refs: List<String>, val last: String?)

    fun list(): List<Source> = state().refs.map { Source(it, nameOf(it)) }

    /** Sumber terakhir dipakai; null bila tidak ada atau sudah dihapus. */
    var last: String?
        get() = state().last
        set(value) {
            val s = state()
            save(State(s.refs, value?.takeIf { it in s.refs }))
        }

    /** Menambah sumber di depan (atau memindahkannya ke depan bila sudah ada) dan menjadikannya terakhir dipakai. */
    fun add(ref: String): Source {
        val r = ref.trim()
        require(r.isNotEmpty() && !r.startsWith("@") && r.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "sumber tidak valid"
        }
        val refs = (listOf(r) + state().refs.filter { it != r }).take(max)
        save(State(refs, r))
        return Source(r, nameOf(r))
    }

    fun remove(ref: String) {
        val s = state()
        val refs = s.refs.filter { it != ref }
        save(State(refs, s.last?.takeIf { it != ref && it in refs }))
    }

    private fun state(): State {
        var last: String? = null
        val refs = ArrayList<String>()
        for (raw in store.read().lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("@")) last = line.substring(1) else if (line !in refs) refs += line
        }
        return State(refs, last?.takeIf { it in refs })
    }

    private fun save(s: State) {
        val head = s.last?.let { "@$it\n" }.orEmpty()
        store.write(head + s.refs.joinToString("\n"))
    }

    companion object {
        const val MAX_SOURCES = 10
        private const val MAX_NAME = 40
        private const val DEFAULT_NAME = "Playlist"

        /** Label chip: nama berkas untuk jalur lokal; host + berkas terakhir untuk tautan. */
        fun nameOf(ref: String): String {
            if (ref.startsWith("/")) return ref.substringAfterLast('/').ifEmpty { DEFAULT_NAME }.take(MAX_NAME)
            val rest = ref.substringAfter("://", "")
            val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfterLast('@').removePrefix("www.")
            val path = rest.substringAfter('/', "").substringBefore('?').substringBefore('#').trimEnd('/')
            val file = path.substringAfterLast('/')
            return (host + if (file.isNotEmpty()) " / $file" else "").ifEmpty { DEFAULT_NAME }.take(MAX_NAME)
        }
    }
}
