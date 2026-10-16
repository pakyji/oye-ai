package com.oyeai.update

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.oyeai.BuildConfig
import com.oyeai.R
import kotlinx.coroutines.launch

/**
 * Checks GitHub for a newer OYE build (at most every six hours) and offers to install it.
 * Shows nothing when offline or up to date.
 */
@Composable
fun UpdateGate() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offer by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val failedText = stringResource(R.string.update_failed)
    val allowText = stringResource(R.string.update_allow_sources)

    LaunchedEffect(Unit) {
        if (!UpdateChecker.checkDue(context)) return@LaunchedEffect
        UpdateChecker.markChecked(context)
        offer = UpdateChecker.fetchNewest(BuildConfig.VERSION_CODE)
    }

    val update = offer ?: return
    AlertDialog(
        onDismissRequest = { if (!busy) offer = null },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_text, update.tag))
                if (error.isNotBlank()) Text(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = ""
                    scope.launch {
                        val file = try {
                            UpdateChecker.download(context, update)
                        } catch (e: Exception) {
                            null
                        }
                        busy = false
                        if (file == null) {
                            error = failedText
                        } else if (!UpdateChecker.install(context, file)) {
                            error = allowText
                        } else {
                            offer = null
                        }
                    }
                }
            ) { Text(stringResource(if (busy) R.string.update_downloading else R.string.update_install)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { offer = null }) { Text(stringResource(R.string.update_later)) }
        }
    )
}
