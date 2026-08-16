package com.example.lifeos.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.lifeos.ui.screens.AIChatScreen
import com.example.lifeos.ui.screens.AnalyticsScreen
import com.example.lifeos.ui.screens.CalendarScreen
import com.example.lifeos.ui.screens.FocusScreen
import com.example.lifeos.ui.screens.GoalsScreen
import com.example.lifeos.ui.screens.HabitsScreen
import com.example.lifeos.ui.screens.ProjectsScreen
import com.example.lifeos.ui.screens.RoutinesScreen
import com.example.lifeos.ui.screens.SettingsScreen
import com.example.lifeos.ui.screens.TasksScreen
import com.example.lifeos.ui.screens.TodayScreen
import com.example.lifeos.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    // baseRoute is what bottom-nav taps navigate to and what selection
    // highlighting compares against. For most screens it's identical to
    // route; Focus overrides it because its route pattern carries an
    // optional {taskId} argument that only "Start Focus" (from a task)
    // fills in, not a plain tab tap.
    open val baseRoute: String get() = route

    object Today : Screen("today", "امروز", Icons.Default.Home)
    object Calendar : Screen("calendar", "تقویم", Icons.Default.DateRange)
    object Tasks : Screen("tasks", "کارها", Icons.Default.Checklist)
    object Goals : Screen("goals", "اهداف", Icons.Default.Flag)
    object Projects : Screen("projects", "پروژه‌ها", Icons.Default.Build)
    object Habits : Screen("habits", "عادت‌ها", Icons.Default.FavoriteBorder)
    object Routines : Screen("routines", "روتین‌ها", Icons.Default.Repeat)
    object Focus : Screen("focus?taskId={taskId}", "فوکوس", Icons.Default.Bolt) {
        override val baseRoute = "focus"
        fun routeForTask(taskId: String) = "focus?taskId=$taskId"
    }
    object Analytics : Screen("analytics", "تحلیل", Icons.Default.Insights)
    object AIChat : Screen("ai_chat", "دستیار", Icons.Default.Star)
    object Settings : Screen("settings", "تنظیمات", Icons.Default.Settings)
}

// Primary tabs get a permanent slot in the bottom bar; everything else lives
// behind "More" as a bottom sheet. Prompt section 5 asks for 11 top-level
// sections but also says navigation "must remain simple" and avoid excessive
// depth — cramming 11 icons into one NavigationBar fails the first goal to
// chase the letter of the second, and on a typical phone width becomes
// genuinely hard to tap accurately. Today/Calendar/Tasks/AI Assistant are the
// screens used many times a day; the rest are one extra tap away via More,
// never buried further than that.
private val primaryNavItems = listOf(
    Screen.Today,
    Screen.Calendar,
    Screen.Tasks,
    Screen.AIChat
)

private val moreNavItems = listOf(
    Screen.Goals,
    Screen.Projects,
    Screen.Habits,
    Screen.Routines,
    Screen.Focus,
    Screen.Analytics,
    Screen.Settings
)

val bottomNavItems = primaryNavItems + moreNavItems

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
            TodayScreen(
                onStartFocus = { task ->
                    navController.navigate(Screen.Focus.routeForTask(task.id))
                }
            )
        }
        composable(Screen.Calendar.route) {
            CalendarScreen()
        }
        composable(Screen.Tasks.route) {
            TasksScreen()
        }
        composable(Screen.Goals.route) {
            GoalsScreen()
        }
        composable(Screen.Projects.route) {
            ProjectsScreen()
        }
        composable(Screen.Habits.route) {
            HabitsScreen()
        }
        composable(Screen.Routines.route) {
            RoutinesScreen()
        }
        composable(
            route = Screen.Focus.route,
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            FocusScreen(preselectTaskId = backStackEntry.arguments?.getString("taskId"))
        }
        composable(Screen.AIChat.route) {
            AIChatScreen()
        }
        composable(Screen.Analytics.route) {
            AnalyticsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeOSBottomNav(navController: NavHostController) {
    // Previously hardcoded to always look dark (GlassPrimaryDark/TextPrimary/TextMuted)
    // regardless of the user's light/dark theme setting. Now follows MaterialTheme
    // like the rest of the app.
    var showMoreSheet by remember { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    fun isSelected(screen: Screen) = currentDestination?.hierarchy?.any { it.route == screen.route } == true
    fun navigateTo(screen: Screen) {
        // Navigate to baseRoute (no arguments) so tapping the tab always
        // opens a clean screen rather than re-using whatever arguments were
        // last on the back stack (relevant for Focus's optional taskId).
        navController.navigate(screen.baseRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        primaryNavItems.forEach { screen ->
            val selected = isSelected(screen)
            NavigationBarItem(
                icon = {
                    Icon(
                        screen.icon,
                        contentDescription = screen.title,
                        tint = if (selected) AccentBlue else unselectedColor
                    )
                },
                label = {
                    Text(screen.title, color = if (selected) AccentBlue else unselectedColor)
                },
                selected = selected,
                onClick = { navigateTo(screen) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = AccentBlue.copy(alpha = 0.15f)
                )
            )
        }

        // "More" is highlighted whenever the current destination is one of
        // the overflow items, so the user always sees which section they're
        // in even though it's not a permanent tab.
        val moreSelected = moreNavItems.any { isSelected(it) }
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "بیشتر",
                    tint = if (moreSelected) AccentBlue else unselectedColor
                )
            },
            label = {
                Text("بیشتر", color = if (moreSelected) AccentBlue else unselectedColor)
            },
            selected = moreSelected,
            onClick = { showMoreSheet = true },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = AccentBlue.copy(alpha = 0.15f)
            )
        )
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = "بخش‌های دیگر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(moreNavItems) { screen ->
                    val selected = isSelected(screen)
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showMoreSheet = false
                                navigateTo(screen)
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            screen.icon,
                            contentDescription = screen.title,
                            tint = if (selected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
