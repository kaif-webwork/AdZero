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
    val screenWidthPx = with(density) { LocalContext.current.resources.displayMetrics.widthPixels.toFloat() }
    val isLandscape = LocalContext.current.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val miniPlayerHeight = 102.dp
        val bottomNavHeight = 76.dp
        val collapseRange = with(density) {
            (maxHeight - miniPlayerHeight - bottomNavHeight).toPx()
        }

        // Initialize anchors if needed
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

        val offset = state.offset.takeIf { !it.isNaN() } ?: 0f
        val fraction = (offset / collapseRange).coerceIn(0f, 1f)

        // Main App Content (Home feed, etc.)
        Box(modifier = Modifier.fillMaxSize()) {
            mainContent()
        }

        // Draggable Player Overlay
        if (state.currentValue != PlayerState.Closed || state.targetValue != PlayerState.Closed) {
            var drag2DX by remember { mutableStateOf(0f) }
            var drag2DY by remember { mutableStateOf(0f) }

            // Reset offsets when expanded
            LaunchedEffect(state.currentValue) {
                if (state.currentValue == PlayerState.Expanded) {
                    drag2DX = 0f
                    drag2DY = 0f
                }
            }

            val cardWidthPx = with(density) { 180.dp.toPx() }
            val minX = -(screenWidthPx - cardWidthPx - with(density) { 28.dp.toPx() })
            val maxX = with(density) { 14.dp.toPx() }
            val minY = -collapseRange + with(density) { 50.dp.toPx() }
            val maxY = with(density) { 40.dp.toPx() }

            val dragModifier = if (state.currentValue == PlayerState.Collapsed) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Smooth snap to left or right screen edge
                            val snapX = if (drag2DX < minX / 2f) minX else 0f
                            drag2DX = snapX
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            drag2DX = (drag2DX + dragAmount.x).coerceIn(minX, maxX)
                            drag2DY = (drag2DY + dragAmount.y).coerceIn(minY, maxY)
                        }
                    )
                }
            } else Modifier

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, if (state.currentValue == PlayerState.Collapsed) 0 else offset.roundToInt()) }
                    .graphicsLayer {
                        translationX = drag2DX
                        translationY = drag2DY
                    }
                    .fillMaxSize()
                    .then(
                        if (isLandscape || state.currentValue == PlayerState.Collapsed) Modifier else Modifier.anchoredDraggable(
                            state = state,
                            orientation = Orientation.Vertical
                        )
                    )
            ) {
                playerContent(1f - fraction, dragModifier)
            }
        }
    }
}
