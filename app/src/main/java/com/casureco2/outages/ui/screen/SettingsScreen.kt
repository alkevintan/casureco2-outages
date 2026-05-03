package com.casureco2.outages.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.util.SecureStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var openCodeKey by remember { mutableStateOf(SecureStorage.openCodeApiKey ?: "") }
    var githubToken by remember { mutableStateOf(SecureStorage.githubToken ?: "") }
    var githubOwner by remember { mutableStateOf(SecureStorage.githubOwner ?: "") }
    var githubRepo by remember { mutableStateOf(SecureStorage.githubRepo ?: "casureco2-outages") }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("API Keys", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = openCodeKey,
                onValueChange = { openCodeKey = it },
                label = { Text("OpenCode API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = githubToken,
                onValueChange = { githubToken = it },
                label = { Text("GitHub Token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Text("GitHub Repository", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = githubOwner,
                onValueChange = { githubOwner = it },
                label = { Text("GitHub Owner (username)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = githubRepo,
                onValueChange = { githubRepo = it },
                label = { Text("Repository Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    SecureStorage.openCodeApiKey = openCodeKey.takeIf { it.isNotBlank() }
                    SecureStorage.githubToken = githubToken.takeIf { it.isNotBlank() }
                    SecureStorage.githubOwner = githubOwner.takeIf { it.isNotBlank() }
                    SecureStorage.githubRepo = githubRepo.takeIf { it.isNotBlank() }
                    scope.launch {
                        snackbarHostState.showSnackbar("Settings saved")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)

            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Database")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Database?") },
            text = { Text("This will delete all posts, outages, and settings. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            OutageDatabase.getInstance(context).clearAllTables()
                            SecureStorage.clearAll()
                            showClearDialog = false
                            snackbarHostState.showSnackbar("Database cleared")
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
