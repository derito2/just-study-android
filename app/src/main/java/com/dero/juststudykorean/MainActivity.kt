package com.dero.juststudykorean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.dero.juststudykorean.ui.theme.JustStudyTheme

// shortcut: empty `background` screen until task 3 brings navigation and the placeholder screens.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JustStudyTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
    }
}
