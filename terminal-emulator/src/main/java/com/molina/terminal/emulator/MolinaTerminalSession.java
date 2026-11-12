package com.molina.terminal.emulator;

/**
 * Kontrak sesi terminal yang dipakai terminal-view. Implementasinya ada di terminal-core
 * (kelas turunan {@link TerminalOutput}); terminal-view hanya bergantung pada kontrak ini.
 */
public interface MolinaTerminalSession {

    /** Emulator milik sesi, atau null bila belum diinisialisasi. */
    TerminalEmulator getEmulator();

    /** Ubah ukuran terminal (kolom, baris, dan ukuran sel dalam piksel). */
    void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /** Tulis teks (UTF-8) ke proses. Dipenuhi oleh {@link TerminalOutput#write(String)}. */
    void write(String data);

    /** Tulis satu code point, dengan awalan ESC bila {@code prependEscape}. */
    void writeCodePoint(boolean prependEscape, int codePoint);

    /** Reset emulator. */
    void reset();

    void onCopyTextToClipboard(String text);

    void onPasteTextFromClipboard();
}
