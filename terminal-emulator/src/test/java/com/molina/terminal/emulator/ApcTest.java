/*
 * Diadaptasi dari termux-app (terminal-emulator), commit 8629e632fcb9. // gate:allow atribusi
 * Lisensi upstream: GPLv3-only; bagian turunan Android-Terminal-Emulator berlisensi Apache 2.0.
 * Perubahan Molina: package diganti ke com.molina.terminal.emulator.
 */
package com.molina.terminal.emulator;

public class ApcTest extends TerminalTestCase {

    public void testApcConsumed() {
        // At time of writing this is part of what yazi sends for probing for kitty graphics protocol support:
        // https://github.com/sxyazi/yazi/blob/0cdaff98d0b3723caff63eebf1974e7907a43a2c/yazi-adapter/src/emulator.rs#L129 // gate:allow atribusi upstream
        // This should not result in anything being written to the screen: If kitty graphics protocol support
        // is implemented it should instead result in an error code on stdin, and if not it should be consumed
        // silently just as xterm does. See https://sw.kovidgoyal.net/kitty/graphics-protocol/. // gate:allow atribusi upstream
        withTerminalSized(2, 2)
            .enterString("\033_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\033\\")
            .assertLinesAre("  ", "  ");

        // It is ok for the APC content to be non printable characters:
        withTerminalSized(12, 2)
            .enterString("hello \033_some\023\033_\\apc#end\033\\ world")
            .assertLinesAre("hello  world", "            ");
    }

}
