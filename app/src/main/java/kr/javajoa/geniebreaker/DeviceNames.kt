package kr.javajoa.geniebreaker

/** Late names replace placeholders, but an absent name never causes permanent loading. */
internal class DeviceNames(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val values = linkedMapOf<String, String>()
    private val observed = hashMapOf<String, Long>()
    fun merge(address: String, incoming: String?) {
        observed.getOrPut(address) { clock() }
        incoming?.trim()?.takeIf { it.isNotEmpty() }?.let { values[address] = it }
    }
    fun seed(address: String, cached: String?) {
        observed.getOrPut(address) { clock() }
        if (!values.containsKey(address)) merge(address, cached)
    }
    fun isPending(address: String) = get(address) == null && observed[address]?.let { clock() - it < 12_000 } == true
    fun get(address: String): String? = values[address]
    fun snapshot(): Map<String, String> = values.toMap()
}
