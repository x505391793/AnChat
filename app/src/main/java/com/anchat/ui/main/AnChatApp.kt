package com.anchat.ui.main

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.anchat.ui.contacts.CharacterEditScreen
import com.anchat.ui.contacts.ContactsScreen
import com.anchat.ui.discover.DiscoverScreen
import com.anchat.ui.history.HistoryScreen
import com.anchat.ui.me.MeScreen
import com.anchat.ui.settings.SettingsScreen

/** WeChat-style green for selected tab. */
private val WeChatGreen = Color(0xFF07C160)

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object WeChat : Screen("wechat", "AnChat", Icons.Filled.ChatBubble)
    data object Contacts : Screen("contacts", "通讯录", Icons.Filled.Contacts)
    data object Discover : Screen("discover", "发现", Icons.Filled.Explore)
    data object Me : Screen("me", "我", Icons.Filled.Person)
}

@Composable
fun AnChatApp() {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 聊天页 / 设置页隐藏底部栏（微信式全屏）
    val showBottomBar = currentRoute != null
        && !currentRoute.startsWith("chat")
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
            composable(Screen.Discover.route) {
                DiscoverScreen()
            }
            composable(Screen.Me.route) {
                MeScreen(navController = navController)
            }
            composable("settings") {
                SettingsScreen(navController = navController)
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
        }
    }
}

@Composable
private fun AnChatBottomBar(navController: NavHostController) {
    val items = listOf(Screen.WeChat, Screen.Contacts, Screen.Discover, Screen.Me)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
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
