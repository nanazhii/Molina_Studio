#!/usr/bin/env python3
"""Port editor Code dari repo lama (Molina-Studio) ke repo baru (Molina_Studio) sebagai modul :code.

Jalankan dari root repo baru:   python3 port_editor.py
Idempotent: aman dijalankan berulang. Tidak melakukan commit/push.
Dikecualikan dari editor lama: palet perintah, jadikan folder proyek, riwayat proyek, aksi file (entri menu),
bagikan (entri menu), kirim ke skrip execution, simpan ke vault, sisipkan ke terminal, jalankan ke terminal.
"""
import os, re, sys, shutil, subprocess, tempfile, pathlib

OLD_URL = "https://github.com/nanazhii/Molina-Studio"
ROOT = pathlib.Path.cwd()

def say(m): print(m, flush=True)
def read(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write(p, s):
    p = pathlib.Path(p); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(s, encoding="utf-8")
def die(m): print("GAGAL: " + m, file=sys.stderr); sys.exit(1)

# ======================================================================= alat transformasi Kotlin
def indent(s): return len(s) - len(s.lstrip(" "))

def scan_depth(line, depth):
    i, n, in_str = 0, len(line), False
    while i < n:
        c = line[i]
        if in_str:
            if c == "\\": i += 2; continue
            if c == '"': in_str = False
        else:
            if c == '"': in_str = True
            elif c == "'":
                m = re.match(r"'(?:\\.|[^'\\])'", line[i:])
                if m: i += m.end(); continue
            elif c == "/" and i + 1 < n and line[i + 1] == "/": break
            elif c in "({": depth += 1
            elif c in ")}": depth -= 1
        i += 1
    return depth

def decl_re(name):
    return re.compile(
        r"^(\s*)(?:(?:internal|private|public|override|inline|suspend|operator|lateinit|const|open|protected)\s+)*"
        r"(?:fun|var|val|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?" + re.escape(name) + r"\b")

def remove_member(text, name):
    lines = text.split("\n"); rx = decl_re(name); count = 0; i = 0
    while i < len(lines):
        m = rx.match(lines[i])
        if not m: i += 1; continue
        base = len(m.group(1)); start = i; k = i - 1
        while k >= 0 and lines[k].strip() and indent(lines[k]) >= base and lines[k].lstrip().startswith(("/**", "*", "//", "@")):
            start = k; k -= 1
        end = i; j = i + 1
        while j < len(lines):
            l = lines[j]
            if not l.strip(): j += 1; continue
            ind = indent(l)
            if ind > base: end = j; j += 1; continue
            if ind == base and l.lstrip()[0] in "})": end = j; break
            break
        del lines[start:end + 1]
        if 0 < start < len(lines) and not lines[start].strip() and not lines[start - 1].strip(): del lines[start]
        count += 1; i = start
    return "\n".join(lines), count

def remove_menu_entry(text, label):
    lines = text.split("\n"); key = 'MenuEntry("' + label
    for i, l in enumerate(lines):
        if key in l:
            depth, j = 0, i
            while True:
                depth = scan_depth(lines[j], depth)
                if depth <= 0: break
                j += 1
            del lines[i:j + 1]
            return "\n".join(lines), True
    return text, False

def remove_lines_containing(text, needle):
    lines = text.split("\n"); keep = [l for l in lines if needle not in l]
    return "\n".join(keep), len(lines) - len(keep)

# ======================================================================= 1. repo lama
def ensure_old():
    d = pathlib.Path(os.environ.get("MOLINA_OLD_DIR") or (pathlib.Path(tempfile.gettempdir()) / "molina-old-src"))
    if (d / ".git").is_dir():
        subprocess.run(["git", "-C", str(d), "pull", "--ff-only", "-q"], check=False)
    else:
        if d.exists(): shutil.rmtree(d)
        r = subprocess.run(["git", "clone", "--depth", "1", "-q", OLD_URL, str(d)])
        if r.returncode != 0: die("clone repo lama gagal: " + OLD_URL)
    if not (d / "feature/code/src/main/kotlin").is_dir(): die("struktur repo lama tidak dikenal (feature/code)")
    return d

# ======================================================================= 2. modul :code
FRAGMENT_KT = '''package com.molina.code

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.molina.code.ui.CodeScreen

/** Tab Code: menampung editor Compose. Sesi hidup di [CodeSessionHolder] sehingga pindah tab tidak menutup tab editor. */
class CodeFragment : Fragment() {
    private val holder: CodeSessionHolder by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { CodeScreen(holder.session, Modifier.fillMaxSize()) }
        }

    override fun onResume() {
        super.onResume()
        holder.session.checkFolderChanges()
    }

    override fun onStop() {
        holder.session.flushDrafts()
        super.onStop()
    }
}
'''

HOLDER_KT = '''package com.molina.code

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.molina.code.ui.CodeSession
import com.molina.code.ui.RunStore
import com.molina.core.common.MolinaPaths
import java.io.File

/** ViewModel Activity: sesi editor (tab terbuka, draf, riwayat) bertahan saat pindah tab dan rotasi layar. */
class CodeSessionHolder(app: Application) : AndroidViewModel(app) {
    val session: CodeSession = CodeSession(MolinaPaths().root.path, File(app.filesDir, "code-drafts")).also { s ->
        val prefs = app.getSharedPreferences("molina_code_run", Context.MODE_PRIVATE)
        s.runStore = object : RunStore {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        }
    }

    override fun onCleared() {
        session.releaseAll()
    }
}
'''

CODE_GRADLE = '''plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.molina.code"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
dependencies {
    implementation(project(":core-common"))
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    implementation(libs.kotlinx.coroutines.android)
}
'''

def transform(mod, stage_kt_old):
    K = f"{mod}/src/main/kotlin/com/molina/code"
    UI, G, H, P = f"{K}/ui", f"{K}/guard", f"{K}/history", f"{K}/preview"
    DELETE = [f"{UI}/CommandPalette.kt", f"{UI}/PythonRunDialog.kt", f"{UI}/ProjectLogDialog.kt",
              f"{UI}/ProjectCommitDialog.kt", f"{UI}/ProjectDiffDialog.kt", f"{UI}/ProjectFileDialog.kt",
              f"{UI}/ProjectRestoreDialog.kt", f"{UI}/TerminalRunUndo.kt",
              f"{G}/ProjectMarker.kt", f"{G}/ProjectStore.kt", f"{G}/ProjectCommit.kt", f"{G}/ProjectRestore.kt",
              f"{G}/NanoRunSnap.kt", f"{H}/NanoInbox.kt", f"{P}/PythonTerminalRun.kt"]
    # PaletteScan (dipakai Struktur dan Cari di berkas) dipindah dari CommandPalette.kt
    lines = read(f"{UI}/CommandPalette.kt").split("\n")
    a = next(i for i, l in enumerate(lines) if l.startswith("internal object PaletteScan"))
    b = next(i for i in range(a, len(lines)) if lines[i] == "}")
    write(f"{UI}/PaletteScan.kt", "package com.molina.code.ui\n\nimport java.io.File\nimport java.nio.file.Files\n\n"
          "/** Pemindai berkas folder kerja untuk Pergi ke simbol dan Cari di berkas. */\n" + "\n".join(lines[a:b + 1]) + "\n")
    # ProjectLog -> ChangeEntry (model data notifikasi perubahan, tanpa log proyek .molina)
    t = read(f"{G}/ProjectLog.kt").replace("object ProjectLog", "object ChangeEntry")
    for n in ("isEnabled", "append", "read", "prune", "lastByte", "FILE", "MAX_ENTRIES"):
        t, _ = remove_member(t, n)
    t = t.replace("import java.io.FileOutputStream\n", "").replace("import java.io.RandomAccessFile\n", "")
    write(f"{G}/ChangeEntry.kt", t); os.remove(f"{G}/ProjectLog.kt")
    for f in DELETE: os.remove(f)
    for p in pathlib.Path(K).rglob("*.kt"):
        s = read(p)
        if re.search(r"\bProjectLog\b", s): write(p, re.sub(r"\bProjectLog\b", "ChangeEntry", s))
    # CodeSession
    cs = f"{UI}/CodeSession.kt"; t = read(cs)
    for n in ["pickProjectFolder", "createProjectAt", "openProjectLog", "commitProject", "restoreProjectFile",
              "restoreProjectAll", "projectLogView", "recordProjectLog", "recordUndoLog", "importNanoRunSnaps",
              "importNanoInbox", "nanoInboxDir", "nanoStorageRoot", "nanoLock", "scriptSink", "terminalSink",
              "vaultSink", "saveToVault", "sendToTerminal", "sendToScripts", "pyRunOpen", "guestPathResolver",
              "lastTerminalRun", "runLang", "runAsk", "saveRunAsk", "runDialogAsks", "terminalRunUndoReady",
              "undoTerminalRun", "runInTerminalForLang", "runScriptFile", "runScriptFileAt", "snippetSink",
              "saveSelectionAsSnippet", "paletteOpen", "kindOf", "checkedByMolinaRun"]:
        t, _ = remove_member(t, n)
    drop_imp = r"import com\.molina\.code\.\w+\.(NanoInbox|NanoRunSnap|ProjectCommit|ProjectRestore|ProjectStore|PythonTerminalRun|TerminalRunLang|ScriptShare|ScriptSink)\b"
    t = "\n".join(l for l in t.split("\n") if not re.match(drop_imp, l))
    for needle in ("importNanoInbox() //", "recordProjectLog(snap, rep, runTitleNow)", "recordUndoLog(c.snap, res)"):
        t, _ = remove_lines_containing(t, needle)
    write(cs, t)
    hd = f"{UI}/HistoryDialog.kt"
    write(hd, read(hd).replace("session.importNanoInbox(); store.list(file) } // [NANO-INBOX]", "store.list(file) }"))
    # Menu titik tiga
    ov = f"{UI}/OverflowMenu.kt"; t = read(ov)
    for label in ["Palet perintah", "Jadikan folder proyek", "Riwayat proyek", "Aksi file", "Bagikan",
                  "Kirim ke Script Execution", "Simpan ke Vault", "Sisipkan ke Terminal", "Jalankan di Terminal",
                  "Simpan sebagai snippet"]:
        t, ok = remove_menu_entry(t, label)
        if not ok: die(f"entri menu tidak ditemukan: {label} (repo lama berubah?)")
    t = re.sub(r"listOf\(\s*\),\s*\n", "", t, count=1)
    t, _ = remove_member(t, "shareText"); t, _ = remove_member(t, "MAX_SHARE_CHARS")
    write(ov, t)
    write(f"{UI}/ShareText.kt", '''package com.molina.code.ui

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
''')
    scr = f"{UI}/CodeScreen.kt"; t = read(scr)
    for needle in ("session.paletteOpen", "PythonRunDialog", "session.projectLogView"):
        t, _ = remove_lines_containing(t, needle)
    write(scr, t)

def build_module(old):
    dest = ROOT / "code"
    marker = dest / "src/main/kotlin/com/molina/code/guard/ChangeEntry.kt"
    if marker.exists(): say("= modul code sudah ada, lewati penyalinan"); return
    if dest.exists(): die("folder code/ sudah ada tapi bukan hasil port ini. Pindahkan dulu lalu jalankan ulang.")
    stage = pathlib.Path(tempfile.mkdtemp(prefix="molina-port-")); mod = stage / "code"
    shutil.copytree(old / "feature/code/src/main", mod / "src/main")
    K = mod / "src/main/kotlin/com/molina/code"
    pv = old / "feature/preview/src/main/kotlin/com/molina"
    shutil.copytree(pv / "code/preview", K / "preview", dirs_exist_ok=True)
    shutil.copytree(pv / "preview", mod / "src/main/kotlin/com/molina/preview")
    ex = old / "core/exec/src/main/kotlin/com/molina/code"
    shutil.copy(ex / "run/ScriptExecutor.kt", K / "run/ScriptExecutor.kt")
    shutil.copy(ex / "lsp/LspTransport.kt", K / "lsp/LspTransport.kt")
    shutil.copy(ex / "preview/RunPlan.kt", K / "preview/RunPlan.kt")
    shutil.copy(ex / "preview/CommandBlockSpec.kt", K / "preview/CommandBlockSpec.kt")
    transform(str(mod), None)
    write(mod / "build.gradle.kts", CODE_GRADLE)
    write(K / "CodeFragment.kt", FRAGMENT_KT)
    write(K / "CodeSessionHolder.kt", HOLDER_KT)
    shutil.copytree(mod, dest)
    shutil.rmtree(stage, ignore_errors=True)
    n = sum(1 for _ in dest.rglob("*") if _.is_file())
    say(f"+ modul code/ dibuat ({n} berkas)")

# ======================================================================= 3. penyambungan repo
def edit(path, fn, label=None):
    p = ROOT / path
    if not p.exists(): die(f"berkas tidak ada: {path}")
    s = read(p); n = fn(s)
    if n != s: write(p, n); say(f"~ {path}")
    else: say(f"= {path} (sudah)")

def toml_add(s, section, key, line):
    if re.search(r"^" + re.escape(key) + r"\s*=", s, re.M): return s
    lines = s.split("\n"); start = next((i for i, l in enumerate(lines) if l.strip() == f"[{section}]"), None)
    if start is None: die(f"seksi [{section}] tidak ada di libs.versions.toml")
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("[")), len(lines))
    k = end - 1
    while k > start and not lines[k].strip(): k -= 1
    lines.insert(k + 1, line)
    return "\n".join(lines)

