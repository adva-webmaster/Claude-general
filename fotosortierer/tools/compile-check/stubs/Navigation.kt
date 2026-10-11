@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.navigation

import android.os.Bundle

class NavDestination { val route: String? get() = null }

class NavBackStackEntry {
    val destination: NavDestination get() = NavDestination()
    val arguments: Bundle? get() = null
}

class PopUpToBuilder {
    var inclusive: Boolean = false
    var saveState: Boolean = false
}

class NavOptionsBuilder {
    var launchSingleTop: Boolean = false
    var restoreState: Boolean = false
    fun popUpTo(route: String, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {}
}

open class NavController {
    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {}
    fun popBackStack(): Boolean = true
}

class NavHostController : NavController()

abstract class NavType<T> {
    companion object {
        val IntType: NavType<Int> = object : NavType<Int>() {}
        val StringType: NavType<String?> = object : NavType<String?>() {}
    }
}

class NamedNavArgument

class NavArgumentBuilder {
    var type: NavType<*>? = null
}

fun navArgument(name: String, builder: NavArgumentBuilder.() -> Unit): NamedNavArgument = NamedNavArgument()
