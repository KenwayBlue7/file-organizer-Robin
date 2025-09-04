package com.kenway.robin.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kenway.robin.data.ImageRepository
import com.kenway.robin.data.TaggedFolder
import com.kenway.robin.services.ScreenshotMonitorService
import com.kenway.robin.services.ServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val application: Application,
    private val imageRepository: ImageRepository
) : ViewModel() {

    data class DashboardUiState(
        val taggedFolders: List<TaggedFolder> = emptyList(),
        val isLoading: Boolean = false,
        val isServiceEnabled: Boolean = false,
        val isMonitoringEnabled: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val prefs: SharedPreferences = application.getSharedPreferences("robin_prefs", Context.MODE_PRIVATE)

    init {
        loadTaggedFolders()
        _uiState.update {
            it.copy(isServiceEnabled = ServiceHelper.isServiceRunning(application, ScreenshotMonitorService::class.java))
        }
    }

    private fun loadTaggedFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val folders = imageRepository.getTaggedFolders()
            _uiState.update {
                it.copy(
                    taggedFolders = folders,
                    isLoading = false
                )
            }
        }
    }

    fun toggleMonitoringService(enable: Boolean) {
        val intent = Intent(application, ScreenshotMonitorService::class.java)
        if (enable) {
            application.startService(intent)
        } else {
            application.stopService(intent)
        }
        _uiState.update { it.copy(isServiceEnabled = enable) }
    }

    // Change from private to public and rename
    fun refreshFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val folders = imageRepository.getTaggedFolders()
                _uiState.update { 
                    it.copy(
                        taggedFolders = folders,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load folders: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadServiceState() {
        val isEnabled = prefs.getBoolean("service_enabled", false)
        _uiState.update { it.copy(isMonitoringEnabled = isEnabled) }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        // Save to preferences
        prefs.edit().putBoolean("service_enabled", enabled).apply()
        // Update state
        _uiState.update { it.copy(isMonitoringEnabled = enabled) }
    }
}
