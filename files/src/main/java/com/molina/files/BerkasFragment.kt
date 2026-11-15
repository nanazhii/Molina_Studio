package com.molina.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.molina.filemanager.FileManagerEmbed

/** [molina-27b] Tab Berkas memakai file manager salinan Terminal. FilesFragment lama tetap ada (balik: 1 baris di MainActivity). */
class BerkasFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (view as FrameLayout).addView(
            FileManagerEmbed.create(requireContext(), viewLifecycleOwner.lifecycleScope),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}
