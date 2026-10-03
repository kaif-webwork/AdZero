package com.adzero.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlayerState {
    Collapsed,
    Expanded,
    Closed
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DraggablePlayerLayout(
    state: AnchoredDraggableState<PlayerState>,
    playerContent: @Composable (fraction: Float, dragModifier: Modifier) -> Unit,
    mainContent: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toFloat() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val miniPlayerHeight = 64.dp
        val bottomNavHeight = 86.dp
        val collapseRange = with(density) {
            (maxHeight - miniPlayerHeight - bottomNavHeight).toPx()
        }

        // Initialize anchors for expanded and collapsed states
        LaunchedEffect(collapseRange) {
            if (state.anchors.size == 0 && collapseRange > 0) {
                state.updateAnchors(
                    DraggableAnchors {
                        PlayerState.Expanded at 0f
                        PlayerState.Collapsed at collapseRange
                        PlayerState.Closed at screenHeightPx
                    }
                )
            }
        }

        val offsetState = remember { derivedStateOf { state.offset.takeIf { !it.isNaN() } ?: 0f } }
        val fractionState = remember(collapseRange) {
            derivedStateOf {
                if (collapseRange > 0) (offsetState.value / collapseRange).coerceIn(0f, 1f) else 0f
            }
        }

        // Main App Content (Home feed, Navigation, etc.)
        Box(modifier = Modifier.fillMaxSize()) {
            mainContent()
        }

        // Draggable Player Overlay
        if (state.currentValue != PlayerState.Closed || state.targetValue != PlayerState.Closed) {
            val isCollapsed = state.currentValue == PlayerState.Collapsed && state.targetValue == PlayerState.Collapsed

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(0, if (isCollapsed) 0 else offsetState.value.roundToInt())
                    }
                    .fillMaxSize()
            ) {
                playerContent(1f - fractionState.value, Modifier)
            }
        }
    }
}
