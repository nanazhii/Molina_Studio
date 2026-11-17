-dontobfuscate

# untuk antarmuka JNI (libplayer.so memanggil kembali metode statis MPVLib)
-keep class com.molina.player.MPVLib {
	*;
}
