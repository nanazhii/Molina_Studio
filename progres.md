# Progres Molina_Studio

## P3 pipeline native mpv (2026-11-12)
- [x] Clone referensi ke ~/ref: Molina_Studio (resmi), Molina-Studio (lama, hanya baca), mpv-android, mpv, termux-app, MaterialFiles
- [x] native/pins.env + native/pins.sha256 dibuat untuk mpv-android ebf1b3f (NDK r30 30.0.16248370, FFmpeg n9.0.2, harfbuzz 15.0.0)
- [x] scripts: ensure_ndk.sh, fetch_native.sh, native_mpv.sh, native_wget.sh, native_audit.sh, ffmpeg_components_audit.sh
- [x] mpv-native: MPVLib.kt (JNI com.molina.cinema.MPVLib), MpvNative memuat libmpv + libplayer
- [x] native-mpv.yml: cache _native_out per-hash pin + skrip build, verifikasi SHA256SUMS saat cache hit, input fresh
- [x] telemetry_gate.py: host github.com dan dl.google.com hanya untuk berkas build-time native (BUILD_FILES)
- [ ] Verifikasi: jalankan Actions > Native mpv (arm64) (belum dijalankan; build penuh pertama = tanpa cache)
- [ ] Setelah sukses: isi FREETYPE_SHA di native/pins.env dari BUILD-INFO.txt
- [ ] Setelah sukses: isi NATIVE_TAG dan NATIVE_SHA256 di native/prebuilt.env dari ringkasan run
- [ ] Berikutnya: P4 sesi pemutar (implementasi MolinaPlayerSession di atas MPVLib)

## Rename applicationId (2026-11-12)
- [x] applicationId com.molina.studio -> com.molina (bentrok dengan APK repo lama; tidak bisa dipasang)
- [x] README dan docs/penamaan.md dicatat; namespace dan JNI tidak berubah
- [ ] Verifikasi: Build debug di Actions, copot APK lama, pasang APK baru
- [x] Pecah modul: player-api dan terminal-api (kontrak dipisah dari engine)
- [x] scripts/module_gate.py + dipasang di build.yml dan jvm-tests.yml
- [x] Token tema core-ui (warna, dimens, styles, tema per tab, molinaThemed)
- [x] docs/arsitektur.md dan docs/tema.md
- [ ] Verifikasi build di GitHub Actions
- [ ] Terapkan molinaThemed di Cinema/Terminal fragment
- [ ] Seragamkan namespace core-log -> com.molina.core.log
- [ ] Pecah MainActivity (navigator + bootstrap storage)
- [ ] Buat player-mpv dan terminal-proot saat kodenya mulai ditulis
- [x] CI hijau untuk pemecahan modul + tema (patch 1-3)
- [x] Modul terminal-emulator (adaptasi termux-app 8629e632fcb9; 16 berkas + 19 uji)
- [x] provenance.md dikoreksi: berkas upstream diperlakukan GPLv3-only
- [ ] Verifikasi CI: jvm-tests (:terminal-emulator:testDebugUnitTest)
- [ ] terminal-view, terminal-pty (JNI), terminal-core wiring, terminal-proot
- [x] Fix nama interface TerminalSessionClient (bug replace substring)
- [ ] Verifikasi CI jvm-tests setelah fix
- [x] CI hijau: module_gate, telemetry_gate, jvm-tests (emulator + view)
- [x] Patch 6: modul terminal-pty (molina_pty.c, PtyNative, PtyProcess.kt, CMake arm64)
- [ ] Verifikasi CI: :terminal-pty:assembleDebug menghasilkan libmolina_pty.so
- [ ] Uji PTY di perangkat Android 8 (spawn /system/bin/sh, resize, waitFor)
- [ ] terminal-core (implement MolinaTerminalSession + TerminalOutput), terminal-proot
- [x] Patch 9c: menu hamburger scrollable (PopupWindow+ScrollView), tekan lama membuka menu
- [ ] Uji perangkat: menu di mode keyboard aktif (ruang sempit), scroll, Mulai ulang sesi
- [ ] Patch 10: terminal-proot (menunggu keputusan: rootfs diunduh vs dibundel)
- [x] Debug memakai kunci tetap Molina (gradle debug signingConfig + build.yml untuk kedua varian)
- [ ] Verifikasi: pasang debug di atas debug tanpa uninstall
