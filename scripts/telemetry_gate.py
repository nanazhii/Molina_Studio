#!/usr/bin/env python3
# Gerbang telemetri, pelacak, donasi, dan identitas dev lama.
# Jalankan dari mana saja: python3 scripts/telemetry_gate.py
# Kecualikan satu baris dengan komentar "gate:allow <alasan>" di baris itu.
import os
import re
import sys
from urllib.parse import urlparse

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SELF = os.path.abspath(__file__)

SCAN_EXT = {".kt", ".kts", ".java", ".xml", ".gradle", ".toml", ".properties",
            ".json", ".c", ".cc", ".cpp", ".h", ".sh", ".yml", ".yaml", ".mk", ".pro"}
SKIP_DIRS = {".git", "build", ".gradle", ".idea", "node_modules", "third_party"}  # third_party = upstream tanpa modifikasi (submodule)
SKIP_PREFIX = ("LICENSE", "NOTICE", "THIRD_PARTY", "COPYING")  # atribusi lisensi dijaga

# Satu-satunya host yang boleh muncul. Tambah hanya jika benar-benar perlu.
ALLOW_HOSTS = {"schemas.android.com", "www.w3.org", "developer.android.com",
               "kotlinlang.org", "docs.gradle.org"}
ALLOW_GITHUB_PATH = ("/nanazhii/",)

# Berkas build-time pipeline native (hanya jalan di CI). Boleh menyebut host sumber upstream dan nama
# proyek upstream; host pelacak/donasi tetap ditolak. Tambah berkas hanya jika benar-benar perlu.
BUILD_FILES = {"scripts/native_wget.sh", "scripts/ensure_ndk.sh"}
BUILD_HOSTS = {"github.com", "dl.google.com"}

# Berkas DATA daftar blokir pelacak (nama pelacak memang harus tertulis). Hanya pola SDK yang dilonggarkan;
# URL pelacak/donasi tetap ditolak. Tambah berkas hanya jika isinya data murni.
BLOCKLIST_FILES = {"core-security/src/main/java/com/molina/core/security/TrackerDomains.kt"}

# Grammar TextMate pihak ketiga (data murni, URL provenance upstream di metadata). Hanya aturan URL yang dilonggarkan;
# pola pelacak, identitas lama, dan donasi (peringatan) tetap berlaku. Lihat docs/provenance.md.
VENDORED_PREFIXES = ("code/src/main/assets/textmate/",)

DONATION_HOSTS = {"ko-fi.com", "patreon.com", "buymeacoffee.com", "paypal.me",
                  "paypal.com", "opencollective.com", "liberapay.com",
                  "saweria.co", "trakteer.id", "sociabuzz.com"}
TRACKER_HOSTS = {"google-analytics.com", "firebaseio.com", "crashlytics.com",
                 "app-measurement.com", "sentry.io", "mixpanel.com",
                 "amplitude.com", "flurry.com", "appcenter.ms", "adjust.com",
                 "appsflyer.com", "onesignal.com", "doubleclick.net"}

# SDK pelacak (koordinat dependensi / nama paket)
TRACKER_PATTERNS = [r"com\.google\.firebase", r"firebase-(analytics|crashlytics)",
                    r"crashlytics", r"io\.sentry", r"com\.microsoft\.appcenter",
                    r"com\.mixpanel", r"com\.amplitude", r"com\.flurry",
                    r"com\.adjust", r"com\.appsflyer", r"com\.onesignal",
                    r"play-services-analytics", r"ch\.acra"]

# Identitas dev/proyek lama. Tambahkan nama lain yang ingin dibersihkan.
OLD_IDENTITY = [r"\btermux\b", r"\bfornwall\b", r"is\.xyz\.mpv",
                r"mpv-android", r"\bsfan5\b",
                # P15-BERSIH: sisa package/JNI lama (bentuk is/xyz, is_xyz, dan kelas JNI lama di com.molina.cinema)
                r"(?<![A-Za-z0-9])is[/_]xyz(?![A-Za-z0-9])",
                r"(?<![A-Za-z0-9])cinema[./_]MPVLib"]

