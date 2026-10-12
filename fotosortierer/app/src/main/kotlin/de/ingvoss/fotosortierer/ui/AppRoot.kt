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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
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
    const val CALENDAR = "calendar/{scope}"
    const val YEAR = "calendar/{scope}/year/{year}"
    const val MONTH = "calendar/{scope}/month/{month}"
    const val PLACES = "places/{scope}"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val FILTER = "filter/{filter}"
    const val SORT = "sort/{filter}/{resume}"

    // "scope" schränkt Kalender (auf Orte) bzw. Karte (auf einen Zeitraum) ein; "-" = keine Einschränkung.
    fun calendar(scope: MediaFilter) = "calendar/${FilterCodec.encode(scope)}"
    fun year(scope: MediaFilter, year: Int) = "calendar/${FilterCodec.encode(scope)}/year/$year"
    fun month(scope: MediaFilter, month: String) = "calendar/${FilterCodec.encode(scope)}/month/$month"
    fun places(scope: MediaFilter) = "places/${FilterCodec.encode(scope)}"
    fun filter(filter: MediaFilter) = "filter/${FilterCodec.encode(filter)}"
    fun sort(filter: MediaFilter, resume: Boolean) = "sort/${FilterCodec.encode(filter)}/${if (resume) 1 else 0}"
}

/** [key] kennzeichnet den Bereich, [target] ist seine Startseite ohne Einschränkung. */
private class TopLevel(val key: String, val target: String, val labelRes: Int, val icon: ImageVector)

private const val KEY_HOME = "home"
private const val KEY_CALENDAR = "calendar"
private const val KEY_PLACES = "places"
private const val KEY_TRASH = "trash"
private const val KEY_SETTINGS = "settings"

private val topLevelDestinations = listOf(
    TopLevel(KEY_HOME, Routes.HOME, R.string.nav_home, AppIcons.Home),
    TopLevel(KEY_CALENDAR, Routes.calendar(MediaFilter.ALL), R.string.nav_calendar, AppIcons.Calendar),
    TopLevel(KEY_PLACES, Routes.places(MediaFilter.ALL), R.string.nav_places, AppIcons.Pin),
    TopLevel(KEY_TRASH, Routes.TRASH, R.string.nav_trash, AppIcons.Trash),
    TopLevel(KEY_SETTINGS, Routes.SETTINGS, R.string.nav_settings_short, AppIcons.Settings)
)

/** Welchem Hauptbereich gehört die aktuelle Route an? null = Vollbild (Onboarding, Sortieren, Detail). */
private fun topLevelOf(route: String?): String? = when {
    route == null -> null
    route == Routes.HOME -> KEY_HOME
    route.startsWith("calendar") -> KEY_CALENDAR
    route.startsWith("places") -> KEY_PLACES
    route == Routes.TRASH -> KEY_TRASH
    route == Routes.SETTINGS -> KEY_SETTINGS
    else -> null
}

@Suppress("ConfigurationScreenWidthHeight") // grobe Breitenklasse genügt für Leiste vs. Rail
@Composable
fun AppRoot(startWithOnboarding: Boolean) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = topLevelOf(entry?.destination?.route)
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP

    // Stabil und mit stets aktuellem Bereich: Ein "eingefrorener" Handler würde den Reiterwechsel falsch beurteilen.
    val currentKey by rememberUpdatedState(current)
    val navigateTop: (String) -> Unit = remember(navController) {
        { key ->
            val target = topLevelDestinations.first { it.key == key }.target
            if (currentKey == key) {
                // Wer den Reiter antippt, in dem er schon ist, kehrt zu dessen Startseite (ohne Einschränkung) zurück.
                navController.navigate(target) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(target) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val host: @Composable (Modifier) -> Unit = { modifier ->
        AppNavHost(navController, startWithOnboarding, navigateTop, modifier)
    }

    if (wide && current != null) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                topLevelDestinations.forEach { item ->
                    NavigationRailItem(
                        selected = current == item.key,
                        onClick = { navigateTop(item.key) },
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
                                selected = current == item.key,
                                onClick = { navigateTop(item.key) },
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
    val scopeArg = listOf(navArgument("scope") { type = NavType.StringType })
    fun NavBackStackEntry.scope(): MediaFilter = FilterCodec.decode(arguments?.getString("scope"))

    NavHost(navController = navController, startDestination = start, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onContinue = { filter, resume -> navController.navigate(Routes.sort(filter, resume)) },
                onOpenCalendar = { navigateTop(KEY_CALENDAR) },
                onOpenPlaces = { navigateTop(KEY_PLACES) },
                onOpenTrash = { navigateTop(KEY_TRASH) },
                onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
                onOpenFilter = { navController.navigate(Routes.filter(it)) }
            )
        }
        composable(Routes.CALENDAR, arguments = scopeArg) { backStack ->
            val scope = remember(backStack) { backStack.scope() }
            CalendarYearsScreen(
                scope = scope,
                onOpenYear = { navController.navigate(Routes.year(scope, it)) },
                onShowMap = { navController.navigate(Routes.places(it)) },
                onClearScope = { navigateTop(KEY_CALENDAR) }
            )
        }
        composable(
            Routes.YEAR,
            arguments = scopeArg + listOf(navArgument("year") { type = NavType.IntType })
        ) { backStack ->
            val scope = remember(backStack) { backStack.scope() }
            CalendarMonthsScreen(
                scope = scope,
                year = backStack.arguments?.getInt("year") ?: 1970,
                onBack = { navController.popBackStack() },
                onOpenMonth = { navController.navigate(Routes.month(scope, it)) },
                onSortYear = { navController.navigate(Routes.sort(it, false)) },
                onShowMedia = { navController.navigate(Routes.filter(it)) },
                onShowMap = { navController.navigate(Routes.places(it)) }
            )
        }
        composable(
            Routes.MONTH,
            arguments = scopeArg + listOf(navArgument("month") { type = NavType.StringType })
        ) { backStack ->
            val scope = remember(backStack) { backStack.scope() }
            CalendarDaysScreen(
                scope = scope,
                month = backStack.arguments?.getString("month").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenDay = { navController.navigate(Routes.filter(it)) },
                onSortMonth = { navController.navigate(Routes.sort(it, false)) },
                onShowMedia = { navController.navigate(Routes.filter(it)) },
                onShowMap = { navController.navigate(Routes.places(it)) }
            )
        }
        composable(Routes.PLACES, arguments = scopeArg) { backStack ->
            val scope = remember(backStack) { backStack.scope() }
            PlacesScreen(
                scope = scope,
                onOpenFilter = { navController.navigate(Routes.filter(it)) },
                onStartSorting = { navController.navigate(Routes.sort(it, false)) },
                onShowCalendar = { navController.navigate(Routes.calendar(it)) },
                onClearScope = { navigateTop(KEY_PLACES) }
            )
        }
        composable(Routes.FILTER, arguments = listOf(navArgument("filter") { type = NavType.StringType })) { backStack ->
            val filter = remember(backStack) { FilterCodec.decode(backStack.arguments?.getString("filter")) }
            FilterDetailScreen(
                filter = filter,
                onBack = { navController.popBackStack() },
                onStartSorting = { navController.navigate(Routes.sort(it, false)) },
                onShowMap = { navController.navigate(Routes.places(it)) },
                onShowCalendar = { navController.navigate(Routes.calendar(it)) }
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
                onOpenTrash = { navigateTop(KEY_TRASH) }
            )
        }
        composable(Routes.TRASH) { TrashScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}

private const val WIDE_LAYOUT_DP = 600
