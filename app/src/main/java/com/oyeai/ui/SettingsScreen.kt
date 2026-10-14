package com.oyeai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.oyeai.AppSettings
import com.oyeai.R
import com.oyeai.ai.PROVIDERS
import com.oyeai.service.AgentService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings = remember { AppSettings(context) }

    var language by remember { mutableStateOf(settings.language) }
    var selectedProvider by remember { mutableStateOf(settings.selectedProvider) }
    var background by remember { mutableStateOf(settings.runInBackground) }
    var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
    var speak by remember { mutableStateOf(settings.speakReplies) }

    fun enableBackground() {
        settings.runInBackground = true
        background = true
        AgentService.start(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { enableBackground() }

    val phonePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val languages = listOf(
        "auto" to stringResource(R.string.lang_auto),
        "it" to "Italiano",
        "en" to "English",
        "ur" to "اردو"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    languages.forEach { (code, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = language == code,
                                onClick = {
                                    language = code
                                    settings.language = code
                                }
                            )
                            Text(label)
                        }
                    }
                }
            }

            Text(stringResource(R.string.ai_providers), style = MaterialTheme.typography.titleMedium)
            PROVIDERS.forEach { spec ->
                var apiKey by remember(spec.id) { mutableStateOf(settings.key(spec.id)) }
                var model by remember(spec.id) { mutableStateOf(settings.savedModel(spec.id)) }
                var saved by remember(spec.id) { mutableStateOf(false) }
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedProvider == spec.id,
                                onClick = {
                                    selectedProvider = spec.id
                                    settings.selectedProvider = spec.id
                                }
                            )
                            Text(spec.name, style = MaterialTheme.typography.titleSmall)
                            if (selectedProvider == spec.id) {
                                Text(
                                    "  · ${stringResource(R.string.provider_first)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; saved = false },
                            label = { Text(stringResource(R.string.api_key)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; saved = false },
                            label = { Text("${stringResource(R.string.model)} (${spec.defaultModel})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                settings.saveProvider(spec.id, apiKey, model)
                                saved = true
                            }) { Text(stringResource(R.string.save)) }
                            TextButton(onClick = { uriHandler.openUri(spec.keyUrl) }) {
                                Text(stringResource(R.string.get_key))
                            }
                            if (saved) Text(stringResource(R.string.saved), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text(stringResource(R.string.background), style = MaterialTheme.typography.titleMedium)
            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.background), modifier = Modifier.weight(1f))
                        Switch(
                            checked = background,
                            onCheckedChange = { on ->
                                if (on) {
                                    val needsNotif = android.os.Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    if (needsNotif) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else enableBackground()
                                } else {
                                    background = false
                                    settings.runInBackground = false
                                    settings.startOnBoot = false
                                    startOnBoot = false
                                    AgentService.stop(context)
                                }
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.start_on_boot), modifier = Modifier.weight(1f))
                        Switch(
                            checked = startOnBoot,
                            enabled = background,
                            onCheckedChange = {
                                startOnBoot = it
                                settings.startOnBoot = it
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.speak_replies), modifier = Modifier.weight(1f))
                        Switch(
                            checked = speak,
                            onCheckedChange = {
                                speak = it
                                settings.speakReplies = it
                            }
                        )
                    }
                }
            }

            Text(stringResource(R.string.permissions), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    phonePermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.allow_phone)) }
            OutlinedButton(
                onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.allow_mic)) }

            Text(stringResource(R.string.limits), style = MaterialTheme.typography.bodySmall)
        }
    }
}
