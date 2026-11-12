package com.molina.cinema

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class CinemaFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TextView(requireContext()).apply {
            text = "Molina Cinema\nmpv: " + MpvNative.isLoaded
            textSize = 24f
            setPadding(48, 96, 48, 48)
        }
}
