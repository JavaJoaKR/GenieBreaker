package kr.javajoa.geniebreaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPairingTest {
    @Test
    fun leavesAnIdleAdapterAlone() {
        assertTrue(stopDiscoveryBeforePairing(
            isDiscovering = { false },
            cancelDiscovery = { error("An idle adapter requires no cancellation") },
            waitForStateUpdate = { error("An idle adapter requires no wait") }
        ))
    }

    @Test
    fun waitsForDiscoveryToStopAfterRequestingCancellation() {
        var active = true
        var cancellations = 0
        var waits = 0
        assertTrue(stopDiscoveryBeforePairing(
            isDiscovering = { active },
            cancelDiscovery = { cancellations++ },
            waitForStateUpdate = { if (++waits == 3) active = false }
        ))
        assertEquals(1, cancellations)
        assertEquals(3, waits)
    }

    @Test
    fun reportsDiscoveryThatNeverStopsWithoutRepeatingCancellation() {
        var cancellations = 0
        var waits = 0
        assertFalse(stopDiscoveryBeforePairing(
            isDiscovering = { true },
            cancelDiscovery = { cancellations++ },
            waitForStateUpdate = { waits++ }
        ))
        assertEquals(1, cancellations)
        assertEquals(40, waits)
    }
}
