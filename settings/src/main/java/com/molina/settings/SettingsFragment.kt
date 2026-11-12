package com.molina.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
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
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(title)
            addView(info)
            addView(logButton)
        }
    }
}
