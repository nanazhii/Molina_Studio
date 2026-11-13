// molina-patch11
package com.molina.log

import java.util.Locale

/**
 * Kontrak peristiwa terstruktur. Tersimpan sebagai awalan pesan log biasa, sehingga baris yang
 * sama muncul di logcat, molina.log, Log Viewer, dan laporan masalah (1-1 dengan adb logcat).
 *
 *   [JENIS:kode] ringkasan | kunci=nilai kunci=nilai
 */
enum class EventKind(val label: String, val defaultLevel: LogLevel) {
    /** Exception tidak tertangani; proses akan mati. */
    CRASH("CRASH", LogLevel.ERROR),

    /** Kegagalan spesifik yang dikenali kode (mis. proot.install-failed). */
    BUG("BUG", LogLevel.ERROR),

    /** Perilaku/alur penting yang berhasil (mis. term.session.start); bukan kesalahan. */
    BEHAVIOR("BEHAV", LogLevel.INFO),
}

class LogEvent(
    val kind: EventKind,
    val code: String,
    val summary: String,
    val fields: Map<String, String>,
)

object LogEvents {
    private const val MAX_SUMMARY = 160
    private const val MAX_VALUE = 200
    private val PARSE = Regex("""^\[(CRASH|BUG|BEHAV):([a-z0-9][a-z0-9.\-]*)\](?: (.*))?$""")
    private val BAD_CODE = Regex("[^a-z0-9.\\-]+")
    private val BAD_KEY = Regex("[^a-z0-9_.\\-]+")
    private val SPACES = Regex("\\s+")

    /** Kode: huruf kecil, angka, titik, hubung. Tidak pernah melempar (logging tidak boleh gagal). */
    fun normalizeCode(raw: String): String {
        val s = raw.lowercase().replace(BAD_CODE, "-").trim('-', '.')
        return s.ifEmpty { "unknown" }
    }

    /** Awali kode dengan area bila belum ada: (proot, install-failed) -> proot.install-failed. */
    fun qualify(area: String, code: String): String {
        val a = normalizeCode(area)
        val c = normalizeCode(code)
        return if (c == a || c.startsWith("$a.")) c else "$a.$c"
    }

    fun format(
        kind: EventKind,
        code: String,
        summary: String = "",
        fields: Map<String, String> = emptyMap(),
    ): String {
        val sb = StringBuilder("[").append(kind.label).append(':').append(normalizeCode(code)).append(']')
        val s = oneLine(summary, MAX_SUMMARY).replace(" | ", " / ")
        if (s.isNotEmpty()) sb.append(' ').append(s)
        val parts = ArrayList<String>()
        for ((k, v) in fields) {
            val key = k.lowercase().replace(BAD_KEY, "_").trim('_')
            if (key.isNotEmpty()) parts.add(key + "=" + value(v))
        }
        if (parts.isNotEmpty()) sb.append(" | ").append(parts.joinToString(" "))
        return sb.toString()
    }

    /** null bila pesan bukan peristiwa terstruktur. */
    fun parse(message: String): LogEvent? {
        val m = PARSE.matchEntire(message) ?: return null
        val kind = EventKind.values().first { it.label == m.groupValues[1] }
        val rest = m.groupValues[3]
        val summary: String
        val fieldText: String
        when {
            rest == "|" -> { summary = ""; fieldText = "" }
            rest.startsWith("| ") -> { summary = ""; fieldText = rest.substring(2) }
            else -> {
                val i = rest.indexOf(" | ")
                if (i >= 0) { summary = rest.substring(0, i); fieldText = rest.substring(i + 3) } else { summary = rest; fieldText = "" }
            }
        }
        val fields = LinkedHashMap<String, String>()
        for (p in fieldText.split(' ')) {
            val eq = p.indexOf('=')
            if (eq > 0) fields[p.substring(0, eq)] = p.substring(eq + 1)
        }
        return LogEvent(kind, m.groupValues[2], summary, fields)
    }

    private fun oneLine(s: String, max: Int): String = s.replace(SPACES, " ").trim().take(max)

    private fun value(v: String): String = oneLine(v, MAX_VALUE).replace(' ', '_').replace('|', '/')
}

/** Titik masuk tunggal untuk mencatat peristiwa. Tag mengikuti konvensi Molina/<area>[/<id>]. */
object MolinaEvents {

    fun crash(
        area: String,
        t: Throwable,
        thread: String,
        fields: Map<String, String> = emptyMap(),
        logger: MolinaLogger = MolinaLog.logger,
    ) {
        val all = LinkedHashMap<String, String>()
        all["thread"] = thread
        all["root"] = rootCauseOf(t).javaClass.name
        all.putAll(fields)
        logger.log(
            EventKind.CRASH.defaultLevel,
            MolinaLogger.tag(area),
            LogEvents.format(EventKind.CRASH, "uncaught", "exception tidak tertangani", all),
            t,
        )
    }

    fun bug(
        area: String,
        code: String,
        summary: String,
        t: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
        id: String? = null,
        level: LogLevel = EventKind.BUG.defaultLevel,
        logger: MolinaLogger = MolinaLog.logger,
    ) {
        logger.log(level, MolinaLogger.tag(area, id), LogEvents.format(EventKind.BUG, LogEvents.qualify(area, code), summary, fields), t)
    }

    fun behavior(
        area: String,
        code: String,
        summary: String = "",
        fields: Map<String, String> = emptyMap(),
        id: String? = null,
        logger: MolinaLogger = MolinaLog.logger,
    ) {
        logger.log(
            EventKind.BEHAVIOR.defaultLevel,
            MolinaLogger.tag(area, id),
            LogEvents.format(EventKind.BEHAVIOR, LogEvents.qualify(area, code), summary, fields),
        )
    }
}
