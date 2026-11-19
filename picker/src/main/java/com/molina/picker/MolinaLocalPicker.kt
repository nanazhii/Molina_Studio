package com.molina.picker

import android.content.Context
import com.molina.core.common.diag.Diag
import com.molina.core.common.picker.LocalPicker
import com.molina.core.common.picker.PickKind
import java.io.File

/**
 * Implementasi [LocalPicker] di atas file manager Molina (modul filemanager). Satu-satunya tempat yang
 * mengenal file manager untuk keperluan memilih; fitur lain cukup memakai kontrak di core-common.
 */
class MolinaLocalPicker : LocalPicker {
    override fun pick(context: Context, kind: PickKind, startDir: File?, onResult: (File?) -> Unit) {
        try {
            PickerDialog(context, kind, startDir, onResult).show()
        } catch (e: Exception) {
            Diag.bug("picker", "show-failed", "pemilih gagal dibuka", e)
            onResult(null)
        }
    }
}
