package kr.javajoa.geniebreaker

import org.junit.Assert.*
import org.junit.Test

class ErrorCodesTest {
    @Test fun shortStableCodesAreSafeToTranscribeAndContainNoIdentifiers() {
        val first = ErrorCodes.create("Bluetooth AA:BB:CC:DD:EE:FF", "SecurityException")
        val second = ErrorCodes.create("Bluetooth 192.168.1.1", "SecurityException")
        assertEquals(first, second)
        assertTrue(first.matches(Regex("GB-[A-Z]{2}-[0-9A-F]{4}")))
        assertEquals(10, first.length)
        assertNotEquals(first, ErrorCodes.create("Bluetooth", "NoSuchMethodException"))
        assertNotEquals(first, ErrorCodes.create("Launch", "SecurityException"))
    }
}
