package ai.revrag.monitoring.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRegistryTest {
    @Test fun registers_and_returns_snapshot() {
        SemanticRegistry.set(
            MonitoringRouteConfig.form,
            SemanticSnapshot(
                title = "Registration Form",
                fields = listOf(SemanticBuilders.field("Email", "user@example.com")),
                buttons = listOf(SemanticBuilders.button("Submit")),
            ),
        )
        val snap = SemanticRegistry.get(MonitoringRouteConfig.form)
        assertEquals("Registration Form", snap.title)
        assertEquals(1, snap.buttons.size)
        assertEquals(1, snap.fields.size)
    }

    @Test fun sensitive_field_is_redacted() {
        val pwd = SemanticBuilders.field("Password", "Hunter2!")
        assertEquals(true, pwd["secure"])
        val redacted = pwd["value"] as String
        assertTrue(redacted.all { it == '•' })
    }

    @Test fun non_sensitive_field_is_not_redacted() {
        val email = SemanticBuilders.field("Email", "user@example.com")
        assertEquals(false, email["secure"])
        assertEquals("user@example.com", email["value"])
    }

    @Test fun checkbox_emits_checked_state() {
        val checked = SemanticBuilders.checkbox("Auto escalation", true, kind = "switch")
        assertEquals(true, checked["checked"])
        assertEquals("checked", checked["value"])
    }

    @Test fun list_item_emits_label() {
        val li = SemanticBuilders.listItem("Plan A")
        assertEquals("list_item", li["role"])
        assertEquals("Plan A", li["text"])
    }
}
