// molina-patch10b
package com.molina.terminal

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
import com.molina.terminal.proot.RootfsPin
import com.molina.terminal.view.TerminalView

class TerminalFragment : Fragment() {

    private val holder: TerminalSessionHolder by activityViewModels()
    private val setup: DebianSetupViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val owner = requireActivity() as? TerminalSessionFactoryOwner
            ?: error("Activity harus mengimplementasikan TerminalSessionFactoryOwner")

        MolinaTerminalColors.apply(ctx)
        if (holder.textSizePx == 0) holder.textSizePx = MolinaTerminalViewClient.defaultTextSizePx(ctx)

        val terminalView = TerminalView(ctx, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_bg))
        }
        terminalView.setTerminalViewClient(MolinaTerminalViewClient(terminalView, holder))
        terminalView.setTextSize(holder.textSizePx)

        val before = holder.session
        val session = holder.ensureSession(owner.terminalSessionFactory)
        if (session !== before) setup.sessionIsDebian = setup.isReady()
        holder.bind(terminalView)
        terminalView.attachSession(session)
        terminalView.requestFocus()

        val actions = TerminalKeyActions(holder, ctx) { terminalView.onScreenUpdated() }
        val extraKeys = ExtraKeysView(ctx, holder.modifiers, actions) { anchor ->
            showTerminalMenu(anchor, actions) {
                setup.sessionIsDebian = setup.isReady()
                terminalView.attachSession(holder.restart(owner.terminalSessionFactory))
                terminalView.requestFocus()
            }
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
                setup.sessionIsDebian = true
                terminalView.attachSession(holder.restart(owner.terminalSessionFactory))
                terminalView.requestFocus()
                Toast.makeText(ctx, getString(R.string.terminal_setup_ready, RootfsPin.DEBIAN_VERSION), Toast.LENGTH_SHORT).show()
            }
        }
        overlay.render(setup.state)
        setup.refresh()

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(terminalView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(extraKeys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(ctx).apply {
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onDestroyView() {
        setup.listener = null
        holder.modifiers.listener = null
        holder.bind(null)
        super.onDestroyView()
    }
}
