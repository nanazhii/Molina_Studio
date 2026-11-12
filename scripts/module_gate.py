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

FEATURE = {"cinema", "files", "terminal", "settings"}
API = {"player-api", "terminal-api"}
ENGINE = {"mpv-native", "terminal-core", "terminal-emulator", "terminal-view"}
OWN_API = {"cinema": {"player-api"}, "terminal": {"terminal-api"},
           "mpv-native": {"player-api"}, "terminal-core": {"terminal-api"}}
OWN_ENGINE = {"cinema": {"mpv-native"},
              "terminal": {"terminal-core", "terminal-view"},
              "terminal-core": {"terminal-emulator"},
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

if errors:
    print("MODULE GATE GAGAL:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("MODULE GATE OK")
