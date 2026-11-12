# Konvensi Penamaan Molina

- Kontrak sesi: `Molina<Domain>Session` (`MolinaTerminalSession`, `MolinaPlayerSession`).
- Dekorator log: `Logging<Kontrak>` (`LoggingTerminalSession`, `LoggingMolinaPlayerSession`).
- Tag log: `Molina/<area>/<id>` (`Molina/term/s1`, `Molina/mpv/p1`), maksimal 23 karakter di logcat.
- Package: `com.molina.*`.
- Log tidak mencatat isi input/output terminal maupun path/query penuh media.
- Package pemutar (mpv-native): `com.molina.cinema`; namespace Gradle-nya `com.molina.cinema.mpv` agar tidak bentrok dengan modul `:cinema`. Tag log tetap `Molina/mpv/<id>`.
