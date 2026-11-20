package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellSplitTest {

    @Test
    fun splitsOnAnyWhitespace() {
        assertEquals(listOf("-o1", "-a1", "-An"), shellSplit("  -o1\t-a1\n\n-An  "))
    }

    @Test
    fun keepsQuotedText() {
        assertEquals(
            listOf("-n", "www.example.com", "-H", "a b"),
            shellSplit("-n \"www.example.com\" -H 'a b'"),
        )
    }

    @Test
    fun keepsEmptyQuotedArgument() {
        assertEquals(listOf("-x", "", "-y"), shellSplit("-x \"\" -y"))
    }

    @Test
    fun unescapesQuoteInsideQuotes() {
        assertEquals(listOf("a\"b"), shellSplit("\"a\\\"b\""))
    }

    @Test
    fun keepsOtherBackslashes() {
        assertEquals(listOf("a\\nb"), shellSplit("\"a\\nb\""))
    }

    @Test
    fun emptyInputGivesNoArguments() {
        assertEquals(emptyList<String>(), shellSplit(""))
        assertEquals(emptyList<String>(), shellSplit("   "))
    }
}
