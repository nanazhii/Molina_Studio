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
internal class ViewLine(val text: String, val kind: EventFilter, val head: Boolean)

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
            out.add(ViewLine(line, kind, own != null))
            lastKey = key
            lastKind = kind
        }
        return out
    }

    private fun markerKind(msg: String): EventFilter? =
        EventFilter.values().firstOrNull { it.marker != null && msg.startsWith(it.marker) }

    fun select(lines: List<ViewLine>, filter: EventFilter, onlyMolina: Boolean): List<ViewLine> =
        lines.filter {
            (filter == EventFilter.ALL || it.kind == filter) &&
                (!onlyMolina || it.text.contains(" Molina/"))
        }

    /** Jumlah peristiwa (baris pembawa penanda) per jenis. */
    fun counts(lines: List<ViewLine>): Map<EventFilter, Int> {
        val out = LinkedHashMap<EventFilter, Int>()
        for (f in EventFilter.values()) if (f != EventFilter.ALL) out[f] = 0
        for (v in lines) if (v.head) out[v.kind] = (out[v.kind] ?: 0) + 1
        return out
    }
}
