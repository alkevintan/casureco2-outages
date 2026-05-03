package com.casureco2.outages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.casureco2.outages.ui.screen.DashboardScreen
import com.casureco2.outages.ui.screen.LogViewerScreen
import com.casureco2.outages.ui.screen.SettingsScreen
import com.casureco2.outages.ui.theme.CASURECO2OutagesTheme
import com.casureco2.outages.util.NotificationHelper
import com.casureco2.outages.worker.WorkScheduler

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannel(this)
        WorkScheduler.scheduleWeekly(this)

        setContent {
            CASURECO2OutagesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToLogs = { navController.navigate("logs") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("logs") {
                            LogViewerScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
