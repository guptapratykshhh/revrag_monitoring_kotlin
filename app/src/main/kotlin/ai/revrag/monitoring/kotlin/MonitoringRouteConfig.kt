package ai.revrag.monitoring.kotlin

object MonitoringRouteConfig {
    const val splash = "Splash"
    const val welcome = "/welcome"
    const val form = "/form"
    const val banner = "/banner"
    const val checkout = "/checkout"
    const val success = "/form-submit-success"
    const val analytics = "/analytics"
    const val settings = "/settings"
    const val timeline = "/timeline"
    const val drawerShell = "/drawer-shell"
    const val tabsShell = "/tabs-shell"
    const val webviewBridge = "/webview-bridge"

    val declaredRoutes: Set<String> = setOf(
        splash,
        welcome,
        form,
        banner,
        checkout,
        success,
        analytics,
        settings,
        timeline,
        drawerShell,
        tabsShell,
        webviewBridge,
    )

    fun resolveWebviewRoutePath(currentRoute: String): String {
        val override = BuildConfig.REVRAG_DEMO_WEBVIEW_ROUTE_PATH.trim()
        if (override.isNotEmpty() && declaredRoutes.contains(override)) return override
        return declaredRoutes.firstOrNull {
            val lower = it.lowercase()
            lower.contains("webview") || lower.contains("browser")
        } ?: if (currentRoute.isNotBlank()) currentRoute else welcome
    }

    fun resolveNativeHandoffTargetRoutePath(fallback: String = welcome): String {
        val override = BuildConfig.REVRAG_DEMO_NATIVE_HANDOFF_TARGET_ROUTE_PATH.trim()
        if (override.isNotEmpty() && declaredRoutes.contains(override)) return override
        return declaredRoutes.firstOrNull { it != webviewBridge && it != splash } ?: fallback
    }
}

