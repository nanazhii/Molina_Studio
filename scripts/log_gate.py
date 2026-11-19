#!/usr/bin/env python3
"""Gerbang jejak log Molina_Studio. Exit 1 bila ada pelanggaran baru.

Tujuan: logcat selalu berguna dan logika log/mpv tidak tersebar lagi.
  R1 raw-log    : android.util.Log langsung (Log.d/i/w/e/v/wtf). Jalur resmi: MolinaEvents (core-log).
  R2 mpv-direct : MPVLib.command/setProperty*/setOption*/getProperty*/observeProperty di luar paket /mpv/.
                  Semua perintah mpv harus lewat gerbang (terekam otomatis).
  R3 log-codes  : kode MolinaEvents.bug/behavior("area","kode") wajib terdaftar di docs/LOG_CODES.md.

R1 dan R2 memakai baseline (ratchet): jumlah per berkas tidak boleh naik, berkas baru mulai dari 0.
Pengecualian satu baris: tambahkan komentar `// gate:allow alasan`.
Perintah: python3 scripts/log_gate.py [--update-baseline [--allow-increase]] [--init-registry]
"""
import json, re, sys, pathlib

ROOT = pathlib.Path(".")
BASELINE = ROOT / "scripts" / "log_gate_baseline.json"
REGISTRY = ROOT / "docs" / "LOG_CODES.md"
RAW_LOG = re.compile(r'(?:(?<![\w.])|(?<=android\.util\.))Log\.(?:v|d|i|w|e|wtf)\(')
MPV_DIRECT = re.compile(r'\bMPVLib\.(?:command|setOption\w*|setProperty\w*|getProperty\w*|observeProperty)\b')
EVENT = re.compile(r'\b(?:MolinaEvents|Diag)\.(?:bug|behavior)\(\s*"([^"]+)"\s*,\s*"([^"]+)"')
ALLOW = "gate:allow"
SKIP_DIRS = {"third_party", "build", ".git", ".gradle"}


def sources():
    for p in sorted(ROOT.glob("*/src/main/**/*.kt")):
        parts = set(p.parts)
        if parts & SKIP_DIRS:
            continue
        yield p


def normalize(raw):
    return re.sub(r'[^a-z0-9.\-]+', '-', raw.lower()).strip('-.') or "unknown"


def qualify(area, code):
    a, c = normalize(area), normalize(code)
    return c if (c == a or c.startswith(a + ".")) else f"{a}.{c}"


def scan():
    raw, mpv, codes = {}, {}, {}
    for p in sources():
        rel = p.as_posix()
        in_core_log = rel.startswith("core-log/")
        in_gateway = "/mpv/" in rel or rel.endswith("/MPVLib.kt")
        text = p.read_text(encoding="utf-8", errors="replace")
        for line in text.splitlines():
            if ALLOW in line:
                continue
            if not in_core_log and RAW_LOG.search(line):
                raw[rel] = raw.get(rel, 0) + 1
            if not in_gateway and MPV_DIRECT.search(line):
                mpv[rel] = mpv.get(rel, 0) + 1
        for m in EVENT.finditer(text):
            codes.setdefault(qualify(m.group(1), m.group(2)), rel)
    return raw, mpv, codes


def registered():
    if not REGISTRY.exists():
        return set()
    return set(re.findall(r'^\|\s*`?([a-z0-9][a-z0-9.\-]*)`?\s*\|', REGISTRY.read_text(encoding="utf-8"), re.M))


def load_baseline():
    if not BASELINE.exists():
        return {"raw_log": {}, "mpv_direct": {}}
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def ratchet(name, hint, current, base, errors, notes):
    for f, n in sorted(current.items()):
        allowed = base.get(f, 0)
        if n > allowed:
            errors.append(f"[{name}] {f}: {n} > baseline {allowed}. {hint}")
        elif n < allowed:
            notes.append(f"[{name}] {f}: turun {allowed} -> {n}")
    for f, allowed in base.items():
        if f not in current and allowed > 0:
            notes.append(f"[{name}] {f}: bersih (baseline {allowed} -> 0)")

REGISTRY_HEAD = """# Daftar kode log Molina

Satu-satunya tempat kode peristiwa log didaftarkan. `scripts/log_gate.py` menggagalkan CI bila
`MolinaEvents.bug/behavior("area", "kode")` memakai kode yang belum ada di tabel ini.

Aturan:
- Kode berbentuk `area.kode` (huruf kecil, angka, titik, hubung). Satu kode = satu makna, jangan dipakai ulang.
- BUG = kegagalan yang dikenali; BEHAV = alur penting yang berhasil.
- Fitur baru yang menyentuh mpv: daftarkan kodenya di sini pada commit yang sama.
- Cari di logcat: `adb logcat | grep "\\[BUG:"` atau `grep "\\[BEHAV:player"`.

| Kode | Jenis | Sumber pertama |
|---|---|---|
"""


def init_registry():
    if REGISTRY.exists():
        print("registry sudah ada, tidak diubah")
        return
    kinds = {}
    for p in sources():
        text = p.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r'\b(?:MolinaEvents|Diag)\.(bug|behavior)\(\s*"([^"]+)"\s*,\s*"([^"]+)"', text):
            kinds.setdefault(qualify(m.group(2), m.group(3)), (m.group(1), p.as_posix()))
    rows = "".join(f"| `{c}` | {'BUG' if k == 'bug' else 'BEHAV'} | {f} |\n" for c, (k, f) in sorted(kinds.items()))
    REGISTRY.parent.mkdir(parents=True, exist_ok=True)
    REGISTRY.write_text(REGISTRY_HEAD + rows, encoding="utf-8")
    print(f"registry dibuat: {len(kinds)} kode")


def main():
    if "--init-registry" in sys.argv:
        init_registry()
        return
    raw, mpv, codes = scan()
    if "--update-baseline" in sys.argv:
        old = load_baseline()
        new = {"raw_log": dict(sorted(raw.items())), "mpv_direct": dict(sorted(mpv.items()))}
        if "--allow-increase" not in sys.argv:
            for key in ("raw_log", "mpv_direct"):
                for f, n in new[key].items():
                    if n > old.get(key, {}).get(f, 0) and old.get(key):
                        print(f"MENOLAK: {key} {f} naik; pakai --allow-increase hanya dengan alasan di commit")
                        sys.exit(1)
        BASELINE.write_text(json.dumps(new, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("baseline diperbarui")
        return
    base = load_baseline()
    errors, notes = [], []
    ratchet("raw-log", "Pakai Diag (core-common) atau MolinaEvents (core-log), atau beri `// gate:allow alasan`.",
            raw, base.get("raw_log", {}), errors, notes)
    ratchet("mpv-direct", "Panggil mpv lewat paket mpv/ (gerbang perintah), bukan MPVLib langsung.",
            mpv, base.get("mpv_direct", {}), errors, notes)
    known = registered()
    for code, f in sorted(codes.items()):
        if code not in known:
            errors.append(f"[log-codes] {code} ({f}) belum terdaftar di docs/LOG_CODES.md")
    for n in notes:
        print("info:", n)
    if errors:
        print("LOG GATE GAGAL:")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print("LOG GATE OK")


main()