def icon_xml(alpha):
    grad = '''<aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="2" android:startY="2"
                android:endX="22" android:endY="22">
                <item android:offset="0.0" android:color="#FF5252" />
                <item android:offset="0.5" android:color="#00E676" />
                <item android:offset="1.0" android:color="#448AFF" />
            </gradient>
        </aapt:attr>'''
    def path(d, w):
        return f'''    <path
        android:pathData="{d}"
        android:strokeWidth="{w}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round">
        {grad}
    </path>
'''
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n    android:width="24dp" android:height="24dp"\n'
            f'    android:viewportWidth="24" android:viewportHeight="24"\n    android:alpha="{alpha}">\n'
            + path("M5,3.5 L19,3.5 A2.5,2.5 0 0 1 21.5,6 L21.5,18 A2.5,2.5 0 0 1 19,20.5 L5,20.5 A2.5,2.5 0 0 1 2.5,18 L2.5,6 A2.5,2.5 0 0 1 5,3.5 Z", "1.8")
            + path("M9.5,9 L6.5,12 L9.5,15", "2") + path("M14.5,9 L17.5,12 L14.5,15", "2") + path("M13,8.5 L11,15.5", "2")
            + '</vector>\n')

def wire():
    # Gradle: katalog versi, plugin Compose, modul, dependensi app
    def catalog(s):
        s = toml_add(s, "versions", "composeBom", 'composeBom = "2024.12.01"')
        s = toml_add(s, "versions", "activityCompose", 'activityCompose = "1.9.3"')
        s = toml_add(s, "versions", "sora", 'sora = "0.23.6"')
        s = toml_add(s, "libraries", "androidx-compose-bom", 'androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }')
        s = toml_add(s, "libraries", "androidx-compose-ui", 'androidx-compose-ui = { module = "androidx.compose.ui:ui" }')
        s = toml_add(s, "libraries", "androidx-compose-foundation", 'androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }')
        s = toml_add(s, "libraries", "androidx-activity-compose", 'androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }')
        s = toml_add(s, "libraries", "sora-editor", 'sora-editor = { module = "io.github.Rosemoe.sora-editor:editor", version.ref = "sora" }')
        s = toml_add(s, "libraries", "sora-language-textmate", 'sora-language-textmate = { module = "io.github.Rosemoe.sora-editor:language-textmate", version.ref = "sora" }')
        s = toml_add(s, "plugins", "kotlin-compose", 'kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }')
        return s
    edit("gradle/libs.versions.toml", catalog)
    edit("build.gradle.kts", lambda s: s if "kotlin.compose" in s else s.replace(
        "    alias(libs.plugins.kotlin.android) apply false\n",
        "    alias(libs.plugins.kotlin.android) apply false\n    alias(libs.plugins.kotlin.compose) apply false\n"))
    edit("settings.gradle.kts", lambda s: s if 'include(":code")' in s else s.rstrip("\n") + '\ninclude(":code")\n')
    edit("app/build.gradle.kts", lambda s: s if 'project(":code")' in s else s.replace(
        '    implementation(project(":settings"))\n', '    implementation(project(":settings"))\n    implementation(project(":code"))\n'))
    # Gerbang CI
    edit("scripts/module_gate.py", lambda s: s if '"code"' in s else s.replace(
        'FEATURE = {"cinema", "files", "terminal", "settings"}', 'FEATURE = {"cinema", "files", "terminal", "settings", "code"}'))
    def tg(s):
        if "VENDORED_PREFIXES" in s: return s
        s = s.replace('DONATION_HOSTS = {', '# Grammar TextMate pihak ketiga (data murni, URL provenance upstream di metadata). Hanya aturan URL yang dilonggarkan;\n'
                      '# pola pelacak, identitas lama, dan donasi (peringatan) tetap berlaku. Lihat docs/provenance.md.\n'
                      'VENDORED_PREFIXES = ("code/src/main/assets/textmate/",)\n\nDONATION_HOSTS = {', 1)
        s = s.replace('    for m in URL_RE.finditer(line):\n', '    vendored = rel.replace(os.sep, "/").startswith(VENDORED_PREFIXES)\n    for m in ([] if vendored else URL_RE.finditer(line)):\n', 1)
        return s
    edit("scripts/telemetry_gate.py", tg)
    # Navigasi: tab ke-5 "Code" (setelah Berkas)
    for name, a in (("on", "1.0"), ("off", "0.5")):
        p = ROOT / f"app/src/main/res/drawable/ic_nav_code_{name}.xml"
        if not p.exists(): write(p, icon_xml(a)); say(f"+ {p.relative_to(ROOT)}")
    sel = ROOT / "app/src/main/res/drawable/ic_nav_code.xml"
    if not sel.exists():
        write(sel, '<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n'
              '    <item android:state_checked="true" android:drawable="@drawable/ic_nav_code_on" />\n'
              '    <item android:state_selected="true" android:drawable="@drawable/ic_nav_code_on" />\n'
              '    <item android:drawable="@drawable/ic_nav_code_off" />\n</selector>\n'); say(f"+ {sel.relative_to(ROOT)}")
    def menu(s):
        if "nav_code" in s: return s
        needle = '        android:title="Berkas" />\n'
        if needle not in s: die("bottom_nav.xml: item Berkas tidak ditemukan")
        return s.replace(needle, needle + '    <item android:id="@+id/nav_code"\n        android:icon="@drawable/ic_nav_code"\n        android:title="Code" />\n', 1)
    edit("app/src/main/res/menu/bottom_nav.xml", menu)
    def main_act(s):
        if "nav_code" in s: return s
        s2 = s.replace("import com.molina.cinema.CinemaFragment\n", "import com.molina.cinema.CinemaFragment\nimport com.molina.code.CodeFragment\n", 1)
        s2 = s2.replace('                R.id.nav_files -> "files"\n', '                R.id.nav_files -> "files"\n                R.id.nav_code -> "code"\n', 1)
        s2, c = re.subn(r'(\n(\s+)"files" -> BerkasFragment\(\)[^\n]*)', lambda m: m.group(1) + f'\n{m.group(2)}"code" -> CodeFragment()', s2, count=1)
        if c != 1 or s2.count("CodeFragment") < 2 or "nav_code" not in s2: die("MainActivity.kt: pola navigasi tidak cocok (repo berubah?)")
        return s2
    edit("app/src/main/java/com/molina/studio/MainActivity.kt", main_act)
    # Dokumentasi
    def arsi(s):
        if "## Editor Code" in s: return s
        s = s.replace("| feature | `cinema`, `files`, `terminal`, `settings` |", "| feature | `cinema`, `files`, `terminal`, `settings`, `code` |")
        s = s.replace("nav 4 tab (Cinema, Berkas, Terminal, Setelan)", "nav 5 tab (Cinema, Berkas, Code, Terminal, Setelan)")
        return s.rstrip("\n") + '''

## Editor Code

Modul `code` (feature) adalah port editor dari repo lama: Sora-editor + TextMate, ditulis dengan Jetpack Compose dan
dihosting `CodeFragment` lewat `ComposeView`. Compose hanya dipakai di modul ini. Sesi editor hidup di
`CodeSessionHolder` (ViewModel Activity) agar tab terbuka bertahan saat pindah tab.
- Hanya bergantung ke `core-common`; tidak ada feature -> feature (pratinjau dan antarmuka eksekutor dilebur ke modul ini).
- `ScriptExecutor`, `StreamExecutor`, `RunStore` adalah titik suntik dari `app`. Selama belum dipasang: Periksa Python/Shell,
  Python langsung (LSP), dan Jalankan di panel Keluaran redup/tidak aktif.
- Fitur lama yang sengaja tidak dibawa: palet perintah, folder proyek dan riwayat proyek, aksi file (entri menu), kirim ke
  Script Execution, simpan ke Vault, sisipkan/jalankan ke Terminal, snippet Terminal, impor inbox nano.
- Notifikasi perubahan dipertahankan; model datanya kini `guard/ChangeEntry.kt` (dulu `ProjectLog`).
'''
    edit("docs/arsitektur.md", arsi)
    def prov(s):
        if "## Editor Code (port" in s: return s
        return s.rstrip("\n") + '''

## Editor Code (port dari repo lama, 2026-11-17)

Sumber: https://github.com/nanazhii/Molina-Studio (`feature/code`, `feature/preview`, subset `core/exec`), diadaptasi
sebagai modul `code` dengan fitur terminal/vault/proyek dibuang.

| Komponen | Lisensi | Catatan |
|---|---|---|
| sora-editor 0.23.6 (`editor`, `language-textmate`) | LGPL-2.1-or-later | Dependensi Maven Central, tanpa modifikasi. Dipin 0.23.6 (compileSdk 35, Kotlin 2.0) |
| Grammar TextMate java, kotlin, python, javascript, xml, html, lua, markdown | dari contoh sora-editor 0.23.6 | `code/src/main/assets/textmate/` |
| Grammar json, shell, css; tema abyss, solarized_dark | MIT (vscode) | Salinan `LICENSE-vscode-MIT.txt` ikut di assets |
| Tema darcula | tidak dipakai | Asal Eclipse tm4e (EPL), tidak kompatibel dengan GPL |
| JetBrains Mono, Fira Code 6.2 | SIL OFL 1.1 | `code/src/main/assets/fonts/` bersama berkas lisensi |
| Jetpack Compose (BOM 2024.12.01), activity-compose 1.9.3 | Apache 2.0 | Dependensi Maven |

URL provenance di dalam metadata grammar TextMate (mis. `github.com/microsoft/...`) tidak diubah; `scripts/telemetry_gate.py`
melonggarkan aturan URL hanya untuk `code/src/main/assets/textmate/` (`VENDORED_PREFIXES`).
'''
    edit("docs/provenance.md", prov)
    def prog(s):
        head = "## P6 port editor Code dari repo lama (2026-11-17)"
        add = [
            "- [x] Modul `code` (feature, Compose) disalin dari repo lama; pratinjau dan antarmuka eksekutor dilebur; tab ke-5 Code di BottomNav via CodeFragment + CodeSessionHolder",
            "- [x] Dikecualikan: palet perintah, folder proyek, riwayat proyek, aksi file (menu), bagikan (menu), kirim ke Script Execution, simpan ke Vault, sisipkan/jalankan ke Terminal, snippet, impor nano; Notifikasi perubahan dipertahankan (ProjectLog -> ChangeEntry)",
            "- [x] Gerbang: module_gate (FEATURE += code), telemetry_gate (VENDORED_PREFIXES untuk grammar textmate); provenance.md + arsitektur.md diperbarui",
            "- [ ] Verifikasi CI: kompilasi :code dan :app (Compose + Sora, belum bisa dicoba di sandbox), module/telemetry/secret gate",
            "- [ ] Uji perangkat Android 8: buka tab Code, buka/simpan berkas di /storage/emulated/0/Molina, pindah tab lalu kembali (tab tetap), putar layar, Notifikasi perubahan, Riwayat versi",
            "- [ ] Berikutnya: adapter ScriptExecutor/StreamExecutor ke ProotExec (Periksa Python/Shell, Python langsung, Jalankan di Keluaran), buka berkas dari tab Berkas ke Code, uji JVM logika murni editor",
        ]
        if head not in s:
            s = s.rstrip("\n") + "\n\n" + head + "\n"
        for line in add:
            if line not in s: s = s.rstrip("\n") + "\n" + line + "\n"
        return s
    p = ROOT / "progres.md"
    if not p.exists(): write(p, "# Progres Molina Studio\n")
    edit("progres.md", prog)

