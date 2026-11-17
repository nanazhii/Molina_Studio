package com.molina.code.ui

import android.content.Context
import android.content.Intent

/** Bagikan teks lewat menu berbagi Android. Dipakai pratinjau keluaran dan kartu notifikasi perubahan. */
internal fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
