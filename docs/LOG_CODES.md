# Daftar kode log Molina

Satu-satunya tempat kode peristiwa log didaftarkan. `scripts/log_gate.py` menggagalkan CI bila
`MolinaEvents.bug/behavior("area", "kode")` memakai kode yang belum ada di tabel ini.

Aturan:
- Kode berbentuk `area.kode` (huruf kecil, angka, titik, hubung). Satu kode = satu makna, jangan dipakai ulang.
- BUG = kegagalan yang dikenali; BEHAV = alur penting yang berhasil.
- Fitur baru yang menyentuh mpv: daftarkan kodenya di sini pada commit yang sama.
- Cari di logcat: `adb logcat | grep "\[BUG:"` atau `grep "\[BEHAV:player"`.

| Kode | Jenis | Sumber pertama |
|---|---|---|
| `app.exit-failed` | BUG | app/src/main/java/com/molina/studio/AppExit.kt |
| `app.exit-hook-timeout` | BUG | app/src/main/java/com/molina/studio/AppExit.kt |
| `app.exit-leftover` | BUG | app/src/main/java/com/molina/studio/AppExit.kt |
| `app.exit.requested` | BEHAV | app/src/main/java/com/molina/studio/AppExit.kt |
| `app.exit.swept` | BEHAV | app/src/main/java/com/molina/studio/AppExit.kt |
| `app.nav` | BEHAV | app/src/main/java/com/molina/studio/MainActivity.kt |
| `app.session-service-failed` | BUG | app/src/main/java/com/molina/studio/MolinaSessionService.kt |
| `app.trim` | BEHAV | app/src/main/java/com/molina/studio/MolinaApplication.kt |
| `debug.crash-trigger` | BEHAV | settings/src/main/java/com/molina/settings/DebugCrash.kt |
| `debug.test-bug` | BUG | settings/src/main/java/com/molina/settings/DebugCrash.kt |
| `mpv.call-failed` | BUG | player/src/main/java/com/molina/player/mpv/MpvThread.kt |
| `mpv.command` | BEHAV | player/src/main/java/com/molina/player/mpv/MpvGateway.kt |
| `mpv.command-empty` | BUG | player/src/main/java/com/molina/player/mpv/MpvGateway.kt |
| `mpv.command-failed` | BUG | player/src/main/java/com/molina/player/mpv/MpvGateway.kt |
| `mpv.initialized` | BEHAV | player/src/main/java/com/molina/player/BaseMPVView.kt |
| `mpv.option-rejected` | BUG | player/src/main/java/com/molina/player/mpv/MpvProperties.kt |
| `mpv.property-failed` | BUG | player/src/main/java/com/molina/player/mpv/MpvProperties.kt |
| `mpv.property-set` | BEHAV | player/src/main/java/com/molina/player/mpv/MpvProperties.kt |
| `mpv.slow-call` | BUG | player/src/main/java/com/molina/player/mpv/MpvGateway.kt |
| `mpv.surface-attached` | BEHAV | player/src/main/java/com/molina/player/BaseMPVView.kt |
| `mpv.surface-detached` | BEHAV | player/src/main/java/com/molina/player/BaseMPVView.kt |
| `net.shield.start` | BEHAV | core-security/src/main/java/com/molina/core/security/LocalShieldProxy.kt |
| `net.shield.start-failed` | BUG | core-security/src/main/java/com/molina/core/security/LocalShieldProxy.kt |
| `net.shield.stop` | BEHAV | core-security/src/main/java/com/molina/core/security/LocalShieldProxy.kt |
| `net.shield.trackers-loaded` | BEHAV | app/src/main/java/com/molina/studio/ShieldBootstrap.kt |
| `net.shield.trackers-skip` | BEHAV | app/src/main/java/com/molina/studio/ShieldBootstrap.kt |
| `picker.cancelled` | BEHAV | picker/src/main/java/com/molina/picker/PickerDialog.kt |
| `picker.picked` | BEHAV | picker/src/main/java/com/molina/picker/PickerDialog.kt |
| `picker.show-failed` | BUG | picker/src/main/java/com/molina/picker/MolinaLocalPicker.kt |
| `picker.shown` | BEHAV | picker/src/main/java/com/molina/picker/PickerDialog.kt |
| `player.about-mpv-failed` | BUG | player/src/main/java/com/molina/player/preferences/AboutActivity.kt |
| `player.audio-focus-change` | BEHAV | player/src/main/java/com/molina/player/audio/MPVActivityAudioFocus.kt |
| `player.audio-focus-denied` | BEHAV | player/src/main/java/com/molina/player/audio/MPVActivityAudioFocus.kt |
| `player.audio-focus-release` | BEHAV | player/src/main/java/com/molina/player/audio/MPVActivityAudioFocus.kt |
| `player.bg-service-start` | BEHAV | player/src/main/java/com/molina/player/BackgroundPlaybackService.kt |
| `player.bg-service-stop` | BEHAV | player/src/main/java/com/molina/player/BackgroundPlaybackService.kt |
| `player.channels-failed` | BUG | player/src/main/java/com/molina/player/home/ChannelsRepository.kt |
| `player.channels-imported` | BEHAV | player/src/main/java/com/molina/player/home/ChannelsImporter.kt |
| `player.channels-loaded` | BEHAV | player/src/main/java/com/molina/player/home/ChannelsRepository.kt |
| `player.channels-open` | BEHAV | player/src/main/java/com/molina/player/home/ChannelsSheet.kt |
| `player.channels-play` | BEHAV | player/src/main/java/com/molina/player/home/HomeController.kt |
| `player.channels-source-added` | BEHAV | player/src/main/java/com/molina/player/home/ChannelsSources.kt |
| `player.channels-source-removed` | BEHAV | player/src/main/java/com/molina/player/home/ChannelsSources.kt |
| `player.cover-failed` | BUG | player/src/main/java/com/molina/player/nowplaying/CoverArt.kt |
| `player.cover-found` | BEHAV | player/src/main/java/com/molina/player/nowplaying/CoverArt.kt |
| `player.cover-none` | BEHAV | player/src/main/java/com/molina/player/nowplaying/CoverArt.kt |
| `player.cover-oom` | BUG | player/src/main/java/com/molina/player/nowplaying/CoverArt.kt |
| `player.dialog-pause` | BEHAV | player/src/main/java/com/molina/player/playback/MPVActivityDialogPause.kt |
| `player.gesture` | BEHAV | player/src/main/java/com/molina/player/gestures/MPVActivityGestures.kt |
| `player.home-action` | BEHAV | player/src/main/java/com/molina/player/home/HomeController.kt |
| `player.home-refresh` | BEHAV | player/src/main/java/com/molina/player/home/HomeController.kt |
| `player.home-return` | BEHAV | player/src/main/java/com/molina/player/MainScreenFragment.kt |
| `player.menu-empty` | BEHAV | player/src/main/java/com/molina/player/menu/MPVActivityMenu.kt |
| `player.open-local` | BEHAV | player/src/main/java/com/molina/player/open/DeviceOpener.kt |
| `player.open-local-folder` | BEHAV | player/src/main/java/com/molina/player/open/DeviceOpener.kt |
| `player.open-picker-missing` | BUG | player/src/main/java/com/molina/player/open/DeviceOpener.kt |
| `player.position-skip-eof` | BEHAV | player/src/main/java/com/molina/player/playback/MPVActivityPosition.kt |
| `player.recent-cleared` | BEHAV | player/src/main/java/com/molina/player/home/HomeController.kt |
| `player.recent-failed` | BUG | player/src/main/java/com/molina/player/home/HomeController.kt |
| `player.recent-recorded` | BEHAV | player/src/main/java/com/molina/player/home/HomeController.kt |
| `proot.cd-request-failed` | BUG | terminal-proot/src/main/kotlin/com/molina/terminal/proot/ShellPrompt.kt |
| `proot.downloaded` | BEHAV | terminal/src/main/java/com/molina/terminal/DebianSetupViewModel.kt |
| `proot.install-failed` | BUG | terminal/src/main/java/com/molina/terminal/DebianSetupViewModel.kt |
| `proot.install.cancelled` | BEHAV | terminal/src/main/java/com/molina/terminal/DebianSetupViewModel.kt |
| `proot.install.done` | BEHAV | terminal/src/main/java/com/molina/terminal/DebianSetupViewModel.kt |
| `proot.install.start` | BEHAV | terminal/src/main/java/com/molina/terminal/DebianSetupViewModel.kt |
| `proot.nano-install` | BEHAV | terminal-proot/src/main/kotlin/com/molina/terminal/proot/NanoSupport.kt |
| `proot.nano-install-failed` | BUG | terminal-proot/src/main/kotlin/com/molina/terminal/proot/NanoSupport.kt |
| `proot.prepare-failed` | BUG | app/src/main/java/com/molina/studio/TerminalWiring.kt |
| `proot.prompt-failed` | BUG | terminal-proot/src/main/kotlin/com/molina/terminal/proot/ShellPrompt.kt |
| `proot.shield-env-failed` | BUG | terminal-proot/src/main/kotlin/com/molina/terminal/proot/ShieldGuestEnv.kt |
| `term.drawer.auto-run-timeout` | BUG | terminal/src/main/java/com/molina/terminal/TerminalFragment.kt |
| `term.drawer.op` | BEHAV | filemanager/src/main/java/com/molina/filemanager/FileDrawerView.kt |
| `term.drawer.op-failed` | BUG | filemanager/src/main/java/com/molina/filemanager/FileDrawerView.kt |
| `term.drawer.open` | BEHAV | filemanager/src/main/java/com/molina/filemanager/FileDrawerView.kt |
| `term.session.start` | BEHAV | app/src/main/java/com/molina/studio/TerminalWiring.kt |
| `term.shield-env` | BEHAV | terminal-proot/src/main/kotlin/com/molina/terminal/proot/ShieldGuestEnv.kt |
| `term.tab` | BEHAV | terminal/src/main/java/com/molina/terminal/TerminalSessionHolder.kt |
| `ytdlp.install-done` | BEHAV | app/src/main/java/com/molina/studio/DebianYtDlpInstaller.kt |
| `ytdlp.install-failed` | BUG | app/src/main/java/com/molina/studio/DebianYtDlpInstaller.kt |
| `ytdlp.install-start` | BEHAV | app/src/main/java/com/molina/studio/DebianYtDlpInstaller.kt |
