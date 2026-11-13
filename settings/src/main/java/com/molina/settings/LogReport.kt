package com.molina.settings

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
    )

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

    fun build(
        buildLine: String,
        entries: List<Entry>,
        last: Int = 50,
        maxStackLines: Int = 12,
        maxChars: Int = 60_000,
    ): String {
        val sb = StringBuilder(buildLine).append('\n')

        val errors = entries.filter { it.level == 'E' }
        if (errors.isNotEmpty()) {
            sb.append("\nError terbaru per area:\n")
            for ((area, list) in errors.groupBy { area(it.tag) }) {
                val latest = list.last()
                val exception = latest.extra.firstOrNull { EXCEPTION.matches(it.trim()) }?.trim()
                sb.append("- ").append(area).append(": ").append(list.size)
                    .append(" error; terakhir: ").append(latest.message.take(160))
                if (exception != null) sb.append(" | ").append(exception.take(160))
                sb.append('\n')
            }
        }

        val blocks = entries.takeLast(last).map { e ->
            val b = StringBuilder(e.head).append('\n')
            e.extra.take(maxStackLines).forEach { b.append(it).append('\n') }
            if (e.extra.size > maxStackLines) {
                b.append("\t... (").append(e.extra.size - maxStackLines).append(" baris lagi)\n")
            }
            b.toString()
        }
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
}
