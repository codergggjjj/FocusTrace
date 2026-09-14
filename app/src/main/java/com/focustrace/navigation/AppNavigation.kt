package com.focustrace.navigation
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.focustrace.data.AppContainer
import com.focustrace.ui.todo.*
import com.focustrace.ui.focus.*
import com.focustrace.ui.statistics.*
import com.focustrace.ui.settings.*
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODO("todo", "待办", Icons.Outlined.CheckCircle),
    FOCUS("focus", "专注", Icons.Outlined.Timer),
    STATISTICS("statistics", "统计", Icons.Outlined.BarChart),
    PROFILE("profile", "我的", Icons.Outlined.Person)
}
@Composable
fun AppNavigation(container: AppContainer) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    Scaffold(bottomBar = {
        NavigationBar {
            Tab.entries.forEach { tab ->
                NavigationBarItem(selected = entry?.destination?.route == tab.route,
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
        NavHost(controller, startDestination = Tab.TODO.route, modifier = Modifier.padding(padding)) {
            composable(Tab.TODO.route) {
                TodoScreen(viewModel(factory = viewModelFactory { initializer { TodoViewModel(container.taskRepository) } }))
            }
            composable(Tab.FOCUS.route) {
                FocusHomeScreen(viewModel(factory = viewModelFactory { initializer { FocusViewModel(container) } }))
            }
            composable(Tab.STATISTICS.route) {
                StatisticsScreen(viewModel(factory = viewModelFactory { initializer { StatisticsViewModel(container.statisticsRepository) } }))
            }
            composable(Tab.PROFILE.route) {
                ProfileScreen(viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container.settingsRepository) } }))
            }
        }
    }
}
