package com.molina.core.common

/**
 * Titik suntik resolver stream. Pemutar hanya mengenal kontrak [StreamResolver]; :app memasang
 * implementasinya saat start (mis. NewPipe), sehingga pemutar tidak bergantung pada modul engine.
 * Pabrik dipanggil malas pada pemakaian pertama, supaya inisialisasi engine tidak membebani start
 * aplikasi dan terjadi di thread pemanggil resolusi (bukan main thread).
 */
object StreamResolverRegistry {
    @Volatile private var factory: (() -> StreamResolver)? = null
    @Volatile private var cached: StreamResolver? = null

    fun install(factory: () -> StreamResolver) {
        synchronized(this) {
            this.factory = factory
            cached = null
        }
    }

    /** Resolver terpasang, atau null bila belum ada yang dipasang. */
    fun get(): StreamResolver? = cached ?: synchronized(this) {
        cached ?: factory?.invoke()?.also { cached = it }
    }
}
