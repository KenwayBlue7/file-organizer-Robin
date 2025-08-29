package com.kenway.robin.ui.organizer

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.kenway.robin.viewmodel.OrganizerViewModel
import kotlin.math.abs

private const val TAG = "OrganizerScreen"

@Composable
fun OrganizerScreen(
    viewModel: OrganizerViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmationDialog by remember { mutableStateOf(false) }
    Log.d(TAG, "OrganizerScreen recomposing. isComplete: ${uiState.isComplete}, isLoading: ${uiState.isLoading}")

    // This BackHandler will now correctly trigger the dialog
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

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                Log.d(TAG, "ConfirmationDialog: Dismissed (tapped outside).")
                showConfirmationDialog = false
            },
            title = { Text("Save Changes?") },
            text = { Text("You have unsaved changes. Would you like to save them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Log.d(TAG, "ConfirmationDialog: 'Save' clicked.")
                        viewModel.finalizeSession()
                        showConfirmationDialog = false
                        navController.popBackStack()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        Log.d(TAG, "ConfirmationDialog: 'Discard' clicked.")
                        showConfirmationDialog = false
                        navController.popBackStack()
                    }
                ) { Text("Discard") }
            }
        )
    }

    if (uiState.showTagDialog) {
        TagSelectionDialog(
            existingTags = uiState.existingTags,
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
            uiState.isComplete -> {
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

        // Undo Button Overlay
        if (uiState.canUndo) {
             IconButton(
                onClick = {
                    Log.d(TAG, "Undo button clicked.")
                    viewModel.onUndo()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                enabled = uiState.canUndo
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo last action"
                )
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

    LaunchedEffect(uiState.currentIndex) {
        if (pagerState.currentPage != uiState.currentIndex && uiState.currentIndex < pagerState.pageCount) {
            pagerState.animateScrollToPage(uiState.currentIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) { page ->
        if (page < uiState.images.size) {
            val imageUri = uiState.images[page]
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Image to organize",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize() // Use fillMaxSize to make the whole area interactive
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    totalDrag = 0f
                                    Log.d(TAG, "Vertical drag started.")
                                },
                                onDragEnd = {
                                    val swipeThreshold = 200
                                    Log.d(TAG, "Vertical drag ended. Total drag: $totalDrag")
                                    when {
                                        // A negative value means swiping up (deleting)
                                        totalDrag < -swipeThreshold -> {
                                            Log.d(TAG, "Swipe Up detected.")
                                            viewModel.onSwipeUp()
                                        }
                                        // A positive value means swiping down (tagging)
                                        totalDrag > swipeThreshold -> {
                                            Log.d(TAG, "Swipe Down detected.")
                                            viewModel.openTagDialog()
                                        }
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                }
                            )
                        }
                )

                val status = uiState.imageStatusMap[imageUri]
                if (status != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = status,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}