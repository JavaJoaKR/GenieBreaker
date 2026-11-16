package kr.javajoa.geniebreaker

/** Names are independent of BluetoothDevice's occasionally stale cache. Keyed by address. */
internal class DeviceNames {
    private val values = linkedMapOf<String, String>()
    fun merge(address: String, incoming: String?) {
        incoming?.trim()?.takeIf { it.isNotEmpty() }?.let { values[address] = it }
    }
    fun seed(address: String, cached: String?) {
        if (!values.containsKey(address)) merge(address, cached)
    }
    fun get(address: String): String? = values[address]
    fun snapshot(): Map<String, String> = values.toMap()
}
