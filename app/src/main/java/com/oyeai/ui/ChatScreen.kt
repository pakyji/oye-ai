package com.oyeai.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyeai.R
import com.oyeai.ai.Action

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenSettings: () -> Unit, vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(notice) {
        val message = notice
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearNotice()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.listen { heard -> vm.send(heard) } else vm.micDenied()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        CreatorCredit()
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_hint)) },
                    maxLines = 4
                )
                IconButton(
                    onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.speak_button))
                }
                IconButton(
                    onClick = {
                        val text = input
                        input = ""
                        vm.send(text)
                    },
                    enabled = input.isNotBlank() && !busy
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.send))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            item {
                SocialCard(onVoice = { micPermission.launch(Manifest.permission.RECORD_AUDIO) })
                Spacer(Modifier.height(8.dp))
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg) { index -> vm.runAction(msg.id, index) }
            }
            if (busy) {
                item {
                    Text(
                        stringResource(R.string.working),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onConfirm: (Int) -> Unit) {
    val mine = msg.role == Role.User
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                msg.text,
                modifier = Modifier.padding(12.dp).widthIn(max = 300.dp),
                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        msg.actions.forEachIndexed { index, item ->
            ActionCard(item) { onConfirm(index) }
        }
    }
}

@Composable
private fun ActionCard(item: ActionItem, onConfirm: () -> Unit) {
    Card(modifier = Modifier.padding(top = 4.dp).widthIn(max = 300.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(describeAction(item.action), style = MaterialTheme.typography.bodyMedium)
            when {
                item.state == ActionState.Pending && item.action.needsConfirmation ->
                    Button(onClick = onConfirm) { Text(stringResource(R.string.confirm)) }
                item.state == ActionState.Pending ->
                    Text(stringResource(R.string.working), style = MaterialTheme.typography.bodySmall)
                else ->
                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun describeAction(action: Action): String = when (action) {
    is Action.OpenApp -> "Open ${action.app}"
    is Action.OpenUrl -> "Open ${action.url}"
    is Action.CallContact -> "Call ${action.name}"
    is Action.SendWhatsApp -> "WhatsApp to ${action.name}: \"${action.message}\""
    is Action.CloseApp -> "Close ${action.app}"
    is Action.Unknown -> "Unsupported action: ${action.type}"
}
