// molina-patch26k4
package com.molina.files

/**
 * Klip "Ekstrak ke…": arsip (path relatif) yang menunggu folder tujuan. Terpisah dari [FilesClip] (Salin/Pindah)
 * agar TransferKind tidak bertambah; keduanya tidak pernah aktif bersamaan. Bertahan saat rotasi lewat Bundle.
 */
/** [only] != null: hanya entri ini (kunci ArchiveTree) yang diekstrak dari arsip tunggal di [paths]. 26k5d:e03 */
internal data class ArchiveClip(val paths: List<String>, val only: Set<String>? = null)
