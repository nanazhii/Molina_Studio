package com.molina.core.common.stream

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem

/**
 * Cache LRU kecil untuk hasil resolve yang berhasil, supaya memutar ulang, mencoba lagi, atau
 * kembali ke item yang sama tidak menjalankan extractor (dan mesin JS-nya) lagi. Hanya Success yang
 * disimpan; entri berakhir sebelum URL kedaluwarsa. Isi cache beberapa KB, dan [clear] dipanggil
 * saat sistem meminta pangkas memori.
 */
class CachingStreamResolver(
    private val delegate: StreamResolver,
    private val maxEntries: Int = 16,
    private val defaultTtlMs: Long = 20 * 60_000L,
    private val safetyMarginMs: Long = 120_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamResolver {
    override val id: String = delegate.id

    private class Entry(val value: ResolvedStream, val validUntilMs: Long)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    override fun canResolve(url: String): Boolean = delegate.canResolve(url)

    override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> {
        val now = clock()
        synchronized(map) {
            val hit = map[pageUrl]
            if (hit != null) {
                if (now < hit.validUntilMs) return StreamOutcome.Success(hit.value)
                map.remove(pageUrl)
            }
        }
        val out = delegate.resolve(pageUrl)
        if (out is StreamOutcome.Success) {
            val expires = out.value.expiresAtMs
            val until = if (expires != null) minOf(now + defaultTtlMs, expires - safetyMarginMs) else now + defaultTtlMs
            if (until > now) synchronized(map) { map[pageUrl] = Entry(out.value, until) }
        }
        return out
    }

    override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
        delegate.search(query, limit)

    fun clear() = synchronized(map) { map.clear() }

    val size: Int get() = synchronized(map) { map.size }
}
