package com.oyeai

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import com.oyeai.update.UpdateGate

/** Set while the app is on screen. Used to decide whether an app can be opened directly. */
object AppVisibility {
    @Volatile
    var resumed: Boolean = false
}

class MainActivity : ComponentActivity() {

    // Without this permission, reminders and voice action buttons never show on Android 13+.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
        // If the user turned "Listen for Oye" on, restart it whenever the app is in front.
        // Opening the app is allowed to start the microphone service, so no button press is needed.
        val settings = AppSettings(this)
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (settings.listenForOye && micOk) {
            try {
                com.oyeai.voice.ListenService.start(this)
            } catch (e: Exception) {
                // Starting the service failed. The user can turn the setting on again in Settings.
            }
        }
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
    UpdateGate()
    when (screen) {
        Screen.Chat -> ChatScreen(onOpenSettings = { screen = Screen.Settings })
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Chat },
            onOpenAbout = { screen = Screen.About }
        )
        Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
    }
}
