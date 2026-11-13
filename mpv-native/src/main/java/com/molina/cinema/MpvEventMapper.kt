// molina-patch13e
package com.molina.cinema

/**
 * Menerjemahkan event/properti mpv mentah menjadi PlayerEvent. Tidak menyentuh MPVLib sehingga
 * aman diuji di JVM. Dipanggil dari thread event mpv; keadaan ditandai @Volatile.
 *
 * Aturan: Position dan perubahan pause hanya diteruskan setelah berkas dimuat; END_FILE tanpa
 * FILE_LOADED setelah START_FILE dianggap gagal memuat (rincian ada di log libmpv, mpv.native).
 */
class MpvEventMapper {
    @Volatile private var uri = ""
    @Volatile private var durationMs = 0L
    @Volatile private var paused = false
    @Volatile private var loaded = false
    @Volatile private var starting = false

    fun onLoadRequested(uri: String) { this.uri = uri }

    fun onDuration(seconds: Long) { durationMs = seconds.coerceAtLeast(0L) * 1000L }

    fun onTimePos(seconds: Long): List<PlayerEvent> =
        if (loaded) listOf(PlayerEvent.Position(seconds.coerceAtLeast(0L) * 1000L, durationMs)) else emptyList()

    fun onPause(value: Boolean): List<PlayerEvent> {
        paused = value
        return if (loaded) listOf(PlayerEvent.StateChanged(if (value) PlayerState.PAUSED else PlayerState.PLAYING)) else emptyList()
    }

    fun onEof(value: Boolean): List<PlayerEvent> {
        if (!value || !loaded) return emptyList()
        loaded = false
        return listOf(PlayerEvent.Ended, PlayerEvent.StateChanged(PlayerState.ENDED))
    }

    fun onEvent(id: Int): List<PlayerEvent> = when (id) {
        MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
            loaded = false
            starting = true
            listOf(PlayerEvent.StateChanged(PlayerState.LOADING))
        }
        MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
            loaded = true
            starting = false
            listOf(
                PlayerEvent.Loaded(uri),
                PlayerEvent.StateChanged(if (paused) PlayerState.PAUSED else PlayerState.PLAYING),
            )
        }
        MPVLib.MpvEvent.MPV_EVENT_END_FILE ->
            if (starting && !loaded) {
                starting = false
                listOf(PlayerEvent.Error(-1, "berkas gagal dimuat"), PlayerEvent.StateChanged(PlayerState.ERROR))
            } else {
                emptyList()
            }
        else -> emptyList()
    }
}
