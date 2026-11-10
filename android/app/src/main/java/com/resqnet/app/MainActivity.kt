package com.resqnet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.resqnet.app.ui.ResQNetApp
import com.resqnet.app.ui.theme.ResQNetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResQNetTheme {
                ResQNetApp()
            }
        }
    }
}
