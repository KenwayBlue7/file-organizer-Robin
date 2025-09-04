package com.kenway.robin.ui.organizer

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.kenway.robin.viewmodel.OrganizerViewModel
import com.kenway.robin.viewmodel.SharedViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "OrganizerScreen"

@Composable
fun OrganizerScreen(
    viewModel: OrganizerViewModel,
    navController: NavController,
    sharedViewModel: SharedViewModel, // Add this parameter
    onOpenFolderPicker: () -> Unit // Add this parameter
) {
    val uiState by viewModel.uiState.collectAsState()
    val existingTags by viewModel.existingTags.collectAsState()
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var showOrganizeAnotherDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Watch for session completion
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            showCompletionDialog = true
        }
    }

    BackHandler {
        Log.d(TAG, "BackHandler triggered.")
        val hasChanges = uiState.sessionTaggedUris.isNotEmpty() || uiState.sessionDeletedUris.isNotEmpty()
        if (hasChanges && !uiState.isComplete) {
            Log.d(TAG, "BackHandler: Unsaved changes found. Showing confirmation dialog.")
            showConfirmationDialog = true
        } else {
            Log.d(TAG, "BackHandler: No changes or session complete. Navigating back.")
            navController.popBackStack()
        }
    }

    // Back-press confirmation dialog
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmationDialog = false
                    coroutineScope.launch {
                        viewModel.finalizeSession()
                        // Set result for dashboard refresh
                        navController.previousBackStackEntry?.savedStateHandle?.set("session_saved", true)
                        navController.popBackStack()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmationDialog = false
                    navController.popBackStack()
                }) {
                    Text("Discard")
                }
            }
        )
    }

    // Session completion dialog
    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = { /* Don't allow dismissing by tapping outside */ },
            title = { Text("Session Complete!") },
            text = { Text("Save your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    showCompletionDialog = false
                    coroutineScope.launch {
                        viewModel.finalizeSession()
                        navController.previousBackStackEntry?.savedStateHandle?.set("session_saved", true)
                        navController.popBackStack()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCompletionDialog = false
                    showOrganizeAnotherDialog = true
                }) {
                    Text("Discard")
                }
            }
        )
    }

    // Organize another folder dialog
    if (showOrganizeAnotherDialog) {
        AlertDialog(
            onDismissRequest = { /* Don't allow dismissing by tapping outside */ },
            title = { Text("Organize another folder?") },
            text = { Text("Would you like to organize another folder?") },
            confirmButton = {
                TextButton(onClick = {
                    showOrganizeAnotherDialog = false
                    // Clear the previous folder selection
                    sharedViewModel.clearSelectedFolder()
                    // Navigate back to dashboard first
                    navController.popBackStack()
                    // Then trigger folder picker
                    onOpenFolderPicker()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOrganizeAnotherDialog = false
                    navController.popBackStack()
                }) {
                    Text("No")
                }
            }
        )
    }

    if (uiState.showTagDialog) {
        TagSelectionDialog(
            existingTags = existingTags,
            onDismissRequest = {
                Log.d(TAG, "TagSelectionDialog: Dismissed.")
                viewModel.dismissTagDialog()
            },
            onTagSelected = { tag ->
                Log.d(TAG, "TagSelectionDialog: Tag '$tag' selected.")
                viewModel.onTagImage(tag)
                viewModel.dismissTagDialog()
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.errorMessage != null -> {
                Text("Error: ${uiState.errorMessage}")
            }
            uiState.isComplete && !showCompletionDialog && !showOrganizeAnotherDialog -> {
                Text("Session Complete!")
            }
            uiState.isLoading -> {
                CircularProgressIndicator()
            }
            uiState.images.isEmpty() && !uiState.isLoading -> {
                Text("No images found in this folder.")
            }
            else -> {
                OrganizerPager(uiState = uiState, viewModel = viewModel)
            }
        }

        // Bottom UI elements - Progress bar and Undo button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Undo button (appears above progress bar when available)
                if (uiState.canUndo) {
                    IconButton(
                        onClick = {
                            Log.d(TAG, "Undo button clicked.")
                            viewModel.onUndo()
                        },
                        modifier = Modifier.background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo last action",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Progress bar
                if (uiState.images.isNotEmpty()) {
                    // Calculate progress: (tagged + deleted) / total images
                    val progress = if (uiState.images.isNotEmpty()) {
                        val processedCount = uiState.sessionTaggedUris.size + uiState.sessionDeletedUris.size
                        processedCount.toFloat() / uiState.images.size.toFloat()
                    } else {
                        0f
                    }
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OrganizerPager(
    uiState: OrganizerViewModel.OrganizerUiState,
    viewModel: OrganizerViewModel
) {
    val pagerState = rememberPagerState(pageCount = { uiState.images.size })
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(uiState.currentIndex) {
        if (pagerState.currentPage != uiState.currentIndex && uiState.currentIndex < pagerState.pageCount) {
            pagerState.animateScrollToPage(uiState.currentIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) { page ->
        if (page < uiState.images.size) {
            val imageUri = uiState.images[page]

            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var isInZoomMode by remember { mutableStateOf(false) }

            // Reset state when the page is changed
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) {
                    scale = 1f
                    offset = Offset.Zero
                    isInZoomMode = false
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Image $page",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isInZoomMode) {
                            detectTapGestures(
                                onTap = {
                                    if (!isInZoomMode) {
                                        viewModel.onQuickTag() // Changed from onTagWithPrevious()
                                    }
                                },
                                onDoubleTap = {
                                    isInZoomMode = !isInZoomMode
                                    if (isInZoomMode) {
                                        // Zoom in to 3x when entering zoom mode
                                        scale = 3f
                                    } else {
                                        // Reset scale and offset when exiting zoom mode
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                },
                                onLongPress = {
                                    // Only handle long press when not in zoom mode
                                    if (!isInZoomMode) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.openTagDialog()
                                    }
                                }
                            )
                        }
                        .pointerInput(isInZoomMode) {
                            if (isInZoomMode) {
                                // Pinch-to-zoom and pan gestures (only in zoom mode)
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 3f)
                                    offset += pan
                                }
                            } else {
                                // Vertical swipe gesture (only when not in zoom mode)
                                var dragOffset = Offset.Zero
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (abs(dragOffset.y) > 100) {
                                            if (dragOffset.y < 0) { // Swiped up
                                                viewModel.onSwipeUp()
                                            }
                                        }
                                    }
                                ) { _, dragAmount ->
                                    dragOffset = Offset(0f, dragOffset.y + dragAmount)
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )

                val status = uiState.imageStatusMap[imageUri]
                if (status != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = status,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    color = when (status) {
                                        "Tagged" -> Color.Green.copy(alpha = 0.8f)
                                        "Deleted" -> Color.Red.copy(alpha = 0.8f)
                                        else -> Color.Gray.copy(alpha = 0.8f)
                                    },
                                    shape = CircleShape
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Add the Edit button for tagged images
                if (imageUri in uiState.sessionTaggedUris) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        IconButton(
                            onClick = { viewModel.openTagDialog() },
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Tag",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}