# ======================================================================= 4. verifikasi
def verify():
    ok = True
    for g in ("scripts/module_gate.py", "scripts/telemetry_gate.py", "scripts/secret_gate.py"):
        if not (ROOT / g).exists(): continue
        r = subprocess.run([sys.executable, g], cwd=ROOT, capture_output=True, text=True)
        tail = (r.stdout.strip().splitlines() or [""])[-1]
        say(f"{'OK  ' if r.returncode == 0 else 'GAGAL'} {g}: {tail}")
        if r.returncode != 0:
            ok = False; say("\n".join(r.stdout.strip().splitlines()[-8:]))
    return ok

def main():
    if not (ROOT / "settings.gradle.kts").exists() or 'rootProject.name = "Molina_Studio"' not in read(ROOT / "settings.gradle.kts"):
        die("jalankan dari root repo Molina_Studio (settings.gradle.kts tidak cocok)")
    old = ensure_old()
    build_module(old)
    wire()
    ok = verify()
    say("\nSelesai. Berikutnya (dua commit terpisah agar CI jalan untuk kode):")
    say("  git checkout -b feat/editor-port && git add code app gradle scripts settings.gradle.kts build.gradle.kts docs")
    say("  git commit -m 'feat(code): port editor Code (Compose) sebagai tab ke-5'")
    say("  git add progres.md && git commit -m 'docs: update progres.md [skip ci]'")
    sys.exit(0 if ok else 1)

main()
