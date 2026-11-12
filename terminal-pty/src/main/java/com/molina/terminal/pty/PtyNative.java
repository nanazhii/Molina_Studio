/*
 * Deklarasi JNI mengikuti JNI.java termux-app @ 8629e632fcb9. // gate:allow atribusi
 * Perubahan Molina: paket com.molina.terminal.pty, kelas PtyNative, pustaka molina_pty, tambah setPtyUTF8Mode.
 */
package com.molina.terminal.pty;

/** Jembatan JNI ke molina_pty.c. Hanya dipakai oleh {@link PtyProcess}. */
final class PtyNative {

    static {
        System.loadLibrary("molina_pty");
    }

    private PtyNative() {}

    /**
     * Buat subproses di PTY baru.
     *
     * @param processId diisi PID anak pada indeks 0
     * @return file descriptor sisi master PTY
     */
    static native int createSubprocess(String cmd, String cwd, String[] args, String[] envVars,
                                       int[] processId, int rows, int columns, int cellWidth, int cellHeight);

    static native void setPtyWindowSize(int fd, int rows, int cols, int cellWidth, int cellHeight);

    static native void setPtyUTF8Mode(int fd);

    /** Blokir sampai proses selesai. Kode keluar, atau -sinyal bila dihentikan sinyal. */
    static native int waitFor(int processId);

    static native void close(int fileDescriptor);
}
