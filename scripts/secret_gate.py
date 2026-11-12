#!/usr/bin/env python3
# Gerbang rahasia: kunci privat, token, kredensial, dan berkas keystore.
#   python3 scripts/secret_gate.py            # berkas ter-track (git ls-files)
#   python3 scripts/secret_gate.py --history  # seluruh riwayat git (baris yang ditambahkan)
# Kecualikan satu baris dengan komentar "gate:allow <alasan>" di baris itu.
# Nilai yang cocok TIDAK pernah dicetak; hanya jenis, berkas, baris, dan commit.
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SELF = os.path.relpath(os.path.abspath(__file__), ROOT).replace(os.sep, "/")
CI = os.environ.get("GITHUB_ACTIONS") == "true"
MAX_BYTES = 2 * 1024 * 1024

SECRET_FILE_EXT = (".jks", ".keystore", ".p12", ".pfx", ".key")
SECRET_FILE_NAMES = ("local.properties", ".env", "keystore.properties", "id_rsa", "id_ed25519")

PATTERNS = [
    ("kunci privat", re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP |ENCRYPTED )?PRIVATE KEY")),
    ("token GitHub", re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\b")),
    ("token GitHub (fine-grained)", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{22,}\b")),
    ("kunci akses AWS", re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("kunci API Google", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b")),
    ("token Slack", re.compile(r"\bxox[abprs]-[0-9A-Za-z\-]{10,}\b")),
    ("kunci API Anthropic", re.compile(r"\bsk-ant-[A-Za-z0-9_\-]{20,}\b")),
    ("kredensial dalam URL", re.compile(r"\bhttps?://[^/\s:@]+:[^/\s:@]{3,}@[^\s/]+")),
    ("kata sandi/token literal", re.compile(
        r"(?i)\b(?:password|passwd|secret|token|api[_-]?key|storepass|keypass)\b\s*[:=]\s*[\x22\x27][A-Za-z0-9/+_=\-]{16,}[\x22\x27]")),
]
ALLOW_RE = re.compile(r"gate:allow")


def emit(rel, no, msg, ref=""):
    where = "%s:%d" % (rel, no) if no else rel
    if CI:
        print("::error file=%s,line=%d::%s%s" % (rel, max(no, 1), msg, ref))
    else:
        print("[GAGAL] %s: %s%s" % (where, msg, ref))


def scan_line(line):
    if ALLOW_RE.search(line):
        return []
    return [name for name, rx in PATTERNS if rx.search(line)]


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], capture_output=True, check=True)


def secret_file_name(path):
    base = os.path.basename(path).lower()
    return base.endswith(SECRET_FILE_EXT) or base in SECRET_FILE_NAMES


def scan_tracked():
    findings = 0
    files = git("ls-files", "-z").stdout.decode("utf-8", "replace").split("\0")
    scanned = 0
    for rel in filter(None, files):
        if rel == SELF:
            continue
        if secret_file_name(rel):
            emit(rel, 0, "berkas rahasia/keystore ter-track")
            findings += 1
            continue
        full = os.path.join(ROOT, rel)
        try:
            if not os.path.isfile(full) or os.path.getsize(full) > MAX_BYTES:
                continue
            with open(full, "rb") as f:
                data = f.read()
        except OSError:
            continue
        if b"\0" in data[:4096]:
            continue
        scanned += 1
        for no, line in enumerate(data.decode("utf-8", "replace").splitlines(), 1):
            for name in scan_line(line):
                emit(rel, no, name)
                findings += 1
    print("Gerbang rahasia (berkas): %d berkas dipindai, %d temuan." % (scanned, findings))
    return findings


def scan_history():
    findings = 0
    commits = 0
    log = git("log", "--all", "--no-color", "-p", "-U0", "--no-ext-diff",
              "--format=@@COMMIT %H").stdout.decode("utf-8", "replace")
    commit, path, no = "", "", 0
    for line in log.splitlines():
        if line.startswith("@@COMMIT "):
            commit, path, no = line[9:21], "", 0
            commits += 1
        elif line.startswith("diff --git "):
            path = ""
        elif line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else ""
        elif line.startswith("@@ "):
            m = re.search(r"\+(\d+)", line)
            no = int(m.group(1)) - 1 if m else 0
        elif line.startswith("+") and path and path != SELF:
            no += 1
            for name in scan_line(line[1:]):
                emit(path, no, name, " (commit %s)" % commit)
                findings += 1
    # Berkas biner (mis. keystore) tidak muncul sebagai teks di diff; cek lewat nama berkas.
    names = git("log", "--all", "--no-color", "--name-only", "--diff-filter=A",
                "--format=@@COMMIT %H").stdout.decode("utf-8", "replace")
    commit = ""
    for line in names.splitlines():
        if line.startswith("@@COMMIT "):
            commit = line[9:21]
        elif line and line != SELF and secret_file_name(line):
            emit(line, 0, "berkas rahasia/keystore pernah ditambahkan", " (commit %s)" % commit)
            findings += 1
    print("Gerbang rahasia (riwayat): %d commit dipindai, %d temuan." % (commits, findings))
    return findings


def main():
    args = sys.argv[1:]
    if args not in ([], ["--history"]):
        print("Pemakaian: secret_gate.py [--history]")
        return 2
    try:
        total = scan_history() if args else scan_tracked()
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print("[GAGAL] git tidak dapat dijalankan: %s" % type(e).__name__)
        return 2
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
