# Konvensi Penamaan Molina

- Kontrak sesi: `Molina<Domain>Session` (`MolinaTerminalSession`, `MolinaPlayerSession`).
- Dekorator log: `Logging<Kontrak>` (`LoggingTerminalSession`, `LoggingMolinaPlayerSession`).
- Tag log: `Molina/<area>/<id>` (`Molina/term/s1`, `Molina/mpv/p1`), maksimal 23 karakter di logcat.
- Package: `com.molina.*`.
- Log tidak mencatat isi input/output terminal maupun path/query penuh media.
- Package pemutar: ditetapkan ulang saat vendoring mpv-android (modul `:mpv-native` dihapus di P11). Tag log tetap `Molina/mpv/<id>`.
- Application ID: `com.molina` (namespace Gradle modul `:app` tetap `com.molina.studio`). Alasan di README.
