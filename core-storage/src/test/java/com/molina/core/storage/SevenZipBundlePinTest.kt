// molina-27r
package com.molina.core.storage

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menjaga biner 7-Zip bawaan: hash `lib7zz.so` harus sama dengan scripts/sevenzip/pins.sha256. Mengubah biner berarti
 * mengubah pin SECARA SADAR (dan mencatatnya di progres.md); tes ini menggagalkan CI bila keduanya tidak cocok.
 */
class SevenZipBundlePinTest {
    private fun repoRoot(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").isFile) d = d.parentFile
        return requireNotNull(d) { "Akar repo tidak ditemukan dari ${File("").absolutePath}" }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun pins(root: File): Map<String, String> =
        File(root, "scripts/sevenzip/pins.sha256").readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { l -> l.split(Regex("\\s+"), limit = 2).let { it[1].trim() to it[0].lowercase() } }

    @Test fun hashBinerBawaanCocokDenganPin() {
        val root = repoRoot()
        val lib = File(root, "app/src/main/jniLibs/arm64-v8a/lib7zz.so")
        assertTrue("lib7zz.so tidak ada", lib.isFile)
        val pin = pins(root)["lib7zz.so"]
        assertTrue("pin lib7zz.so tidak ada di pins.sha256", pin != null)
        assertEquals(pin, sha256(lib))
    }

    @Test fun lisensiBawaanDisertakan() {
        val lic = File(repoRoot(), "app/src/main/assets/licenses/7zip-License.txt")
        assertTrue("teks lisensi 7-Zip tidak ada", lic.isFile && lic.length() > 0)
    }
}
