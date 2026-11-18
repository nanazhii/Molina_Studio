package com.molina.core.common.stream

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Rantai resolver berurutan (ringan dulu, berat belakangan). Pakai resolver pertama yang
 * [StreamResolver.canResolve]. UNSUPPORTED, PARSE, dan UNKNOWN berarti "bukan urusan saya": lanjut ke
 * berikutnya. NOT_FOUND, NETWORK, PRIVATE, AGE_RESTRICTED, dan GEO_BLOCKED bersifat final: berhenti,
 * karena resolver cadangan tidak akan mengubah hasilnya dan hanya memboroskan RAM serta jaringan.
 * Pembatalan coroutine selalu diteruskan. [onEvent] menerima baris log tanpa URL, mis. "newpipe: UNSUPPORTED".
 */
class ChainStreamResolver(
    private val resolvers: List<StreamResolver>,
    private val onEvent: (String) -> Unit = {},
) : StreamResolver {
    override val id: String = "chain"

    override fun canResolve(url: String): Boolean = resolvers.any { it.canResolve(url) }

    override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> {
        var best: StreamOutcome.Failure? = null
        for (r in resolvers) {
            currentCoroutineContext().ensureActive()
            if (!r.canResolve(pageUrl)) continue
            val out = try {
                r.resolve(pageUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StreamOutcome.Failure(StreamError.UNKNOWN, "${r.id}: ${e.javaClass.simpleName}")
            }
            when (out) {
                is StreamOutcome.Success -> {
                    onEvent("${r.id}: ok")
                    return out
                }
                is StreamOutcome.Failure -> {
                    onEvent("${r.id}: ${out.error}")
                    if (out.error in FINAL) return out
                    if (best == null || rank(out) > rank(best)) best = out
                }
            }
        }
        return best ?: StreamOutcome.Failure(StreamError.UNSUPPORTED, "tidak ada resolver yang cocok")
    }

    override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> {
        var last: StreamOutcome.Failure? = null
        for (r in resolvers) {
            currentCoroutineContext().ensureActive()
            when (val out = r.search(query, limit)) {
                is StreamOutcome.Success -> return out
                is StreamOutcome.Failure -> if (out.error != StreamError.UNSUPPORTED || last == null) last = out
            }
        }
        return last ?: StreamOutcome.Failure(StreamError.UNSUPPORTED, "tanpa pencarian")
    }

    private fun rank(f: StreamOutcome.Failure): Int {
        val base = when (f.error) {
            StreamError.PARSE -> 2
            StreamError.UNKNOWN -> 1
            else -> 0
        }
        return base * 2 + if (f.action != null) 1 else 0
    }

    private companion object {
        val FINAL = setOf(
            StreamError.NOT_FOUND, StreamError.NETWORK, StreamError.PRIVATE,
            StreamError.AGE_RESTRICTED, StreamError.GEO_BLOCKED,
        )
    }
}
