package com.oyeai.actions

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.oyeai.ai.Action

data class Outcome(val ok: Boolean, val message: String)

/**
 * Runs phone actions with ordinary Android intents. It cannot tap or type in other apps,
 * because that needs an accessibility service, which this version does not include.
 */
class ActionExecutor(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun execute(action: Action): Outcome = try {
        when (action) {
            is Action.OpenApp -> openApp(action.app)
            is Action.OpenUrl -> launch(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)), "Opened ${action.url}")
            is Action.CallContact -> call(action.name)
            is Action.SendWhatsApp -> sendWhatsApp(action.name, action.message)
            is Action.CloseApp -> closeApp(action.app)
            is Action.Unknown -> Outcome(false, "Unsupported action: ${action.type}")
        }
    } catch (e: ActivityNotFoundException) {
        Outcome(false, "No app on this phone can do that")
    } catch (e: SecurityException) {
        Outcome(false, "A permission is missing: ${e.message}")
    }

    /** Finds a launcher app by name. Returns (label, package), exact match first. */
    private fun findApp(name: String): Pair<String, String>? {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0).map { info ->
            info.loadLabel(pm).toString() to info.activityInfo.packageName
        }
        val wanted = name.trim().lowercase()
        return apps.firstOrNull { it.first.lowercase() == wanted }
            ?: apps.firstOrNull { it.first.lowercase().contains(wanted) }
    }

    private fun openApp(app: String): Outcome {
        val (label, pkg) = findApp(app) ?: return Outcome(false, "I could not find an app called $app")
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return Outcome(false, "$label cannot be opened")
        return launch(intent, "Opened $label")
    }

    private fun call(name: String): Outcome {
        val number = Contacts.resolveNumber(context, name)
            ?: return Outcome(false, "No phone number found for $name")
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        return if (canCall) {
            launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")), "Calling $name")
        } else {
            launch(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
                "Opened the dialer for $name. Allow calls in Settings to call directly."
            )
        }
    }

    private fun sendWhatsApp(name: String, message: String): Outcome {
        val number = Contacts.resolveNumber(context, name)
            ?: return Outcome(false, "No phone number found for $name")
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return Outcome(false, "The number for $name is not valid")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}"))
        if (isInstalled("com.whatsapp")) intent.setPackage("com.whatsapp")
        return launch(intent, "The WhatsApp chat with $name is open with your message. Tap Send to deliver it.")
    }

    private fun closeApp(app: String): Outcome {
        val (label, pkg) = findApp(app) ?: return Outcome(false, "I could not find an app called $app")
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(pkg)
        launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "")
        return Outcome(
            true,
            "Stopped the background processes of $label and returned to the home screen. " +
                "Android does not let apps force-close an app the user is using."
        )
    }

    private fun launch(intent: Intent, okMessage: String): Outcome {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Outcome(true, okMessage)
    }

    private fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
