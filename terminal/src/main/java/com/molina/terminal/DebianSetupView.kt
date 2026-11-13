// molina-patch10b
package com.molina.terminal

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR
import com.molina.terminal.proot.ProotLocation
import com.molina.terminal.proot.RootfsPin

/**
 * Lapisan penyiapan Debian di atas tab Terminal. Kartu (tawaran, progres, gagal, proot belum
 * siap) menutupi terminal; setelah "Nanti" tinggal sebaris spanduk. Warna mengikuti panel tombol.
 */
internal class DebianSetupView(
    context: Context,
    private val actions: Actions,
) : FrameLayout(context) {

    interface Actions {
        fun onInstall()
        fun onCancel()
        fun onDismiss()
        fun onRecheck()
        fun onReopen()
    }

    private val accent = color(UiR.color.molina_terminal_accent)
    private val onAccent = color(UiR.color.molina_on_terminal_accent)
    private val container = color(UiR.color.molina_terminal_container)
    private val surface = color(UiR.color.molina_surface)
    private val surfaceVariant = color(UiR.color.molina_surface_variant)
    private val outline = color(UiR.color.molina_outline)
    private val onSurface = color(UiR.color.molina_on_surface)
    private val onSurfaceVariant = color(UiR.color.molina_on_surface_variant)
    private val danger = color(UiR.color.molina_error)
    private val scrim = ColorUtils.setAlphaComponent(color(UiR.color.molina_bg), 0xE6)

    private var installBar: ProgressBar? = null
    private var installText: TextView? = null
    private var showingInstall = false

    init {
        visibility = GONE
    }

    fun render(state: SetupState) {
        when (state) {
            SetupState.Hidden -> {
                showingInstall = false
                visibility = GONE
            }
            is SetupState.Dismissed -> showBanner(state)
            is SetupState.Installing -> showInstalling(state)
            else -> showCard(state)
        }
    }

    private fun showBanner(state: SetupState.Dismissed) {
        showingInstall = false
        setBackgroundColor(0)
        isClickable = false
        setLayoutHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        removeAllViews()
        val res = if (state.rootfsMissing) R.string.terminal_setup_banner_install else R.string.terminal_setup_banner_proot
        val banner = TextView(context).apply {
            setText(res)
            setTextColor(accent)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(container)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { actions.onReopen() }
        }
        addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        visibility = VISIBLE
    }

    private fun showCard(state: SetupState) {
        showingInstall = false
        card {
            when (state) {
                SetupState.Offer -> {
                    title(str(R.string.terminal_setup_offer_title, RootfsPin.DEBIAN_VERSION))
                    body(str(R.string.terminal_setup_offer_body, megabytes(RootfsPin.SIZE_BYTES)))
                    buttons(
                        button(str(R.string.terminal_setup_later), false) { actions.onDismiss() },
                        button(str(R.string.terminal_setup_install), true) { actions.onInstall() },
                    )
                }
                is SetupState.Failed -> {
                    title(str(R.string.terminal_setup_failed_title))
                    body(state.message, danger)
                    body(str(R.string.terminal_setup_failed_hint))
                    buttons(
                        button(str(R.string.terminal_setup_close), false) { actions.onDismiss() },
                        button(str(R.string.terminal_setup_retry), true) { actions.onInstall() },
                    )
                }
                is SetupState.NeedsProot -> {
                    title(str(R.string.terminal_setup_proot_title))
                    if (state.problems.isNotEmpty()) {
                        body(state.problems.joinToString("\n") { "\u2022 $it" }, danger)
                    }
                    body(str(R.string.terminal_setup_proot_hint, ProotLocation.EXTERNAL_DIR))
                    buttons(
                        button(str(R.string.terminal_setup_close), false) { actions.onDismiss() },
                        button(str(R.string.terminal_setup_recheck), true) { actions.onRecheck() },
                    )
                }
                else -> Unit
            }
        }
    }

    private fun showInstalling(state: SetupState.Installing) {
        if (!showingInstall) {
            card {
                title(str(R.string.terminal_setup_installing_title, RootfsPin.DEBIAN_VERSION))
                val label = TextView(context).apply {
                    setTextColor(accent)
                    textSize = 13f
                    setPadding(0, dp(12), 0, dp(8))
                }
                val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    progressTintList = ColorStateList.valueOf(accent)
                    progressBackgroundTintList = ColorStateList.valueOf(outline)
                }
                addView(label)
                addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)))
                buttons(button(str(R.string.terminal_setup_cancel), false) { actions.onCancel() })
                installText = label
                installBar = bar
            }
            showingInstall = true
        }
        val bar = installBar ?: return
        val label = installText ?: return
        when (state.stage) {
            SetupStage.DOWNLOAD -> {
                bar.isIndeterminate = false
                bar.progress = permille(0.0, 0.4, state.done, state.total)
                label.text = str(R.string.terminal_setup_stage_download, megabytes(state.done), megabytes(state.total))
            }
            SetupStage.VERIFY -> {
                bar.isIndeterminate = true
                label.text = str(R.string.terminal_setup_stage_verify)
            }
            SetupStage.EXTRACT -> {
                bar.isIndeterminate = false
                bar.progress = permille(0.5, 0.5, state.done, state.total)
                label.text = str(R.string.terminal_setup_stage_extract, state.done.toInt(), state.total.toInt())
            }
        }
    }

    private fun card(build: LinearLayout.() -> Unit) {
        setBackgroundColor(scrim)
        isClickable = true
        setLayoutHeight(ViewGroup.LayoutParams.MATCH_PARENT)
        removeAllViews()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), outline)
            }
            build()
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
        lp.setMargins(dp(12), dp(12), dp(12), dp(12))
        // [SETUP-SCROLL] area terminal sempit (keyboard terbuka): kartu bisa digulir, tombol tetap terjangkau
        val centered = FrameLayout(context)
        centered.addView(box, lp)
        val scroll = ScrollView(context)
        scroll.isFillViewport = true
        scroll.overScrollMode = OVER_SCROLL_NEVER
        scroll.addView(centered, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        visibility = VISIBLE
    }

    private fun LinearLayout.title(s: String) {
        addView(
            TextView(context).apply {
                text = s
                setTextColor(onSurface)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            },
        )
    }

    private fun LinearLayout.body(s: String, c: Int = onSurfaceVariant) {
        addView(
            TextView(context).apply {
                text = s
                setTextColor(c)
                textSize = 14f
                setPadding(0, dp(8), 0, 0)
            },
        )
    }

    private fun LinearLayout.buttons(vararg items: TextView) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(16), 0, 0)
        }
        items.forEachIndexed { i, v ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.leftMargin = dp(8)
            row.addView(v, lp)
        }
        addView(row)
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            setTextColor(if (primary) onAccent else onSurface)
            background = GradientDrawable().apply {
                setColor(if (primary) accent else surfaceVariant)
                cornerRadius = dp(10).toFloat()
                if (!primary) setStroke(dp(1), outline)
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun setLayoutHeight(h: Int) {
        val lp = layoutParams ?: return
        if (lp.height != h) {
            lp.height = h
            layoutParams = lp
        }
    }

    private fun permille(base: Double, span: Double, done: Long, total: Long): Int {
        val f = if (total > 0) done.toDouble() / total else 0.0
        return ((base + span * f) * 1000).toInt().coerceIn(0, 1000)
    }

    private fun megabytes(bytes: Long): Int = ((bytes + 500_000) / 1_000_000).toInt()

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
