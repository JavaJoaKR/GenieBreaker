package kr.javajoa.geniebreaker

internal fun stopDiscoveryBeforePairing(
    isDiscovering: () -> Boolean,
    cancelDiscovery: () -> Unit,
    waitForStateUpdate: () -> Unit = { Thread.sleep(50) }
): Boolean {
    if (!isDiscovering()) return true

    // Cancellation posts a request to the Bluetooth service. Its state may update later.
    cancelDiscovery()
    repeat(40) {
        if (!isDiscovering()) return true
        waitForStateUpdate()
    }
    return !isDiscovering()
}
