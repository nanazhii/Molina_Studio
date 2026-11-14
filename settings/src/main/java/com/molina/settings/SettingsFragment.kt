package com.molina.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.molina.log.MolinaLogSetup

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val containerId = container?.id ?: 0
        val title = TextView(ctx).apply { text = "Setelan"; textSize = 24f }
        val info = TextView(ctx).apply { text = "File log: " + MolinaLogSetup.PUBLIC_DIR + "/molina.log" }
        val logButton = MaterialButton(ctx).apply {
            text = "Logcat (setara adb)"
            setOnClickListener {
                parentFragmentManager.commit {
                    replace(containerId, LogViewerFragment())
                    addToBackStack(null)
                }
            }
        }
        val aboutButton = MaterialButton(ctx).apply {
            text = "Tentang"
            setOnClickListener {
                parentFragmentManager.commit {
                    replace(containerId, AboutFragment())
                    addToBackStack(null)
                }
            }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(title)
            addView(info)
            addView(logButton)
            addView(TextView(ctx).apply { text = "Terminal"; textSize = 18f; setPadding(0, pad, 0, 0) })
            addView(MaterialSwitch(ctx).apply {
                text = "Tampilkan nama root di prompt (bawaan: user)"
                isChecked = TerminalPrefs.showRoot(ctx)
                setOnCheckedChangeListener { _, on -> TerminalPrefs.setShowRoot(ctx, on) }
            })
            addView(TextView(ctx).apply {
                text = "Berlaku di sesi terminal baru. Hanya mengubah tampilan prompt; hak akses di Debian tidak berubah."
                textSize = 12f
            })
            addView(TextView(ctx).apply { text = "Jalankan berkas dari drawer"; textSize = 14f; setPadding(0, pad / 2, 0, 0) })
            addView(RadioGroup(ctx).apply {
                val current = TerminalPrefs.runMode(ctx)
                listOf(
                    TerminalPrefs.RUN_SMART to "Buka di nano lalu RUN (tab folder sama, atau tab baru)",
                    TerminalPrefs.RUN_ASK to "Tanya setiap kali",
                    TerminalPrefs.RUN_ACTIVE to "Langsung di sesi aktif",
                    TerminalPrefs.RUN_NEW to "Langsung di sesi baru",
                ).forEach { (value, label) ->
                    addView(RadioButton(ctx).apply {
                        text = label
                        isChecked = value == current
                        setOnCheckedChangeListener { _, on -> if (on) TerminalPrefs.setRunMode(ctx, value) }
                    })
                }
            })
            addView(MaterialSwitch(ctx).apply {
                text = "Catat keluaran Jalankan ke Molina/run-logs"
                isChecked = TerminalPrefs.runLog(ctx)
                setOnCheckedChangeListener { _, on -> TerminalPrefs.setRunLog(ctx, on) }
            })
            addView(TextView(ctx).apply {
                text = "Keluaran tetap tampil di terminal dan disalin ke berkas log. Program layar penuh (mis. curses) bisa tidak berfungsi saat dicatat; python dijalankan dengan -u, bahasa lain bisa menahan keluaran sampai skrip selesai."
                textSize = 12f
            })
            addView(IconPicker.build(ctx))
            addView(aboutButton)
            if (BuildConfig.DEBUG) {
                addView(MaterialButton(ctx).apply {
                    text = "[DEBUG] Uji BUG terkontrol"
                    setOnClickListener { DebugCrash.bug() }
                })
                addView(MaterialButton(ctx).apply {
                    text = "[DEBUG] Picu CRASH (aplikasi menutup)"
                    setOnClickListener { DebugCrash.crash() }
                })
            }
        }
    }
}
