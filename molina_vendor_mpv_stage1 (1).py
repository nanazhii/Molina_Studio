#!/usr/bin/env python3
"""
Molina_Studio - P12: vendoring tahap 1 mpv-android penuh ke modul :player-mpv (library).
Jalankan SETELAH P11 (molina_remove_mpv_native_patch.py), dari root repo:
    python3 molina_vendor_mpv_stage1.py [/sdcard/Molina_Studio] [/jalur/clone/mpv-android]
Argumen ke-2 opsional (clone yang sudah ada). Tanpa itu, skrip meng-clone sumber resmi ke folder sementara dan
memastikan commit-nya sama dengan PIN di bawah (bila beda, berhenti).

Aman dijalankan berulang (idempotent): bila player-mpv/NOTICE-mpv-android sudah ada, bagian vendoring dilewati.
Semua berkas dihitung di memori lalu divalidasi; bila ada yang gagal, TIDAK ada yang ditulis.
Perbaikan vs versi awal: BuildConfig.FLAVOR di MPVFilePickerFragment.java diganti false (modul tanpa flavor).
"""
import os
import re
import subprocess
import sys
import tempfile

PIN = "ebf1b3f0cbe9d6154a43cf6ec1ad6fc32a1b2d47"
UPSTREAM_URL = "https://github.com/mpv-android/mpv-android"
PKG = "com.molina.player.mpv"
PKG_FP = "com.molina.player.filepicker"
APP_ID = "com.molina"  # applicationId :app (untuk targetPackage di preferences_root.xml)
UP_VERSION_NAME = "2026-09-17-release"
UP_VERSION_CODE = 48

ROOT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.getcwd()
UP_ARG = sys.argv[2] if len(sys.argv) > 2 else None
MOD = "player-mpv"

for rel in ("settings.gradle.kts", "gradle/libs.versions.toml", "scripts/module_gate.py", "progres.md"):
    if not os.path.isfile(os.path.join(ROOT, rel)):
        sys.exit("Bukan root Molina_Studio atau berkas hilang: " + rel)
if '":mpv-native"' in open(os.path.join(ROOT, "settings.gradle.kts"), encoding="utf-8").read():
    sys.exit("Jalankan dulu P11 (molina_remove_mpv_native_patch.py): :mpv-native masih terdaftar.")

# ------------------------------------------------------------------ sumber upstream
tmp = None
if UP_ARG:
    UP = os.path.abspath(UP_ARG)
else:
    tmp = tempfile.mkdtemp(prefix="mpv-upstream-")
    UP = os.path.join(tmp, "src")
    subprocess.check_call(["git", "clone", "--quiet", UPSTREAM_URL, UP])
    subprocess.check_call(["git", "-C", UP, "checkout", "--quiet", PIN])
head = subprocess.check_output(["git", "-C", UP, "rev-parse", "HEAD"]).decode().strip()
if head != PIN:
    sys.exit("Commit upstream %s tidak sama dengan PIN %s. Checkout PIN dulu atau perbarui PIN secara sadar." % (head, PIN))
if not os.path.isdir(os.path.join(UP, "app", "src", "main")):
    sys.exit("Struktur upstream tidak dikenal: " + UP)

NOTICE_REL = MOD + "/NOTICE-mpv-android"
already = os.path.exists(os.path.join(ROOT, NOTICE_REL))

out = {}   # rel -> str | bytes   (berkas baru di player-mpv)
edits = {}  # rel -> teks baru (berkas repo yang sudah ada)

# ------------------------------------------------------------------ transformasi
TEXT_EXT = {".kt", ".java", ".xml", ".cpp", ".h", ".mk", ".pro", ".txt"}
ALLOW_HOSTS = {"schemas.android.com", "www.w3.org", "developer.android.com", "kotlinlang.org", "docs.gradle.org"}
URL_RE = re.compile(r"https?://[^\s\"'<>)\]}\\]+", re.I)
LICENSE_URL = re.compile(r"mozilla\.org/MPL|gnu\.org/licenses", re.I)
NO_COLON_SLASHES = re.compile(r"(?<!:)//")
problems = []


