package com.oyeai.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oyeai.R
import com.oyeai.social.AccessibilityStatus
import com.oyeai.social.Phase
import com.oyeai.social.SessionEngine
import com.oyeai.social.SocialActions
import com.oyeai.social.SocialApp
import com.oyeai.social.SocialCommand
import com.oyeai.social.SocialControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Opens Android's accessibility settings so the user can turn OYE's service on or off. */
fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // This phone has no accessibility settings screen. Nothing else to do.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialCard(onVoice: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val social by SocialControl.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf<String?>(null) }
    var accessibility by remember { mutableStateOf(SocialControl.accessibilityStatus(context)) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    // Refreshes the countdown and the accessibility status once a second.
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            accessibility = SocialControl.accessibilityStatus(context)
            delay(1_000)
        }
    }

    val active = social.phase != Phase.Idle
    val running = social.phase == Phase.Running

    fun dispatch(command: SocialCommand) {
        scope.launch {
            note = SocialActions.run(context, command)
            accessibility = SocialControl.accessibilityStatus(context)
        }
    }

    val pillText = when (social.phase) {
        Phase.Running -> stringResource(R.string.social_status_running)
        Phase.Paused -> stringResource(R.string.social_status_paused)
        Phase.Idle -> stringResource(R.string.social_status_off)
    }
    val pillColor = when (social.phase) {
        Phase.Running -> OyeCyan
        Phase.Paused -> OyeViolet
        Phase.Idle -> OyeMist
    }

    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.social_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = pillColor.copy(alpha = 0.16f)) {
                    Text(
                        pillText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = pillColor
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            // Shortcuts and the everyday controls.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { dispatch(SocialCommand.Open(SocialApp.INSTAGRAM, reels = false)) }, modifier = Modifier.weight(1f)) {
                    Text(SocialApp.INSTAGRAM.label)
                }
                OutlinedButton(onClick = { dispatch(SocialCommand.Open(SocialApp.FACEBOOK, reels = false)) }, modifier = Modifier.weight(1f)) {
                    Text(SocialApp.FACEBOOK.label)
                }
            }
            Button(onClick = onVoice, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.social_voice))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.social_auto), modifier = Modifier.weight(1f))
                Switch(
                    checked = active,
                    onCheckedChange = { on -> dispatch(if (on) SocialCommand.Start else SocialCommand.Stop) }
                )
            }
            if (active) {
                val remaining = ((social.endsAt - now).coerceAtLeast(0L)) / 1000
                Text(
                    stringResource(R.string.social_timer, "%02d:%02d".format(remaining / 60, remaining % 60)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OyeMist
                )
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = OyeCyan)
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.social_intro), style = MaterialTheme.typography.bodyMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { dispatch(SocialCommand.Start) },
                            enabled = !running,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(if (social.phase == Phase.Paused) R.string.social_resume else R.string.social_start))
                        }
                        OutlinedButton(
                            onClick = { dispatch(SocialCommand.Pause) },
                            enabled = running,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.social_pause)) }
                        OutlinedButton(
                            onClick = { dispatch(SocialCommand.Stop) },
                            enabled = active,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.social_stop)) }
                    }

                    Text(stringResource(R.string.social_speed), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = listOf(
                            R.string.speed_slow to SessionEngine.SPEED_SLOW_MS,
                            R.string.speed_normal to SessionEngine.SPEED_NORMAL_MS,
                            R.string.speed_fast to SessionEngine.SPEED_FAST_MS
                        )
                        presets.forEach { (label, ms) ->
                            FilterChip(
                                selected = social.intervalMs == ms,
                                onClick = {
                                    SocialControl.setSpeed(context, ms)
                                    note = context.getString(
                                        R.string.social_speed_set,
                                        SocialActions.speedName(context, ms)
                                    )
                                },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.social_limit, SettingsMinutes.get(context)),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(stringResource(R.string.accessibility_status), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (accessibility) {
                                AccessibilityStatus.On -> R.string.acc_on
                                AccessibilityStatus.Starting -> R.string.acc_starting
                                AccessibilityStatus.Off -> R.string.acc_off
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (accessibility == AccessibilityStatus.On) OyeCyan else OyeMist
                    )
                    OutlinedButton(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.acc_open))
                    }

                    Text(stringResource(R.string.social_privacy), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.social_limits), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.social_examples), style = MaterialTheme.typography.bodyMedium, color = OyeMist)
                }
            }
        }
    }
}

/** Reads the session length from settings for display. */
private object SettingsMinutes {
    fun get(context: Context): Int = com.oyeai.AppSettings(context).maxSessionMinutes
}
