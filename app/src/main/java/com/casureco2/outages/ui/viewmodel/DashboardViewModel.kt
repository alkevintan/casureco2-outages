package com.casureco2.outages.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.data.model.Barangay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    data class DashboardState(
        val lastRunTime: String = "Never",
        val lastRunStatus: String = "Idle",
        val totalPosts: Int = 0,
        val pendingPosts: Int = 0,
        val parsedPosts: Int = 0,
        val failedPosts: Int = 0,
        val totalOutages: Int = 0,
        val syncedOutages: Int = 0,
        val totalBarangays: Int = 0,
        val isRunning: Boolean = false
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    fun loadStats(database: OutageDatabase) {
        viewModelScope.launch {
            val rawPostDao = database.rawPostDao()
            val outageDao = database.parsedOutageDao()
            val barangayDao = database.barangayDao()

            _state.value = _state.value.copy(
                totalPosts = rawPostDao.count(),
                pendingPosts = rawPostDao.countPending(),
                parsedPosts = rawPostDao.countParsed(),
                failedPosts = rawPostDao.countFailed(),
                totalOutages = outageDao.count(),
                syncedOutages = outageDao.countSynced(),
                totalBarangays = barangayDao.count()
            )
        }
    }

    fun setRunning(running: Boolean) {
        _state.value = _state.value.copy(isRunning = running)
    }

    fun setLastRun(status: String, time: String) {
        _state.value = _state.value.copy(lastRunStatus = status, lastRunTime = time)
    }
}
