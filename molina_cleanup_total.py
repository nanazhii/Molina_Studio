#!/usr/bin/env python3
# Molina: pembersihan total mpv/Cinema. Jalankan dari root repo: python3 molina_cleanup_total.py
# Idempotent: aman dijalankan berulang.

# ================= tahap: remove_mpv =================
import pathlib, re, shutil

ROOT = pathlib.Path(".")
changed = []

def rm(p):
    p = ROOT / p
    if p.is_dir():
        shutil.rmtree(p); changed.append(f"hapus dir  {p}")
    elif p.exists():
        p.unlink(); changed.append(f"hapus file {p}")

def edit(path, fn):
    p = ROOT / path
    if not p.exists():
        return
    old = p.read_text(encoding="utf-8")
    new = fn(old)
    if new != old:
        p.write_text(new, encoding="utf-8"); changed.append(f"ubah       {path}")

# 1. Hapus modul, skrip, dan workflow mpv
for t in ["player-mpv", "third_party/mpv-android",
          "scripts/native_mpv.sh", "scripts/fetch_native.sh", "scripts/native_audit.sh",
          "scripts/ffmpeg_components_audit.sh", "native/molina_ffmpeg_profile.sh",
          "native/mpv.pins.env", "native/prebuilt.env", ".github/workflows/native-mpv.yml",
          ".github/workflows/mpv-android.yml"]:
    rm(t)

# 2. Gradle
edit("settings.gradle.kts", lambda s: s.replace('include(":player-mpv")\n', ""))
edit("app/build.gradle.kts", lambda s: re.sub(r'[ \t]*implementation\(project\(":(player-mpv|cinema)"\)\)\n', "", s))

# 3. Navigasi: hapus tab Cinema
edit("app/src/main/res/menu/bottom_nav.xml", lambda s: re.sub(
    r'[ \t]*<item android:id="@\+id/nav_cinema".*?/>\n', "", s, flags=re.S))

def main_activity(s):
    s = s.replace("import com.molina.cinema.CinemaFragment\n", "")
    s = s.replace('                R.id.nav_cinema -> "cinema"\n', "")
    s = s.replace('                "cinema" -> CinemaFragment()\n', "")
    s = s.replace("nav.selectedItemId = R.id.nav_cinema", "nav.selectedItemId = R.id.nav_files")
    s = s.replace("        // MPVActivity mengisi HOST_PLAYER; kembalikan saat layar utama di depan lagi.\n", "")
    return s
edit("app/src/main/java/com/molina/studio/MainActivity.kt", main_activity)

# 4. CI
edit(".github/workflows/build.yml", lambda s: re.sub(
    r"      - name: Pustaka native mpv \(terverifikasi SHA-256\)\n(?:        .*\n)+", "", s))
edit(".github/workflows/jvm-tests.yml", lambda s: s.replace(" :player-mpv:assembleDebug", ""))

# 5. Gate
def module_gate(s):
    s = s.replace('"engine-newpipe", "player-mpv", ', '"engine-newpipe", ')
    s = s.replace('              "player-mpv": {"player-subtitle", "player-spectrum"},\n', "")
    s = re.sub(r"# \[P17-SHIELD\].*?(?=# \[P18-LOADFILE\])", "", s, flags=re.S)
    s = re.sub(r"# \[P18-LOADFILE\].*?(?=\nif errors:)", "", s, flags=re.S)
    return s
edit("scripts/module_gate.py", module_gate)

def telemetry(s):
    return re.sub(
        r'BUILD_FILES = \{"scripts/native_wget.sh", "scripts/ensure_ndk.sh".*?\}\n',
        'BUILD_FILES = {"scripts/native_wget.sh", "scripts/ensure_ndk.sh"}\n', s, flags=re.S)
edit("scripts/telemetry_gate.py", telemetry)

edit("native/pins.env", lambda s: s.replace("# Pin sumber mpv: native/mpv.pins.env. Pin proot: native/proot.pins.env.",
                                            "# Pin proot: native/proot.pins.env."))

print("\n".join(changed) if changed else "Tidak ada perubahan (sudah bersih).")

# ================= tahap: remove_orphans =================
import pathlib, re, shutil

ROOT = pathlib.Path(".")
MODS = ["cinema", "player-api", "player-subtitle", "player-spectrum", "core-stream", "engine-newpipe"]
changed = []

