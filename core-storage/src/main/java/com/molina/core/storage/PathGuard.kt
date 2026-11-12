package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import java.io.File

/** Memastikan path relatif tidak keluar dari root (".." dan symlink dibereskan lewat canonical path). */
object PathGuard {
    fun resolveInside(root: File, relative: String): MolinaResult<File> {
        if (relative.contains('\u0000')) return invalid()
        return try {
            val base = root.canonicalFile
            val target = File(base, relative).canonicalFile
            val inside = target == base || target.path.startsWith(base.path + File.separator)
            if (inside) MolinaResult.Success(target) else invalid()
        } catch (e: java.io.IOException) {
            MolinaResult.Failure(MolinaError(ErrorKind.IO, "Kesalahan I/O", e))
        }
    }

    private fun invalid() =
        MolinaResult.Failure(MolinaError(ErrorKind.INVALID_PATH, "Path tidak valid"))
}
