package com.molina.player.subtitle

import org.junit.Test


private var failed = 0
private fun check(name: String, cond: Boolean) {
    if (cond) println("OK   $name") else { println("GAGAL $name"); failed++ }
}

private val base = listOf(SubCue(1_000, 3_000, "Satu"), SubCue(4_000, 6_000, "Dua"), SubCue(7_000, 9_000, "Tiga"))

fun runSubEditTests() {
    // --- TimeCode
    check("parse HH:MM:SS,mmm", TimeCode.parse("01:02:03,450") == 3_723_450L)
    check("parse MM:SS.m (1 digit = 100ms)", TimeCode.parse("02:03.5") == 123_500L)
    check("parse detik polos", TimeCode.parse("62.5") == 62_500L)
    check("parse H:MM:SS tanpa pecahan", TimeCode.parse("1:00:00") == 3_600_000L)
    check("menit > 59 ditolak", TimeCode.parse("00:75:00") == null)
    check("sampah ditolak", TimeCode.parse("abc") == null && TimeCode.parse("") == null && TimeCode.parse("1:2:3:4") == null)
    check("format dua arah", TimeCode.format(3_723_450L) == "01:02:03.450" && TimeCode.parse(TimeCode.format(3_723_450L)) == 3_723_450L)

    // --- replace: ubah teks/waktu, urut ulang, indeks baru
    val r1 = SubEdit.replace(base, 0, 5_000, 5_500, "Satu baru") as EditResult.Ok
    check("replace mengurutkan ulang", r1.cues.map { it.text } == listOf("Dua", "Satu baru", "Tiga") && r1.index == 1)
    check("replace tidak mengubah daftar lama", base[0].text == "Satu")
    check("replace: akhir < awal ditolak", (SubEdit.replace(base, 0, 2_000, 1_000, "x") as EditResult.Error).error == EditError.END_BEFORE_START)
    check("replace: teks kosong ditolak", (SubEdit.replace(base, 0, 1, 2, "  \n ") as EditResult.Error).error == EditError.EMPTY_TEXT)
    check("replace: indeks salah", (SubEdit.replace(base, 9, 1, 2, "x") as EditResult.Error).error == EditError.NO_SUCH_CUE)
    check("replace: waktu negatif", (SubEdit.replace(base, 0, -1, 2, "x") as EditResult.Error).error == EditError.BAD_TIME)
    check("replace: teks terlalu panjang", (SubEdit.replace(base, 0, 1, 2, "a".repeat(2_001)) as EditResult.Error).error == EditError.TOO_LONG)
    check("cleanText CRLF + tepi", SubEdit.cleanText("\r\nA\r\nB\r\n") == "A\nB")

    // --- delete / insert
    val d = SubEdit.delete(base, 1) as EditResult.Ok
    check("delete", d.cues.map { it.text } == listOf("Satu", "Tiga") && d.index == -1)
    val ins = SubEdit.insert(base, 3_500, "Sisip") as EditResult.Ok
    check("insert terurut + durasi bawaan", ins.cues[1].text == "Sisip" && ins.index == 1 && ins.cues[1].endMs == 5_500L)
    check("insert teks kosong ditolak", SubEdit.insert(base, 0, "") is EditResult.Error)

    // --- nudge dijepit
    val n = SubEdit.nudge(base, 0, -5_000, -5_000) as EditResult.Ok
    check("nudge dijepit ke 0", n.cues[0].startMs == 0L && n.cues[0].endMs == 0L)
    val n2 = SubEdit.nudge(base, 1, 500, -3_000) as EditResult.Ok
    check("nudge akhir tidak boleh < awal", n2.cues.first { it.text == "Dua" }.let { it.endMs >= it.startMs })

    // --- cueAt (biner) dan find
    check("cueAt dalam cue", SubEdit.cueAt(base, 4_500) == 1)
    check("cueAt di celah = cue sebelumnya", SubEdit.cueAt(base, 6_500) == 1)
    check("cueAt sebelum semua = 0", SubEdit.cueAt(base, 0) == 0)
    check("cueAt daftar kosong", SubEdit.cueAt(emptyList(), 100) == -1)
    check("find abaikan huruf besar", SubEdit.find(base, "dUA", 0) == 1)
    check("find memutar ke awal", SubEdit.find(base, "satu", 2) == 0)
    check("find tidak ketemu", SubEdit.find(base, "zzz", 0) == -1)

    // --- overlap
    val ov = listOf(SubCue(0, 5_000, "a"), SubCue(4_000, 6_000, "b"), SubCue(5_500, 7_000, "c"), SubCue(8_000, 9_000, "d"))
    check("overlap terdeteksi (b dan c)", SubEdit.overlaps(ov) == setOf(1, 2))
    check("tanpa overlap", SubEdit.overlaps(base).isEmpty())

    // --- sesi undo/redo/dirty
    val s = SubEditSession(base)
    check("awal tidak kotor", !s.dirty && !s.canUndo && !s.canRedo)
    s.apply(SubEdit.replace(s.cues, 0, 1_000, 3_000, "Ubah"))
    check("kotor setelah ubah", s.dirty && s.canUndo && s.cues[0].text == "Ubah")
    check("operasi gagal tidak mengubah apa pun", s.apply(SubEdit.replace(s.cues, 0, 5, 1, "x")) == null && s.lastError == EditError.END_BEFORE_START && s.cues[0].text == "Ubah")
    check("undo", s.undo() && s.cues == base && !s.dirty)
    s.redo(); s.markSaved()
    check("markSaved: tidak kotor, undo tetap ada", !s.dirty && s.canUndo)
    s.undo()
    check("undo setelah simpan = kotor lagi", s.dirty)
    s.redo(); s.undo()
    check("redo", s.redo() && s.cues[0].text == "Ubah")
    s.undo(); s.apply(SubEdit.delete(s.cues, 0))
    check("aksi baru membersihkan redo", !s.canRedo)
    val big = SubEditSession(base, maxHistory = 3)
    repeat(10) { big.apply(SubEdit.nudge(big.cues, 0, 10, 10)) }
    var steps = 0; while (big.undo()) steps++
    check("riwayat dibatasi", steps == 3)

    // --- round trip lewat codec (SRT dan ASS)
    val multi = listOf(SubCue(1_000, 2_500, "Baris 1\nBaris 2"), SubCue(3_000, 4_000, "Tes"))
    check("round trip SRT", SrtCodec.parse(SrtCodec.write(multi)) == multi)
    check("round trip ASS", AssCodec.parse(AssCodec.write(multi)) == multi)

    org.junit.Assert.assertEquals("$failed pemeriksaan GAGAL (lihat keluaran tes)", 0, failed)
}

class SubEditTest {
    @Test fun semuaPemeriksaan() { runSubEditTests() }
}
