#!/usr/bin/env python3
"""
Molina_Studio - P11: hapus modul :mpv-native seluruhnya (wrapper Kotlin, uji, aset cacert.pem, jniLibs) untuk
persiapan vendoring mpv-android penuh. Jalankan dari root repo:
    python3 molina_remove_mpv_native_patch.py [/sdcard/Molina_Studio]
Aman dijalankan berulang (idempotent). Semua target diperiksa dulu; bila ada yang tidak cocok skrip berhenti
SEBELUM menulis atau menghapus apa pun.

Yang dilakukan:
  - hapus folder mpv-native/
  - settings.gradle.kts, cinema/build.gradle.kts, jvm-tests.yml, module_gate.py: buang semua sebutan mpv-native
  - cinema: PlayerBackendStub.kt (PlayerSurface, MpvPlayback, MpvPlayerFactory yang selalu mengembalikan null)
    supaya :cinema dan :app tetap terkompilasi; layar Cinema menampilkan "mpv belum tersedia"
  - build.yml: langkah fetch_native.sh dinonaktifkan sementara (tujuan ekstraknya sudah tidak ada)
  - docs (arsitektur, penamaan, provenance) dan progres.md diperbarui
TIDAK dihapus (di luar cakupan): scripts/native_mpv.sh, native_wget.sh, fetch_native.sh, native_audit.sh, native/,
.github/workflows/native-mpv.yml. Ganti atau hapus saat vendoring.
"""
import os
import shutil
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
CIN = "cinema/src/main/java/com/molina/cinema"

for rel in ("settings.gradle.kts", "cinema/build.gradle.kts", CIN + "/CinemaFragment.kt", "progres.md"):
    if not os.path.isfile(os.path.join(ROOT, rel)):
        sys.exit("Bukan root Molina_Studio atau berkas hilang: " + rel)

files = {}
new_files = {}


def rd(rel):
    if rel not in files:
        with open(os.path.join(ROOT, rel), "r", encoding="utf-8") as f:
            files[rel] = f.read()
    return files[rel]


def patch(rel, old, new, gone=None):
    """Ganti `old` -> `new` tepat sekali. Bila `gone` (teks yang harus sudah tidak ada) tidak lagi ada, anggap sudah patch."""
    t = rd(rel)
    if old not in t and (gone is not None and gone not in t):
        print("[lewati ] sudah patch :", rel)
        return
    n = t.count(old)
    if n != 1:
        sys.exit("GAGAL %s: potongan target ditemukan %d kali (harus 1):\n%s" % (rel, n, old))
    files[rel] = t.replace(old, new)
    print("[diubah ] patch       :", rel)


# ------------------------------------------------------------------ wiring Gradle / CI / gate
patch("settings.gradle.kts", '":core-ui", ":mpv-native", ":player-api"', '":core-ui", ":player-api"', gone='":mpv-native"')
patch("cinema/build.gradle.kts", '    implementation(project(":mpv-native"))\n', "", gone='":mpv-native"')
patch(
    ".github/workflows/jvm-tests.yml",
    " :mpv-native:testDebugUnitTest",
    "",
    gone=":mpv-native:testDebugUnitTest",
)
patch(
    ".github/workflows/build.yml",
    "        if: hashFiles('scripts/fetch_native.sh') != ''\n",
    "        if: false # P11: nonaktif sementara; :mpv-native dihapus, aktifkan lagi setelah vendoring mesin mpv\n",
    gone="if: hashFiles('scripts/fetch_native.sh') != ''",
)
patch("scripts/module_gate.py", '"mpv-native", "terminal-core", "terminal-emulator", "terminal-view"', '"terminal-core", "terminal-emulator", "terminal-view"', gone='ENGINE = {"mpv-native"')
patch(
    "scripts/module_gate.py",
    '           "mpv-native": {"player-api"}, "terminal-core": {"terminal-api"}}',
    '           "terminal-core": {"terminal-api"}}',
    gone='"mpv-native": {"player-api"}',
)
patch("scripts/module_gate.py", '              "cinema": {"mpv-native"},\n', "", gone='"cinema": {"mpv-native"}')

# ------------------------------------------------------------------ cinema: stub supaya tetap terkompilasi
STUB = '''// molina-p11
package com.molina.cinema

import android.content.Context
import android.view.Surface

/*
 * [P11] Pengganti sementara untuk kelas yang dulu ada di :mpv-native (dihapus). Cinema tetap terkompilasi,
 * tetapi pemutaran tidak tersedia sampai mesin mpv divendor penuh. Hapus berkas ini saat modul pemutar
 * baru menyediakan MpvPlayback/MpvPlayerFactory (atau ganti panggilannya di CinemaFragment).
 */

/** Permukaan video. attach/detach mengikuti siklus SurfaceHolder; aman dipanggil berulang. */
interface PlayerSurface {
    fun attach(surface: Surface)
    fun resize(width: Int, height: Int)
    fun detach()
}

/** Sesi + permukaan hasil [MpvPlayerFactory.open]. */
class MpvPlayback(val sessionId: String, val session: MolinaPlayerSession, val surface: PlayerSurface)

object MpvPlayerFactory {
    /** Selalu null sampai mesin mpv baru divendor. */
    @Suppress("UNUSED_PARAMETER")
    fun open(context: Context): MpvPlayback? = null
}
'''
stub_rel = CIN + "/PlayerBackendStub.kt"
if os.path.exists(os.path.join(ROOT, stub_rel)):
    print("[lewati ] sudah ada   :", stub_rel)
