// molina-patch12
package com.molina.settings

import com.molina.log.EventKind
import com.molina.log.LogEvent
import com.molina.log.LogEvents

/** Menyusun laporan masalah dari isi molina.log. Logika murni, tanpa dependensi Android. */
internal object LogReport {
    private val HEADER = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} ([DIWE])/(\S+): (.*)$""")
    private val EXCEPTION = Regex("""^(Caused by: )?[\w.$]+(Exception|Error|Throwable)\b.*""")

    /** Satu entri log: baris judul ditambah baris lanjutan (mis. stack trace). */
    class Entry(
        val level: Char,
        val tag: String,
        val head: String,
        val message: String,
        val extra: List<String>,
    ) {
        /** Peristiwa terstruktur menurut docs/log-contract.md; null untuk baris log biasa. */
        val event: LogEvent? by lazy(LazyThreadSafetyMode.NONE) { LogEvents.parse(message) }

        /** Jam entri (HH:mm:ss.SSS) dari baris judul; kosong bila bentuknya tak dikenali. */
        val time: String get() = if (head.length >= 23) head.substring(11, 23) else ""
    }

    /** Baris sebelum header pertama (potongan awal hasil baca ekor berkas) dibuang. */
    fun parse(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        var level = ' '
        var tag = ""
        var head: String? = null
        var message = ""
        var extra = ArrayList<String>()
        fun flush() {
            head?.let { out.add(Entry(level, tag, it, message, extra)) }
        }
        for (line in text.lineSequence()) {
            val m = HEADER.matchEntire(line)
            if (m != null) {
                flush()
                level = m.groupValues[1][0]
                tag = m.groupValues[2]
                message = m.groupValues[3]
                head = line
                extra = ArrayList()
            } else if (head != null && line.isNotEmpty()) {
                extra.add(line)
            }
        }
        flush()
        return out
    }

    fun area(tag: String): String = tag.split('/').getOrNull(1) ?: tag

    /**
     * Urutan laporan: info build, Crash terbaru, Bug, Urutan perilaku sebelum crash,
     * Error terbaru per area, Log terakhir. Hanya "Log terakhir" yang dipangkas oleh [maxChars];
     * bagian peristiwa dibatasi oleh [maxCrashes], [maxBugs], [maxBehaviors] (ukurannya kecil).
     */
    fun build(
        buildLine: String,
        entries: List<Entry>,
        last: Int = 50,
        maxStackLines: Int = 12,
        maxChars: Int = 60_000,
        maxCrashes: Int = 3,
        maxBugs: Int = 10,
        maxBehaviors: Int = 15,
    ): String {
        val sb = StringBuilder(buildLine).append('\n')
        appendCrashes(sb, entries, maxCrashes, maxStackLines)
        appendBugs(sb, entries, maxBugs)
        appendBehaviors(sb, entries, maxBehaviors)

        val errors = entries.filter { it.level == 'E' }
        if (errors.isNotEmpty()) {
            sb.append("\nError terbaru per area:\n")
            for ((area, list) in errors.groupBy { area(it.tag) }) {
                val latest = list.last()
                val exception = firstException(latest)
                sb.append("- ").append(area).append(": ").append(list.size)
                    .append(" error; terakhir: ").append(latest.message.take(160))
                if (exception != null) sb.append(" | ").append(exception.take(160))
                sb.append('\n')
            }
        }

        val blocks = entries.takeLast(last).map { blockOf(it, maxStackLines) }
        var budget = maxChars - sb.length - 64
        var from = blocks.size
        while (from > 0 && budget - blocks[from - 1].length >= 0) {
            from--
            budget -= blocks[from].length
        }
        val shown = blocks.subList(from, blocks.size)
        sb.append("\nLog terakhir (").append(shown.size).append(" entri):\n")
        shown.forEach { sb.append(it) }
        return sb.toString()
    }

    private fun firstException(e: Entry): String? =
        e.extra.firstOrNull { EXCEPTION.matches(it.trim()) }?.trim()

    private fun blockOf(e: Entry, maxStackLines: Int): String {
        val b = StringBuilder(e.head).append('\n')
        e.extra.take(maxStackLines).forEach { b.append(it).append('\n') }
        if (e.extra.size > maxStackLines) {
            b.append("\t... (").append(e.extra.size - maxStackLines).append(" baris lagi)\n")
        }
        return b.toString()
    }

    /** "kode — ringkasan | kunci=nilai", satu baris, dipangkas. */
    private fun describe(ev: LogEvent): String {
        val sb = StringBuilder(ev.code)
        if (ev.summary.isNotEmpty()) sb.append(" — ").append(ev.summary)
        if (ev.fields.isNotEmpty()) {
            sb.append(" | ").append(ev.fields.entries.joinToString(" ") { it.key + "=" + it.value })
        }
        return sb.toString().take(240)
    }

    private fun appendCrashes(sb: StringBuilder, entries: List<Entry>, max: Int, maxStack: Int) {
        val crashes = entries.filter { it.event?.kind == EventKind.CRASH }
        if (crashes.isEmpty()) return
        val shown = crashes.takeLast(max).asReversed()
        sb.append("\nCrash terbaru (").append(shown.size).append(" dari ").append(crashes.size).append("):\n")
        shown.forEach { sb.append(blockOf(it, maxStack)) }
    }

    private fun appendBugs(sb: StringBuilder, entries: List<Entry>, max: Int) {
        val bugs = entries.filter { it.event?.kind == EventKind.BUG }
        if (bugs.isEmpty()) return
        // Kunci = kode bug, urut dari yang terakhir muncul; isi daftar = kejadian terbaru dulu.
        val byCode = LinkedHashMap<String, MutableList<Entry>>()
        for (e in bugs.asReversed()) byCode.getOrPut(e.event!!.code) { ArrayList() }.add(e)
        sb.append("\nBug (").append(bugs.size).append(" kejadian, ").append(byCode.size).append(" jenis):\n")
        for ((code, list) in byCode.entries.take(max)) {
            val latest = list.first()
            val ev = latest.event!!
            sb.append("- ").append(code).append(" (").append(list.size).append("x); terakhir ")
                .append(latest.time).append(": ")
            val body = StringBuilder(ev.summary)
            if (ev.fields.isNotEmpty()) {
                if (body.isNotEmpty()) body.append(' ')
                body.append("| ").append(ev.fields.entries.joinToString(" ") { it.key + "=" + it.value })
            }
            sb.append(body.toString().take(200))
            firstException(latest)?.let { sb.append(" | ").append(it.take(160)) }
            sb.append('\n')
        }
        if (byCode.size > max) sb.append("- ... (").append(byCode.size - max).append(" jenis lagi)\n")
    }

    private fun appendBehaviors(sb: StringBuilder, entries: List<Entry>, max: Int) {
        val crashAt = entries.indexOfLast { it.event?.kind == EventKind.CRASH }
        val upto = if (crashAt >= 0) crashAt else entries.size
        val seq = entries.subList(0, upto).filter {
            val k = it.event?.kind
            k == EventKind.BEHAVIOR || k == EventKind.BUG
        }
        if (crashAt < 0 && seq.isEmpty()) return
        val shown = seq.takeLast(max)
        if (crashAt >= 0) {
            val crash = entries[crashAt]
            sb.append("\nUrutan perilaku sebelum crash (").append(shown.size).append(" peristiwa terakhir):\n")
            if (shown.isEmpty()) sb.append("  (tidak ada peristiwa BEHAV/BUG tercatat sebelum crash)\n")
            shown.forEach { appendBehaviorLine(sb, it) }
            sb.append("  ").append(crash.time).append(" CRASH ").append(crash.event!!.code)
                .append("  <-- proses berhenti\n")
        } else {
            sb.append("\nPerilaku terakhir (tanpa crash, ").append(shown.size).append(" peristiwa):\n")
            shown.forEach { appendBehaviorLine(sb, it) }
        }
    }

    private fun appendBehaviorLine(sb: StringBuilder, e: Entry) {
        val ev = e.event ?: return
        sb.append("  ").append(e.time).append(' ').append(ev.kind.label).append(' ')
            .append(describe(ev)).append('\n')
    }
}
