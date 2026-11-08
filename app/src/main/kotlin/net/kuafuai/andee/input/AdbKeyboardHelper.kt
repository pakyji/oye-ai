package net.kuafuai.andee.input

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * Helper for ADBKeyboard installation and management.
 * ADBKeyboard is a virtual keyboard that accepts input via ADB/broadcast.
 */
object AdbKeyboardHelper {

    const val PACKAGE_NAME = "com.adbkeyboard"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.adbkeyboard"
    const val GITHUB_URL = "https://github.com/senzhk/ADBKeyboard"

    /** Check if ADBKeyboard is installed */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Check if ADBKeyboard is enabled as IME */
    fun isEnabled(context: Context): Boolean {
        val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return currentIme?.contains(PACKAGE_NAME) == true
    }

    /** Check if ADBKeyboard is available (installed + enabled) */
    fun isAvailable(context: Context): Boolean = isInstalled(context) && isEnabled(context)

    /** Open Play Store to install ADBKeyboard */
    fun openInstallPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /** Open GitHub releases page */
    fun openGithubPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /** Open IME settings to enable ADBKeyboard */
    fun openImeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /** Send text via ADBKeyboard broadcast */
    fun sendText(context: Context, text: String): Boolean {
        try {
            val intent = Intent("com.adbkeyboard.INPUT_TEXT")
            intent.putExtra("text", text)
            intent.package = PACKAGE_NAME
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Send key event via ADBKeyboard broadcast */
    fun sendKeyEvent(context: Context, keyCode: Int, action: Int): Boolean {
        try {
            val intent = Intent("com.adbkeyboard.KEY_EVENT")
            intent.putExtra("keycode", keyCode)
            intent.putExtra("action", action) // 0 = down, 1 = up
            intent.package = PACKAGE_NAME
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Type text with delay between characters (for apps that need it) */
    fun typeText(context: Context, text: String, delayMs: Long = 50) {
        Thread {
            for (char in text) {
                sendText(context, char.toString())
                Thread.sleep(delayMs)
            }
        }.start()
    }
}