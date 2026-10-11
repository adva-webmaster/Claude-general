@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController

class NavGraphBuilder

@Composable fun rememberNavController(): NavHostController = NavHostController()

@Composable
fun NavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit
) {}

fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {}

@Composable
fun NavController.currentBackStackEntryAsState(): State<NavBackStackEntry?> = error("stub")
