# Metode native dipanggil lewat JNI; jangan di-rename atau dibuang oleh R8.
-keepclasseswithmembernames class com.molina.terminal.pty.PtyNative {
    native <methods>;
}
