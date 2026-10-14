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
import com.oyeai.ui.AboutScreen
import com.oyeai.ui.ChatScreen
import com.oyeai.ui.OyeTheme
import com.oyeai.ui.SettingsScreen
import com.oyeai.ui.SplashScreen

/** Set while the app is on screen. Used to decide whether an app can be opened directly. */
object AppVisibility {
    @Volatile
    var resumed: Boolean = false
}

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

    override fun onResume() {
        super.onResume()
        AppVisibility.resumed = true
    }

    override fun onPause() {
        AppVisibility.resumed = false
        super.onPause()
    }
}

private enum class Screen { Chat, Settings, About }

@Composable
private fun OyeRoot() {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }

    if (!splashDone) {
        SplashScreen(onFinished = { splashDone = true })
        return
    }
    when (screen) {
        Screen.Chat -> ChatScreen(onOpenSettings = { screen = Screen.Settings })
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Chat },
            onOpenAbout = { screen = Screen.About }
        )
        Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
    }
}
