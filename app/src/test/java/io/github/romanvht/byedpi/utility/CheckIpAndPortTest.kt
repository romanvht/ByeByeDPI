package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckIpAndPortTest {

    private fun check(command: String) = shellSplit(command).checkIpAndPortInArgs()

    @Test
    fun commandWithoutAddress() {
        assertEquals(null to null, check("-o1 -a1 -An -Ku -a3 -An"))
    }

    @Test
    fun shortOptionsWithSeparateValues() {
        assertEquals("10.0.0.1" to "1081", check("-i 10.0.0.1 -p 1081 -o1"))
    }

    @Test
    fun shortOptionsWithAttachedValues() {
        assertEquals("10.0.0.1" to "1081", check("-i10.0.0.1 -p1081"))
    }

    @Test
    fun longOptions() {
        assertEquals("10.0.0.2" to "2000", check("--ip 10.0.0.2 --port=2000"))
    }

    @Test
    fun abbreviatedLongOption() {
        assertEquals(null to "3000", check("--po 3000"))
    }

    @Test
    fun flagsDoNotTakeValues() {
        assertEquals(null to "1082", check("-N --no-domain -p 1082"))
    }

    @Test
    fun valueOfAnotherOptionIsNotAnAddress() {
        assertEquals(null to null, check("-f -p9"))
    }

    @Test
    fun stopsAtDoubleDash() {
        assertEquals(null to null, check("-o1 -- -p 1"))
    }
}
