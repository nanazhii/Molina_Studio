#!/bin/sh
# Molina Terminal - pembungkus nano (dibuat otomatis, ditimpa tiap sesi).
# Tugas: pakai nanorc Molina bila pengguna tak punya ~/.nanorc, memberi judul sesi "nano: <berkas>" agar aplikasi
# menampilkan baris tombol nano, mengisi shebang untuk skrip baru, dan menawarkan perbaikan header/penutup skrip.
real="${MOLINA_NANO_BIN:-/usr/bin/nano}"
if [ ! -x "$real" ]; then
  echo "nano belum terpasang. Pasang dengan: apt-get install nano" >&2
  exit 127
fi
h="${HOME:-/root}"
rc="${MOLINA_NANORC:-/etc/molina/nanorc}"
if [ -f "$h/.nanorc" ] || [ -f "${XDG_CONFIG_HOME:-$h/.config}/nano/nanorc" ]; then rc=""; fi
# Berkas yang diedit = argumen bukan-opsi terakhir (opsi -x dan +BARIS dilewati).
file=""
for a in "$@"; do
  case "$a" in -*|+*) ;; *) file="$a" ;; esac
done
name="(baru)"
if [ -n "$file" ]; then
  case "$file" in /*) ;; *) file="$PWD/$file" ;; esac
  MOLINA_NANO_FILE="$file"; export MOLINA_NANO_FILE
  name=$(basename "$file" | tr -d '\000-\037\177' | cut -c1-40)
else
  unset MOLINA_NANO_FILE
fi
if [ -t 1 ]; then
  printf '\033]0;nano: %s\007' "$name"
  trap 'printf "\033]0;\007"' EXIT
  trap 'exit 143' TERM HUP
fi
# Berkas skrip baru/kosong (ekstensi dikenal): isi shebang dulu, kursor di baris 2. Tak diubah = dikembalikan.
# Matikan: MOLINA_NANO_TEMPLATE=off.
runbin="${MOLINA_RUN_BIN:-/usr/local/bin/molina-run}"
tpl_made=0; tpl_existed=0; tpl_sum=""
if [ -n "$file" ] && [ "${MOLINA_NANO_TEMPLATE:-on}" != "off" ] && [ -x "$runbin" ] && [ ! -d "$file" ] && [ ! -s "$file" ]; then
  if [ -e "$file" ]; then tpl_existed=1; fi
  if "$runbin" --init "$file" >/dev/null 2>&1; then
    tpl_made=1; tpl_sum=$(cksum < "$file")
    has_line=0
    for a in "$@"; do case "$a" in +*) has_line=1 ;; esac; done
    if [ "$has_line" -eq 0 ]; then set -- +2 "$@"; fi
  fi
fi
# Alt+R: simpan + berkas penanda + keluar; skrip dijalankan dari berkasnya sesudah nano selesai.
flag="${TMPDIR:-/tmp}/molina-run.$$"
rm -f "$flag"
MOLINA_RUN_FLAG="$flag"; export MOLINA_RUN_FLAG
# Alt+F: simpan + keluar + berkas penanda; molina-run --fix dijalankan, lalu nano dibuka lagi.
fixflag="$flag.fix"
MOLINA_FIX_FLAG="$fixflag"; export MOLINA_FIX_FLAG
while :; do
  rm -f "$fixflag"
  if [ -n "$rc" ]; then "$real" --rcfile="$rc" "$@"; else "$real" "$@"; fi
  st=$?
  if [ -e "$fixflag" ] && [ "$st" -eq 0 ] && [ -n "${MOLINA_NANO_FILE:-}" ] && [ -f "$MOLINA_NANO_FILE" ]; then
    rm -f "$fixflag"
    if [ -t 1 ]; then printf '\033]0;\007'; fi
    "$runbin" --fix "$MOLINA_NANO_FILE"
    if [ -t 1 ] && ( : < /dev/tty ) 2>/dev/null; then
      printf 'Tekan Enter untuk kembali ke nano... '
      IFS= read -r _ < /dev/tty
      printf '\033]0;nano: %s\007' "$name"
      continue
    fi
  fi
  break
done
rm -f "$fixflag"
# Template tak disentuh -> kembalikan.
if [ "$tpl_made" -eq 1 ] && [ -f "$file" ] && [ "$(cksum < "$file")" = "$tpl_sum" ]; then
  if [ "$tpl_existed" -eq 1 ]; then : > "$file"; else rm -f "$file"; fi
fi
# Sesudah nano ditutup biasa: saran header/penutup + tawaran perbaiki (hanya di terminal).
if [ ! -e "$flag" ] && [ "$st" -eq 0 ] && [ -x "$runbin" ] && [ -n "${MOLINA_NANO_FILE:-}" ] && [ -f "$MOLINA_NANO_FILE" ] \
   && [ -t 1 ] && ( : < /dev/tty ) 2>/dev/null; then
  "$runbin" --hint "$MOLINA_NANO_FILE"
  if [ "$?" -eq 10 ]; then
    printf 'Perbaiki sekarang (molina-run --fix)? [Y/n] '
    IFS= read -r ans < /dev/tty
    case "$ans" in n|N|t|T) ;; *) "$runbin" --fix "$MOLINA_NANO_FILE" ;; esac
  fi
fi
if [ -e "$flag" ]; then
  rm -f "$flag"
  if [ "$st" -eq 0 ] && [ -n "${MOLINA_NANO_FILE:-}" ] && [ -f "$MOLINA_NANO_FILE" ]; then
    if [ -t 1 ]; then printf '\033]0;\007'; fi
    trap : INT
    "$runbin" "$MOLINA_NANO_FILE"
    st=$?
  fi
fi
exit $st
