package com.salehilove.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {

    @Test
    fun `round trip object`() {
        val doc: LinkedHashMap<String, Any?> = linkedMapOf(
            "name" to "صالحی",
            "age" to 30L,
            "score" to 4.5,
            "active" to true,
            "missing" to null,
            "tags" to listOf("a", "یادداشت", "c"),
            "nested" to mapOf("x" to 1L),
        )
        val text = Json.write(doc)
        val back = Json.parse(text)
        assertEquals(doc, back)
    }

    @Test
    fun `string escaping round trip`() {
        val tricky = "خط \"یک\"\nمتن\tبا\\اسلش و \u0007 کنترل"
        val text = Json.write(mapOf("v" to tricky))
        val back = Json.parse(text) as Map<*, *>
        assertEquals(tricky, back["v"])
    }

    @Test
    fun `persian and unicode escapes`() {
        val json = "{\"k\":\"\\u0633\\u0644\\u0627\\u0645\"}"
        val back = Json.parse(json) as Map<*, *>
        assertEquals("سلام", back["k"])
    }

    @Test
    fun `numbers parse to long and double`() {
        val back = Json.parse("[1, -2, 3.5, 1e3]") as List<*>
        assertEquals(1L, back[0])
        assertEquals(-2L, back[1])
        assertEquals(3.5, back[2])
        assertEquals(1000.0, back[3])
    }

    @Test
    fun `arrays round trip`() {
        val list = listOf("one", "دو", "سه", "۴")
        val text = Json.write(list)
        assertEquals("""["one","دو","سه","۴"]""", text)
        assertEquals(list, Json.parse(text))
    }

    @Test
    fun `empty structures`() {
        assertEquals(emptyMap<String, Any?>(), Json.parse("{}"))
        assertEquals(emptyList<Any?>(), Json.parse("[]"))
        assertEquals("{}", Json.write(emptyMap<String, Any?>()))
        assertEquals("[]", Json.write(emptyList<Any?>()))
    }

    @Test
    fun `rejects malformed input`() {
        val bad = listOf(
            "{", "}", "[", "]", "{\"a\"}", "{\"a\":}", "[1,]", "{\"a\":1,}",
            "tru", "\"unterminated", "{1:2}", "01x", "", "  ", "[1] trailing",
        )
        bad.forEach { input ->
            try {
                Json.parse(input)
                fail("expected rejection for: $input")
            } catch (expected: Exception) {
                assertTrue(
                    "expected JsonException for $input but got ${expected::class.java}",
                    expected is Json.JsonException,
                )
            }
        }
    }

    @Test
    fun `object key order preserved`() {
        val json = """{"z":1,"a":2,"م":3}"""
        val back = Json.parse(json) as LinkedHashMap<*, *>
        assertEquals(listOf("z", "a", "م"), back.keys.toList())
    }
}