def rm(p):
    p = ROOT / p
    if p.is_dir():
        shutil.rmtree(p); changed.append(f"hapus dir  {p}")
    elif p.exists():
        p.unlink(); changed.append(f"hapus file {p}")

def edit(path, fn):
    p = ROOT / path
    if not p.exists():
        return
    old = p.read_text(encoding="utf-8")
    new = fn(old)
    if new != old:
        p.write_text(new, encoding="utf-8"); changed.append(f"ubah       {path}")

# 1. Hapus modul + pin NewPipe
for m in MODS:
    rm(m)
rm("scripts/newpipe")

# 2. settings.gradle.kts: include + JitPack (khusus NewPipe)
def settings(s):
    for m in MODS:
        s = s.replace(f'include(":{m}")\n', "")
        s = s.replace(f', ":{m}"', "")
    s = re.sub(r'        // JitPack hanya untuk grup.*?\n        exclusiveContent \{.*?\n        \}\n', "", s, flags=re.S)
    return s
edit("settings.gradle.kts", settings)

# 3. :app
edit("app/build.gradle.kts", lambda s: re.sub(
    r'[ \t]*implementation\(project\(":(core-stream|engine-newpipe)"\)\)\n', "", s))
def app_class(s):
    s = s.replace("import com.molina.core.stream.StreamResolvers\n", "")
    s = s.replace("import com.molina.engine.newpipe.NewPipeStreamResolver\n", "")
    s = re.sub(r"        // Resolver stream dibuat malas.*?\n        StreamResolvers\.install \{ NewPipeStreamResolver\(\) \}\n", "", s)
    return s
edit("app/src/main/java/com/molina/studio/MolinaApplication.kt", app_class)

# 4. CI
def jvm(s):
    for t in [":core-stream:testDebugUnitTest", ":player-subtitle:testDebugUnitTest",
              ":player-spectrum:testDebugUnitTest", ":engine-newpipe:verifyNewPipePins",
              ":engine-newpipe:assembleDebug", ":cinema:testDebugUnitTest"]:
        s = s.replace(" " + t, "")
    return s
edit(".github/workflows/jvm-tests.yml", jvm)

# 5. Gate
def gate(s):
    s = s.replace('FEATURE = {"cinema", "files"', 'FEATURE = {"files"')
    s = s.replace('API = {"player-api", "terminal-api"}', 'API = {"terminal-api"}')
    s = s.replace('ENGINE = {"engine-newpipe", "player-subtitle", "player-spectrum", ', 'ENGINE = {')
    s = s.replace('OWN_API = {"cinema": {"player-api"}, "terminal": {"terminal-api"},', 'OWN_API = {"terminal": {"terminal-api"},')
    s = s.replace('              "cinema": set(),\n', "")
    return s
edit("scripts/module_gate.py", gate)

print("\n".join(changed) if changed else "Tidak ada perubahan (sudah bersih).")

# ================= tahap: clean_total =================
import pathlib, re, shutil

ROOT = pathlib.Path(".")
changed = []

def rm(p):
    p = ROOT / p
    if p.is_dir():
        shutil.rmtree(p); changed.append(f"hapus dir  {p}")
    elif p.exists():
        p.unlink(); changed.append(f"hapus file {p}")

def edit(path, *fns):
    p = ROOT / path
    if not p.exists():
        return
    old = p.read_text(encoding="utf-8")
    new = old
    for fn in fns:
        new = fn(new)
    if new != old:
        p.write_text(new, encoding="utf-8"); changed.append(f"ubah       {path}")

def rep(a, b):
    return lambda s: s.replace(a, b)

def rx(pat, b, flags=0):
    return lambda s: re.sub(pat, b, s, flags=flags)

SEC = "core-security/src"
# 1. core-security: kode khusus mpv
for f in ["main/java/com/molina/core/security/LavfOptions.kt", "main/java/com/molina/core/security/PlaybackGuard.kt",
          "test/java/com/molina/core/security/LavfOptionsTest.kt", "test/java/com/molina/core/security/PlaybackGuardTest.kt"]:
    rm(f"{SEC}/{f}")
edit(f"{SEC}/main/java/com/molina/core/security/ShieldContract.kt",
     rx(r"/\*\* Perlakuan arus langsung \(rtmp/rtsp\).*?\n}\n\n", "", re.S),
     rx(r"\n    val directStreams: DirectStreamPolicy = DirectStreamPolicy\.PREFLIGHT,", ""))
