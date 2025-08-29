package com.kenway.robin.ui.organizer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kenway.robin.viewmodel.OrganizerViewModel

@Composable
fun OrganizerScreen(
    viewModel: OrganizerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
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
                onClick = { viewModel.onUndo() },
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
        userScrollEnabled = false 
    ) { page ->
        if (page < uiState.images.size) {
            val imageUri = uiState.images[page]
            AsyncImage(
                model = imageUri,
                contentDescription = "Image to organize",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val swipeThreshold = 200 
                                when {
                                    totalDrag < -swipeThreshold -> viewModel.onSwipeUp()
                                    totalDrag > swipeThreshold -> viewModel.onTagImage("Saved")
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            }
                        )
                    }
            )
        }
    }
}