/*
 * Diadaptasi dari termux-app (terminal-emulator), commit 8629e632fcb9. // gate:allow atribusi
 * Lisensi upstream: GPLv3-only; bagian turunan Android-Terminal-Emulator berlisensi Apache 2.0.
 * Perubahan Molina: package diganti ke com.molina.terminal.emulator.
 * Perubahan: tipe TerminalSession pada TerminalSessionClient diganti TerminalOutput (sesi ada di terminal-core).
 */
package com.molina.terminal.emulator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The interface for communication between {@link TerminalOutput} and its client. It is used to
 * send callbacks to the client when {@link TerminalOutput} changes or for sending other
 * back data to the client like logs.
 */
public interface TerminalOutputClient {

    void onTextChanged(@NonNull TerminalOutput changedSession);

    void onTitleChanged(@NonNull TerminalOutput changedSession);

    void onSessionFinished(@NonNull TerminalOutput finishedSession);

    void onCopyTextToClipboard(@NonNull TerminalOutput session, String text);

    void onPasteTextFromClipboard(@Nullable TerminalOutput session);

    void onBell(@NonNull TerminalOutput session);

    void onColorsChanged(@NonNull TerminalOutput session);

    void onTerminalCursorStateChange(boolean state);

    void setTerminalShellPid(@NonNull TerminalOutput session, int pid);



    Integer getTerminalCursorStyle();



    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