def host_ok(url):
    m = re.match(r"https?://([^/:?#]+)", url, re.I)
    h = (m.group(1) if m else "").lower()
    return any(h == d or h.endswith("." + d) for d in ALLOW_HOSTS)


def rename_pkg(t):
    t = t.replace("`is`.xyz.filepicker", PKG_FP).replace("`is`.xyz.mpv", PKG)
    t = t.replace("is.xyz.filepicker", PKG_FP).replace("is.xyz.mpv", PKG)
    t = t.replace("is/xyz/filepicker", PKG_FP.replace(".", "/")).replace("is/xyz/mpv", PKG.replace(".", "/"))
    t = t.replace("is_xyz_mpv", PKG.replace(".", "_")).replace("is_xyz_filepicker", PKG_FP.replace(".", "_"))
    return t


def clean_urls(rel, t):
    xml = rel.endswith(".xml")
    opener, closer = ("<!--", "-->") if xml else ("/*", "*/")
    in_block = False
    lines = t.split("\n")
    for i, line in enumerate(lines):
        start_in_block = in_block
        pos = 0
        while True:
            if not in_block:
                j = line.find(opener, pos)
                if j < 0:
                    break
                in_block, pos = True, j + len(opener)
            else:
                j = line.find(closer, pos)
                if j < 0:
                    break
                in_block, pos = False, j + len(closer)
        if "gate:allow" in line or not URL_RE.search(line):
            continue
        if LICENSE_URL.search(line):
            if line.rstrip().endswith(("*/", "-->")):
                problems.append("%s:%d baris lisensi berakhir di penutup komentar" % (rel, i + 1))
            else:
                lines[i] = line + " gate:allow lisensi"  # header lisensi dipertahankan utuh
            continue

        def sub(m):
            url = m.group(0)
            if host_ok(url):
                return url
            before = line[: m.start()]
            in_comment = start_in_block or bool(NO_COLON_SLASHES.search(before)) or opener in before or before.strip().startswith(("*", "~", "#"))
            if not in_comment:
                problems.append("%s:%d URL di luar komentar: %s" % (rel, i + 1, url))
                return url
            return "(tautan upstream dihapus)"

        lines[i] = URL_RE.sub(sub, line)
    return "\n".join(lines)


def transform_text(rel_dest, t):
    t = rename_pkg(t)
    t = clean_urls(rel_dest, t)
    t = t.replace("        // ~sfan5, 2022-06-30\n", "")
    t = t.replace("mpv-android", "Molina Player")
    if rel_dest.endswith("res/xml/preferences_root.xml"):
        t = t.replace('android:targetPackage="%s"' % PKG, 'android:targetPackage="%s"' % APP_ID)
    if rel_dest.endswith("MPVFilePickerFragment.java"):
        # modul library tanpa product flavor: BuildConfig.FLAVOR tidak dibuat AGP; flavor allstorage tidak dibawa
        t = t.replace('BuildConfig.FLAVOR.equals("allstorage")', "false")
    return t


def dest_of(src_rel):
    """src_rel relatif terhadap app/src -> relatif terhadap root repo."""
    parts = src_rel.split("/")
    if "java" in parts[:2] and len(parts) > 3 and parts[2:4] == ["is", "xyz"]:
        parts = parts[:2] + ["com", "molina", "player"] + parts[4:]
    return MOD + "/src/" + "/".join(parts)


