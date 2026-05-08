package ai.revrag.monitoring.kotlin

import android.app.Application
import ai.revrag.embed.android.EmbedSDK
import ai.revrag.embed.core.events.EventKeys

object MonitoringConfig {
    private const val DEFAULT_FLOW = "monitoring_demo"
    private const val DEFAULT_APP_USER_ID = "kotlin_monitoring_demo_user"

    val apiKey: String = BuildConfig.REVRAG_EMBED_API_KEY
    val embedUrl: String = BuildConfig.REVRAG_EMBED_URL
    val flowName: String = BuildConfig.REVRAG_DEMO_FLOW_NAME.ifBlank { DEFAULT_FLOW }
    val appUserId: String = BuildConfig.REVRAG_DEMO_APP_USER_ID.ifBlank { DEFAULT_APP_USER_ID }
    val webViewUrl: String = BuildConfig.REVRAG_DEMO_WEBVIEW_URL
    val startRoute: String = BuildConfig.REVRAG_DEMO_START_ROUTE.ifBlank { MonitoringRouteConfig.welcome }
    val serverBootstrapTimeoutMs: Int = BuildConfig.REVRAG_DEMO_SERVER_BOOTSTRAP_TIMEOUT_MS
    val allowedWebViewHost: String = BuildConfig.REVRAG_DEMO_WEBVIEW_ALLOWED_HOST.trim()
    val prefetchRouteComponents: Boolean = BuildConfig.REVRAG_DEMO_PREFETCH_ROUTE_COMPONENTS
    val snapshotOfflineDefault: Boolean = BuildConfig.REVRAG_DEMO_SNAPSHOT_OFFLINE
    val snapshotAutoRecoverMs: Int = BuildConfig.REVRAG_DEMO_SNAPSHOT_AUTO_RECOVER_MS
    val snapshotSimulateBackend: Boolean = BuildConfig.REVRAG_DEMO_SNAPSHOT_SIMULATE_BACKEND
    val autofill: Boolean = BuildConfig.REVRAG_DEMO_AUTOFILL
}

class MonitoringApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        SemanticSeeds.installAll()

        OfflineQueue.initialize(
            context = this,
            defaultOffline = MonitoringConfig.snapshotOfflineDefault,
            simulateBackend = MonitoringConfig.snapshotSimulateBackend,
        )

        EmbedSDK.initialize(
            context = this,
            apiKey = MonitoringConfig.apiKey,
            embedUrl = MonitoringConfig.embedUrl,
        ) { result ->
            if (!result.success) return@initialize
            EmbedSDK.setCurrentFlow(MonitoringConfig.flowName)

            EmbedSDK.event(
                EventKeys.USER_DATA,
                mapOf(
                    "app_user_id" to MonitoringConfig.appUserId,
                    "flow" to MonitoringConfig.flowName,
                    "platform" to "android_kotlin_monitoring",
                    "initial_route" to MonitoringRouteConfig.welcome,
                    "named_routes_discovered_from_app_shell" to MonitoringRouteConfig.declaredRoutes.toList(),
                ),
            )

            EmbedSDK.event(
                EventKeys.ANALYTICS_DATA,
                mapOf(
                    "event_name" to "app_routes_catalog",
                    "routes" to emptyList<String>(),
                    "route_edges" to emptyList<Map<String, String>>(),
                    "route_count" to 0,
                    "source" to "session.reset.startup",
                    "flow" to MonitoringConfig.flowName,
                ),
            )

            // Coming back online ⇒ flush any persisted offline events.
            OfflineQueue.flushNow()
        }
    }
}