_extra = os.path.join(os.path.dirname(SELF), "old_identity.txt")
if os.path.exists(_extra):
    with open(_extra, encoding="utf-8") as _f:
        for _l in _f:
            _l = _l.strip()
            if _l and not _l.startswith("#"):
                OLD_IDENTITY.append(_l)

WARN_PATTERNS = [(r"donat", "kata donasi ditemukan, periksa manual"),
                 (r"android\.permission\.INTERNET", "izin INTERNET dideklarasikan"),
                 (r"usesCleartextTraffic", "usesCleartextTraffic dideklarasikan")]

URL_RE = re.compile(r"https?://[^\s\"'<>)\]}\\]+", re.I)
CI = os.environ.get("GITHUB_ACTIONS") == "true"


def host_match(host, domains):
    return any(host == d or host.endswith("." + d) for d in domains)


def emit(kind, rel, no, msg):
    if CI:
        print("::%s file=%s,line=%d::%s" % (kind, rel, no, msg))
    else:
        print("%s %s:%d: %s" % ("[GAGAL]" if kind == "error" else "[PERINGATAN]", rel, no, msg))


def scan_line(rel, no, line, errors, warns):
    build = rel.replace(os.sep, "/") in BUILD_FILES
    blocklist = rel.replace(os.sep, "/") in BLOCKLIST_FILES
    vendored = rel.replace(os.sep, "/").startswith(VENDORED_PREFIXES)
    for m in ([] if vendored else URL_RE.finditer(line)):
        url = m.group(0)
        u = urlparse(url)
        host = (u.hostname or "").lower()
        if host_match(host, DONATION_HOSTS):
            errors.append((rel, no, "URL donasi: " + url))
        elif host_match(host, TRACKER_HOSTS):
            errors.append((rel, no, "URL pelacak/telemetri: " + url))
        elif host == "github.com" and u.path.lower().startswith(tuple(p.lower() for p in ALLOW_GITHUB_PATH)):
            continue
        elif host_match(host, ALLOW_HOSTS):
            continue
        elif build and host_match(host, BUILD_HOSTS):
            continue
        else:
            errors.append((rel, no, "URL di luar allowlist: " + url))
    for pat in ([] if blocklist else TRACKER_PATTERNS):
        if re.search(pat, line, re.I):
            errors.append((rel, no, "SDK pelacak terdeteksi (%s)" % pat))
    for pat in ([] if build else OLD_IDENTITY):
        if re.search(pat, line, re.I):
            errors.append((rel, no, "identitas dev lama terdeteksi (%s)" % pat))
    for pat, msg in WARN_PATTERNS:
        if re.search(pat, line, re.I):
            warns.append((rel, no, msg))


def main():
    errors, warns, files = [], [], 0
    for root, dirs, names in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        rel_root = os.path.relpath(root, ROOT).replace(os.sep, "/")
        if re.search(r"(^|/)is/xyz(/|$)", rel_root):
            errors.append((rel_root, 0, "folder package lama is/xyz"))
        for name in names:
            full = os.path.join(root, name)
            if full == SELF or name.upper().startswith(SKIP_PREFIX):
                continue
            if os.path.splitext(name)[1].lower() not in SCAN_EXT:
                continue
            files += 1
            rel = os.path.relpath(full, ROOT)
            try:
                with open(full, encoding="utf-8", errors="ignore") as f:
                    for no, line in enumerate(f, 1):
                        if "gate:allow" in line:
                            continue
                        scan_line(rel, no, line, errors, warns)
            except OSError as e:
                errors.append((rel, 0, "tidak bisa dibaca: %s" % e))
    for rel, no, msg in warns:
        emit("warning", rel, no, msg)
    for rel, no, msg in errors:
        emit("error", rel, no, msg)
    print("Gerbang selesai: %d berkas dipindai, %d kegagalan, %d peringatan." % (files, len(errors), len(warns)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