else:
    new_files[stub_rel] = STUB
    print("[dibuat ] berkas baru :", stub_rel)

patch(
    CIN + "/CinemaFragment.kt",
    'status?.text = "mpv tidak tersedia. Lihat Log Viewer (mpv.native-missing / mpv.init-failed)."',
    'status?.text = "mpv belum tersedia: mesin pemutar sedang diganti (vendoring mpv penuh)."',
    gone="mpv tidak tersedia. Lihat Log Viewer",
)

# ------------------------------------------------------------------ docs
patch("docs/arsitektur.md", "| engine | `mpv-native`, `terminal-core`", "| engine | `terminal-core`", gone="| engine | `mpv-native`")
patch(
    "docs/arsitektur.md",
    "- Jangan ubah nama/package `MPVLib` tanpa menyamakan `scripts/native_mpv.sh`.",
    "- `:mpv-native` dihapus (P11). Mesin pemutar akan divendor dari mpv-android penuh; package JNI Kotlin dan C/JNI hasil vendoring harus disamakan.",
    gone="Jangan ubah nama/package `MPVLib` tanpa menyamakan",
)
patch(
    "docs/penamaan.md",
    "- Package pemutar (mpv-native): `com.molina.cinema`; namespace Gradle-nya `com.molina.cinema.mpv` agar tidak bentrok dengan modul `:cinema`. Tag log tetap `Molina/mpv/<id>`.",
    "- Package pemutar: ditetapkan ulang saat vendoring mpv-android (modul `:mpv-native` dihapus di P11). Tag log tetap `Molina/mpv/<id>`.",
    gone="- Package pemutar (mpv-native):",
)
patch(
    "docs/provenance.md",
    "| mpv-android | ebf1b3f0cbe9 | MIT | MPVLib.kt (mpv-native, hanya package diganti). Build native memakai sumber upstream + patch JNI/FFmpeg lewat scripts/native_mpv.sh |",
    "| mpv-android | ebf1b3f0cbe9 | MIT | Dulu: MPVLib.kt di mpv-native (dihapus di P11). Rencana: vendoring penuh mpv-android; atribusi MIT tetap wajib. Skrip build native lama (scripts/native_mpv.sh) belum dihapus |",
    gone="| MPVLib.kt (mpv-native, hanya package diganti).",
)
patch(
    "docs/provenance.md",
    "## Aset: cacert.pem (Patch A)\n",
    "## Aset: cacert.pem (Patch A) - DIHAPUS di P11\n\nBerkas dan ujinya hilang bersama `:mpv-native`; bundel sertifikat harus diadakan lagi saat vendoring (tls-verify=yes tetap wajib). Catatan riwayat:\n",
    gone="## Aset: cacert.pem (Patch A)\n",
)

ENTRY = """
## P11 hapus modul :mpv-native untuk vendoring mpv-android penuh (2026-11-17)
- [x] Hapus mpv-native/ seluruhnya (MPVLib, MpvNative, MpvConfig, MpvLogScrub, MpvLogBridge, MpvEventMapper, MpvTrackParser, MpvPlayerSession, LoggingMolinaPlayerSession + 7 uji, cacert.pem)
- [x] settings.gradle.kts, cinema/build.gradle.kts, jvm-tests.yml, module_gate.py dibersihkan; build.yml: langkah fetch_native.sh dinonaktifkan sementara
- [x] cinema: PlayerBackendStub.kt (PlayerSurface, MpvPlayback, MpvPlayerFactory -> null); Cinema menampilkan "mpv belum tersedia"
- [ ] HILANG sampai dibangun ulang: privasi Shield di mpv (blacklist protokol lavf, http_proxy fail-closed, preflight rtmp/rtsp), tls-verify + cacert, scrub log mpv, bridge log mpv ke MolinaLog, mapper trek/event. Wajib ada lagi SEBELUM rilis
- [ ] Vendoring mpv-android penuh: tetapkan package JNI (upstream is.xyz.mpv vs com.molina.cinema), atribusi MIT di provenance.md, lolos telemetry_gate
- [ ] Bersihkan sisa pipeline lama: scripts/native_mpv.sh, native_wget.sh, fetch_native.sh, native_audit.sh, native/, workflow native-mpv.yml (masih menunjuk mpv-native)
- [ ] Verifikasi CI: kompilasi :cinema dan :app, gate module/telemetry/secret
"""
if "## P11 hapus modul :mpv-native" in rd("progres.md"):
    print("[lewati ] sudah patch : progres.md")
else:
    files["progres.md"] = rd("progres.md").rstrip("\n") + "\n" + ENTRY
    print("[diubah ] patch       : progres.md")

# ------------------------------------------------------------------ tulis + hapus (hanya bila semua cocok)
for rel, text in files.items():
    with open(os.path.join(ROOT, rel), "w", encoding="utf-8") as f:
        f.write(text)
for rel, text in new_files.items():
    full = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        f.write(text)
mod = os.path.join(ROOT, "mpv-native")
if os.path.isdir(mod):
    shutil.rmtree(mod)
    print("[dihapus] folder      : mpv-native/")
else:
    print("[lewati ] sudah hilang: mpv-native/")
print("Selesai.")