edit("settings/src/main/java/com/molina/settings/ShieldPrefs.kt",
     rep("import com.molina.core.security.DirectStreamPolicy\n", ""),
     rx(r'    private const val KEY_DIRECT = .*\n    private const val DIRECT_BLOCK = .*\n    private const val DIRECT_PREFLIGHT = .*\n', ""),
     rep("    /** Pemetaan murni (diuji tanpa Android). Nilai tak dikenal jatuh ke PREFLIGHT. */", "    /** Pemetaan murni (diuji tanpa Android). */"),
     rep("        stripReferer: Boolean,\n        directStreams: String?,\n", "        stripReferer: Boolean,\n"),
     rx(r"\n        directStreams = if \(directStreams == DIRECT_BLOCK\).*", ""),
     rx(r"\n            directStreams = p\.getString\(KEY_DIRECT, DIRECT_PREFLIGHT\),", ""),
     rx(r"\n            \.putString\(KEY_DIRECT, .*", ""))
edit("settings/src/test/java/com/molina/settings/ShieldPrefsTest.kt",
     rep("import com.molina.core.security.DirectStreamPolicy\n", ""),
     rx(r"    @Test fun blockMapsToBlock\(\) \{.*?\n    }\n\n    @Test fun unknownOrNullFallsBackToPreflight\(\) \{.*?\n    }\n\n", "", re.S),
     rep(', stripReferer = true, directStreams = "preflight")', ', stripReferer = true)'))
edit("settings/src/main/java/com/molina/settings/SecurityFragment.kt",
     rep("import com.molina.core.security.DirectStreamPolicy\n", ""),
     rx(r'            addView\(TextView\(ctx\)\.apply \{ text = "Arus langsung rtmp/rtsp".*?addView\(note\("Preflight tidak menutup DNS rebinding cepat; pilih tolak bila perlu\."\)\)\n', "", re.S),
     rep("Menyaring http/https pemutar lewat proxy lokal. Bila dimatikan, lalu lintas langsung tanpa penyaringan. Berlaku di pemutar yang dibuka berikutnya.",
         "Menyaring lalu lintas http/https lewat proxy lokal. Bila dimatikan, lalu lintas langsung tanpa penyaringan. Berlaku di sesi terminal yang dibuka berikutnya."),
     rep("Nyalakan hanya bila memutar dari server di rumah.", "Nyalakan hanya bila perlu mengakses server di jaringan rumah."),
     rep("Cinema memakainya pada pemutar yang dibuka berikutnya.", "Terminal memakainya pada sesi yang dibuka berikutnya."))

# 2. Ikon peluncur Cinema + pemilih ikon
rm("settings/src/main/java/com/molina/settings/IconPicker.kt")
edit("settings/src/main/java/com/molina/settings/SettingsFragment.kt", rx(r"            addView\(IconPicker\.build\(ctx\)\)\n", ""))
edit("app/src/main/AndroidManifest.xml",
     rx(r'        <activity-alias\s+android:name="\.IconAmber".*?</activity-alias>\n', "", re.S),
     rep("<!-- Satu alias aktif pada satu waktu; dipilih di Setelan (IconPicker). -->", "<!-- Alias peluncur tunggal. -->"))
for f in ["app/src/main/res/mipmap-anydpi-v26/ic_launcher_cinema.xml",
          "app/src/main/res/drawable/ic_launcher_cinema_monochrome.xml",
          "app/src/main/res/drawable/ic_launcher_cinema_foreground.xml",
          "app/src/main/res/drawable/ic_launcher_cinema_background.xml",
          "app/src/main/res/drawable/ic_nav_cinema.xml", "app/src/main/res/drawable/ic_nav_cinema_on.xml",
          "app/src/main/res/drawable/ic_nav_cinema_off.xml"]:
    rm(f)
# pengaman: pengguna yang sempat memilih ikon Cinema tidak kehilangan peluncur
def app_icon_guard(s):
    if "IconGreen" in s:
        return s
    s = s.replace("import android.app.Application\n", "import android.app.Application\nimport android.content.ComponentName\nimport android.content.pm.PackageManager\n")
    return s.replace("        BuildInfo.log(this)\n",
        "        BuildInfo.log(this)\n"
        "        // Alias ikon Cinema sudah dihapus: kembalikan alias peluncur ke status bawaan manifest.\n"
        "        packageManager.setComponentEnabledSetting(\n"
        "            ComponentName(this, \"com.molina.studio.IconGreen\"),\n"
        "            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP,\n"
        "        )\n", 1)
