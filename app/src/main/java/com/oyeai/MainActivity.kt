package com.oyeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.oyeai.ui.ChatScreen
import com.oyeai.ui.OyeTheme
import com.oyeai.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OyeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OyeRoot()
                }
            }
        }
    }
}

@Composable
private fun OyeRoot() {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        ChatScreen(onOpenSettings = { showSettings = true })
    }
}
