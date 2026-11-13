// molina-patch11 (dihasilkan workflow "Native proot (arm64)"; jangan edit manual)
package com.molina.terminal.proot

/** Pin Release proot arm64 statis. Aplikasi mengunduh, memverifikasi SHA-256, lalu memasang ke filesDir. */
object ProotPin {
    const val CONFIGURED = true
    const val TAG = "native-proot-d6c15f7-run3"
    const val URL = "https://github.com/nanazhii/Molina_Studio/releases/download/native-proot-d6c15f7-run3/molina-proot-arm64.tgz" // gate:allow Release repo sendiri, dipin SHA-256
    const val TGZ_SHA256 = "f6e26c82b6bb8d1f35a4edfd7a08e74747fef28187fdab6c39140011639a93bc"
    const val BIN_SHA256 = "cc451612e01b8161c75df469a69dcd5f46ea681b3ac86fb5059a63d1005c111f"
    const val SIZE_BYTES = 124104L
}
