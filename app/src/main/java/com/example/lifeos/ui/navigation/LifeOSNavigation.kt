package com.example.lifeos.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.lifeos.ui.screens.AIChatScreen
import com.example.lifeos.ui.screens.CalendarScreen
import com.example.lifeos.ui.screens.HabitsScreen
import com.example.lifeos.ui.screens.ProjectsScreen
import com.example.lifeos.ui.screens.SettingsScreen
import com.example.lifeos.ui.screens.TodayScreen
import com.example.lifeos.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Today : Screen("today", "امروز", Icons.Default.Home)
    object Calendar : Screen("calendar", "تقویم", Icons.Default.DateRange)
    object Projects : Screen("projects", "اهداف", Icons.Default.Flag)
    object Habits : Screen("habits", "عادت‌ها", Icons.Default.FavoriteBorder)
    object AIChat : Screen("ai_chat", "دستیار", Icons.Default.Star)
    object Settings : Screen("settings", "تنظیمات", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Today,
    Screen.Calendar,
    Screen.Projects,
    Screen.Habits,
    Screen.AIChat,
    Screen.Settings
)

@Composable
fun LifeOSNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Today.route,
        modifier = modifier
    ) {
        composable(Screen.Today.route) {
            TodayScreen()
        }
        composable(Screen.Calendar.route) {
            CalendarScreen()
        }
        composable(Screen.Projects.route) {
            ProjectsScreen()
        }
        composable(Screen.Habits.route) {
            HabitsScreen()
        }
        composable(Screen.AIChat.route) {
            AIChatScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun LifeOSBottomNav(navController: NavHostController) {
    // Previously hardcoded to always look dark (GlassPrimaryDark/TextPrimary/TextMuted)
    // regardless of the user's light/dark theme setting. Now follows MaterialTheme
    // like the rest of the app.
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

        bottomNavItems.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        screen.icon,
                        contentDescription = screen.title,
                        tint = if (isSelected) AccentBlue else unselectedColor
                    )
                },
                label = {
                    Text(
                        screen.title,
                        color = if (isSelected) AccentBlue else unselectedColor
                    )
                },
                selected = isSelected,
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
                    indicatorColor = AccentBlue.copy(alpha = 0.15f)
                )
            )
        }
    }
}
