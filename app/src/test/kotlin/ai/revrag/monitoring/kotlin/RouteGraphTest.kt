package ai.revrag.monitoring.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGraphTest {
    @Test fun declared_routes_contain_webview_bridge() {
        assertTrue(MonitoringRouteConfig.declaredRoutes.contains(MonitoringRouteConfig.webviewBridge))
    }

    @Test fun declared_routes_contain_all_monitoring_screens() {
        val expected = listOf(
            MonitoringRouteConfig.welcome,
            MonitoringRouteConfig.form,
            MonitoringRouteConfig.banner,
            MonitoringRouteConfig.checkout,
            MonitoringRouteConfig.success,
            MonitoringRouteConfig.analytics,
            MonitoringRouteConfig.settings,
            MonitoringRouteConfig.timeline,
            MonitoringRouteConfig.drawerShell,
            MonitoringRouteConfig.tabsShell,
            MonitoringRouteConfig.webviewBridge,
        )
        expected.forEach {
            assertTrue("Missing route: $it", MonitoringRouteConfig.declaredRoutes.contains(it))
        }
    }

    @Test fun resolve_webview_route_path_prefers_webview_keyword() {
        val resolved = MonitoringRouteConfig.resolveWebviewRoutePath(MonitoringRouteConfig.welcome)
        assertEquals(MonitoringRouteConfig.webviewBridge, resolved)
    }

    @Test fun resolve_native_handoff_target_excludes_webview_and_splash() {
        val resolved = MonitoringRouteConfig.resolveNativeHandoffTargetRoutePath()
        assertNotNull(resolved)
        assertFalse(resolved == MonitoringRouteConfig.webviewBridge)
        assertFalse(resolved == MonitoringRouteConfig.splash)
    }
}
