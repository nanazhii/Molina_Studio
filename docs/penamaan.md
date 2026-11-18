# Konvensi Penamaan Molina

- Kontrak sesi: `Molina<Domain>Session` (`MolinaTerminalSession`).
- Dekorator log: `Logging<Kontrak>` (`LoggingTerminalSession`).
- Tag log: `Molina/<area>/<id>` (`Molina/term/s1`, `Molina/proot`), maksimal 23 karakter di logcat.
- Package: `com.molina.*`.
- Log tidak mencatat isi input/output terminal maupun path/query penuh media.
- Application ID: `com.molina` (namespace Gradle modul `:app` tetap `com.molina.studio`). Alasan di README.