edit("app/src/main/java/com/molina/studio/MolinaApplication.kt", app_icon_guard)

# 3. Log viewer: jenis sesi Cinema
edit("settings/src/main/java/com/molina/settings/LogFilter.kt",
     rep("p<angka> = Cinema (mpv). */", "lainnya = OTHER. */"),
     rep("enum class SessionKind { TERMINAL, CINEMA, OTHER }", "enum class SessionKind { TERMINAL, OTHER }"),
     rep('    private val CINEMA_ID = Regex("p\\\\d+")\n', ""),
     rep("        CINEMA_ID.matches(id) -> SessionKind.CINEMA\n", ""),
     rep('Teks chip: "Term t1", "Cinema p1", atau', 'Teks chip: "Term t1" atau'),
     rep('        SessionKind.CINEMA -> "Cinema $id"\n', ""))
edit("settings/src/main/java/com/molina/settings/LogViewerFragment.kt",
     rep("Teal untuk sesi Terminal (t*), amber untuk Cinema (p*); null = gaya bawaan.", "Teal untuk sesi Terminal (t*); null = gaya bawaan."),
     rep("            SessionKind.CINEMA -> UiR.color.molina_cinema_accent\n", ""))
edit("settings/src/test/java/com/molina/settings/LogFilterBrandingTest.kt",
     rep("terminalIdsAreTealKindAndCinemaIdsAreAmberKind", "terminalIdsAreTealKind"),
     rep('        assertEquals(SessionKind.CINEMA, LogFilter.sessionKind("p3"))\n', '        assertEquals(SessionKind.OTHER, LogFilter.sessionKind("p3"))\n'),
     rep('        assertEquals("Cinema p2", LogFilter.sessionLabel("p2"))\n', ""))

# 4. Crash context / attribution
edit("core-common/src/main/java/com/molina/core/common/CrashContext.kt",
     rep('    const val HOST_PLAYER = "Player (mpv)"\n', ""),
     rep("[HOST_APP] atau [HOST_PLAYER] (MPVActivity).", "[HOST_APP]."),
     rep('Contoh: "Aplikasi > Cinema", atau "Player (mpv)"; "?"', 'Contoh: "Aplikasi > Berkas"; "?"'),
     rep("        if (h == HOST_PLAYER) return HOST_PLAYER\n", ""))
edit("core-log/src/main/java/com/molina/log/CrashReporter.kt",
     rep('    private val MPV_CLASS = Regex("(?i)(mpv|player)")\n\n', ""),
     rep('        "com.molina.player." to "mpv",\n', ""),
     rx(r'        if \(className\.startsWith\("com\.molina\.cinema\."\)\) \{\n.*?\n.*?\n        \}\n', "", re.S),
     rep("Crash native (SIGSEGV di libmpv) tidak lewat sini;", "Crash native (SIGSEGV di pustaka .so) tidak lewat sini;"))
edit("core-log/src/test/java/com/molina/log/CrashReporterTest.kt",
     rx(r'        assertEquals\("mpv", area\("com\.molina\.player\.mpv\.MPVLib"\)\)\n        assertEquals\("mpv", area\("com\.molina\.player\.filepicker\.FilePickerActivity"\)\)\n        assertEquals\("mpv", area\("com\.molina\.cinema\.LoggingMolinaPlayerSession"\)\)\n        assertEquals\("cinema", area\("com\.molina\.cinema\.CinemaFragment"\)\)\n', ""),
     rep('trace("com.molina.cinema.MpvNative")', 'trace("com.molina.files.Browser")'),
     rep('assertEquals("Molina/mpv", e.tag)', 'assertEquals("Molina/files", e.tag)'),
     rep('{ "Player (mpv)" })', '{ "Aplikasi > Berkas" })'),
     rep('trace("com.molina.player.MPVActivity")', 'trace("com.molina.files.Browser")'),
     rep('contains("mode=Player")', 'contains("mode=Aplikasi")'))
