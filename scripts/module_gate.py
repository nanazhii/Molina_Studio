#!/usr/bin/env python3
"""Gerbang aturan modular Molina_Studio. Exit 1 bila ada dependensi terlarang.

Lapisan: app > feature > (api, engine) > core. Aturan:
- core   : hanya core lain.
- api    : hanya core (kontrak murni, tanpa implementasi).
- engine : core + api miliknya + engine miliknya (terminal-core dan terminal-view -> terminal-emulator).
- feature: core + api miliknya + engine miliknya (sementara, sampai wiring lewat app).
- app    : bebas.
"""
import re, sys, pathlib

FEATURE = {"cinema", "files", "terminal", "settings", "code"}
API = {"player-api", "terminal-api"}
ENGINE = {"engine-newpipe", "player-mpv", "terminal-core", "terminal-emulator", "terminal-view", "terminal-pty", "terminal-proot", "filemanager"}
OWN_API = {"cinema": {"player-api"}, "terminal": {"terminal-api"},
           "terminal-core": {"terminal-api"}}
OWN_ENGINE = {"files": {"filemanager"},
              "cinema": set(),
              "terminal": {"terminal-core", "terminal-view", "terminal-proot", "filemanager"},
              "terminal-core": {"terminal-emulator", "terminal-pty"},
              "terminal-view": {"terminal-emulator"}}
PAT = re.compile(r'(?:api|implementation|compileOnly|runtimeOnly)\(project\(":([\w-]+)"\)\)')

def kind(n):
    if n == "app": return "app"
    if n in FEATURE: return "feature"
    if n in API: return "api"
    if n in ENGINE: return "engine"
    if n.startswith("core-"): return "core"
    return "other"

def allowed(src, dst):
    ks, kd = kind(src), kind(dst)
    if ks == "app": return True
    if kd == "core": return dst != src
    if dst in OWN_API.get(src, set()): return True
    if dst in OWN_ENGINE.get(src, set()): return True
    return False

errors = []
for f in sorted(pathlib.Path(".").glob("*/build.gradle.kts")):
    src = f.parent.name
    for dst in PAT.findall(f.read_text()):
        if not allowed(src, dst):
            errors.append(f"{src} ({kind(src)}) -> {dst} ({kind(dst)}) dilarang")

# [P17-SHIELD] Gerbang manifest :player-mpv: tidak boleh ada komponen exported=true atau filter VIEW/SEND/BROWSABLE
# (jalur pemutaran dari luar app akan melewati Shield).
import xml.etree.ElementTree as ET
_A = "{http://schemas.android.com/apk/res/android}"
_mf = pathlib.Path("player-mpv/src/main/AndroidManifest.xml")
if _mf.exists():
    for _el in ET.parse(_mf).getroot().iter():
        if _el.tag not in ("activity", "activity-alias", "service", "receiver", "provider"):
            continue
        _n = _el.get(_A + "name")
        if _el.get(_A + "exported") == "true":
            errors.append(f"player-mpv manifest: {_n} exported=true dilarang (Shield bypass)")
        for _f in _el.findall("intent-filter"):
            _acts = {a.get(_A + "name") for a in _f.findall("action")}
            _cats = {c.get(_A + "name") for c in _f.findall("category")}
            if _acts & {"android.intent.action.VIEW", "android.intent.action.SEND", "android.intent.action.SEND_MULTIPLE"} \
                    or "android.intent.category.BROWSABLE" in _cats:
                errors.append(f"player-mpv manifest: {_n} punya filter VIEW/SEND/BROWSABLE dilarang (Shield bypass)")

# [P18-LOADFILE] Pemuatan media ("loadfile") hanya boleh di sesi Shield dan dua berkas debug upstream.
# Jalur lain melewati PlaybackGuard (blacklist protokol, preflight, proxy Shield).
_LOADFILE_OK = {"MpvPlayerSession.kt", "MPVActivity.kt", "BaseMPVView.kt", "MpvLoad.kt"}  # MpvLoad = satu-satunya penyusun loadfile, lewat PlaybackGuard
for _p in sorted(pathlib.Path(".").glob("*/src/main/**/*")):
    if _p.suffix not in (".kt", ".java") or _p.name in _LOADFILE_OK:
        continue
    if "loadfile" in _p.read_text(encoding="utf-8", errors="ignore"):
        errors.append(f"{_p}: memuat 'loadfile' di luar sesi Shield (bypass PlaybackGuard)")

if errors:
    print("MODULE GATE GAGAL:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("MODULE GATE OK")