if not already:
    srcroot = os.path.join(UP, "app", "src")
    for sub in ("main", "debug"):
        for base, _dirs, names in os.walk(os.path.join(srcroot, sub)):
            for n in names:
                full = os.path.join(base, n)
                if os.path.islink(full):  # mis. jniLibs (symlink ke hasil build native): tahap 2
                    print("[lewati ] symlink upstream :", os.path.relpath(full, srcroot))
                    continue
                rel = os.path.relpath(full, srcroot).replace(os.sep, "/")
                dest = dest_of(rel)
                ext = os.path.splitext(n)[1].lower()
                data = open(full, "rb").read()
                if ext in TEXT_EXT:
                    try:
                        out[dest] = transform_text(dest, data.decode("utf-8"))
                        continue
                    except UnicodeDecodeError:
                        pass
                out[dest] = data
    out[MOD + "/proguard-rules.pro"] = transform_text(MOD + "/proguard-rules.pro", open(os.path.join(UP, "app", "proguard-rules.pro"), encoding="utf-8").read())
    out[MOD + "/LICENSE"] = open(os.path.join(UP, "LICENSE"), encoding="utf-8").read()

    mrel = MOD + "/src/main/AndroidManifest.xml"
    man = out[mrel]
    m = re.search(r"<application\b[^>]*>", man)
    if not m:
        sys.exit("GAGAL: tag <application> tidak ditemukan di manifest upstream")
    tag = re.sub(r'\s+android:(icon|roundIcon|banner|label|allowBackup|resizeableActivity|requestLegacyExternalStorage|preserveLegacyExternalStorage|supportsRtl|appCategory|localeConfig)="[^"]*"', "", m.group(0))
    man = man[: m.start()] + tag + man[m.end():]
    man, n_launch = re.subn(r'[ \t]*<category android:name="android\.intent\.category\.(?:LEANBACK_)?LAUNCHER" />\r?\n', "", man)
    if n_launch != 2:
        problems.append("kategori LAUNCHER yang dibuang = %d (harus 2)" % n_launch)
    defman = open(os.path.join(UP, "app", "src", "default", "AndroidManifest.xml"), encoding="utf-8").read()
    perms = re.findall(r'<uses-permission[^>]*?/>', defman, re.S)
    anchor = '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n'
    if man.count(anchor) != 1 or not perms:
        problems.append("jangkar izin manifest tidak cocok")
    else:
        block = "    <!-- izin flavor default upstream -->\n" + "\n".join("    " + re.sub(r"\s*\n\s*", " ", p) for p in perms) + "\n"
        man = man.replace(anchor, anchor + "\n" + block)
    out[mrel] = man

    BAD = [r"\bis\.xyz\b", r"`is`", r"mpv-android", r"\bsfan5\b", r"is_xyz", r"is/xyz", r"BuildConfig\.FLAVOR"]
    for rel, content in out.items():
        if not isinstance(content, str) or os.path.basename(rel).upper().startswith(("LICENSE", "NOTICE")):
            continue
        for no, line in enumerate(content.split("\n"), 1):
            for pat in BAD:
                if re.search(pat, line):
                    problems.append("%s:%d sisa identitas lama (%s)" % (rel, no, pat))

    out[NOTICE_REL] = """NOTICE - modul player-mpv (Molina_Studio)

Modul ini adalah salinan yang DIUBAH dari mpv-android:
  sumber   : %s
  commit   : %s
  lisensi  : MIT. Hak cipta (c) 2016 Ilya Zhuravlev dan (c) 2016 sfan5. Teks lengkap ada di berkas LICENSE di folder ini.

Bagian pihak ketiga di dalam salinan:
  - file picker (package com.molina.player.filepicker, layout nnf_*): berasal dari pustaka nononsense-filepicker; header lisensi
    (MPL-2.0 atau LGPL-3.0 per berkas) dipertahankan utuh di setiap berkas. Beberapa berkas bersumber dari AOSP (Apache-2.0).
  - src/main/assets/cacert.pem: bundel sertifikat akar Mozilla hasil ekstraksi curl (data Mozilla, MPL-2.0); disalin apa adanya.
  - libmpv, FFmpeg, libass dan pustaka native lain TIDAK ada di modul ini (dibangun di tahap 2); lisensinya dicatat di docs/provenance.md.

Perubahan oleh Molina_Studio (tahap 1):
  - package is.xyz.mpv -> com.molina.player.mpv, is.xyz.filepicker -> com.molina.player.filepicker (termasuk nama fungsi JNI)
  - tautan dan nama pengembang/proyek lama dihapus dari kode, resource, dan komentar (LICENSE dan NOTICE ini dikecualikan)
  - diadaptasi menjadi modul library: tanpa ikon launcher, atribut <application> dibuang, izin flavor default digabung
  - BuildConfig.FLAVOR di file picker diganti false (tanpa flavor allstorage)
  - tanpa buildscripts, ABI split, flavor allstorage, docs/, fastlane/ upstream
Berkas dalam Molina_Studio berlisensi GPL-3.0 (lihat LICENSE di root); lisensi MIT, MPL-2.0, dan LGPL-3.0 di atas kompatibel
dengan penggabungan itu dan tetap berlaku untuk bagian masing-masing.
""" % (UPSTREAM_URL, PIN)

    out[MOD + "/build.gradle.kts"] = '''// molina-p12
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "%(pkg)s"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\\"%(vn)s\\"") // versi upstream yang divendor
        buildConfigField("int", "VERSION_CODE", "%(vc)d")
        consumerProguardFiles("proguard-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
}
''' % {"pkg": PKG, "vn": UP_VERSION_NAME, "vc": UP_VERSION_CODE}
else:
    print("[lewati ] sudah divendor :", NOTICE_REL)

