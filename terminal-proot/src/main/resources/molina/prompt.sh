# Molina Terminal: prompt bawaan. Dibuat otomatis oleh aplikasi dan ditimpa tiap sesi baru; jangan diedit.
# Hijau di home (~) dan di /sdcard/Molina (dengan label [MOLINA]); merah di tempat lain.
[ -n "${BASH_VERSION-}" ] || return 0
case $- in *i*) ;; *) return 0 ;; esac

__molina_ps1() {
  [ -r /etc/molina/prompt.env ] && . /etc/molina/prompt.env
  local name="${MOLINA_PS_NAME:-user}" ok="${MOLINA_PS_OK:-1;32}" bad="${MOLINA_PS_BAD:-1;31}"
  local sgr="$bad" tag="" p="$PWD"
  case "$p" in
    /storage/emulated/0|/storage/emulated/0/*) p="/sdcard${p#/storage/emulated/0}" ;;
  esac
  case "$PWD" in
    "$HOME"|"$HOME"/*) [ -n "$HOME" ] && [ "$HOME" != / ] && sgr="$ok" ;;
  esac
  case "$p" in
    /sdcard/Molina|/sdcard/Molina/*) sgr="$ok"; tag="[MOLINA] " ;;
  esac
  PS1='\[\e['"$sgr"'m\]'"$tag$name"'@localhost:\w#\[\e[0m\] '
}
case ";${PROMPT_COMMAND-};" in
  *";__molina_ps1;"*) ;;
  *) PROMPT_COMMAND="${PROMPT_COMMAND:+$PROMPT_COMMAND;}__molina_ps1" ;;
esac
