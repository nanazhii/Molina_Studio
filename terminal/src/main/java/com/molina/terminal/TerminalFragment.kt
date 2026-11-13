// molina-patch18
package com.molina.terminal

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.molina.core.ui.R as UiR
import com.molina.terminal.proot.ProotCompat
import com.molina.terminal.proot.RootfsPin
import com.molina.terminal.view.TerminalView

class TerminalFragment : Fragment() {

    private val holder: TerminalSessionHolder by activityViewModels()
    private val setup: DebianSetupViewModel by activityViewModels()

    private lateinit var factory: TerminalSessionFactory
    private lateinit var terminalView: TerminalView
    private lateinit var tabBar: SessionTabBar
    private lateinit var compatPanel: CompatPanelView
    private var closeDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val owner = requireActivity() as? TerminalSessionFactoryOwner
            ?: error("Activity harus mengimplementasikan TerminalSessionFactoryOwner")
        factory = owner.terminalSessionFactory

        MolinaTerminalColors.apply(ctx)
        if (holder.textSizePx == 0) holder.textSizePx = MolinaTerminalViewClient.defaultTextSizePx(ctx)

        terminalView = TerminalView(ctx, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_bg))
        }
        terminalView.setTerminalViewClient(MolinaTerminalViewClient(terminalView, holder))
        terminalView.setTextSize(holder.textSizePx)

        val before = holder.session
        val session = holder.ensureSession(factory)
        if (session !== before) setup.sessionIsDebian = setup.isReady()
        holder.bind(terminalView)
        terminalView.attachSession(session)
        terminalView.requestFocus()

        val actions = TerminalKeyActions(holder, ctx) { terminalView.onScreenUpdated() }
        val extraKeys = ExtraKeysView(ctx, holder.modifiers, actions) { anchor ->
            showTerminalMenu(anchor, actions) { restartActive() }
        }
        holder.modifiers.listener = { extraKeys.refresh() }

        val overlay = DebianSetupView(ctx, object : DebianSetupView.Actions {
            override fun onInstall() = setup.install()
            override fun onCancel() = setup.cancel()
            override fun onDismiss() = setup.dismiss()
            override fun onRecheck() = setup.refresh()
            override fun onReopen() = setup.reopen()
        })
        setup.listener = { state ->
            overlay.render(state)
            if (state === SetupState.Hidden && setup.isReady() && !setup.sessionIsDebian) {
                restartActive()
                Toast.makeText(ctx, getString(R.string.terminal_setup_ready, RootfsPin.DEBIAN_VERSION), Toast.LENGTH_SHORT).show()
            }
        }
        overlay.render(setup.state)
        setup.refresh()

        compatPanel = CompatPanelView(ctx) {
            ProotCompat.enabled = true
            restartActive()
        }

        tabBar = SessionTabBar(ctx, onSelect = { holder.select(it) }, onAdd = { addSession() }, onClose = { confirmClose(it) })
        holder.tabsListener = { syncTabs() }
        syncTabs()

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, SessionTabBar.HEIGHT_DP)))
            addView(terminalView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(compatPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(extraKeys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(ctx).apply {
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    /** Samakan bilah tab, sesi yang tampil di view, dan panel kompatibilitas dengan holder. */
    private fun syncTabs() {
        val current = holder.session ?: return
        val fallback = getString(
            if (setup.isReady()) R.string.terminal_tab_default_debian else R.string.terminal_tab_default_shell,
        )
        tabBar.render(List(holder.count) { holder.titleOf(it) ?: fallback }, holder.currentIndex, holder.canAdd)
        if (terminalView.currentSession !== current) {
            terminalView.attachSession(current)
            terminalView.requestFocus()
        }
        compatPanel.render(holder.failure, ProotCompat.enabled)
    }

    private fun restartActive() {
        setup.sessionIsDebian = setup.isReady()
        holder.restart(factory)
    }

    private fun addSession() {
        if (!holder.canAdd) {
            Toast.makeText(
                requireContext(),
                getString(R.string.terminal_tab_max, TerminalSessionHolder.MAX_SESSIONS),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        setup.sessionIsDebian = setup.isReady()
        holder.newSession(factory)
    }

    private fun confirmClose(index: Int) {
        closeDialog?.dismiss()
        closeDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.terminal_tab_close_title, index + 1))
            .setMessage(R.string.terminal_tab_close_body)
            .setPositiveButton(R.string.terminal_tab_close_confirm) { _, _ ->
                setup.sessionIsDebian = setup.isReady()
                holder.closeSession(index, factory)
            }
            .setNegativeButton(R.string.terminal_tab_cancel, null)
            .show()
    }

    private fun dp(ctx: android.content.Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroyView() {
        closeDialog?.dismiss()
        closeDialog = null
        setup.listener = null
        holder.modifiers.listener = null
        holder.tabsListener = null
        holder.bind(null)
        super.onDestroyView()
    }
}
