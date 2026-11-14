// molina-patch18
package com.molina.terminal

import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.molina.core.ui.R as UiR
import com.molina.terminal.core.PtyTerminalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.molina.log.MolinaEvents
import com.molina.terminal.proot.NanoProtocol
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
    private var fileDrawer: FileDrawerView? = null
    private var terminalHeader: TerminalHeaderView? = null
    private var nanoKeys: NanoKeysView? = null

    /** Back menutup drawer berkas lebih dulu; aktif hanya saat drawer terbuka. */
    private val drawerBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { fileDrawer?.close() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val owner = requireActivity() as? TerminalSessionFactoryOwner
            ?: error("Activity harus mengimplementasikan TerminalSessionFactoryOwner")
        factory = owner.terminalSessionFactory
        (owner as? NanoKeysStateOwner)?.let { holder.nanoKeysOpen = it.nanoKeysOpen }

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
        val extraKeys = ExtraKeysView(ctx, holder.modifiers, actions, onDrawer = { fileDrawer?.toggle() }) { anchor ->
            showTerminalMenu(anchor, actions) { restartActive() }
        }
        holder.modifiers.listener = { extraKeys.refresh() }

        val nano = NanoKeysView(
            ctx,
            send = { holder.session?.write(it) },
            title = { holder.titleOf(holder.currentIndex) },
            readRows = { bottom ->
                val em = holder.session?.getEmulator()
                val scr = em?.screen
                if (em == null || scr == null) {
                    null
                } else {
                    val first = if (bottom <= 0) 0 else maxOf(0, em.mRows - bottom)
                    (first until em.mRows).map { r -> scr.getSelectedText(0, r, em.mColumns, r) }
                }
            },
            isOpen = { holder.nanoKeysOpen },
            setOpen = { v ->
                holder.nanoKeysOpen = v
                (owner as? NanoKeysStateOwner)?.nanoKeysOpen = v
            },
        )
        nanoKeys = nano

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

        // [SETUP-IN-FRAME] overlay Debian hanya menutupi area terminal, bukan bilah tab
        val terminalArea = FrameLayout(ctx).apply {
            addView(terminalView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val drawerView = FileDrawerView(
            ctx, holder.drawerState, viewLifecycleOwner.lifecycleScope,
            object : FileDrawerView.Host {
                override fun send(text: String) { holder.session?.write(text) }
                override fun hasSession(): Boolean = holder.session != null
                override fun isProotSession(): Boolean = holder.isProot(holder.currentIndex)
                override fun isFullScreenProgram(): Boolean = holder.session?.getEmulator()?.isAlternateBufferActive == true
                override fun runInNewSession(line: (Boolean) -> String?): Boolean {
                    if (!holder.canAdd) {
                        Toast.makeText(ctx, getString(R.string.terminal_tab_max, TerminalSessionHolder.MAX_SESSIONS), Toast.LENGTH_SHORT).show()
                        return false
                    }
                    setup.sessionIsDebian = setup.isReady()
                    val s = holder.newSession(factory) ?: return false
                    val text = line(s.isProot)
                    if (text == null) {
                        Toast.makeText(ctx, R.string.drawer_not_visible, Toast.LENGTH_SHORT).show()
                        return false
                    }
                    sendWhenReady(s, text)
                    return true
                }
                override fun activateSessionAt(guestDir: String): Boolean {
                    val i = holder.indexOfDir(guestDir)
                    if (i < 0) return false
                    holder.select(i)
                    return true
                }
                override fun showNanoKeys() {
                    holder.nanoKeysOpen = true
                    (owner as? NanoKeysStateOwner)?.nanoKeysOpen = true
                    nanoKeys?.refresh()
                }
                // molina-patch30
                override fun autoRunNano() {
                    val s = holder.session ?: return
                    fun nanoUp(): Boolean {
                        val em = s.getEmulator() ?: return false
                        return NanoProtocol.isEditorTitle(em.title?.trim()) && em.isAlternateBufferActive
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        var waited = 0L
                        while (waited < 12000L && s.isRunning && !nanoUp()) {
                            delay(100)
                            waited += 100
                        }
                        if (s.isRunning && nanoUp()) {
                            delay(300)
                            if (s.isRunning && nanoUp()) {
                                s.write(NanoProtocol.Seq.RUN)
                                MolinaEvents.behavior("term", "drawer.op", fields = mapOf("op" to "auto_run_sent", "waited_ms" to waited.toString()))
                                return@launch
                            }
                        }
                        MolinaEvents.bug("term", "drawer.auto-run-timeout", "RUN otomatis nano tidak terkirim", fields = mapOf("waited_ms" to waited.toString()))
                    }
                }
                override fun runMode(): RunMode = (owner as? RunPrefsOwner)?.runMode ?: RunMode.ASK
                override fun runLogEnabled(): Boolean = (owner as? RunPrefsOwner)?.runLogEnabled == true
                override fun onRootChanged(path: String) { terminalHeader?.setPath(path) }
                override fun onOpenChanged(open: Boolean) {
                    drawerBack.isEnabled = open
                    if (!open) terminalView.requestFocus()
                }
            },
        )
        fileDrawer = drawerView

        val headerView = TerminalHeaderView(
            ctx,
            onFolder = { fileDrawer?.toggle() },
            onMenu = { anchor -> showTerminalMenu(anchor, actions, below = true, withKeys = false) { restartActive() } },
        )
        headerView.setPath(holder.drawerState.root?.path ?: DrawerFs.DEFAULT_ROOT)
        terminalHeader = headerView
        headerView.setCwd(holder.cwdOf(holder.currentIndex))
        holder.screenListener = { terminalHeader?.setCwd(holder.cwdOf(holder.currentIndex)) }
        syncTabs()

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                headerView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(ctx, 6), dp(ctx, 4), dp(ctx, 6), dp(ctx, 2))
                },
            )
            addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, SessionTabBar.HEIGHT_DP)))
            addView(
                framed(ctx, terminalArea),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 4))
                },
            )
            addView(compatPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(nano, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(extraKeys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(ctx).apply {
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(drawerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, drawerBack)
    }

    /** Samakan bilah tab, sesi yang tampil di view, dan panel kompatibilitas dengan holder. */
    private fun syncTabs() {
        val current = holder.session ?: return
        val debianLabel = getString(R.string.terminal_tab_default_debian)
        val shellLabel = getString(R.string.terminal_tab_default_shell)
        val titles = List(holder.count) { holder.titleOf(it) ?: if (holder.isProot(it)) debianLabel else shellLabel }
        val shells = List(holder.count) { !holder.isProot(it) }
        tabBar.render(titles, shells, holder.currentIndex, holder.canAdd)
        if (terminalView.currentSession !== current) {
            terminalView.attachSession(current)
            terminalView.requestFocus()
        }
        compatPanel.render(holder.failure, ProotCompat.enabled)
        nanoKeys?.refresh()
        terminalHeader?.setCwd(holder.cwdOf(holder.currentIndex))
        terminalHeader?.setStatus(
            ContextCompat.getColor(requireContext(), if (holder.failure != null) UiR.color.molina_key_red else UiR.color.molina_key_green),
        )
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

    /** Kirim [text] ke sesi baru setelah shell siap: kanal PTY terbuka, dan untuk proot juga prompt pertama (cwd terlapor). */
    private fun sendWhenReady(s: PtyTerminalSession, text: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val limit = if (s.isProot) 4000L else 1500L
            var waited = 0L
            while (waited < limit && (!s.isRunning || (s.isProot && s.getEmulator()?.cwd.isNullOrBlank()))) {
                delay(100)
                waited += 100
            }
            if (!s.isProot) delay(200)
            if (s.isRunning) s.write(text)
        }
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

    /** [TERMINAL-FRAME] Bingkai abu-abu membulat di sekeliling layar terminal (desain Molina-Studio). */
    private fun framed(ctx: android.content.Context, content: View): FrameLayout {
        val radius = dp(ctx, 12).toFloat()
        return FrameLayout(ctx).apply {
            setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(ContextCompat.getColor(ctx, UiR.color.molina_bg))
            }
            foreground = GradientDrawable().apply {
                cornerRadius = radius
                setStroke(dp(ctx, 2), ContextCompat.getColor(ctx, UiR.color.molina_terminal_frame))
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            clipToOutline = true
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun dp(ctx: android.content.Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroyView() {
        closeDialog?.dismiss()
        closeDialog = null
        fileDrawer = null
        nanoKeys = null
        terminalHeader = null
        drawerBack.isEnabled = false
        setup.listener = null
        holder.modifiers.listener = null
        holder.tabsListener = null
        holder.screenListener = null
        holder.bind(null)
        super.onDestroyView()
    }
}
