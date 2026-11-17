package com.molina.code.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** [CODE-HEADER-FRAME] [MINT-HEADER] Header path editor: hitam pekat tanpa bingkai luar, sama dengan header Terminal. */
internal fun Modifier.codeHeaderFrame(): Modifier =
    this.background(Color.Black)
