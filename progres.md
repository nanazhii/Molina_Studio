
## 2026-11-18 — Pipeline native mpv (arm64)
- [x] scripts/native_mpv.sh: libmpv+libplayer dari skrip upstream (third_party/mpv-build, tidak diubah), NDK dipin, unduhan terverifikasi SHA-256
- [x] .github/workflows/native-mpv.yml: build -> Release -> native/mpv.pin.env
- [x] build.yml: pasang libs dari Release dipin sebelum assemble
- [ ] Run pertama "Native mpv (arm64)" sampai hijau
- [ ] Setelah hijau: pin SHA git dep (dav1d, libass, libplacebo, mpv, ffmpeg) dari BUILD-INFO.txt
