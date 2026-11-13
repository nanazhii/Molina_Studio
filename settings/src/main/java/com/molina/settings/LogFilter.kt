// molina-patch12
package com.molina.settings

/** Filter cepat Log Viewer menurut jenis peristiwa terstruktur (docs/log-contract.md). */
internal enum class EventFilter(val label: String, val marker: String?) {
    ALL("Semua", null),
    CRASH("CRASH", "[CRASH:"),
    BUG("BUG", "[BUG:"),
    BEHAV("BEHAV", "[BEHAV:"),
}

/** Satu baris Log Viewer. [head] = baris pembawa penanda; false = baris biasa atau lanjutan stack trace. */
internal class ViewLine(val text: String, val kind: EventFilter, val head: Boolean, val tag: String = "")

/** Logika murni (tanpa Android) agar bisa diuji di JVM. */
internal object LogFilter {
    // threadtime: 11-13 09:12:44.101  1234  1234 E Molina/term: pesan  (tag bisa diberi spasi kanan)
    private val THREADTIME =
        Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+(\d+) [VDIWEF] (.*?): (.*)$""")
    private val STACK =
        Regex("""^\s*(at |Caused by: |Suppressed: |\.\.\. \d+ |[\w.$]+(Exception|Error|Throwable)\b)""")

    /**
     * Beri jenis tiap baris. Penanda hanya dikenali pada tag Molina/. Baris stack trace yang
     * mengikuti CRASH/BUG (pid, tid, tag sama) ikut jenis induknya, sehingga filter tidak
     * memotong jejak.
     */
    fun classify(lines: Iterable<String>): List<ViewLine> {
        val out = ArrayList<ViewLine>()
        var lastKey: String? = null
        var lastKind = EventFilter.ALL
        for (line in lines) {
            val m = THREADTIME.matchEntire(line)
            if (m == null) {
                lastKey = null
                lastKind = EventFilter.ALL
                out.add(ViewLine(line, EventFilter.ALL, false))
                continue
            }
            val tag = m.groupValues[3].trim()
            val key = m.groupValues[1] + " " + m.groupValues[2] + " " + tag
            val msg = m.groupValues[4]
            val own = if (tag.startsWith("Molina/")) markerKind(msg) else null
            val carry = own == null && key == lastKey &&
                (lastKind == EventFilter.CRASH || lastKind == EventFilter.BUG) && STACK.containsMatchIn(msg)
            val kind = own ?: if (carry) lastKind else EventFilter.ALL
            out.add(ViewLine(line, kind, own != null, tag))
            lastKey = key
            lastKind = kind
        }
        return out
    }

    private fun markerKind(msg: String): EventFilter? =
        EventFilter.values().firstOrNull { it.marker != null && msg.startsWith(it.marker) }

    fun select(lines: List<ViewLine>, filter: EventFilter, onlyMolina: Boolean, session: String? = null): List<ViewLine> =
        lines.filter {
            (filter == EventFilter.ALL || it.kind == filter) &&
                (!onlyMolina || it.text.contains(" Molina/")) &&
                (session == null || sessionOf(it.tag) == session)
        }

    /** Id sesi dari tag Molina/<area>/<id> (mis. "t1"); null untuk tag tanpa id atau bukan Molina. */
    fun sessionOf(tag: String): String? {
        val p = tag.trim().split('/')
        return if (p.size >= 3 && p[0] == "Molina") p[2].ifEmpty { null } else null
    }

    /** Id sesi yang terlihat di buffer, urut kemunculan pertama; [max] terakhir saja. */
    fun sessions(lines: List<ViewLine>, max: Int = 8): List<String> {
        val seen = LinkedHashSet<String>()
        for (v in lines) sessionOf(v.tag)?.let { seen.add(it) }
        return seen.toList().takeLast(max)
    }

    /** Teks untuk ditempel ke chat: baris build, keterangan filter, lalu [max] baris terakhir. */
    fun copyText(
        buildLine: String,
        shown: List<ViewLine>,
        filter: EventFilter,
        onlyMolina: Boolean,
        session: String?,
        max: Int = 300,
    ): String {
        val tail = shown.takeLast(max)
        val sb = StringBuilder(buildLine).append('\n')
        sb.append("Tampilan: ").append(filter.label)
            .append(" | sesi=").append(session ?: "semua")
            .append(" | hanya-Molina=").append(if (onlyMolina) "ya" else "tidak")
            .append(" | ").append(tail.size).append(" baris").append('\n')
        if (shown.size > tail.size) {
            sb.append('(').append(shown.size - tail.size).append(" baris lebih lama dipangkas)\n")
        }
        sb.append('\n')
        for (v in tail) sb.append(v.text).append('\n')
        return sb.toString()
    }

    /** Jumlah peristiwa (baris pembawa penanda) per jenis. */
    fun counts(lines: List<ViewLine>): Map<EventFilter, Int> {
        val out = LinkedHashMap<EventFilter, Int>()
        for (f in EventFilter.values()) if (f != EventFilter.ALL) out[f] = 0
        for (v in lines) if (v.head) out[v.kind] = (out[v.kind] ?: 0) + 1
        return out
    }
}
