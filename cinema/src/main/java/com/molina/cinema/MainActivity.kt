package com.molina.cinema

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Molina Cinema\nmpv: " + com.molina.mpv.MpvNative.isLoaded
            textSize = 24f
            setPadding(48, 96, 48, 48)
        })
    }
}
