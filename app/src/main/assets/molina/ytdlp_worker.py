#!/usr/bin/env python3
# Worker yt-dlp untuk Molina Player. Dijalankan di Debian PRoot oleh aplikasi (DebianYtDlpWorker):
#   /root/molina-venv/bin/python /root/.molina/ytdlp_worker.py
# Proses ini hidup lama supaya biaya import yt-dlp (beberapa detik) hanya dibayar sekali.
#
# Protokol baris lewat stdin/stdout. Kolom dipisah TAB, semua nilai teks di-percent-encode (UTF-8):
#   masuk : GET <id> <url>   |   QUIT
#   keluar: READY <versi>
#           OK <id> <url> <audio> <headers> <kedaluwarsa_ms> <durasi_dtk> <judul>
#           ERR <id> <KODE> <pesan>
# Hanya protokol ini yang boleh ke stdout. Log dibuang; URL dan cookie tidak pernah ditulis ke stderr.
# Cookie opsional: ~/.config/molina/cookies.txt (format Netscape), dibaca bila ada.
import os
import re
import sys
from urllib.parse import parse_qs, quote, urlparse

PROTO = sys.stdout
sys.stdout = sys.stderr  # print() nyasar dari pustaka tidak boleh merusak protokol

COOKIE_FILE = os.path.expanduser("~/.config/molina/cookies.txt")
# Satu berkas bila ada (sudah berisi audio); selain itu pasangan video + audio. Maksimal 1080p.
FORMAT = "bv*[height<=?1080]+ba/b[height<=?1080]/b"
# Hanya header ini yang diteruskan ke mpv. Accept-Encoding dan sejenisnya SENGAJA dibuang: mpv tidak
# mendekompresi badan media, jadi header itu bisa merusak pemutaran.
KEEP_HEADERS = ("user-agent", "referer", "origin", "cookie")
URL_RE = re.compile(r"https?://\S+", re.IGNORECASE)


class Quiet(object):
    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        pass


def enc(value):
    return quote("" if value is None else str(value), safe="")


def send(*cols):
    PROTO.write("\t".join(cols) + "\n")
    PROTO.flush()


def scrub(text):
    return URL_RE.sub("<url>", " ".join(str(text).split()))[:200]


def classify(exc):
    low = str(exc).lower()
    if "unsupported url" in low:
        return "UNSUPPORTED"
    if any(k in low for k in ("age-restricted", "confirm your age", "age restricted")):
        return "AGE"
    if any(k in low for k in ("not available in your country", "geo", "region")):
        return "GEO"
    if any(k in low for k in ("log in", "login", "sign in", "registered users", "--cookies", "cookies", "authentication")):
        return "LOGIN"
    if "private" in low:
        return "PRIVATE"
    if any(k in low for k in ("404", "not found", "removed", "deleted", "does not exist", "no video", "unavailable")):
        return "NOT_FOUND"
    if any(k in low for k in ("timed out", "timeout", "unable to download", "temporary failure", "name resolution",
                              "connection", "urlopen", "network is unreachable", "ssl", "http error 5", "http error 429")):
        return "NETWORK"
    return "PARSE"


def expires_ms(url):
    # TikTok: expire=<epoch dtk>. CDN Facebook: oe=<epoch dtk heksadesimal>.
    try:
        query = parse_qs(urlparse(url).query)
        for key, base in (("expire", 10), ("oe", 16), ("Expires", 10)):
            if query.get(key):
                n = int(query[key][0], base)
                if n > 1000000000:
                    return n * 1000
    except Exception:
        pass
    return None


def headers_for(ydl, info, fmt, url):
    merged = {}
    merged.update(info.get("http_headers") or {})
    merged.update(fmt.get("http_headers") or {})
    out = {}
    for name, value in merged.items():
        if str(name).lower() in KEEP_HEADERS and value:
            out[str(name)] = str(value)
    if not any(k.lower() == "cookie" for k in out):
        cookie = fmt.get("cookies") or ""
        if not cookie:
            try:
                cookie = ydl.cookiejar.get_cookie_header(url) or ""
            except Exception:
                cookie = ""
        if cookie:
            out["Cookie"] = cookie
    return out


def build_ok(rid, ydl, info):
    formats = [f for f in (info.get("requested_formats") or [info]) if f.get("url")]
    video = None
    audio = None
    for f in formats:
        has_video = f.get("vcodec") != "none"
        has_audio = f.get("acodec") != "none"
        if has_video and video is None:
            video = f
        elif has_audio and not has_video and audio is None:
            audio = f
    if video is None:
        video = audio if audio is not None else None
        audio = None
    if video is None:
        return ["ERR", rid, "NOT_FOUND", enc("tidak ada format yang bisa diputar")]
    headers = headers_for(ydl, info, video, video["url"])
    pairs = "&".join(quote(k, safe="") + "=" + quote(v, safe="") for k, v in headers.items())
    stamps = [t for t in (expires_ms(video["url"]), expires_ms(audio["url"]) if audio else None) if t]
    duration = info.get("duration")
    return [
        "OK", rid, enc(video["url"]), enc(audio["url"] if audio else ""), pairs,
        str(min(stamps)) if stamps else "",
        str(int(duration)) if isinstance(duration, (int, float)) and duration > 0 else "",
        enc(info.get("title") or info.get("fulltitle") or ""),
    ]


def cookie_stamp():
    try:
        return os.path.getmtime(COOKIE_FILE)
    except OSError:
        return None


def make_ydl(yt_dlp):
    opts = {
        "quiet": True, "no_warnings": True, "noprogress": True, "no_color": True,
        "logger": Quiet(), "noplaylist": True, "skip_download": True,
        "format": FORMAT, "socket_timeout": 20, "retries": 1, "extractor_retries": 1,
        "cachedir": False, "ignoreconfig": True,
        "allowed_extractors": ["facebook.*", "tiktok.*"],
    }
    if os.path.isfile(COOKIE_FILE):
        opts["cookiefile"] = COOKIE_FILE
    return yt_dlp.YoutubeDL(opts)


def close_quietly(ydl):
    try:
        ydl.close()
    except Exception:
        pass


def main():
    try:
        import yt_dlp
    except Exception:
        send("ERR", "0", "NOT_INSTALLED", "")
        return 3
    version = getattr(getattr(yt_dlp, "version", None), "__version__", "")
    send("READY", enc(version))
    ydl = None
    stamp = None
    for line in sys.stdin:
        parts = line.rstrip("\r\n").split("\t")
        if parts[0] == "QUIT":
            break
        if parts[0] != "GET" or len(parts) != 3 or not parts[1].isdigit():
            continue
        rid, url = parts[1], parts[2]
        if not url.lower().startswith(("http://", "https://")):
            send("ERR", rid, "UNSUPPORTED", enc("bukan http(s)"))
            continue
        try:
            current = cookie_stamp()
            if ydl is None or current != stamp:
                if ydl is not None:
                    close_quietly(ydl)
                ydl = make_ydl(yt_dlp)
                stamp = current
            info = ydl.extract_info(url, download=False)
            if not info:
                send("ERR", rid, "NOT_FOUND", enc("tidak ada hasil"))
            else:
                send(*build_ok(rid, ydl, info))
        except BrokenPipeError:
            break
        except Exception as exc:
            send("ERR", rid, classify(exc), enc(scrub(exc)))
    if ydl is not None:
        close_quietly(ydl)
    return 0


if __name__ == "__main__":
    sys.exit(main())
