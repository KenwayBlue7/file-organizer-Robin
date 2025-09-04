package com.kenway.robin.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedViewModel : ViewModel() {
    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri = _selectedFolderUri.asStateFlow()

    // Change this method name from selectFolder to setSelectedFolderUri
    fun setSelectedFolderUri(uri: Uri) {
        _selectedFolderUri.value = uri
    }
    
    // Keep the old method for backward compatibility if needed
    fun selectFolder(uri: Uri) {
        _selectedFolderUri.value = uri
    }
    
    // Add this new method
    fun clearSelectedFolder() {
        _selectedFolderUri.value = null
    }
}