for f in ["core-log/src/test/java/com/molina/log/LogEventsTest.kt",
          "settings/src/test/java/com/molina/settings/LogReportEventsTest.kt"]:
    edit(f, rx(r"\bmpv\b", "net"))
edit("settings/src/test/java/com/molina/settings/LogReportTest.kt",
     rep("com.molina.cinema.Mpv.load(Mpv.kt:42)", "com.molina.files.Net.load(Net.kt:42)"), rx(r"\bmpv\b", "net"))

# 5. Folder Molina/cinema dan pintasan Berkas
edit("core-common/src/main/java/com/molina/core/common/MolinaPaths.kt",
     rep('    val cinema: File get() = File(root, "cinema")\n', ""),
     rep("listOf(root, logs, terminal, cinema)", "listOf(root, logs, terminal)"))
edit("files/src/main/java/com/molina/files/FilesNav.kt", rep('        "cinema" to "Cinema",\n', ""))
edit("files/src/test/java/com/molina/files/FilesNavTest.kt",
     rep('"Favorit", "Cinema", "Terminal"', '"Favorit", "Terminal"'), rep('"cinema"', '"terminal"'))
edit("files/src/test/java/com/molina/files/BrowserPathTest.kt", rep("cinema", "media"))
st = "core-storage/src/test/java/com/molina/core/storage/"
edit(st + "LocalMolinaStorageTest.kt", rep(' && File(root, "cinema").isDirectory)', ")"))
edit(st + "LocalMolinaStorageTrashTest.kt", rep('"cinema"', '"terminal"'))
edit(st + "TrashBinTest.kt", rep('dir(r, "cinema")', 'dir(r, "terminal")'))
edit(st + "ExplorerFavoritesTest.kt", rep("cinema", "media"))
edit(st + "ScopedKeyValueStoreTest.kt", rep("cinema", "media"))
edit(st + "ArchiveNamesTest.kt", rep('"Cinema"', '"Arsip"'))

# 6. Token warna & tema: cinema -> brand
def brand(s):
    s = s.replace("molina_on_cinema_accent", "molina_on_brand_accent").replace("molina_cinema_", "molina_brand_")
    return s.replace("Theme.Molina.Cinema", "Theme.Molina.Brand").replace("Theme_Molina_Cinema", "Theme_Molina_Brand")
for p in list(ROOT.glob("*/src/**/*.kt")) + list(ROOT.glob("*/src/**/*.xml")):
    edit(str(p), brand)

# 7. Teks UI & komentar
edit("settings/src/main/java/com/molina/settings/AboutFragment.kt", rep("Molina Studio: Terminal dan Cinema", "Molina Studio: Terminal, Berkas, dan Code"))
edit("app/src/main/java/com/molina/studio/ShieldBootstrap.kt", rep("SEBELUM sesi mpv pertama dibuat", "SEBELUM sesi terminal pertama dibuat"))
edit("app/src/main/java/com/molina/studio/AppExit.kt", rep("kait modul (mpv dilepas rapi)", "kait modul (sumber daya dilepas rapi)"))
edit("core-log/src/main/java/com/molina/log/ExitHooks.kt", rep("(mis. mpv)", "(mis. proxy Shield)"))
edit("core-log/src/main/java/com/molina/log/MolinaLogger.kt", rep("Molina/mpv/p1", "Molina/proot"))
edit("core-ui/src/main/java/com/molina/core/ui/FileKind.kt", rep("drawer Terminal, Berkas, dan Cinema", "drawer Terminal dan Berkas"))
edit("files/src/main/java/com/molina/files/PropsSheet.kt", rep("gaya Molina Cinema", "gaya Molina"))
edit("code/src/main/kotlin/com/molina/code/ui/CodeColors.kt", rep('"Molina Cinema"', '"Molina"'))
edit("code/src/main/kotlin/com/molina/code/ui/LiveAndroid.kt", rep("(keluarga Molina Cinema, sama dengan penanda aktif pemutar)", "(keluarga aksen hijau Molina)"))
edit("README.md", rx(r"- JNI mpv \(`com\.molina\.player\.mpv\.MPVLib`\) tidak terpengaruh\.\n", ""))
edit(".gitignore", rep("(pipeline mpv)", "(pipeline native)"))
edit("native/pins.env", rep("pipeline native (mpv, proot)", "pipeline native (proot)"),
     rx(r"# NDK harus sama dengan v_ndk.*\n", ""))
