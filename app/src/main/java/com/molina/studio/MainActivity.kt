package com.molina.studio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.molina.cinema.CinemaFragment
import com.molina.files.FilesFragment
import com.molina.settings.SettingsFragment
import com.molina.terminal.TerminalFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_mpv -> CinemaFragment()
                R.id.nav_files -> FilesFragment()
                R.id.nav_terminal -> TerminalFragment()
                else -> SettingsFragment()
            }
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.commit { replace(R.id.container, fragment) }
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_mpv
    }
}
