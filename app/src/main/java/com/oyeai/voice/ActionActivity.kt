package com.oyeai.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import com.oyeai.R
import com.oyeai.actions.ActionExecutor

/**
 * Runs the action behind a voice notification's Run or Confirm button.
 * It is an activity, not a receiver, because Android 12+ blocks receivers from starting
 * other apps from a notification tap. This one has no UI and closes at once.
 */
class ActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(ActionNotifier.EXTRA_ID, -1)
        val launch = if (id != -1) ActionNotifier.takeLaunch(id) else null
        if (id != -1 && launch != null) {
            // A screen to open. Android reports only whether it could be handed off.
            val text = try {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                getString(R.string.launch_done)
            } catch (e: ActivityNotFoundException) {
                getString(R.string.control_no_handler)
            }
            ActionNotifier.showResult(this, id, text)
        } else if (id != -1) {
            val action = ActionNotifier.take(id)
            val text = if (action == null) {
                getString(R.string.action_expired)
            } else {
                ActionExecutor(this).execute(action).message
            }
            ActionNotifier.showResult(this, id, text)
        }
        finish()
    }
}
