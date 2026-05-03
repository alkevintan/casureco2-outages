package com.casureco2.outages.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.data.db.BarangaySeedData
import com.casureco2.outages.ui.viewmodel.DashboardViewModel
import com.casureco2.outages.worker.WorkScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val db = OutageDatabase.getInstance(context)
        // Seed barangays if empty
        if (db.barangayDao().count() == 0) {
            db.barangayDao().insertAll(BarangaySeedData.BARRANGAYS)
        }
        viewModel.loadStats(db)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CASURECO 2 Outages") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            // Status Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last Run", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(status = state.lastRunStatus)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.lastRunTime, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Stats Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(modifier = Modifier.weight(1f), title = "Posts", value = state.totalPosts.toString())
                StatCard(modifier = Modifier.weight(1f), title = "Pending", value = state.pendingPosts.toString())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(modifier = Modifier.weight(1f), title = "Parsed", value = state.parsedPosts.toString())
                StatCard(modifier = Modifier.weight(1f), title = "Failed", value = state.failedPosts.toString())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(modifier = Modifier.weight(1f), title = "Outages", value = state.totalOutages.toString())
                StatCard(modifier = Modifier.weight(1f), title = "Synced", value = state.syncedOutages.toString())
            }

            StatCard(modifier = Modifier.fillMaxWidth(), title = "Barangays", value = state.totalBarangays.toString())

            // Actions
            Button(
                onClick = {
                    scope.launch {
                        viewModel.setRunning(true)
                        WorkScheduler.runNow(context)
                        viewModel.setLastRun("Running", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                        snackbarHostState.showSnackbar("Pipeline started")
                    }
                },
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isRunning) "Running..." else "Run Now")
            }

            OutlinedButton(
                onClick = onNavigateToLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Log")
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status.lowercase()) {
        "success" -> MaterialTheme.colorScheme.primaryContainer
        "failed" -> MaterialTheme.colorScheme.errorContainer
        "running" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (status.lowercase()) {
        "success" -> MaterialTheme.colorScheme.onPrimaryContainer
        "failed" -> MaterialTheme.colorScheme.onErrorContainer
        "running" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = textColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
