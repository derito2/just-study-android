package com.dero.juststudykorean

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// shortcut: placeholder until phase 2 brings Compose + the design system.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { setText(R.string.app_name); textSize = 24f; setPadding(48, 200, 48, 48) })
    }
}
