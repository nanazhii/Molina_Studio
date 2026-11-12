// molina-patch8
package com.molina.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.molina.core.ui.R as UiR
import com.molina.terminal.view.TerminalView

class TerminalFragment : Fragment() {

    private val holder: TerminalSessionHolder by activityViewModels()

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

        val session = holder.ensureSession(owner.terminalSessionFactory)
        holder.bind(terminalView)
        terminalView.attachSession(session)
        terminalView.requestFocus()

        val actions = TerminalKeyActions(holder, ctx) { terminalView.onScreenUpdated() }
        val extraKeys = ExtraKeysView(ctx, holder.modifiers, actions) { anchor ->
            showTerminalMenu(anchor, actions) {
                terminalView.attachSession(holder.restart(owner.terminalSessionFactory))
                terminalView.requestFocus()
            }
        }
        holder.modifiers.listener = { extraKeys.refresh() }

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(terminalView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(extraKeys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onDestroyView() {
        holder.modifiers.listener = null
        holder.bind(null)
        super.onDestroyView()
    }
}