if problems:
    print("GAGAL, tidak ada yang ditulis:")
    for p in problems[:60]:
        print(" -", p)
    sys.exit(1)


# ------------------------------------------------------------------ berkas repo yang sudah ada
def rd(rel):
    if rel not in edits:
        edits[rel] = open(os.path.join(ROOT, rel), encoding="utf-8").read()
    return edits[rel]


def patch(rel, old, new, gone, optional=False):
    t = rd(rel)
    if gone in t:
        print("[lewati ] sudah patch :", rel)
        return
    if t.count(old) != 1 and optional:
        print("[PERINGATAN] jangkar docs tidak cocok, edit manual:", rel, "->", old[:60])
        return
    if t.count(old) != 1:
        sys.exit("GAGAL %s: target ditemukan %d kali (harus 1):\n%s" % (rel, t.count(old), old))
    edits[rel] = t.replace(old, new)
    print("[diubah ] patch       :", rel)


patch("settings.gradle.kts", '":core-ui", ":player-api"', '":core-ui", ":player-mpv", ":player-api"', '":player-mpv"')
patch(
    "gradle/libs.versions.toml",
    'sora = "0.23.6"\n',
    'sora = "0.23.6"\nmedia = "1.7.0" # molina-p12\npreference = "1.2.1" # molina-p12\n',
    "molina-p12",
)
patch(
    "gradle/libs.versions.toml",
    "\n[plugins]",
    '\nandroidx-media = { module = "androidx.media:media", version.ref = "media" } # molina-p12\n'
    'androidx-preference-ktx = { module = "androidx.preference:preference-ktx", version.ref = "preference" } # molina-p12\n\n[plugins]',
    "androidx-preference-ktx",
)
patch("scripts/module_gate.py", 'ENGINE = {"terminal-core"', 'ENGINE = {"player-mpv", "terminal-core"', '"player-mpv", "terminal-core"')
patch(
    "scripts/module_gate.py",
    '           "terminal-core": {"terminal-api"}}',
    '           "player-mpv": {"player-api"}, "terminal-core": {"terminal-api"}}',
    '"player-mpv": {"player-api"}',
)
patch(
    "scripts/module_gate.py",
    'OWN_ENGINE = {"files": {"filemanager"},\n',
    'OWN_ENGINE = {"files": {"filemanager"},\n              "cinema": {"player-mpv"},\n',
    '"cinema": {"player-mpv"}',
)
patch(
    ".github/workflows/jvm-tests.yml",
    ":settings:testDebugUnitTest --stacktrace",
    ":settings:testDebugUnitTest :player-mpv:assembleDebug --stacktrace",
    ":player-mpv:assembleDebug",
)
patch("docs/arsitektur.md", "| engine | `terminal-core`", "| engine | `player-mpv`, `terminal-core`", "`player-mpv`, `terminal-core`", optional=True)
patch(
    "docs/arsitektur.md",
    "- `player-mpv`: implementasi `MolinaPlayerSession` di atas `MPVLib`.",
    "- `player-mpv`: SUDAH ADA (P12): mpv-android penuh divendor, package `com.molina.player.mpv`. Implementasi `MolinaPlayerSession` di atasnya menyusul (tahap 2/3).",
    "SUDAH ADA (P12)",
    optional=True,
)

