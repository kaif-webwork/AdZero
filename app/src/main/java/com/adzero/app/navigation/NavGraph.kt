package com.adzero.app.navigation

import androidx.compose.animation.core.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adzero.app.components.DraggablePlayerLayout
import com.adzero.app.components.PlayerState
import com.adzero.app.models.Video
import com.adzero.app.screens.*
import com.adzero.app.theme.ThemeMode
import kotlinx.coroutines.launch

private sealed class Tab(
    val route: String,
    val title: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    object Home : Tab(Screen.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Shorts : Tab(Screen.Shorts.route, "Shorts", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow)
    object Subscriptions : Tab(Screen.Subscriptions.route, "Subscriptions", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions)
    object Profile : Tab(Screen.Profile.route, "You", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var activeVideo by remember { mutableStateOf<Video?>(null) }

    val density = LocalDensity.current
    val decaySpec = rememberSplineBasedDecay<Float>()
    val playerDraggableState = remember(decaySpec) {
        AnchoredDraggableState(
            initialValue = PlayerState.Closed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessLow),
            decayAnimationSpec = decaySpec
        )
    }

    val bottomTabs = listOf(Tab.Home, Tab.Shorts, Tab.Subscriptions, Tab.Profile)

    DraggablePlayerLayout(
        state = playerDraggableState,
        mainContent = {
            Scaffold(
                containerColor = Color(0xFF000000),
                bottomBar = {
                    val showBottomBar = currentRoute in listOf(
                        Screen.Home.route,
                        Screen.Subscriptions.route,
                        Screen.Profile.route,
                        Screen.Shorts.route
                    )
                    if (showBottomBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, bottom = 20.dp)
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "liquidAura")
                            val animShift by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "liquidAuraAnim"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(58.dp)
                                    .offset(y = 12.dp)
                                    .blur(26.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF2E335A).copy(alpha = 0.5f),
                                                Color(0xFF1C1C22).copy(alpha = 0.6f),
                                                Color(0xFF2E335A).copy(alpha = 0.5f)
                                            ),
                                            startX = 0f,
                                            endX = 1000f + (animShift * 200f)
                                        )
                                    )
                            )

                            PureAppleLiquidGlassDock(
                                tabs = bottomTabs,
                                currentRoute = currentRoute,
                                onTabSelected = { tab ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (tab.route != currentRoute) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onVideoClick = { video ->
                                    activeVideo = video
                                    scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) }
                                },
                                onSearchClick = { navController.navigate(Screen.Search.route) },
                                onChannelClick = { channelName ->
                                    navController.navigate(Screen.Channel.createRoute(channelName))
                                }
                            )
                        }
                        composable(Screen.Shorts.route) {
                            ShortsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Subscriptions.route) {
                            SubscriptionsScreen(
                                onVideoClick = { video ->
                                    activeVideo = video
                                    scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) }
                                },
                                onChannelClick = { channelName ->
                                    navController.navigate(Screen.Channel.createRoute(channelName))
                                }
                            )
                        }
                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                currentTheme = currentTheme,
                                onThemeChange = onThemeChange,
                                onVideoClick = { video ->
                                    activeVideo = video
                                    scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) }
                                },
                                onSettingsClick = { navController.navigate(Screen.Settings.route) }
                            )
                        }
                        composable(Screen.Search.route) {
                            SearchScreen(
                                onBack = { navController.popBackStack() },
                                onVideoClick = { video ->
                                    activeVideo = video
                                    scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) }
                                },
                                onChannelClick = { channelName ->
                                    navController.navigate(Screen.Channel.createRoute(channelName))
                                }
                            )
                        }
                        composable(
                            route = Screen.Channel.route,
                            arguments = listOf(navArgument("channelName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val channelName = backStackEntry.arguments?.getString("channelName") ?: ""
                            ChannelScreen(
                                channelName = channelName,
                                onBack = { navController.popBackStack() },
                                onVideoClick = { video ->
                                    activeVideo = video
                                    scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) }
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                currentTheme = currentTheme,
                                onThemeChange = onThemeChange,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.22f),
                                        Color.Black.copy(alpha = 0.50f)
                                    )
                                )
                            )
                    )
                }
            }
        },
        playerContent = { fraction, dragModifier ->
            activeVideo?.let { video ->
                PlayerScreen(
                    video = video,
                    fraction = fraction,
                    dragModifier = dragModifier,
                    onMinimize = { scope.launch { playerDraggableState.animateTo(PlayerState.Collapsed) } },
                    onClose = {
                        activeVideo = null
                        scope.launch { playerDraggableState.animateTo(PlayerState.Closed) }
                    },
                    onExpand = { scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) } },
                    onVideoClick = { newVideo ->
                        activeVideo = newVideo
                        scope.launch { playerDraggableState.animateTo(PlayerState.Expanded) }
                    },
                    onChannelClick = { channelName ->
                        navController.navigate(Screen.Channel.createRoute(channelName))
                    }
                )
            }
        }
    )
}

@Composable
private fun PureAppleLiquidGlassDock(
    tabs: List<Tab>,
    currentRoute: String?,
    onTabSelected: (Tab) -> Unit
) {
    var activeRoute by remember(currentRoute) { mutableStateOf(currentRoute) }
    val selectedIndex = tabs.indexOfFirst { it.route == activeRoute }.coerceAtLeast(0)
    var pressedTab by remember { mutableStateOf<Int?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = RoundedCornerShape(31.dp),
        color = Color(0xEE141624), // 93% high-contrast deep dark glass fill
        border = BorderStroke(
            2.2.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.98f), // Ultra-bright glowing 3D top rim
                    Color.White.copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.85f)  // Bright bottom specular light edge
                )
            )
        ),
        shadowElevation = 32.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            val tabWidth = maxWidth / tabs.size
            val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }

            // ── 1. High-Contrast Frosted Liquid Backdrop Blur (40px Blur Radius) ─────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp))
                    .graphicsLayer {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        )
                    )
            )

            // ── 2. Sliding Active Chip (transition: 0.45s cubic-bezier(0.34, 1.56, 0.64, 1)) ────
            val animXTranslation = androidx.compose.animation.core.animateFloatAsState(
                targetValue = tabWidthPx * selectedIndex,
                animationSpec = spring(
                    stiffness = 260f,
                    dampingRatio = 0.58f
                ),
                label = "appleLiquidPillX"
            )

            // ── 3. Ultra-Highlighted Active Tab Glass Chip (rgba(255,255,255,0.35)) ──
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animXTranslation.value
                    }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    Color.White.copy(alpha = 0.50f)
                                )
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
            )

            // ── 4. Icon Items Layer with 0.88 Active Press Scale Feedback ──────────────
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val isPressed = pressedTab == index

                    val animIconScale = androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            isPressed -> 0.88f
                            isSelected -> 1.12f
                            else -> 0.95f
                        },
                        animationSpec = spring(
                            stiffness = 400f,
                            dampingRatio = 0.60f
                        ),
                        label = "tabIconScale"
                    )

                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                pressedTab = index
                                activeRoute = tab.route
                                onTabSelected(tab)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = animIconScale.value
                                scaleY = animIconScale.value
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = tab.title,
                                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
