package com.kenway.robin.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kenway.robin.data.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "OrganizerViewModel"

sealed class UndoAction {
    data class Delete(val uri: android.net.Uri) : UndoAction()
    data class Tag(val uri: android.net.Uri, val tagName: String) : UndoAction()
}

class OrganizerViewModel(
    private val application: Application,
    private val imageRepository: ImageRepository
    // folderUri is removed from the constructor
) : ViewModel() {

    val existingTags: StateFlow<List<String>> = imageRepository.getUniqueTagNames()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val undoStack = mutableListOf<UndoAction>()
    private var lastUsedTag: String = "Saved"

    data class OrganizerUiState(
        val images: List<Uri> = emptyList(),
        val currentIndex: Int = 0,
        // Corrected property names
        val sessionTaggedUris: Map<Uri, String> = emptyMap(),
        val sessionDeletedUris: Set<Uri> = emptySet(),
        val imageStatusMap: Map<Uri, String> = emptyMap(),
        val isLoading: Boolean = false,
        val canUndo: Boolean = false,
        val isComplete: Boolean = false,
        val errorMessage: String? = null,
        val showTagDialog: Boolean = false
    )

    private val _uiState = MutableStateFlow(OrganizerUiState())
    val uiState: StateFlow<OrganizerUiState> = _uiState.asStateFlow()

    // The init block is no longer needed here, as loading is triggered from the UI
    init {
        Log.d(TAG, "ViewModel initialized.")
    }

    // The loadImages function is now public and takes the folderUri as a parameter
    fun loadImages(folderUri: Uri) {
        Log.d(TAG, "loadImages: called with folderUri: $folderUri")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val imageList = imageRepository.getImagesFromFolder(folderUri)
                Log.d(TAG, "loadImages: Found ${imageList.size} images.")
                _uiState.update { currentState ->
                    currentState.copy(
                        images = imageList,
                        currentIndex = 0,
                        // Corrected property names to match the data class
                        sessionTaggedUris = emptyMap(),
                        sessionDeletedUris = emptySet(),
                        imageStatusMap = emptyMap(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadImages: Failed to load images.", e)
                _uiState.update {
                    it.copy(errorMessage = "Failed to load images: ${e.message}", isLoading = false)
                }
            }
        }
    }

    fun onSwipeUp() {
        val currentState = _uiState.value
        if (currentState.currentIndex >= currentState.images.size) return
        
        val currentUri = currentState.images[currentState.currentIndex]
        Log.d(TAG, "onSwipeUp: Deleting image at index ${currentState.currentIndex}, uri: $currentUri")
        undoStack.add(UndoAction.Delete(currentUri))

        _uiState.update {
            val newIndex = it.currentIndex + 1
            it.copy(
                sessionDeletedUris = it.sessionDeletedUris + currentUri,
                sessionTaggedUris = it.sessionTaggedUris - currentUri,
                imageStatusMap = it.imageStatusMap + (currentUri to "Deleted"),
                currentIndex = newIndex,
                canUndo = undoStack.isNotEmpty(),
                isComplete = newIndex >= it.images.size
            )
        }
    }

    fun onTagImage(tagName: String) {
        val currentState = _uiState.value
        if (currentState.currentIndex >= currentState.images.size) return

        val currentUri = currentState.images[currentState.currentIndex]
        Log.d(TAG, "onTagImage: Tagging image at index ${currentState.currentIndex} with tag '$tagName', uri: $currentUri")
        this.lastUsedTag = tagName
        // Store the previous tag if we're overwriting, for a more robust undo
        val previousTag = currentState.sessionTaggedUris[currentUri]
        undoStack.add(UndoAction.Tag(currentUri, previousTag ?: tagName))


        _uiState.update {
            val newIndex = it.currentIndex + 1
            it.copy(
                sessionTaggedUris = it.sessionTaggedUris + (currentUri to tagName),
                sessionDeletedUris = it.sessionDeletedUris - currentUri,
                imageStatusMap = it.imageStatusMap + (currentUri to "Tagged"),
                currentIndex = newIndex,
                canUndo = undoStack.isNotEmpty(),
                isComplete = newIndex >= it.images.size
            )
        }
    }

    fun onQuickTag() {
        onTagImage(lastUsedTag)
    }

    fun onPageChanged(newIndex: Int) {
        _uiState.update { it.copy(currentIndex = newIndex) }
    }

    fun onUndo() {
        if (undoStack.isEmpty()) {
            Log.w(TAG, "onUndo: Undo stack is empty, cannot undo.")
            return
        }

        val lastAction = undoStack.removeLast()
        Log.d(TAG, "onUndo: Undoing last action: $lastAction")
        _uiState.update { currentState ->
            // Corrected logic: only change the sets, not the index
            val updatedState = when (lastAction) {
                is UndoAction.Delete -> {
                    currentState.copy(
                        sessionDeletedUris = currentState.sessionDeletedUris - lastAction.uri,
                        imageStatusMap = currentState.imageStatusMap - lastAction.uri
                    )
                }
                is UndoAction.Tag -> {
                    currentState.copy(
                        sessionTaggedUris = currentState.sessionTaggedUris - lastAction.uri,
                        imageStatusMap = currentState.imageStatusMap - lastAction.uri
                    )
                }
            }
            // Corrected syntax for updating canUndo
            updatedState.copy(canUndo = undoStack.isNotEmpty())
        }
    }

    fun openTagDialog() {
        Log.d(TAG, "openTagDialog: called.")
        _uiState.update { it.copy(showTagDialog = true) }
    }

    fun dismissTagDialog() {
        Log.d(TAG, "dismissTagDialog: called.")
        _uiState.update { it.copy(showTagDialog = false) }
    }

    fun finalizeSession() {
        Log.d(TAG, "finalizeSession: Starting.")
        viewModelScope.launch {
            val currentState = _uiState.value
            
            // Finalize tagged images
            Log.d(TAG, "finalizeSession: Finalizing ${currentState.sessionTaggedUris.size} tagged images.")
            currentState.sessionTaggedUris.forEach { (uri, tagName) ->
                try {
                    imageRepository.finalizeTag(uri, tagName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to finalize tag for $uri", e)
                }
            }

            // Move deleted images to trash
            Log.d(TAG, "finalizeSession: Moving ${currentState.sessionDeletedUris.size} images to trash.")
            currentState.sessionDeletedUris.forEach { uri ->
                try {
                    imageRepository.moveFileToTrash(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to move file to trash: $uri", e)
                }
            }

            _uiState.update {
                it.copy(
                    sessionTaggedUris = emptyMap(),
                    sessionDeletedUris = emptySet(),
                    isComplete = true // Mark session as complete
                )
            }
            Log.d(TAG, "finalizeSession: Finished.")
        }
    }
}