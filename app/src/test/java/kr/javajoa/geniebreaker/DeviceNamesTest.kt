package kr.javajoa.geniebreaker

import org.junit.Assert.*
import org.junit.Test

class DeviceNamesTest {
    @Test fun unknownThenLateNameUpdatesSameAddress() {
        val names = DeviceNames()
        names.seed("A", null)
        assertNull(names.get("A"))
        names.merge("A", "  Galaxy Buds FE  ")
        assertEquals("Galaxy Buds FE", names.get("A"))
        assertEquals(1, names.snapshot().size)
    }
    @Test fun emptyDiscoveryDoesNotEraseResolvedName() {
        val names = DeviceNames()
        names.merge("A", "Headphones")
        names.merge("A", null); names.merge("A", "  ")
        assertEquals("Headphones", names.get("A"))
    }
    @Test fun staleDeviceCacheCannotOverwriteNameBroadcast() {
        val names = DeviceNames()
        names.seed("A", "Old name")
        names.merge("A", "New name")
        names.seed("A", "Old name")
        assertEquals("New name", names.get("A"))
    }
    @Test fun nameBeforeDiscoveryIsRetainedAndDevicesStaySeparate() {
        val names = DeviceNames()
        names.merge("A", "Headphones"); names.seed("A", null)
        names.merge("B", "Keyboard")
        assertEquals("Headphones", names.get("A"))
        assertEquals("Keyboard", names.get("B"))
        assertEquals(2, names.snapshot().size)
    }
}
