package com.salehilove.core.designsystem.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Tiny dependency-free navigation for Compose: an ordered back stack of
 * routes (strings). Implemented by hand because androidx.navigation isn't
 * available in the offline mirror — the state holder's behaviour (push, pop,
 * popUpTo, state restoration) is covered by unit tests.
 */
class AppNav(initial: String) {

    private val stack = mutableStateOf(listOf(initial))

    val current: String get() = stack.value.last()

    val canPop: Boolean get() = stack.value.size > 1

    /** Navigation state as an observable snapshot. */
    val state: List<String> get() = stack.value

    fun push(route: String) {
        if (stack.value.lastOrNull() == route) return
        stack.value = stack.value + route
    }

    fun replace(route: String) {
        stack.value = stack.value.dropLast(1) + route
    }

    fun pop(): Boolean {
        if (stack.value.size <= 1) return false
        stack.value = stack.value.dropLast(1)
        return true
    }

    fun popUpTo(route: String) {
        val idx = stack.value.indexOf(route)
        if (idx >= 0) {
            stack.value = stack.value.subList(0, idx + 1)
        }
    }

    fun resetTo(route: String) {
        stack.value = listOf(route)
    }

    companion object {

        const val HOME = "home"

        /** Route separator for state serialisation; routes never contain it. */
        private const val SEP: String = "|"

        fun fromStack(routes: List<String>): AppNav =
            AppNav(routes.lastOrNull() ?: HOME).apply {
                if (routes.isNotEmpty()) stack.value = routes
            }

        fun serialize(nav: AppNav): String = nav.state.joinToString(SEP)

        fun deserialize(text: String): AppNav = fromStack(
            text.split(SEP).filter { it.isNotBlank() }.ifEmpty { listOf(HOME) },
        )

        val SAVER: Saver<AppNav, String> = Saver(
            save = { nav -> serialize(nav) },
            restore = { saved -> deserialize(saved) },
        )
    }
}

@Composable
fun rememberAppNav(initial: String = AppNav.HOME): AppNav =
    rememberSaveable(saver = AppNav.SAVER) { AppNav(initial) }
