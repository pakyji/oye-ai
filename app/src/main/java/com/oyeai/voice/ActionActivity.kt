package com.oyeai.voice

import android.app.Activity
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
        if (id != -1) {
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
