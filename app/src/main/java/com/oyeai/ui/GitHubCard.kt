package com.oyeai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.oyeai.AppSettings
import com.oyeai.github.GitHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings card to push one file to GitHub. Every push asks for confirmation first. */
@Composable
fun GitHubCard(settings: AppSettings) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(settings.githubToken) }
    var repo by remember { mutableStateOf(settings.githubRepo) }
    var branch by remember { mutableStateOf(settings.githubBranch) }
    var path by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GitHub", style = MaterialTheme.typography.titleSmall)
            Text(
                "The token is saved only on this phone, encrypted. Use a fine-grained token for one repository, with Contents: read and write. Never paste it in chat.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it }, label = { Text("Token") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repo, onValueChange = { repo = it }, label = { Text("Repo (owner/name)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = branch, onValueChange = { branch = it }, label = { Text("Branch") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                settings.githubToken = token
                settings.githubRepo = repo
                settings.githubBranch = branch
                status = "GitHub settings saved."
            }) { Text("Save GitHub settings") }

            OutlinedTextField(
                value = path, onValueChange = { path = it }, label = { Text("File path (e.g. notes/todo.md)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = content, onValueChange = { content = it }, label = { Text("File content") },
                minLines = 3, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = message, onValueChange = { message = it }, label = { Text("Commit message") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(enabled = !busy, onClick = {
                status = when {
                    token.isBlank() || repo.isBlank() -> "Enter the token and repo first."
                    path.isBlank() -> "Enter a file path."
                    else -> { confirm = true; "" }
                }
            }) { Text("Push file") }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Push this file?") },
            text = {
                Text("This creates one commit on ${repo.trim()} (${branch.trim().ifBlank { "main" }}) at ${path.trim()}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    status = "Pushing..."
                    // Save what is in the fields first, so the push never uses older saved values.
                    settings.githubToken = token
                    settings.githubRepo = repo
                    settings.githubBranch = branch
                    val cleanPath = path.trim()
                    val commitMessage = message.ifBlank { "Update $cleanPath from OYE" }
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            GitHub.pushFile(
                                settings.githubToken, settings.githubRepo, settings.githubBranch,
                                cleanPath, content, commitMessage
                            )
                        }
                        status = result.message
                        busy = false
                    }
                }) { Text("Push") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
