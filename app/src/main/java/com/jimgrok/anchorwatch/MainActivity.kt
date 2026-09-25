package com.jimgrok.anchorwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.jimgrok.anchorwatch.ui.AnchorWatchTheme
import com.jimgrok.anchorwatch.ui.WatchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContent {
            AnchorWatchTheme {
                WatchScreen()
            }
        }
    }
}
