package com.salehilove.core.designsystem.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavTest {

    @Test
    fun `push and pop behave like a back stack`() {
        val nav = AppNav("home")
        assertEquals("home", nav.current)
        assertFalse(nav.canPop)
        assertFalse(nav.pop()) // cannot pop last

        nav.push("words")
        nav.push("words/42")
        assertEquals("words/42", nav.current)
        assertTrue(nav.pop())
        assertEquals("words", nav.current)
        assertTrue(nav.canPop)
    }

    @Test
    fun `pushing the current route does not duplicate top of stack`() {
        val nav = AppNav("home")
        nav.push("words")
        nav.push("words")
        assertEquals(listOf("home", "words"), nav.state)
        assertTrue(nav.pop())
        assertEquals("home", nav.current)
        assertFalse(nav.canPop)
    }

    @Test
    fun `popUpTo clears forward entries`() {
        val nav = AppNav.fromStack(listOf("home", "a", "b", "c"))
        nav.popUpTo("a")
        assertEquals(listOf("home", "a"), nav.state)
        assertEquals("a", nav.current)
    }

    @Test
    fun `popUpTo with unknown route is a no-op`() {
        val nav = AppNav.fromStack(listOf("home", "a"))
        nav.popUpTo("zzz")
        assertEquals(listOf("home", "a"), nav.state)
    }

    @Test
    fun `resetTo drops everything`() {
        val nav = AppNav.fromStack(listOf("home", "a", "b"))
        nav.resetTo("settings")
        assertEquals(listOf("settings"), nav.state)
    }

    @Test
    fun `replace swaps the top`() {
        val nav = AppNav.fromStack(listOf("home", "a"))
        nav.replace("b")
        assertEquals(listOf("home", "b"), nav.state)
    }

    @Test
    fun `serialize round trip preserves stack`() {
        val nav = AppNav.fromStack(listOf("home", "words", "quiz"))
        val restored = AppNav.deserialize(AppNav.serialize(nav))
        assertEquals(nav.state, restored.state)
        assertEquals("quiz", restored.current)
    }

    @Test
    fun `deserialize of empty text falls back home`() {
        val restored = AppNav.deserialize("")
        assertEquals(listOf(AppNav.HOME), restored.state)
    }
}
