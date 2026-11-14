// molina-patch26k4
package com.molina.files

/**
 * Klip "Ekstrak ke…": arsip (path relatif) yang menunggu folder tujuan. Terpisah dari [FilesClip] (Salin/Pindah)
 * agar TransferKind tidak bertambah; keduanya tidak pernah aktif bersamaan. Bertahan saat rotasi lewat Bundle.
 */
internal data class ArchiveClip(val paths: List<String>)
