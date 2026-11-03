package com.clarity.finance.data.model

import com.google.firebase.Timestamp

/** Stable IDs are stored on transactions; renaming/archiving never rewrites financial history. */
data class CatalogueEntry(val id: String, val kind: String, val name: String, val archived: Boolean = false,
    val defaultValue: String = if (kind == "category") "discretionary" else "LKR", val updatedAt: Timestamp? = null) {
    fun values(): Map<String, Any> = mapOf("name" to name, "archived" to archived, "defaultValue" to defaultValue)
    fun validate() {
        require(kind in setOf("category", "source")) { "Choose a catalogue type." }
        require(CatalogueDefaults.validId(kind, id)) { "Invalid catalogue identity." }
        require(name == name.trim() && name.isNotBlank() && name.length <= 100 && name.none { it.isISOControl() }) { "Use a name of 1 to 100 visible characters." }
        require(defaultValue in if (kind == "category") setOf("committed", "discretionary") else setOf("LKR", "USD")) { "Invalid catalogue default." }
    }
}

object CatalogueDefaults {
    val sources = linkedMapOf("salary" to "Salary", "freelance" to "Freelance", "adsense" to "AdSense")
    fun validId(kind: String, id: String): Boolean = id in (if (kind == "category") ExpenseCategories.labels else sources) ||
        id.matches(Regex("custom_[A-Za-z0-9_-]{1,100}"))
    fun entries(): List<CatalogueEntry> = ExpenseCategories.labels.map { (id, name) ->
        CatalogueEntry(id, "category", name, defaultValue = if (id in setOf("housing", "utilities")) "committed" else "discretionary")
    } + sources.map { (id, name) -> CatalogueEntry(id, "source", name, defaultValue = if (id == "adsense") "USD" else "LKR") }
    fun merge(overrides: List<CatalogueEntry>): List<CatalogueEntry> =
        (entries().associateBy { it.kind to it.id } + overrides.associateBy { it.kind to it.id }).values
            .sortedWith(compareBy<CatalogueEntry> { it.kind }.thenBy { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    fun validateComplete(rows: List<CatalogueEntry>) {
        val defaults = entries().associateBy { it.kind to it.id }
        check(rows.map { it.kind to it.id }.toSet().size == rows.size && rows.map { it.kind to it.id }.containsAll(defaults.keys)) { "Incomplete catalogue cache; reconnect to load it." }
        rows.forEach {
            it.validate()
            check(it.updatedAt != null || it == defaults[it.kind to it.id]) { "Missing confirmed catalogue version." }
        }
    }
}

data class CatalogueSnapshot(val entries: List<CatalogueEntry>? = null, val fromCache: Boolean = true, val error: String? = null) {
    fun label(kind: String, id: String) = entries?.firstOrNull { it.kind == kind && it.id == id }?.name
        ?: "${kind.replaceFirstChar { it.uppercase() }} label unavailable ($id)"
    fun choices(kind: String, includeId: String? = null) = entries.orEmpty().filter { it.kind == kind && (!it.archived || it.id == includeId) }
    fun labels(kind: String): Map<String, String> = entries.orEmpty().filter { it.kind == kind }.associate { it.id to it.name }
    fun defaultValue(kind: String, id: String): String? = entries?.firstOrNull { it.kind == kind && it.id == id }?.defaultValue
    fun accepts(kind: String, id: String, unchangedId: String? = null) = entries?.any {
        it.kind == kind && it.id == id && (!it.archived || id == unchangedId)
    } == true
}
