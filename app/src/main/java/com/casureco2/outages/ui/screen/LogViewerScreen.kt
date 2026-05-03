package com.casureco2.outages.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.casureco2.outages.util.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val entries = remember { mutableStateListOf<AppLog.LogEntry>() }

    LaunchedEffect(Unit) {
        entries.addAll(AppLog.getEntries())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(entries) { entry ->
                val color = when (entry.level) {
                    "ERROR" -> MaterialTheme.colorScheme.error
                    "WARN" -> Color(0xFFFFA000)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = "[${entry.timestamp.take(19)}] ${entry.level}: ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}