edit("native/proot.pins.env", rep("sama dengan pipeline mpv (native/pins.env", "sama dengan pipeline native lain (native/pins.env"))
edit("core-storage/src/test/java/com/molina/core/storage/ArchiveNamesTest.kt", lambda s: s)

print("\n".join(changed) if changed else "Tidak ada perubahan (sudah bersih).")

# ================= tahap: clean_total2 =================
import pathlib
ROOT = pathlib.Path(".")
changed = []
def rm(p):
    p = ROOT / p
    if p.exists():
        p.unlink(); changed.append(f"hapus file {p}")
def edit(path, *pairs):
    p = ROOT / path
    if not p.exists():
        return
    old = p.read_text(encoding="utf-8"); new = old
    for a, b in pairs:
        new = new.replace(a, b)
    if new != old:
        p.write_text(new, encoding="utf-8"); changed.append(f"ubah       {path}")
S = "core-security/src/"
rm(S + "main/java/com/molina/core/security/ShieldHost.kt")
rm(S + "test/java/com/molina/core/security/ShieldHostTest.kt")
m = S + "main/java/com/molina/core/security/"
edit(m + "LocalShieldProxy.kt", ("http/https mesin pemutar lewat sini", "http/https klien (mis. terminal/PRoot) lewat sini"))
edit(m + "ShieldContract.kt",
     ("Alamat proxy lokal untuk mesin pemutar.", "Alamat proxy lokal untuk klien jaringan."),
     ("Kontrak bersama Molina Shield. Cinema, Terminal, dan Setelan", "Kontrak bersama Molina Shield. Terminal dan Setelan"),
     ("Alamat proxy untuk mesin pemutar, atau null", "Alamat proxy untuk klien jaringan, atau null"))
edit(m + "ShieldRegistry.kt", ("Modul engine (mis. mpv-native) membaca", "Modul engine (mis. terminal-proot) membaca"))
edit(m + "UrlPolicy.kt", ("Kebijakan URL untuk semua yang akan dibuka mpv atau yt-dlp.", "Kebijakan URL untuk semua yang akan dibuka klien jaringan."))
edit(m + "ShieldPolicy.kt", ("tidak pernah dibutuhkan pemutar media", "tidak pernah dibutuhkan lalu lintas web biasa"))
edit("settings/src/main/java/com/molina/settings/ShieldSummary.kt", ("Lalu lintas pemutar tidak disaring.", "Lalu lintas jaringan tidak disaring."),
     ("(teal vs amber)", "(teal vs aksen peringatan)"))
edit("app/src/main/java/com/molina/studio/MolinaApplication.kt", ("Alias ikon Cinema sudah dihapus:", "Alias ikon alternatif sudah dihapus:"))
print("\n".join(changed) if changed else "Tidak ada perubahan (sudah bersih).")

# --- sisa kecil: komentar rtmp/rtsp dan impor tak terpakai
changed2 = []
def edit2(path, *pairs):
    p = ROOT / path
    if not p.exists():
        return
    old = p.read_text(encoding="utf-8"); new = old
    for a, b in pairs:
        new = new.replace(a, b)
    if new != old:
        p.write_text(new, encoding="utf-8"); changed2.append(f"ubah       {path}")
edit2("settings/src/main/java/com/molina/settings/ShieldPrefs.kt", (", LAN ditolak, rtmp/rtsp diperiksa dulu.", ", LAN ditolak."))
edit2("app/src/main/java/com/molina/studio/ShieldBootstrap.kt", (", LAN ditolak, rtmp/rtsp diperiksa dulu.", ", LAN ditolak."))
edit2("settings/src/main/java/com/molina/settings/SecurityFragment.kt",
      ("import android.widget.RadioButton\n", ""), ("import android.widget.RadioGroup\n", ""))
edit2("files/src/main/java/com/molina/files/PropsSheet.kt", (", selaras Berkas (sebelumnya amber cinema)", ", selaras Berkas"))
print("\n".join(changed2) if changed2 else "")

# ================= tahap: clean_docs =================
import pathlib, re
ROOT = pathlib.Path(".")
changed = []
def edit(path, *fns):
    p = ROOT / path
    if not p.exists():
        return
    old = p.read_text(encoding="utf-8"); new = old
    for f in fns:
        new = f(new)
    if new != old:
        p.write_text(new, encoding="utf-8"); changed.append(f"ubah       {path}")
