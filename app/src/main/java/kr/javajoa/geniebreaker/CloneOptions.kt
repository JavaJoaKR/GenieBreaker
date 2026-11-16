package kr.javajoa.geniebreaker

/** Optional transformations only. Unique package/provider/permission identities remain mandatory. */
internal data class CloneOptions(
    val renameApp: Boolean = true,
    val renameLaunchers: Boolean = true,
    val renameResources: Boolean = true,
    val isolateTasks: Boolean = true,
    val extractLibraries: Boolean = true
) {
    fun values() = mapOf("renameApp" to renameApp, "renameLaunchers" to renameLaunchers,
        "renameResources" to renameResources, "isolateTasks" to isolateTasks, "extractLibraries" to extractLibraries)
    companion object {
        fun from(values: Map<String, Boolean>) = CloneOptions(values["renameApp"] ?: true, values["renameLaunchers"] ?: true,
            values["renameResources"] ?: true, values["isolateTasks"] ?: true, values["extractLibraries"] ?: true)
    }
}
