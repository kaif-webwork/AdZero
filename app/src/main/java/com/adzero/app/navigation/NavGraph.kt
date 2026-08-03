package com.adzero.app.navigation

import androidx.activity.compose.BackHandler
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
import com.adzero.app.data.UpdateInfo
import com.adzero.app.data.UpdateManager
import com.adzero.app.models.Video
import com.adzero.app.screens.HomeScreen
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

    // Update Logic
    val updateState by com.adzero.app.data.UpdateManager.updateState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        com.adzero.app.data.UpdateManager.checkForUpdates(context)
    }

    if (updateState.hasUpdate || updateState.isDownloading) {
        com.adzero.app.components.UpdateDialog(
            updateInfo = updateState,
            onUpdateClick = {
                if (updateState.isDownloaded && updateState.localApkFile != null) {
                    com.adzero.app.data.UpdateManager.installApk(context, updateState.localApkFile!!)
                } else if (updateState.downloadUrl.isNotBlank()) {
                    scope.launch {
                        com.adzero.app.data.UpdateManager.downloadAndInstallApk(context, updateState.downloadUrl)
                    }
                }
            },
            onDismissClick = {
                com.adzero.app.data.UpdateManager.dismissUpdate()
            }
        )
    }
    // ── Gesture Navigation & BackHandler Management ──────────────────────────────
    val isPlayerExpanded = playerDraggableState.targetValue == PlayerState.Expanded || 
                           playerDraggableState.currentValue == PlayerState.Expanded

    val isPlayerCollapsed = playerDraggableState.targetValue == PlayerState.Collapsed || 
                            playerDraggableState.currentValue == PlayerState.Collapsed

    val isOnSubTab = currentRoute in listOf(
        Screen.Shorts.route,
        Screen.Subscriptions.route,
        Screen.Profile.route
    )

    val isOnHomeTab = currentRoute == Screen.Home.route
    val isPlayerClosed = playerDraggableState.currentValue == PlayerState.Closed && 
                         playerDraggableState.targetValue == PlayerState.Closed

    // 1. Back gesture while player is expanded -> Minimize to Mini Player
    BackHandler(enabled = isPlayerExpanded) {
        scope.launch {
            playerDraggableState.animateTo(PlayerState.Collapsed)
        }
    }

    // 2. Back gesture while player is in Mini Player mode -> Close Mini Player
    BackHandler(enabled = isPlayerCollapsed) {
        scope.launch {
            playerDraggableState.animateTo(PlayerState.Closed)
        }
    }

    // 3. Back gesture on Sub-tabs (Shorts, Subscriptions, Profile) -> Return to Home Tab
    BackHandler(enabled = !isPlayerExpanded && !isPlayerCollapsed && isOnSubTab) {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    // 4. Back gesture on Home root screen -> 2-Tap exit toast protection (Prevents accidental app closing!)
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = !isPlayerExpanded && !isPlayerCollapsed && isOnHomeTab && isPlayerClosed) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    DraggablePlayerLayout(
        state = playerDraggableState,
        mainContent = {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { _ ->
                val showBottomBar = currentRoute in listOf(
                    Screen.Home.route,
                    Screen.Subscriptions.route,
                    Screen.Profile.route,
                    Screen.Shorts.route
                )

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.fillMaxSize()
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
                                },
                                onSearchClick = { navController.navigate(Screen.Search.route) }
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

                    if (showBottomBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            AdZeroLiquidGlassDock(
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
private fun AdZeroLiquidGlassDock(
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
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            )
        ),
        shadowElevation = 8.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val tabWidth = maxWidth / tabs.size
            val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }

            // ── 1. Sliding Active Pill (Spring Animation) ────────────────────
            val animXTranslation = androidx.compose.animation.core.animateFloatAsState(
                targetValue = tabWidthPx * selectedIndex,
                animationSpec = spring(
                    stiffness = 300f,
                    dampingRatio = 0.65f
                ),
                label = "dockPillX"
            )

            // ── 2. Active Tab Glow Capsule ─────────────────────────────
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animXTranslation.value
                    }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFF2661).copy(alpha = 0.28f),
                                Color(0xFFFF2661).copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Color(0xFFFF2661).copy(alpha = 0.6f)
                        ),
                        RoundedCornerShape(28.dp)
                    )
            )

            // ── 3. Icon + Micro-Label Content Row ──────────────────────
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val isPressed = pressedTab == index

                    val animScale = androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            isPressed -> 0.88f
                            isSelected -> 1.05f
                            else -> 0.92f
                        },
                        animationSpec = spring(
                            stiffness = 400f,
                            dampingRatio = 0.60f
                        ),
                        label = "tabScale"
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
                        Column(
                            modifier = Modifier.graphicsLayer {
                                scaleX = animScale.value
                                scaleY = animScale.value
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = tab.title,
                                tint = if (isSelected) Color(0xFFFF2661) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size(if (isSelected) 21.dp else 19.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}
