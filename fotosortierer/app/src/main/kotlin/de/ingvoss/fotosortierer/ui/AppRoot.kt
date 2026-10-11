package de.ingvoss.fotosortierer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.FilterCodec
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.ui.calendar.CalendarDaysScreen
import de.ingvoss.fotosortierer.ui.calendar.CalendarMonthsScreen
import de.ingvoss.fotosortierer.ui.calendar.CalendarYearsScreen
import de.ingvoss.fotosortierer.ui.detail.FilterDetailScreen
import de.ingvoss.fotosortierer.ui.home.HomeScreen
import de.ingvoss.fotosortierer.ui.onboarding.OnboardingScreen
import de.ingvoss.fotosortierer.ui.places.PlacesScreen
import de.ingvoss.fotosortierer.ui.settings.SettingsScreen
import de.ingvoss.fotosortierer.ui.sort.SortScreen
import de.ingvoss.fotosortierer.ui.sort.SortViewModel
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import de.ingvoss.fotosortierer.ui.trash.TrashScreen

/** Navigationsrouten (String-Routen; Filter werden als URL-sicheres Token übergeben). */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val YEAR = "calendar/year/{year}"
    const val MONTH = "calendar/month/{month}"
    const val PLACES = "places"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val FILTER = "filter/{filter}"
    const val SORT = "sort/{filter}/{resume}"

    fun year(year: Int) = "calendar/year/$year"
    fun month(month: String) = "calendar/month/$month"
    fun filter(filter: MediaFilter) = "filter/${FilterCodec.encode(filter)}"
    fun sort(filter: MediaFilter, resume: Boolean) = "sort/${FilterCodec.encode(filter)}/${if (resume) 1 else 0}"
}

private class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, AppIcons.Home),
    TopLevel(Routes.CALENDAR, R.string.nav_calendar, AppIcons.Calendar),
    TopLevel(Routes.PLACES, R.string.nav_places, AppIcons.Pin),
    TopLevel(Routes.TRASH, R.string.nav_trash, AppIcons.Trash),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, AppIcons.Settings)
)

/** Welchem Hauptbereich gehört die aktuelle Route an? null = Vollbild (Onboarding, Sortieren, Detail). */
private fun topLevelOf(route: String?): String? = when {
    route == null -> null
    route == Routes.HOME -> Routes.HOME
    route.startsWith("calendar") -> Routes.CALENDAR
    route == Routes.PLACES -> Routes.PLACES
    route == Routes.TRASH -> Routes.TRASH
    route == Routes.SETTINGS -> Routes.SETTINGS
    else -> null
}

@Composable
fun AppRoot(startWithOnboarding: Boolean) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = topLevelOf(entry?.destination?.route)
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val host: @Composable (Modifier) -> Unit = { modifier ->
        AppNavHost(navController, startWithOnboarding, ::navigateTop, modifier)
    }

    if (wide && current != null) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                topLevelDestinations.forEach { item ->
                    NavigationRailItem(
                        selected = current == item.route,
                        onClick = { navigateTop(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { host(Modifier.fillMaxSize()) }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (current != null) {
                    NavigationBar {
                        topLevelDestinations.forEach { item ->
                            NavigationBarItem(
                                selected = current == item.route,
                                onClick = { navigateTop(item.route) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { inner ->
            host(Modifier.padding(inner).consumeWindowInsets(inner))
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startWithOnboarding: Boolean,
    navigateTop: (String) -> Unit,
    modifier: Modifier
) {
    val start = remember { if (startWithOnboarding) Routes.ONBOARDING else Routes.HOME }
    NavHost(navController = navController, startDestination = start, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onContinue = { filter, resume -> navController.navigate(Routes.sort(filter, resume)) },
                onOpenCalendar = { navigateTop(Routes.CALENDAR) },
                onOpenPlaces = { navigateTop(Routes.PLACES) },
                onOpenTrash = { navigateTop(Routes.TRASH) },
                onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) }
            )
        }
        composable(Routes.CALENDAR) {
            CalendarYearsScreen(onOpenYear = { navController.navigate(Routes.year(it)) })
        }
        composable(Routes.YEAR, arguments = listOf(navArgument("year") { type = NavType.IntType })) { backStack ->
            CalendarMonthsScreen(
                year = backStack.arguments?.getInt("year") ?: 1970,
                onBack = { navController.popBackStack() },
                onOpenMonth = { navController.navigate(Routes.month(it)) }
            )
        }
        composable(Routes.MONTH, arguments = listOf(navArgument("month") { type = NavType.StringType })) { backStack ->
            val month = backStack.arguments?.getString("month").orEmpty()
            CalendarDaysScreen(
                month = month,
                onBack = { navController.popBackStack() },
                onOpenDay = { day -> navController.navigate(Routes.filter(MediaFilter(localDate = day))) },
                onSortMonth = { filter -> navController.navigate(Routes.sort(filter, false)) }
            )
        }
        composable(Routes.PLACES) {
            PlacesScreen(
                onOpenFilter = { navController.navigate(Routes.filter(it)) },
                onStartSorting = { navController.navigate(Routes.sort(it, false)) }
            )
        }
        composable(Routes.FILTER, arguments = listOf(navArgument("filter") { type = NavType.StringType })) { backStack ->
            val filter = remember(backStack) { FilterCodec.decode(backStack.arguments?.getString("filter")) }
            FilterDetailScreen(
                filter = filter,
                onBack = { navController.popBackStack() },
                onStartSorting = { navController.navigate(Routes.sort(it, false)) }
            )
        }
        composable(
            Routes.SORT,
            arguments = listOf(
                navArgument("filter") { type = NavType.StringType },
                navArgument("resume") { type = NavType.IntType }
            )
        ) { backStack ->
            val token = backStack.arguments?.getString("filter")
            val resume = backStack.arguments?.getInt("resume") == 1
            val filter = remember(token) { FilterCodec.decode(token) }
            val viewModel = containerViewModel(key = "sort-$token-$resume") { SortViewModel(it, filter, resume) }
            SortScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onOpenTrash = { navigateTop(Routes.TRASH) }
            )
        }
        composable(Routes.TRASH) { TrashScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}

private const val WIDE_LAYOUT_DP = 600
