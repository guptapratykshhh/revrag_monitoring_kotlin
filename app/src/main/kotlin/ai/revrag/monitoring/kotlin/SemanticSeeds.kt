package ai.revrag.monitoring.kotlin

/**
 * Pre-populates [SemanticRegistry] with the same widget bucket data each
 * screen emits when it first composes. This guarantees that the offstage
 * prefetch loop on cold start produces non-empty `widget_tree.semantic`
 * JSON for every declared route — equivalent to Flutter's
 * `_prefetchOtherRoutesOffstage` writing snapshots before user navigation.
 *
 * When a screen actually composes it overwrites the seed with live state.
 */
object SemanticSeeds {
    fun installAll() {
        SemanticRegistry.set(
            MonitoringRouteConfig.splash,
            SemanticSnapshot(
                title = "RevRag Kotlin Monitoring",
                buttons = listOf(SemanticBuilders.button("Start", kind = "elevated")),
                texts = listOf("RevRag Kotlin Monitoring", "Monitoring Active (Silent Mode)"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.welcome,
            SemanticSnapshot(
                title = "Welcome",
                buttons = listOf(
                    SemanticBuilders.button("Go to Registration Form", kind = "elevated"),
                    SemanticBuilders.button("View Content Banner", kind = "text"),
                    SemanticBuilders.button("Checkout & Payment", kind = "filled_tonal"),
                    SemanticBuilders.button("Open Analytics Dashboard", kind = "filled_tonal"),
                    SemanticBuilders.button("Open Automation Settings", kind = "outlined"),
                    SemanticBuilders.button("View Incident Timeline", kind = "outlined"),
                    SemanticBuilders.button("Open Drawer Navigation Demo", kind = "outlined"),
                    SemanticBuilders.button("Open Bottom Tabs Demo", kind = "outlined"),
                    SemanticBuilders.button("POC: Transfer Native -> WebView Agent", kind = "filled_tonal"),
                ),
                texts = listOf("Welcome"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.form,
            SemanticSnapshot(
                title = "Registration Form",
                fields = listOf(
                    SemanticBuilders.field("Full name", ""),
                    SemanticBuilders.field("Phone", ""),
                    SemanticBuilders.field("Email", ""),
                    SemanticBuilders.field("Password", "", secureOverride = true),
                ),
                buttons = listOf(SemanticBuilders.button("Submit", kind = "elevated")),
                texts = listOf("Registration Form"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.banner,
            SemanticSnapshot(
                title = "Content Banner",
                lists = (1..8).map { SemanticBuilders.listItem("Offer Card #$it") },
                buttons = listOf(SemanticBuilders.button("Checkout & Payment", kind = "elevated")),
                texts = listOf("Content Banner"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.checkout,
            SemanticSnapshot(
                title = "Payment Checkout",
                fields = listOf(
                    SemanticBuilders.field("Amount", "4999"),
                    SemanticBuilders.field("Coupon", ""),
                    SemanticBuilders.field("Card", ""),
                ),
                buttons = listOf(SemanticBuilders.button("Pay Now", kind = "elevated")),
                texts = listOf("Payment Checkout"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.success,
            SemanticSnapshot(
                title = "Form Submitted Successfully",
                buttons = listOf(SemanticBuilders.button("Back to Welcome", kind = "elevated")),
                texts = listOf("Form Submitted Successfully"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.analytics,
            SemanticSnapshot(
                title = "Analytics Dashboard",
                checkboxes = listOf(SemanticBuilders.checkbox("Critical only", false)),
                lists = listOf(
                    SemanticBuilders.listItem("Checkout Drop 17.2% (critical)"),
                    SemanticBuilders.listItem("OTP Timeout 8.4% (critical)"),
                    SemanticBuilders.listItem("Banner CTR +2.1%"),
                ),
                buttons = listOf(
                    SemanticBuilders.button("Open Settings", kind = "outlined"),
                    SemanticBuilders.button("View Timeline", kind = "filled_tonal"),
                    SemanticBuilders.button("Transfer -> WebView", kind = "outlined"),
                ),
                texts = listOf("Analytics Dashboard", "Funnels", "Latency", "Quality"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.settings,
            SemanticSnapshot(
                title = "Automation Settings",
                checkboxes = listOf(
                    SemanticBuilders.checkbox("Auto escalation", true, kind = "switch"),
                    SemanticBuilders.checkbox("Slack notifications", true, kind = "switch"),
                ),
                fields = listOf(
                    SemanticBuilders.field(
                        "Confidence Threshold", "75%", kind = "slider", secureOverride = false,
                    ),
                ),
                buttons = listOf(SemanticBuilders.button("Go To Incident Timeline", kind = "elevated")),
                texts = listOf("Automation Settings", "Advanced Conditions"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.timeline,
            SemanticSnapshot(
                title = "Incident Timeline",
                lists = listOf(
                    SemanticBuilders.listItem("Event Detected - Checkout latency crossed 600ms threshold"),
                    SemanticBuilders.listItem("Context Snapshot - Widget tree + screenshot collected"),
                    SemanticBuilders.listItem("Routing Decision - Escalated to payment QA squad"),
                    SemanticBuilders.listItem("Resolution - Rollback complete"),
                ),
                buttons = listOf(
                    SemanticBuilders.button("Next", kind = "text"),
                    SemanticBuilders.button("Back", kind = "text"),
                    SemanticBuilders.button("Open Drawer Navigation Demo", kind = "elevated"),
                ),
                texts = listOf("Incident Timeline"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.drawerShell,
            SemanticSnapshot(
                title = "Drawer Navigation Hub",
                lists = listOf(
                    SemanticBuilders.listItem("Analytics"),
                    SemanticBuilders.listItem("Bottom Tabs Demo"),
                ),
                buttons = listOf(
                    SemanticBuilders.button("Analytics", kind = "outlined"),
                    SemanticBuilders.button("Bottom Tabs Demo", kind = "outlined"),
                ),
                texts = listOf("Drawer Navigation Hub"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.tabsShell,
            SemanticSnapshot(
                title = "Bottom Tabs Demo",
                buttons = listOf(
                    SemanticBuilders.button("Open Analytics", kind = "elevated"),
                    SemanticBuilders.button("Open Timeline", kind = "outlined"),
                    SemanticBuilders.button("Settings", kind = "elevated"),
                ),
                lists = listOf(
                    SemanticBuilders.listItem("Home Tab"),
                    SemanticBuilders.listItem("Reports Tab"),
                    SemanticBuilders.listItem("Profile Tab"),
                ),
                texts = listOf("Bottom Tabs Demo"),
            ),
        )
        SemanticRegistry.set(
            MonitoringRouteConfig.webviewBridge,
            SemanticSnapshot(
                title = "WebView Bridge",
                buttons = listOf(SemanticBuilders.button("Back to native", kind = "elevated")),
                texts = listOf("WebView Bridge"),
            ),
        )
    }
}
