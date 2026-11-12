// molina-patch9
package com.molina.terminal

import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.widget.PopupMenu

/** Menu tombol ≡: simbol yang sulit diketik, END/PGUP/PGDN, dan mulai ulang sesi. */
internal fun showTerminalMenu(anchor: View, actions: TerminalKeyActions, onRestart: () -> Unit) {
    val items = buildList<Pair<String, () -> Unit>> {
        listOf("/", "|", "~", "\\", "_", "&").forEach { s -> add(s to { actions.send(s) }) }
        add("END" to { actions.sendKey(KeyEvent.KEYCODE_MOVE_END) })
        add("PGUP" to { actions.sendKey(KeyEvent.KEYCODE_PAGE_UP) })
        add("PGDN" to { actions.sendKey(KeyEvent.KEYCODE_PAGE_DOWN) })
        add(anchor.context.getString(R.string.terminal_restart_session) to onRestart)
    }
    val popup = PopupMenu(anchor.context, anchor)
    items.forEachIndexed { i, item -> popup.menu.add(Menu.NONE, i, i, item.first) }
    popup.setOnMenuItemClickListener { menuItem ->
        items[menuItem.itemId].second.invoke()
        true
    }
    popup.show()
}
