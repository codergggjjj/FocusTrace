package com.focustrace.navigation
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.focustrace.data.AppContainer
import com.focustrace.ui.todo.*
import com.focustrace.ui.focus.*
import com.focustrace.ui.statistics.*
import com.focustrace.ui.settings.*
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODO("todo", "待办", Icons.Outlined.CheckCircle),
    STATISTICS("statistics", "统计", Icons.Outlined.BarChart),
    PROFILE("profile", "我的", Icons.Outlined.Person)
}
@Composable
fun AppNavigation(container: AppContainer) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    Scaffold(bottomBar = {
        if (entry?.destination?.route != "report/{sessionId}" && entry?.destination?.route != "focus") NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Tab.entries.forEach { tab ->
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant), selected = entry?.destination?.route == tab.route,
                    onClick = {
                        controller.navigate(tab.route) {
                            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }, icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(tab.label) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            NavHost(controller, startDestination = Tab.TODO.route, modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                composable(Tab.TODO.route) {
                    TodoScreen(viewModel(factory = viewModelFactory { initializer { TodoViewModel(container) } }), onReport = { id -> controller.navigate("report/$id") }) {
                        controller.navigate("focus") {
                            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                composable("focus") {
                    FocusHomeScreen(viewModel(factory = viewModelFactory { initializer { FocusViewModel(container) } }), onExit = { controller.popBackStack("todo", false) }) { id ->
                        controller.navigate("report/$id") { launchSingleTop = true }
                    }
                }
                composable("report/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { reportEntry ->
                    val sessionId = reportEntry.arguments!!.getLong("sessionId")
                    FocusResultScreen(viewModel(factory = viewModelFactory { initializer { FocusResultViewModel(container.focusRepository, sessionId) } })) {
                        controller.popBackStack("todo", false)
                    }
                }
                composable(Tab.STATISTICS.route) {
                    StatisticsScreen(viewModel(factory = viewModelFactory { initializer { StatisticsViewModel(container.statisticsRepository, createSavedStateHandle()) } }))
                }
                composable(Tab.PROFILE.route) {
                    ProfileScreen(viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container.settingsRepository) } }))
                }
            }
        }
    }
}
