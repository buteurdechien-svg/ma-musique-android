package com.ahize.mamusique

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahize.mamusique.ui.MaMusiqueApp
import com.ahize.mamusique.ui.MaMusiqueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaMusiqueTheme {
                MaMusiqueApp()
            }
        }
    }
}
