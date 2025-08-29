package com.kenway.robin.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedViewModel : ViewModel() {
    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri = _selectedFolderUri.asStateFlow()

    fun selectFolder(uri: Uri) {
        _selectedFolderUri.value = uri
    }
}
