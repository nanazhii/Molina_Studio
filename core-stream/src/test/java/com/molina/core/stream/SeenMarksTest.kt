package com.molina.core.stream

import org.junit.Test

private var fails = 0
private fun check(name: String, ok: Boolean) {
    if (ok) println("ok   $name") else { fails++; println("GAGAL $name") }
}

fun runSeenMarksTests() {
    val id = "dQw4w9WgXcQ"
    val key = "yt:$id"
    check("watch?v=", VideoKey.of("https://www.youtube.com/watch?v=$id") == key) // gate:allow URL contoh uji
    check("watch tanpa www", VideoKey.of("https://youtube.com/watch?v=$id") == key) // gate:allow URL contoh uji
    check("m.youtube", VideoKey.of("https://m.youtube.com/watch?v=$id") == key) // gate:allow URL contoh uji
    check("music.youtube", VideoKey.of("https://music.youtube.com/watch?v=$id") == key) // gate:allow URL contoh uji
    check("parameter tambahan setelah id", VideoKey.of("https://www.youtube.com/watch?v=$id&list=PL1&t=30s") == key) // gate:allow URL contoh uji
    check("v= bukan parameter pertama", VideoKey.of("https://www.youtube.com/watch?feature=share&v=$id") == key) // gate:allow URL contoh uji
    check("youtu.be", VideoKey.of("https://youtu.be/$id") == key) // gate:allow URL contoh uji
    check("youtu.be dengan ?t=", VideoKey.of("https://youtu.be/$id?t=5") == key) // gate:allow URL contoh uji
    check("shorts", VideoKey.of("https://www.youtube.com/shorts/$id") == key) // gate:allow URL contoh uji
    check("live", VideoKey.of("https://www.youtube.com/live/$id") == key) // gate:allow URL contoh uji
    check("embed", VideoKey.of("https://www.youtube.com/embed/$id") == key) // gate:allow URL contoh uji
    check("spasi di tepi dipangkas", VideoKey.of("  https://youtu.be/$id \n") == key) // gate:allow URL contoh uji
    check("huruf besar skema/host", VideoKey.of("HTTPS://WWW.YOUTUBE.COM/watch?v=$id") == key) // gate:allow URL contoh uji
    check("id besar-kecil dipertahankan", VideoKey.of("https://youtu.be/AbCdEfGhIjK") == "yt:AbCdEfGhIjK") // gate:allow URL contoh uji
    check("bukan YouTube -> null", VideoKey.of("https://example.com/watch?v=$id") == null) // gate:allow URL contoh uji
    check("playlist saja -> null", VideoKey.of("https://www.youtube.com/playlist?list=PLabc") == null) // gate:allow URL contoh uji
    check("id 10 karakter -> null", VideoKey.of("https://youtu.be/dQw4w9WgXc") == null) // gate:allow URL contoh uji
    check("id 12 karakter -> null", VideoKey.of("https://youtu.be/dQw4w9WgXcQa") == null) // gate:allow URL contoh uji
    check("kosong -> null", VideoKey.of("") == null)
    check("path lokal -> null", VideoKey.of("/storage/emulated/0/a.mp4") == null)

    check("push: terbaru di atas", SeenMarks.push(listOf("b", "a"), "c") == listOf("c", "b", "a"))
    check("push: duplikat pindah ke atas", SeenMarks.push(listOf("c", "b", "a"), "a") == listOf("a", "c", "b"))
    check("push: batas jumlah", SeenMarks.push(listOf("c", "b", "a"), "d", max = 3) == listOf("d", "c", "b"))
    check("push: batas bawaan", (1..SeenMarks.MAX + 10).fold(emptyList<String>()) { l, i -> SeenMarks.push(l, "k$i") }.size == SeenMarks.MAX)
    val l = listOf("yt:aaaaaaaaaaa", "yt:bbbbbbbbbbb")
    check("encode/decode bolak-balik", SeenMarks.decode(SeenMarks.encode(l)) == l)
    check("decode null -> kosong", SeenMarks.decode(null).isEmpty())
    check("decode kosong -> kosong", SeenMarks.decode("").isEmpty())
    check("decode: baris kosong dan duplikat dibuang", SeenMarks.decode("a\n\n b \na\n") == listOf("a", "b"))

    org.junit.Assert.assertEquals("$fails pemeriksaan GAGAL (lihat keluaran tes)", 0, fails)
}

class SeenMarksTest {
    @Test fun semuaPemeriksaan() { runSeenMarksTests() }
}