def rep(a, b): return lambda s: s.replace(a, b)
def rx(pat, b, fl=0): return lambda s: re.sub(pat, b, s, flags=fl)

edit("docs/arsitektur.md",
     rep("nav 5 tab (Cinema, Berkas, Code, Terminal, Setelan)", "nav 4 tab (Berkas, Code, Terminal, Setelan)"),
     rep("| feature | `cinema`, `files`,", "| feature | `files`,"),
     rep("| api | `player-api`, `terminal-api` |", "| api | `terminal-api` |"),
     rep("| engine | `player-mpv`, `terminal-core`,", "| engine | `terminal-core`,"),
     rep("(JNI mpv, proses terminal)", "(proses terminal, PTY, PRoot)"),
     rep("(`com.molina.cinema`, `com.molina.terminal.core`) agar JNI `Java_com_molina_cinema_MPVLib_*` dan impor tetap aman.", "(mis. `com.molina.terminal.core`) agar impor tetap aman."),
     rx(r"- `:mpv-native` dihapus \(P11\)\..*\n", ""),
     rx(r"## Modul yang direncanakan \(belum dibuat\)\n- `player-mpv`: SUDAH ADA.*\n", "## Modul lain\n"))
edit("docs/branding.md",
     rx(r"- Cinema: ikon layar dengan segitiga play.*\n", ""),
     rx(r"- Ikon Cinema \(`@mipmap/ic_launcher_cinema`\).*\n", ""),
     rx(r"## Pemilih ikon dan Tentang\n- Pemilih ikon:.*\n- Label peluncur ikut berganti:.*\n",
        "## Ikon dan Tentang\n- Satu ikon peluncur: `activity-alias` `.IconGreen` (aktif bawaan) menunjuk `.MainActivity`. Pemilih ikon dan ikon alternatif dihapus; `MolinaApplication` mengembalikan alias ke status bawaan manifest.\n"),
     rx(r"## Rencana pemisahan Molina Player\n(?:.*\n)*?(?=\n## Baris versi di log)", ""),
     rep("(mpv, term, proot, files, dst.,", "(term, proot, files, dst.,"))
edit("docs/memory-design.md",
     rep("(Cinema/mpv, Berkas, Terminal, Setelan)", "(Berkas, Code, Terminal, Setelan)"),
     rx(r"\| mpv \| 1 sesi aktif \|.*\n\| mpv cache \|.*\n", ""),
     rep("kait modul (mpv `release`)", "kait modul (mis. proxy Shield)"),
     rx(r"- Lepas sesi mpv bila dijeda.*\n", ""),
     rep("- Setelan batas cache mpv dan baris transcript terminal.", "- Setelan batas baris transcript terminal."))
edit("docs/penamaan.md",
     rep("(`MolinaTerminalSession`, `MolinaPlayerSession`)", "(`MolinaTerminalSession`)"),
     rep("(`LoggingTerminalSession`, `LoggingMolinaPlayerSession`)", "(`LoggingTerminalSession`)"),
     rep("`Molina/mpv/p1`", "`Molina/proot`"),
     rx(r"- Package pemutar: ditetapkan ulang.*\n", ""))
edit("docs/tema.md",
     rx(r"- Cinema \(Home Theater, ala Linux Mint, patch C1\):", "- Aksen hijau merek (token `molina_brand_*`, dulu `molina_cinema_*`; ala Linux Mint):"),
     rx(r"  Catatan: `Theme\.Molina\.App` memakai token `molina_cinema_\*`.*\n",
        "  Catatan: `Theme.Molina.App` memakai token `molina_brand_*` sebagai warna sekunder.\n"),
     rep("`Theme.Molina.Cinema`, `Theme.Molina.Terminal`", "`Theme.Molina.Brand`, `Theme.Molina.Terminal`"),
     rep("Theme_Molina_Cinema", "Theme_Molina_Brand"))