PROV = """
## player-mpv: vendoring mpv-android tahap 1 (P12)
- Sumber: https://github.com/mpv-android/mpv-android, commit `%s`, lisensi MIT (c) 2016 Ilya Zhuravlev, sfan5. Teks lengkap: `player-mpv/LICENSE`; ringkasan atribusi dan perubahan: `player-mpv/NOTICE-mpv-android`.
- Di-rename: `is.xyz.mpv` -> `com.molina.player.mpv`, `is.xyz.filepicker` -> `com.molina.player.filepicker`, termasuk fungsi JNI `Java_com_molina_player_mpv_MPVLib_*`.
- Dibersihkan: URL di luar allowlist (komentar), nama proyek/pengembang lama, docs/, fastlane/, README upstream. Header lisensi per berkas (MPL-2.0 file picker, LGPL-3.0 satu layout) dipertahankan; barisnya ditandai `gate:allow lisensi` agar lolos telemetry_gate.
- Belum ada: buildscripts native (libmpv, FFmpeg, libass, dll.) dan lisensinya (tahap 2), cacert.pem tetap dari upstream, pengaturan privasi Shield.
"""
if "## player-mpv: vendoring mpv-android tahap 1" in rd("docs/provenance.md"):
    print("[lewati ] sudah patch : docs/provenance.md")
else:
    edits["docs/provenance.md"] = rd("docs/provenance.md").rstrip("\n") + "\n" + PROV % PIN
    print("[diubah ] patch       : docs/provenance.md")

ENTRY = """
## P12 vendoring mpv-android tahap 1 -> :player-mpv (2026-11-17)
- [x] Modul library :player-mpv: app/src/{main,debug} upstream commit %s, package com.molina.player.mpv (JNI ikut), semua bahasa, Activity, pengaturan, file picker
- [x] Dibersihkan: URL non-allowlist, nama dev/proyek lama; tersisa hanya LICENSE, NOTICE-mpv-android, header lisensi per berkas, docs/provenance.md
- [x] Adaptasi library: tanpa launcher, atribut <application> dibuang, izin default digabung, BuildConfig VERSION_*, targetPackage = applicationId :app
- [x] Fix: BuildConfig.FLAVOR di MPVFilePickerFragment diganti false (modul tanpa flavor allstorage)
- [x] Gradle: katalog versi (media, preference), settings, module_gate (engine player-mpv, cinema -> player-mpv), jvm-tests (:player-mpv:assembleDebug)
- [ ] Verifikasi CI: jalankan jvm-tests manual (kompilasi :player-mpv: viewBinding, R non-transitive, material 1.12 vs 1.14 upstream); gate module/telemetry/secret
- [ ] Tahap 2: buildscripts native (libmpv, FFmpeg, libass, dll.) + profil FFmpeg Molina, workflow, jniLibs arm64; gerbang URL build; lisensi pustaka native
- [ ] Tahap 3: :app/:cinema memakai :player-mpv (ganti PlayerBackendStub), MolinaPlayerSession di atas MPVLib, bangun ulang privasi Shield (proxy, blacklist protokol, tls-verify, scrub log)
- [ ] Belum ada Activity yang bisa dijalankan sebelum .so ada (jangan hubungkan ke :app dulu: Activity VIEW upstream akan crash tanpa libmpv)
""" % PIN
if "## P12 vendoring mpv-android tahap 1" in rd("progres.md"):
    print("[lewati ] sudah patch : progres.md")
else:
    edits["progres.md"] = rd("progres.md").rstrip("\n") + "\n" + ENTRY
    print("[diubah ] patch       : progres.md")

# ------------------------------------------------------------------ tulis semua
for rel, content in list(out.items()) + list(edits.items()):
    full = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    if isinstance(content, bytes):
        open(full, "wb").write(content)
    else:
        open(full, "w", encoding="utf-8", newline="").write(content)
print("[dibuat ] %d berkas di %s/" % (len(out), MOD))
print("Selesai.")
