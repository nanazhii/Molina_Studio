
## 2026-11-18 — Pipeline native mpv (arm64)
- [x] scripts/native_mpv.sh: libmpv+libplayer dari skrip upstream (third_party/mpv-build, tidak diubah), NDK dipin, unduhan terverifikasi SHA-256
- [x] .github/workflows/native-mpv.yml: build -> Release -> native/mpv.pin.env
- [x] build.yml: pasang libs dari Release dipin sebelum assemble
- [ ] Run pertama "Native mpv (arm64)" sampai hijau
- [ ] Setelah hijau: pin SHA git dep (dav1d, libass, libplacebo, mpv, ffmpeg) dari BUILD-INFO.txt
- 2026-11-18: B2-2 dipasang di Molina_Studio (pin ytdl-runtime, install_ytdl_libs.sh, langkah CI build.yml). Insiden: MOLINA_ROOT di terminal menunjuk /sdcard/Molina sehingga file sempat tertulis ke repo lain; sudah dipindah dan script diperbaiki tanpa variabel lingkungan.
- 2026-11-18: repo Molina_Studio di-clone ulang (HEAD ede0ac7). Aturan allowlist telemetry_gate dipetakan; langkah 2 (YtdlRuntime, input teks/URL, dialog Coba lagi) dibuat ulang dari repo tanpa URL di luar allowlist.
