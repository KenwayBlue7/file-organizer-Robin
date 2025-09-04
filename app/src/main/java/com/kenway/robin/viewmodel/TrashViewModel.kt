package com.kenway.robin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kenway.robin.data.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class TrashViewModel(
    private val imageRepository: ImageRepository
) : ViewModel() {

    data class TrashUiState(
        val trashedFiles: List<File> = emptyList()
    )

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        loadTrashedFiles()
    }

    private fun loadTrashedFiles() {
        viewModelScope.launch {
            val files = imageRepository.getTrashedFiles()
            _uiState.update { it.copy(trashedFiles = files) }
        }
    }

    fun restoreFile(file: File) {
        viewModelScope.launch {
            imageRepository.restoreFile(file)
            loadTrashedFiles() // Refresh the list
        }
    }

    fun deletePermanently(file: File) {
        viewModelScope.launch {
            imageRepository.deletePermanently(file)  // ✅ Correct method name
            loadTrashedFiles() // Refresh the list
        }
    }
}
