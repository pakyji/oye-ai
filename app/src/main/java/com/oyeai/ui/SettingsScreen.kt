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
import com.oyeai.voice.ListenService
import com.oyeai.ai.PROVIDERS
import com.oyeai.service.AgentService
import com.oyeai.social.AccessibilityStatus
import com.oyeai.social.SocialControl
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit = {}) {
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

    var listening by remember { mutableStateOf(settings.listenForOye) }

    fun enableListening() {
        settings.listenForOye = true
        listening = true
        ListenService.start(context)
    }

    val listenPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableListening() else listening = false
    }

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
            var customList by remember { mutableStateOf(settings.customProviders()) }
            (PROVIDERS + customList).forEach { spec ->
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
                            if (spec.keyUrl.isNotBlank()) {
                                TextButton(onClick = { uriHandler.openUri(spec.keyUrl) }) {
                                    Text(stringResource(R.string.get_key))
                                }
                            }
                            if (spec.id.startsWith("custom-")) {
                                TextButton(onClick = {
                                    settings.removeCustomProvider(spec.id)
                                    customList = settings.customProviders()
                                }) { Text("Delete") }
                            }
                            if (saved) Text(stringResource(R.string.saved), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            var newName by remember { mutableStateOf("") }
            var newUrl by remember { mutableStateOf("") }
            var newModel by remember { mutableStateOf("") }
            var newKey by remember { mutableStateOf("") }
            var addError by remember { mutableStateOf("") }
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add your own AI provider", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Any service with an OpenAI-compatible chat endpoint. The URL must start with https://",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("Base URL (https://.../v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newModel, onValueChange = { newModel = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = newKey, onValueChange = { newKey = it }, label = { Text(stringResource(R.string.api_key)) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (addError.isNotBlank()) Text(addError, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        when {
                            newName.isBlank() || newModel.isBlank() -> addError = "Name and model are required."
                            !newUrl.trim().startsWith("https://") -> addError = "The URL must start with https://"
                            newKey.isBlank() -> addError = "API key is required."
                            else -> {
                                settings.addCustomProvider(newName, newUrl, newModel, newKey)
                                customList = settings.customProviders()
                                newName = ""; newUrl = ""; newModel = ""; newKey = ""; addError = ""
                            }
                        }
                    }) { Text("Add provider") }
                }
            }

            GitHubCard(settings)

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

            Text(stringResource(R.string.listen_for_oye), style = MaterialTheme.typography.titleMedium)
            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.listen_for_oye), modifier = Modifier.weight(1f))
                        Switch(
                            checked = listening,
                            onCheckedChange = { on ->
                                if (!on) {
                                    listening = false
                                    settings.listenForOye = false
                                    ListenService.stop(context)
                                } else {
                                    val hasMic = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) enableListening()
                                    else listenPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }
                    Text(
                        stringResource(R.string.listen_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
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

            Text(stringResource(R.string.social_title), style = MaterialTheme.typography.titleMedium)
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var interval by remember { mutableStateOf(settings.scrollIntervalMs / 1000f) }
                    Text(stringResource(R.string.social_interval_setting, interval.roundToInt()))
                    Slider(
                        value = interval,
                        onValueChange = { interval = it },
                        valueRange = 3f..20f,
                        steps = 15,
                        onValueChangeFinished = {
                            SocialControl.setSpeed(context, (interval * 1000f).roundToLong())
                        }
                    )
                    var minutes by remember { mutableStateOf(settings.maxSessionMinutes) }
                    Text(stringResource(R.string.social_session_setting, minutes))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(
                                selected = minutes == m,
                                onClick = {
                                    minutes = m
                                    settings.maxSessionMinutes = m
                                },
                                label = { Text("$m") }
                            )
                        }
                    }
                    Text(stringResource(R.string.accessibility_status), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            when (SocialControl.accessibilityStatus(context)) {
                                AccessibilityStatus.On -> R.string.acc_on
                                AccessibilityStatus.Starting -> R.string.acc_starting
                                AccessibilityStatus.Off -> R.string.acc_off
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.acc_open))
                    }
                    Text(stringResource(R.string.social_privacy), style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.about_button)) }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OyeWordmark(size = 18)
                CreatorCredit()
            }
        }
    }
}
