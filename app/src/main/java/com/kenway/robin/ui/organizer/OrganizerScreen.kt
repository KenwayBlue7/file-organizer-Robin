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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlin.math.abs

private const val TAG = "OrganizerScreen"

@Composable
fun OrganizerScreen(
    viewModel: OrganizerViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val existingTags by viewModel.existingTags.collectAsState()
    var showConfirmationDialog by remember { mutableStateOf(false) }

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

        // This explicit Box as a separate layer ensures it recomposes correctly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
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

            // Reset state when the page is changed
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Image to organize",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(Unit) {
                            coroutineScope {
                                // This detector handles single taps, double taps, and long presses.
                                launch {
                                    detectTapGestures(
                                        onTap = {
                                            Log.d(TAG, "Single tap detected (Quick Tag).")
                                            viewModel.onQuickTag()
                                        },
                                        onDoubleTap = {
                                            Log.d(TAG, "Double tap detected (Zoom).")
                                            scale = if (scale > 1f) 1f else 3f
                                            offset = Offset.Zero // Reset pan on zoom toggle
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onLongPress = {
                                            Log.d(TAG, "Long press detected (Open Tag Dialog).")
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.openTagDialog()
                                        }
                                    )
                                }

                                // This detector handles pinch-to-zoom and panning when the image is zoomed in.
                                launch {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (scale > 1f) {
                                            scale = (scale * zoom).coerceIn(1f, 5f)

                                            // Calculate bounds to prevent panning image out of view
                                            val maxOffsetX = (size.width * (scale - 1)) / 2
                                            val maxOffsetY = (size.height * (scale - 1)) / 2

                                            val newOffset = offset + pan
                                            offset = Offset(
                                                x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                                y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        }
                                    }
                                }

                                // This detector handles the vertical swipe-up gesture for deletion when not zoomed.
                                launch {
                                    var totalDrag = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            if (scale == 1f) {
                                                totalDrag = 0f
                                                Log.d(TAG, "Vertical drag started.")
                                            }
                                        },
                                        onDragEnd = {
                                            if (scale == 1f) {
                                                val swipeThreshold = 200
                                                Log.d(TAG, "Vertical drag ended. Total drag: $totalDrag")
                                                // A negative value means swiping up (deleting)
                                                if (totalDrag < -swipeThreshold) {
                                                    Log.d(TAG, "Swipe Up detected.")
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.onSwipeUp()
                                                }
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (scale == 1f) {
                                                change.consume()
                                                totalDrag += dragAmount
                                            }
                                        }
                                    )
                                }
                            }
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

                // Add the Edit button for tagged images
                if (imageUri in uiState.sessionTaggedUris) {
                    IconButton(
                        onClick = { viewModel.openTagDialog() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Tag",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}