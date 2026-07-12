package com.anchat.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchat.push.NotificationNavigation
import com.anchat.ui.theme.AnChatTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anchat.ui.chat.ChatScreen
import com.anchat.ui.contacts.CharacterCardScreen
import com.anchat.ui.contacts.CharacterEditScreen
import com.anchat.ui.contacts.ContactsScreen
import com.anchat.ui.discover.DiscoverScreen
import com.anchat.ui.history.HistoryScreen
import com.anchat.ui.me.MeScreen
import com.anchat.ui.me.ProfileEditScreen
import com.anchat.ui.settings.ModelManageScreen
import com.anchat.ui.settings.SettingsScreen
import com.anchat.ui.settings.LogScreen

/** WeChat-style green for selected tab. */
private val WeChatGreen = Color(0xFF07C160)

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object WeChat : Screen("wechat", "AnChat", Icons.Filled.ChatBubble)
    data object Contacts : Screen("contacts", "通讯录", Icons.Filled.Contacts)
    data object Discover : Screen("discover", "发现", Icons.Filled.Explore)
    data object Me : Screen("me", "我", Icons.Filled.Person)
}

/**
 * Host that resolves the active theme from the user's setting and provides it
 * to the whole tree (via [LocalIsDark]) before rendering [AnChatApp].
 */
@Composable
fun AnChatAppHost() {
    val app = LocalApp.current
    val themeMode by app.configManager.themeModeFlow.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        AnChatTheme(darkTheme = darkTheme) {
            val view = LocalView.current
            // colorScheme 必须在 @Composable 上下文取，先算好再传入 SideEffect
            // 状态栏颜色跟随顶栏（TopAppBar 默认 containerColor = colorScheme.surface），与顶部栏一致
            val statusBarColor = MaterialTheme.colorScheme.surface.toArgb()
            SideEffect {
                val window = (view.context as? ComponentActivity)?.window
                if (window != null) {
                    // 状态栏颜色与顶栏一致（surface），消除顶部色差（不延伸内容，位置不变）
                    window.statusBarColor = statusBarColor
                    // 浅色模式用深色状态栏图标，深色模式用浅色图标
                    WindowCompat.getInsetsController(window, view)
                        .isAppearanceLightStatusBars = !darkTheme
                }
            }
            AnChatApp()
        }
    }
}

@Composable
fun AnChatApp() {
    val navController = rememberNavController()

    // 通知点击带会话 id 进来：组合时跳转到对应聊天，然后清空
    val pendingNav by NotificationNavigation.target.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNav) {
        pendingNav?.let { id ->
            navController.navigate("chat/$id")
            NotificationNavigation.set(null)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 聊天页 / 角色名片·编辑页 / 对话身份编辑页 / 设置页 / 模型管理页隐藏底部栏（微信式全屏二级页）
    val showBottomBar = currentRoute != null
        && !currentRoute.startsWith("chat")
        && !currentRoute.startsWith("character")
        && !currentRoute.startsWith("conversation")
        && !currentRoute.startsWith("models")
        && !currentRoute.startsWith("profile")
        && !currentRoute.startsWith("logs")
        && currentRoute != "settings"

    Scaffold(
        bottomBar = {
            if (showBottomBar) AnChatBottomBar(navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.WeChat.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.WeChat.route) {
                HistoryScreen(navController = navController)
            }
            composable(Screen.Contacts.route) {
                ContactsScreen(navController = navController)
            }
            composable("character/create") {
                CharacterEditScreen(navController = navController)
            }
            composable(
                route = "character/{characterId}",
                arguments = listOf(
                    navArgument("characterId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val characterId = backStackEntry.arguments?.getLong("characterId") ?: -1L
                CharacterCardScreen(
                    navController = navController,
                    characterId = characterId
                )
            }
            composable(
                route = "character/edit/{editId}",
                arguments = listOf(
                    navArgument("editId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val editId = backStackEntry.arguments?.getLong("editId") ?: -1L
                CharacterEditScreen(
                    navController = navController,
                    editId = editId
                )
            }
            composable(Screen.Discover.route) {
                DiscoverScreen()
            }
            composable(Screen.Me.route) {
                MeScreen(navController = navController)
            }
            composable("profile/edit") {
                ProfileEditScreen(navController = navController)
            }
            composable("profile/edit") {
                ProfileEditScreen(navController = navController)
            }
            composable("settings") {
                SettingsScreen(navController = navController)
            }
            composable("logs") {
                LogScreen(navController = navController)
            }
            composable("models") {
                ModelManageScreen(navController = navController)
            }
            composable(
                route = "chat/{convId}?characterId={characterId}",
                arguments = listOf(
                    navArgument("convId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("characterId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val convId = backStackEntry.arguments?.getLong("convId") ?: -1L
                val characterId = backStackEntry.arguments?.getLong("characterId") ?: -1L
                ChatScreen(
                    navController = navController,
                    convId = convId,
                    characterId = characterId
                )
            }
            composable(
                route = "conversation/{convId}?part={part}",
                arguments = listOf(
                    navArgument("convId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("part") {
                        type = NavType.StringType
                        defaultValue = "ai"
                    }
                )
            ) { backStackEntry ->
                val convId = backStackEntry.arguments?.getLong("convId") ?: -1L
                val part = backStackEntry.arguments?.getString("part") ?: "ai"
                CharacterEditScreen(
                    navController = navController,
                    convId = convId,
                    part = part
                )
            }
        }
    }
}

@Composable
private fun AnChatBottomBar(navController: NavHostController) {
    val items = listOf(Screen.WeChat, Screen.Contacts, Screen.Discover, Screen.Me)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 任意会话有未读 → 底部「AnChat」栏显示小红点
    val unread by LocalApp.current.localRepository.observeUnread()
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val hasUnread = unread.values.any { it > 0 }

    NavigationBar(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        items.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Box {
                        Icon(screen.icon, contentDescription = screen.label)
                        if (screen == Screen.WeChat && hasUnread) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-4).dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE54D42))
                            )
                        }
                    }
                },
                label = { Text(screen.label) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WeChatGreen,
                    selectedTextColor = WeChatGreen,
                    indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}
