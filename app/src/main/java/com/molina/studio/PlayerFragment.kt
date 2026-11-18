package com.molina.studio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.molina.player.MainScreenFragment

/**
 * Tab Player: menanam layar home mpv asli (MainScreenFragment dari :player).
 * Pemilih berkas, Setelan mpv, dan pemutar tetap Activity bawaan mpv (lihat manifest :player).
 */
class PlayerFragment : Fragment(R.layout.fragment_player) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.player_home_container, MainScreenFragment())
                .commit()
        }
    }
}
