package ai.revrag.monitoring.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import ai.revrag.embed.android.EmbedSDK
import ai.revrag.embed.core.events.EventKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var backgroundResetJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BootstrapHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        backgroundResetJob?.cancel()
        backgroundResetJob = null
        // Returning to foreground: flush any offline queue immediately.
        OfflineQueue.flushNow()
    }

    override fun onStop() {
        super.onStop()
        backgroundResetJob?.cancel()
        backgroundResetJob = lifecycleScope.launch {
            delay(25_000)
            EmbedSDK.event(
                EventKeys.ANALYTICS_DATA,
                mapOf(
                    "event_name" to "app_routes_catalog",
                    "routes" to emptyList<String>(),
                    "route_edges" to emptyList<Map<String, String>>(),
                    "route_count" to 0,
                    "source" to "session.reset.background_timeout",
                    "flow" to MonitoringConfig.flowName,
                ),
            )
        }
    }

    override fun onDestroy() {
        // Force-kill / detach: send the immediate detached reset, mirroring
        // Flutter's `_publishSessionRouteCatalogReset(reason: 'app_detached')`.
        if (isFinishing) {
            try {
                EmbedSDK.event(
                    EventKeys.ANALYTICS_DATA,
                    mapOf(
                        "event_name" to "app_routes_catalog",
                        "routes" to emptyList<String>(),
                        "route_edges" to emptyList<Map<String, String>>(),
                        "route_count" to 0,
                        "source" to "session.reset.app_detached",
                        "flow" to MonitoringConfig.flowName,
                    ),
                )
            } catch (_: Throwable) {
                // ignore — process is dying.
            }
        }
        super.onDestroy()
    }
}

@Composable
private fun BootstrapHost() {
    var snapshot by remember { mutableStateOf<ServerBootstrapSnapshot?>(null) }
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        snapshot = withContext(Dispatchers.IO) { ServerBootstrapLoader.load() }
    }
    if (snapshot == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text("Loading monitoring bootstrap...")
        }
        return
    }
    val resolved = snapshot!!
    val resolvedStartRoute = resolved.prioritizedRoutes.firstOrNull {
        MonitoringRouteConfig.declaredRoutes.contains(it)
    } ?: MonitoringConfig.startRoute
    MonitoringNavGraph(
        navController = navController,
        startDestination = resolvedStartRoute,
        bootstrap = resolved,
    )
}
