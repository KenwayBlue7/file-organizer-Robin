package com.kenway.robin.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kenway.robin.data.ImageRepository // The crucial import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class UndoAction {
    data class Delete(val uri: android.net.Uri) : UndoAction()
    data class Tag(val uri: android.net.Uri, val tagName: String) : UndoAction()
}

class OrganizerViewModel(
    private val imageRepository: ImageRepository,
    private val application: Application
) : ViewModel() {

    private val undoStack = mutableListOf<UndoAction>()

    data class OrganizerUiState(
        val images: List<Uri> = emptyList(),
        val currentIndex: Int = 0,
        // Corrected property names
        val sessionTaggedUris: Set<Uri> = emptySet(),
        val sessionDeletedUris: Set<Uri> = emptySet(),
        val isLoading: Boolean = false,
        val canUndo: Boolean = false
    )

    private val _uiState = MutableStateFlow(OrganizerUiState())
    val uiState: StateFlow<OrganizerUiState> = _uiState.asStateFlow()

    fun loadImages(folderUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val imageList = imageRepository.getImagesFromFolder(folderUri)
                _uiState.update { currentState ->
                    currentState.copy(
                        images = imageList,
                        currentIndex = 0,
                        // Corrected property names to match the data class
                        sessionTaggedUris = emptySet(),
                        sessionDeletedUris = emptySet()
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSwipeUp() {
        val currentState = _uiState.value
        if (currentState.currentIndex >= currentState.images.size) return
        
        val currentUri = currentState.images[currentState.currentIndex]
        undoStack.add(UndoAction.Delete(currentUri))

        _uiState.update {
            it.copy(
                sessionDeletedUris = it.sessionDeletedUris + currentUri,
                sessionTaggedUris = it.sessionTaggedUris - currentUri,
                currentIndex = it.currentIndex + 1,
                canUndo = undoStack.isNotEmpty()
            )
        }
    }

    fun onTagImage(tagName: String) {
        val currentState = _uiState.value
        if (currentState.currentIndex >= currentState.images.size) return

        val currentUri = currentState.images[currentState.currentIndex]
        undoStack.add(UndoAction.Tag(currentUri, tagName))

        _uiState.update {
            it.copy(
                sessionTaggedUris = it.sessionTaggedUris + currentUri,
                sessionDeletedUris = it.sessionDeletedUris - currentUri,
                currentIndex = it.currentIndex + 1,
                canUndo = undoStack.isNotEmpty()
            )
        }
    }

    fun onUndo() {
        if (undoStack.isEmpty()) return

        val lastAction = undoStack.removeLast()
        _uiState.update { currentState ->
            // Corrected logic: only change the sets, not the index
            when (lastAction) {
                is UndoAction.Delete -> {
                    currentState.copy(
                        sessionDeletedUris = currentState.sessionDeletedUris - lastAction.uri
                    )
                }
                is UndoAction.Tag -> {
                    currentState.copy(
                        sessionTaggedUris = currentState.sessionTaggedUris - lastAction.uri
                    )
                }
            }
            // Corrected syntax for updating canUndo
            .copy(canUndo = undoStack.isNotEmpty())
        }
    }
}