// molina-patch11 (dihasilkan workflow "Native proot (arm64)"; jangan edit manual)
package com.molina.terminal.proot

/** Pin Release proot arm64 statis. Aplikasi mengunduh, memverifikasi SHA-256, lalu memasang ke filesDir. */
object ProotPin {
    const val CONFIGURED = true
    const val TAG = "native-proot-f1a7d14-run1"
    const val URL = "https://github.com/nanazhii/Molina_Studio/releases/download/native-proot-f1a7d14-run1/molina-proot-arm64.tgz" // gate:allow Release repo sendiri, dipin SHA-256
    const val TGZ_SHA256 = "504a064647dd9216d0d1c26743278da5b1bb9bd5f55867f690fa504cfc80fc19"
    const val BIN_SHA256 = "a52828acacb3d5d194d570dbf82026dfa7c9d4ea0940da1f8e43c27b41d6107a"
    const val SIZE_BYTES = 518112L
}
