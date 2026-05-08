package ai.revrag.monitoring.kotlin

/**
 * Per-route semantic snapshot equivalent to the JSON tree
 * `revrag_monitoring`'s `buildSemanticScreenSnapshot` produces in Flutter.
 *
 * Every screen registers its own bucketed widget data via
 * [SemanticRegistry.set]; the capture pipeline reads it and emits the same
 * `widget_tree.semantic` JSON shape: { title, buttons, checkboxes, fields,
 * lists, texts }.
 */
data class SemanticSnapshot(
    val title: String? = null,
    val buttons: List<Map<String, Any?>> = emptyList(),
    val checkboxes: List<Map<String, Any?>> = emptyList(),
    val fields: List<Map<String, Any?>> = emptyList(),
    val lists: List<Map<String, Any?>> = emptyList(),
    val texts: List<String> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "buttons" to buttons,
        "checkboxes" to checkboxes,
        "fields" to fields,
        "lists" to lists,
        "texts" to texts,
    )
}

object SemanticRegistry {
    @Volatile
    private var snapshots: Map<String, SemanticSnapshot> = emptyMap()

    fun set(route: String, snapshot: SemanticSnapshot) {
        if (route.isBlank()) return
        synchronized(this) {
            snapshots = snapshots + (route to snapshot)
        }
    }

    fun get(route: String): SemanticSnapshot =
        snapshots[route] ?: SemanticSnapshot(title = route)

    /** Returns a route → snapshot map for diagnostics / tests. */
    fun all(): Map<String, SemanticSnapshot> = snapshots
}

object SemanticBuilders {
    private val SENSITIVE_LABEL_PATTERNS = listOf(
        "password", "passcode", "pin", "otp", "ssn", "cvv", "card",
    )
    private val CREDIT_CARD_REGEX = Regex("\\b(?:\\d[ -]?){13,16}\\b")
    private val OTP_REGEX = Regex("^\\s*\\d{4,8}\\s*$")

    fun button(text: String, kind: String = "elevated", enabled: Boolean = true): Map<String, Any?> =
        mapOf("role" to "button", "text" to text, "kind" to kind, "enabled" to enabled)

    fun checkbox(
        text: String,
        checked: Boolean,
        kind: String = "switch",
        enabled: Boolean = true,
    ): Map<String, Any?> = mapOf(
        "role" to "checkbox",
        "text" to text,
        "value" to if (checked) "checked" else "unchecked",
        "checked" to checked,
        "labels" to listOf(text),
        "enabled" to enabled,
        "kind" to kind,
    )

    fun field(
        label: String,
        value: String,
        kind: String = "text_field",
        secureOverride: Boolean? = null,
    ): Map<String, Any?> {
        val secure = secureOverride ?: isSensitive(label, value)
        return mapOf(
            "role" to "field",
            "text" to label,
            "value" to if (secure) redact(value) else value,
            "labels" to listOf(label),
            "secure" to secure,
            "kind" to kind,
        )
    }

    fun listItem(text: String, kind: String = "list_item"): Map<String, Any?> =
        mapOf(
            "role" to "list_item",
            "text" to text,
            "labels" to listOf(text),
            "kind" to kind,
        )

    private fun isSensitive(label: String, value: String): Boolean {
        val lower = label.lowercase()
        if (SENSITIVE_LABEL_PATTERNS.any { lower.contains(it) }) return true
        if (CREDIT_CARD_REGEX.containsMatchIn(value)) return true
        if (OTP_REGEX.matches(value) && lower.contains("otp")) return true
        return false
    }

    private fun redact(value: String): String =
        if (value.isBlank()) "" else "•".repeat(value.length.coerceAtMost(10))
}