edit("docs/log-contract.md",
     rep("`core`, `app`, `term`, `proot`, `mpv`, `cinema`, `files`, `settings`", "`core`, `app`, `term`, `proot`, `files`, `code`, `settings`"),
     rep("Crash native (SIGSEGV di libmpv)", "Crash native (SIGSEGV di pustaka .so)"),
     rep("`molina_cinema_accent`", "`molina_brand_accent`"),
     rep("Term t1 | Cinema p1 | ...", "Term t1 | ..."),
     rep("(teal untuk sesi Terminal `t*`, amber untuk Cinema/mpv `p*`)", "(teal untuk sesi Terminal `t*`)"),
     rx(r"## Kode baku: mpv\n(?:.*\n)*?Path dan URL media tidak pernah dicatat; hanya nama berkas\.\n\n", ""),
     rep("(cinema, files, terminal, settings)", "(files, code, terminal, settings)"),
     rep("(mis. mpv release)", "(mis. proxy Shield)"))
edit("docs/provenance.md",
     rx(r"\| mpv \| bcde6b93cca4 \|.*\n\| mpv-android \| ebf1b3f0cbe9 \|.*\n\| NewPipe \| 9486b0148af8 \|.*\n", ""),
     rep("- Komponen yang di-link (libmpv, FFmpeg, dan lainnya)", "- Komponen native yang di-link (mis. proot, 7-Zip)"),
     rx(r"Lisensi libmpv dan FFmpeg bergantung pada opsi build; periksa sebelum rilis\.\n", ""),
     rx(r"## Pin build native \(P3\)\n(?:.*\n)*?(?=\n## Berkas adaptasi: termux-app)",
        "## Pin build native\n\nSumber build native dipin di `native/pins.env` (NDK), `native/proot.pins.env` (proot, talloc), dan `native/pins.sha256` (hash unduhan).\nUbah sadar dan catat di progres.md.\n"),
     rx(r"## Aset: cacert\.pem \(Patch A\).*\n(?:.*\n)*?(?=## Editor Code)", ""),
     rx(r"\n## player-mpv: vendoring mpv-android tahap 1 \(P12\)\n(?:.*\n?)*$", "\n"))
edit("docs/security.md",
     rep("Feature (Cinema, Terminal, Setelan)", "Feature (Terminal, Setelan)"),
     rep("- Tidak mencakup rtmp/rtsp (tidak lewat proxy HTTP) dan proses lain seperti yt-dlp di Debian.", "- Hanya http/https. Protokol lain (mis. ssh, rtmp) dan proses di Debian yang mengabaikan variabel proxy tidak tersaring."),
     rx(r"## Rencana penyambungan \(belum dikerjakan\)\n(?:.*\n)*?(?=\n## Cakupan diperluas)",
        "## Penyambungan\n1. `:app` membuat `LocalShieldProxy` dan memanggil `start()` saat proses mulai (`ShieldBootstrap`).\n2. Setelan: sakelar Shield, pelacak, LAN, Referer, ringkasan dari `snapshot` dan `recentBlocks()` (SELESAI: Setelan > Keamanan, `ShieldPrefs`). Daftar pengecualian pelacak belum punya UI.\n3. Pengumpul `AppFootprint` di lapisan Android, pemindaian dijalankan atas permintaan pengguna (belum dikerjakan).\n"),
     rx(r"\| http/https mpv \|.*\n\| Shield menyala tetapi proxy mati \|.*\n\| Shield dimulai ulang \|.*\n\| rtmp/rtmps/rtsp \|.*\n", ""),
     rx(r"Batas: preflight rtmp/rtsp tidak menutup DNS rebinding cepat.*\nUji perangkat wajib:.*\n",
        "Uji perangkat wajib: curl https publik, IP LAN (harus ditolak), dan Shield dimatikan lewat prefs.\n"),
     rx(r"\| Cinema \| Sumber ditolak:.*\n", ""))
print("\n".join(changed) if changed else "Tidak ada perubahan (sudah bersih).")

# ================= tahap: komentar desugaring =================
import pathlib as _pl
_p = _pl.Path("app/build.gradle.kts")
if _p.exists():
    _s = _p.read_text(encoding="utf-8")
    _n = _s.replace(
        "    // NewPipeExtractor memakai API Java 11+ (mis. List.of); API 26 memerlukan desugaring.\n",
        "    // Core library desugaring dulu diperlukan NewPipeExtractor (API Java 11+). Tetap aktif sampai CI membuktikan\n"
        "    // tidak ada kode lain yang membutuhkannya; setelah itu lepas bersama isCoreLibraryDesugaringEnabled.\n")
    if _n != _s:
        _p.write_text(_n, encoding="utf-8"); print("ubah       app/build.gradle.kts (komentar desugaring)")
