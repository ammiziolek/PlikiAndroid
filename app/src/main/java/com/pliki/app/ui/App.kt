package com.pliki.app.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pliki.app.ui.screens.*
import com.pliki.app.ui.theme.PC

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse?path={path}"
    const val TAGS = "tags"
    const val TAG = "tag/{id}"
    const val SEARCH = "search?tag={tag}"
    const val STORAGE = "storage"
    const val CLEANUP = "cleanup/{kind}"
    const val CATEGORY = "category/{cat}"
    const val TRASH = "trash"

    fun browse(path: String? = null) = if (path == null) "browse" else "browse?path=${Uri.encode(path)}"
    fun tag(id: Long) = "tag/$id"
    fun search(tagId: Long? = null) = if (tagId == null) "search" else "search?tag=$tagId"
    fun cleanup(kind: String) = "cleanup/$kind"
    fun category(cat: String) = "category/$cat"
}

private data class Tab(val route: String, val nav: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, Routes.HOME, "Start", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.BROWSE, "browse", "Pliki", Icons.Outlined.Folder, Icons.Rounded.Folder),
    Tab(Routes.TAGS, Routes.TAGS, "Zbiory", Icons.Outlined.Sell, Icons.Rounded.Sell),
    Tab(Routes.STORAGE, Routes.STORAGE, "Miejsce", Icons.Outlined.PieChart, Icons.Rounded.PieChart),
)

fun NavHostController.goTab(route: String) {
    // "Start" to zawsze czysty ekran startowy (bez przywracania zapisanego stosu).
    if (route == Routes.HOME) {
        if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun PlikiRoot() {
    var granted by remember { mutableStateOf(com.pliki.app.util.StorageAccess.granted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = com.pliki.app.util.StorageAccess.granted() }
    if (!granted) {
        PermissionScreen()
        return
    }
    val vm: MainViewModel = viewModel()
    LaunchedEffect(Unit) { vm.startIndex() }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsState()
    LaunchedEffect(Unit) {
        vm.messages.collect {
            // Krótko, żeby nie zasłaniać paska akcji na dole.
            kotlinx.coroutines.withTimeoutOrNull(2500) { snackbar.showSnackbar(it, duration = SnackbarDuration.Indefinite) }
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = PC.Bg,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data, containerColor = PC.Surface3, contentColor = PC.Text, actionColor = PC.AccentText,
                    modifier = Modifier.clickable { data.dismiss() },
                )
            }
        },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = PC.Bar, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        val sel = route == t.route
                        NavigationBarItem(
                            selected = sel,
                            onClick = { nav.goTab(t.nav) },
                            icon = { Icon(if (sel) t.iconSelected else t.icon, null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PC.AccentText,
                                selectedTextColor = PC.Text,
                                indicatorColor = PC.AccentSoft,
                                unselectedIconColor = PC.Muted,
                                unselectedTextColor = PC.Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding()).consumeWindowInsets(inner)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(vm, nav) }
                composable(
                    Routes.BROWSE,
                    arguments = listOf(navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { BrowserScreen(vm, nav, it.arguments?.getString("path")) }
                composable(Routes.TAGS) { TagsScreen(vm, nav) }
                composable(Routes.TAG, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    TagDetailScreen(vm, nav, it.arguments!!.getLong("id"))
                }
                composable(
                    Routes.SEARCH,
                    arguments = listOf(navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { SearchScreen(vm, nav, it.arguments?.getString("tag")?.toLongOrNull()) }
                composable(Routes.STORAGE) { StorageScreen(vm, nav) }
                composable(Routes.CLEANUP) { CleanupScreen(vm, nav, it.arguments!!.getString("kind")!!) }
                composable(Routes.CATEGORY) { CategoryScreen(vm, nav, it.arguments!!.getString("cat")!!) }
                composable(Routes.TRASH) { TrashScreen(vm, nav) }
            }
            AnimatedVisibility(busy != null, modifier = Modifier.statusBarsPadding()) {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = PC.Accent, trackColor = PC.Surface)
                    Text(busy ?: "", style = MaterialTheme.typography.labelMedium, color = PC.Muted2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}
