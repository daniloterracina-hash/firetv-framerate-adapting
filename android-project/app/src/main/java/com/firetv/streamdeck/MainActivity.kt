package com.firetv.streamdeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.firetv.streamdeck.ui.DarkBackground
import com.firetv.streamdeck.ui.DarkSurface
import com.firetv.streamdeck.ui.FireTvDeckScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkBackground,
                    surface = DarkSurface
                )
            ) {
                FireTvDeckScreen()
            }
        }
    }
}
