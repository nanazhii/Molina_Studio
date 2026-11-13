// molina-patch11 (dihasilkan workflow "Native proot (arm64)"; jangan edit manual)
package com.molina.terminal.proot

/** Pin Release proot arm64 statis. Aplikasi mengunduh, memverifikasi SHA-256, lalu memasang ke filesDir. */
object ProotPin {
    const val CONFIGURED = true
    const val TAG = "native-proot-9cb0374-run2"
    const val URL = "https://github.com/nanazhii/Molina_Studio/releases/download/native-proot-9cb0374-run2/molina-proot-arm64.tgz" // gate:allow Release repo sendiri, dipin SHA-256
    const val TGZ_SHA256 = "580c0506278998e464bfaad3821a836c6d3bc4b97ef1bd11a35c2a8c1e4637bf"
    const val BIN_SHA256 = "cc451612e01b8161c75df469a69dcd5f46ea681b3ac86fb5059a63d1005c111f"
    const val SIZE_BYTES = 124102L
}
