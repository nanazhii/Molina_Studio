#!/usr/bin/env python3
"""Molina_Studio, tahap 1 porting mpv: ganti pipeline native (idempotent).

Jalankan dari root repo Molina_Studio:  python3 stage1_mpv_native.py
Env opsional: OLD_DIR=<clone repo lama>  (jika kosong, repo lama diambil pada commit dipin)

Yang dilakukan:
  1. Hapus submodule third_party/mpv-android, mpv-overlay/, workflow mpv-android.yml
  2. Bawa buildscript libmpv + libplayer dari repo lama (JNI diganti ke com.molina.player)
  3. Pisahkan pin: native/pins.env (NDK bersama) + native/mpv.pins.env (sumber mpv)
  4. Gabungkan native/pins.sha256, pasang langkah fetch_native di build.yml
  5. Daftarkan berkas build-time di telemetry_gate.py
Modul :cinema dan :player-api TIDAK disentuh di tahap ini (diganti di tahap 3).
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

OLD_URL = "https://github.com/nanazhii/Molina-Studio"
OLD_SHA = "c1a07191dd5d6b67cea3e2a7a6f71ab095598f08"
ROOT = os.getcwd()
changed = []


def log(msg):
    print("  " + msg)


def run(*cmd, cwd=None, check=True):
    return subprocess.run(cmd, cwd=cwd or ROOT, check=check, capture_output=True, text=True)


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def write(rel, text):
    p = os.path.join(ROOT, rel)
    if os.path.exists(p) and read(rel) == text:
        return False
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    changed.append(rel)
    log("tulis  " + rel)
    return True


def replace_once(rel, old, new, done_marker):
    """Ganti tepat satu kemunculan; lewati bila penanda hasil sudah ada."""
    t = read(rel)
    if done_marker in t:
        return False
    if t.count(old) != 1:
        sys.exit("GAGAL: %s: pola tidak ditemukan tepat satu kali (upstream berubah?):\n%s" % (rel, old))
    write(rel, t.replace(old, new, 1))
    return True


# ---------------------------------------------------------------- preflight
if not os.path.isfile(os.path.join(ROOT, "settings.gradle.kts")) or not os.path.isdir(os.path.join(ROOT, "scripts")):
    sys.exit("Jalankan dari root repo Molina_Studio.")
if "Molina_Studio" not in read("settings.gradle.kts"):
    sys.exit("settings.gradle.kts bukan milik Molina_Studio.")

tmp = None
OLD = os.environ.get("OLD_DIR")
if not OLD:
    tmp = tempfile.mkdtemp(prefix="molina-old-")
    OLD = tmp
    print("Mengambil repo lama pada %s ..." % OLD_SHA[:7])
    run("git", "init", "-q", cwd=OLD)
    run("git", "remote", "add", "origin", OLD_URL, cwd=OLD)
    run("git", "fetch", "-q", "--depth", "1", "origin", OLD_SHA, cwd=OLD)
    run("git", "checkout", "-q", "FETCH_HEAD", cwd=OLD)


def from_old(rel):
    with open(os.path.join(OLD, rel), encoding="utf-8") as f:
        return f.read()


# ---------------------------------------------------------------- 1. hapus
print("[1] Hapus infrastruktur mpv-android lama")
tracked = run("git", "ls-files", "--stage", "third_party/mpv-android", "mpv-overlay",
              ".github/workflows/mpv-android.yml").stdout.strip()
if tracked:
    run("git", "rm", "-rq", "-f", "--ignore-unmatch", "third_party/mpv-android", "mpv-overlay",
        ".github/workflows/mpv-android.yml")
    changed.append("(git rm) submodule + overlay + workflow mpv-android")
    log("git rm submodule/overlay/workflow")
for leftover in ("third_party/mpv-android", "mpv-overlay", ".github/workflows/mpv-android.yml"):
    p = os.path.join(ROOT, leftover)
    if os.path.isdir(p):
        shutil.rmtree(p)
    elif os.path.exists(p):
        os.remove(p)
gm = os.path.join(ROOT, ".gitmodules")
if os.path.exists(gm) and not read(".gitmodules").strip():
    run("git", "rm", "-q", "-f", ".gitmodules", check=False)
    if os.path.exists(gm):
        os.remove(gm)
    log("hapus .gitmodules (kosong)")
tp = os.path.join(ROOT, "third_party")
if os.path.isdir(tp) and not os.listdir(tp):
    os.rmdir(tp)

# ---------------------------------------------------------------- 2. pins
print("[2] Pisahkan pin: NDK bersama vs sumber mpv")
write("native/mpv.pins.env", (
    "# Pin sumber build native mpv Molina (libmpv + libplayer). Ubah SADAR dan catat di progres.md.\n"
    "# NDK ada di native/pins.env (dipakai bersama pipeline proot dan Gradle).\n"
    "# Snapshot: mpv-android commit 62013d4 (2026-10-20). Dependensi git yang di upstream\n"
    "# mengambang (HEAD) dipin ke commit terakhir sebelum waktu snapshot itu.\n"
    "UPSTREAM_SHA=62013d44a6d30c26649a07c703c7626e9dd2ea99\n"
    "MPV_SHA=6a262d11f63733d37e3ee6d5fafcb1be1ef83952\n"
    "DAV1D_SHA=4c844317b728c1b6e6d86545e6c0bfc88abd119d\n"
    "LIBASS_SHA=f61db567e6593df3470e91594bcd4ad2d0473aff\n"
    "LIBPLACEBO_SHA=285d1cc8af2ab79b72ac662eb09736de8c12392b\n"
    "# FFmpeg n9.0.2 (commit hasil peel dari tag beranotasi)\n"
    "FFMPEG_SHA=946fcce07b6dcd0331c8cc609192aeff5e1924f8\n"
))
if "UPSTREAM_SHA" in read("native/pins.env"):
    write("native/pins.env",
          "# Pin NDK bersama: pipeline native (mpv, proot) dan Gradle. Ubah SADAR dan catat di progres.md.\n"
          "# Pin sumber mpv: native/mpv.pins.env. Pin proot: native/proot.pins.env.\n"
          "# NDK harus sama dengan v_ndk dan v_ndk_n di depinfo.sh upstream mpv-android (dicek saat build).\n"
          "NDK_VERSION=r30\n"
          "NDK_REVISION=30.0.16248370\n")

cur = read("native/pins.sha256").rstrip("\n").split("\n")
have = {l.split(None, 1)[1].strip() for l in cur if l.strip()}
add = []
for l in from_old("native/pins.sha256").splitlines():
    if l.strip() and l.split(None, 1)[1].strip() not in have:
        add.append(l.strip())
for l in from_old("native/pins.sha256").splitlines():  # URL sama harus hash sama
    h, u = l.split(None, 1)
    for c in cur:
        if c.strip() and c.split(None, 1)[1].strip() == u.strip() and c.split(None, 1)[0] != h:
            sys.exit("GAGAL: hash berbeda untuk URL sama di pins.sha256: " + u)
if add:
    write("native/pins.sha256", "\n".join(cur + add) + "\n")

# ---------------------------------------------------------------- 3. skrip
print("[3] Bawa buildscript native dari repo lama")
for rel in ("native/molina_ffmpeg_profile.sh", "scripts/native_audit.sh",
            "scripts/ffmpeg_components_audit.sh", "scripts/fetch_native.sh",
            "scripts/native_mpv.sh", ".github/workflows/native-mpv.yml"):
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        write(rel, from_old(rel))

if not os.path.exists(os.path.join(ROOT, "native/prebuilt.env")):
    write("native/prebuilt.env",
      "# Pin pustaka native untuk APK. Isi SADAR setelah run workflow 'Native mpv (arm64)' di repo ini\n"
      "# (tag Release + SHA-256 tgz dari ringkasan run). Kosong = APK dibangun tanpa libmpv/libplayer.\n"
      "# Pin lama dari repo Molina-Studio TIDAK dipakai: JNI berubah ke com.molina.player, harus dibangun ulang.\n"
      "NATIVE_TAG=\n"
      "NATIVE_SHA256=\n")

# native_mpv.sh: sumber pin, JNI com.molina.player, NDK lewat ensure_ndk.sh
S = "scripts/native_mpv.sh"
replace_once(S, '. "$ROOT/native/pins.env"\n',
             '. "$ROOT/native/pins.env"\n# shellcheck source=/dev/null\n. "$ROOT/native/mpv.pins.env"\n',
             "mpv.pins.env")
t = read(S)
if "molina/cinema/MPVLib" in t or "molina_cinema_MPVLib" in t or "com.molina.cinema" in t:
    t = (t.replace("com/molina/cinema/MPVLib", "com/molina/player/MPVLib")
          .replace("Java_com_molina_cinema_MPVLib_", "Java_com_molina_player_MPVLib_")
          .replace("com.molina.cinema (idempoten)", "com.molina.player (idempoten)"))
    write(S, t)
assert "molina/player/MPVLib" in read(S) and "molina_player_MPVLib_" in read(S)
replace_once(
    S,
    'if [ ! -d "sdk/android-ndk-${v_ndk}" ]; then\n'
    '  (cd sdk && $WGET "https://dl.google.com/android/repository/android-ndk-${v_ndk}-linux.zip" \\\n'
    '    && unzip -q "android-ndk-${v_ndk}-linux.zip" && rm "android-ndk-${v_ndk}-linux.zip")\n'
    'fi\n',
    '[ "${v_ndk}" = "$NDK_VERSION" ] && [ "${v_ndk_n}" = "$NDK_REVISION" ] \\\n'
    '  || { echo "NDK upstream (${v_ndk}/${v_ndk_n}) beda dari pin native/pins.env" >&2; exit 1; }\n'
    'bash "$ROOT/scripts/ensure_ndk.sh" --dest "$PWD/sdk"\n',
    'ensure_ndk.sh" --dest')
replace_once(S, "# Molina Cinema 5a: bangun libmpv", "# Molina Player: bangun libmpv", "# Molina Player: bangun libmpv")

# fetch_native.sh: tujuan modul baru
F = "scripts/fetch_native.sh"
replace_once(F, "DEST=feature/mpv/src/main", "DEST=player-mpv/src/main", "DEST=player-mpv/src/main")
replace_once(F, 'rm -rf "$DEST/jniLibs"', 'mkdir -p "$DEST"; rm -rf "$DEST/jniLibs"', 'mkdir -p "$DEST"; rm -rf')
t = read(F).replace("ekstrak ke feature/mpv", "ekstrak ke player-mpv")
write(F, t)

# workflow native
W = ".github/workflows/native-mpv.yml"
replace_once(W, "name: Native mpv (Molina Cinema 5a)", "name: Native mpv (arm64)", "name: Native mpv (arm64)")

# ---------------------------------------------------------------- 4. CI
print("[4] build.yml dan gerbang telemetri")
B = ".github/workflows/build.yml"
replace_once(
    B,
    "      - name: Pastikan NDK sesuai pin\n",
    "      - name: Pustaka native mpv (terverifikasi SHA-256)\n"
    "        if: hashFiles('scripts/fetch_native.sh') != ''\n"
    "        env:\n"
    "          GH_TOKEN: ${{ github.token }}\n"
    "        run: bash scripts/fetch_native.sh\n"
    "      - name: Pastikan NDK sesuai pin\n",
    "scripts/fetch_native.sh")

G = "scripts/telemetry_gate.py"
replace_once(
    G,
    'BUILD_FILES = {"scripts/native_wget.sh", "scripts/ensure_ndk.sh", ".github/workflows/mpv-android.yml"}',
    'BUILD_FILES = {"scripts/native_wget.sh", "scripts/ensure_ndk.sh", "scripts/native_mpv.sh",\n'
    '               "scripts/fetch_native.sh", "scripts/native_audit.sh", "scripts/ffmpeg_components_audit.sh",\n'
    '               "native/molina_ffmpeg_profile.sh", ".github/workflows/native-mpv.yml"}',
    ".github/workflows/native-mpv.yml")

# ---------------------------------------------------------------- 5. misc
gi = ".gitignore"
t = read(gi)
if "_native_out/" not in t:
    write(gi, t.rstrip("\n") + "\n\n# keluaran build native lokal (pipeline mpv)\n_native/\n_native_out/\n")

if tmp:
    shutil.rmtree(tmp, ignore_errors=True)
print("\nSelesai. Berkas berubah: %d" % len(changed) if changed else "\nTidak ada perubahan (sudah terapkan).")
