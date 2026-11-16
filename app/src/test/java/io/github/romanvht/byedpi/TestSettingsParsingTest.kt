package io.github.romanvht.byedpi

import io.github.romanvht.byedpi.data.parseTestCommands
import org.junit.Assert.assertEquals
import org.junit.Test

class TestSettingsParsingTest {
    @Test
    fun skipsCommentsAndSubstitutesSni() {
        val content = """
            # Zapret-derived strategies

            -s 1+s
            -f 1+s:6 -n {sni}
        """.trimIndent()

        assertEquals(
            listOf("-s 1+s", "-f 1+s:6 -n \"example.com\""),
            parseTestCommands(content, "example.com")
        )
    }
}
