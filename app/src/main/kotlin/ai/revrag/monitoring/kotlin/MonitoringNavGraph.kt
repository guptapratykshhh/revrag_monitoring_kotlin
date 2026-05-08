package ai.revrag.monitoring.kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import ai.revrag.embed.android.EmbedButtonVisibilityConfig
import ai.revrag.embed.android.EmbedProviderComposable
import ai.revrag.embed.android.EmbedRouteManager
import ai.revrag.embed.android.EmbedSDK
import ai.revrag.embed.core.events.EventKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

@Composable
fun MonitoringNavGraph(
    navController: NavHostController,
    startDestination: String,
    bootstrap: ServerBootstrapSnapshot? = null,
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: MonitoringRouteConfig.splash
    var previousRoute by remember { mutableStateOf<String?>(null) }
    var managerAllowedRoutes by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun emitWidgetTreeSnapshot(route: String) {
        val semantic = SemanticRegistry.get(route)
        val widgetTreePayload = mapOf(
            "runtimeType" to "ComposeScreen",
            "route" to route,
            "semantic" to semantic.toMap(),
        )
        val paths = ScreenCaptureStore.saveSnapshot(
            context = context,
            rootView = rootView,
            route = route,
            payload = mapOf(
                "screen_name" to route,
                "captured_at" to System.currentTimeMillis(),
                "semantic" to semantic.toMap(),
                "widget_tree" to widgetTreePayload,
            ),
            capturePng = (route == currentRoute),
        )
        val payload = mapOf(
            "screen_name" to route,
            "route" to route,
            "flow" to MonitoringConfig.flowName,
            "captured_at" to System.currentTimeMillis(),
            "semantic" to semantic.toMap(),
            "widget_tree" to widgetTreePayload,
            "snapshot_json_path" to paths.jsonPath,
            "screenshot_path" to paths.screenshotPath,
        )
        if (!OfflineQueue.maybeQueue(EventKeys.WIDGET_TREE_SNAPSHOT, payload)) {
            EmbedSDK.event(EventKeys.WIDGET_TREE_SNAPSHOT, payload)
        }
    }

    fun emitScreenState(route: String) {
        val analytics = mapOf(
            "event_name" to "screen_open",
            "screen" to route,
            "route" to route,
            "screen_name" to route,
            "flow" to MonitoringConfig.flowName,
        )
        if (!OfflineQueue.maybeQueue(EventKeys.ANALYTICS_DATA, analytics)) {
            EmbedSDK.event(EventKeys.ANALYTICS_DATA, analytics)
        }
        EmbedSDK.event(
            EventKeys.SCREEN_STATE,
            mapOf("screen" to route, "route" to route, "flow" to MonitoringConfig.flowName),
        )
    }

    LaunchedEffect(Unit) {
        while (!EmbedSDK.isInitialized()) delay(200)
        EmbedRouteManager.registerDeclaredRoutes(MonitoringRouteConfig.declaredRoutes)
        val initial = startDestination
        MonitoringRouteConfig.declaredRoutes
            .filter { it != MonitoringRouteConfig.splash && it != initial }
            .forEach { route ->
                EmbedRouteManager.registerRouteTransition(initial, route)
                EmbedRouteManager.registerRouteTransition(route, initial)
            }
    }

    // Offstage prefetch equivalent: emit a widget-tree snapshot per declared
    // route on cold start so the workspace component catalog populates from the
    // first screen, mirroring Flutter's `_prefetchOtherRoutesOffstage`.
    LaunchedEffect(Unit) {
        if (!MonitoringConfig.prefetchRouteComponents) return@LaunchedEffect
        while (!EmbedSDK.isInitialized()) delay(200)
        MonitoringRouteConfig.declaredRoutes
            .filter { it != MonitoringRouteConfig.splash }
            .forEach { route ->
                emitWidgetTreeSnapshot(route)
                delay(120)
            }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == MonitoringRouteConfig.splash) return@LaunchedEffect
        previousRoute?.takeIf { it != currentRoute }?.let {
            EmbedRouteManager.registerRouteTransition(it, currentRoute)
        }
        previousRoute = currentRoute
        EmbedRouteManager.updateRoute(currentRoute)
        emitScreenState(currentRoute)
        // Equivalent to Flutter's `WidgetsBinding.instance.endOfFrame`: let the
        // new route fully compose + draw before we grab the screenshot, so the
        // PNG matches the JSON for the same route.
        delay(220)
        emitWidgetTreeSnapshot(currentRoute)
    }

    LaunchedEffect(Unit) {
        while (true) {
            managerAllowedRoutes = withContext(Dispatchers.IO) {
                runCatching {
                    EmbedRouteManager.fetchManagerIncludedRoutes(
                        MonitoringConfig.appUserId,
                        MonitoringConfig.flowName,
                    )
                }.getOrElse { emptyList() }
            }
            delay(1_000)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(MonitoringRouteConfig.splash) {
            SplashScreen { navController.navigate(MonitoringRouteConfig.welcome) }
        }
        composable(MonitoringRouteConfig.welcome) {
            WelcomeScreen(
                bootstrap = bootstrap,
                onGoForm = { navController.navigate(MonitoringRouteConfig.form) },
                onGoBanner = { navController.navigate(MonitoringRouteConfig.banner) },
                onGoCheckout = { navController.navigate(MonitoringRouteConfig.checkout) },
                onWebView = {
                    val target = MonitoringRouteConfig.resolveWebviewRoutePath(currentRoute)
                    val payload = buildAgentHandoffPayload(
                        direction = "native_to_webview",
                        source = currentRoute,
                        target = target,
                        triggerRoute = currentRoute,
                        data = mapOf(
                            "reason" to "user_opened_webview_bridge",
                            "requested_agent_mode" to "webview",
                        ),
                    )
                    AgentHandoffController.transferFromNative(payload)
                    trackAgentHandoff(payload)
                    navController.navigate(target)
                },
                onAnalytics = { navController.navigate(MonitoringRouteConfig.analytics) },
                onSettings = { navController.navigate(MonitoringRouteConfig.settings) },
                onTimeline = { navController.navigate(MonitoringRouteConfig.timeline) },
                onDrawerShell = { navController.navigate(MonitoringRouteConfig.drawerShell) },
                onTabsShell = { navController.navigate(MonitoringRouteConfig.tabsShell) },
            )
        }
        composable(MonitoringRouteConfig.form) {
            FormScreen { navController.navigate(MonitoringRouteConfig.success) }
        }
        composable(MonitoringRouteConfig.banner) {
            BannerScreen { navController.navigate(MonitoringRouteConfig.checkout) }
        }
        composable(MonitoringRouteConfig.checkout) {
            CheckoutScreen { navController.navigate(MonitoringRouteConfig.success) }
        }
        composable(MonitoringRouteConfig.success) {
            SuccessScreen {
                navController.navigate(MonitoringRouteConfig.welcome) {
                    popUpTo(MonitoringRouteConfig.welcome) { inclusive = true }
                }
            }
        }
        composable(MonitoringRouteConfig.analytics) {
            AnalyticsScreen(
                onSettings = { navController.navigate(MonitoringRouteConfig.settings) },
                onTimeline = { navController.navigate(MonitoringRouteConfig.timeline) },
                onWebview = { navController.navigate(MonitoringRouteConfig.webviewBridge) },
            )
        }
        composable(MonitoringRouteConfig.settings) {
            SettingsScreen { navController.navigate(MonitoringRouteConfig.timeline) }
        }
        composable(MonitoringRouteConfig.timeline) {
            TimelineScreen { navController.navigate(MonitoringRouteConfig.drawerShell) }
        }
        composable(MonitoringRouteConfig.drawerShell) {
            DrawerShellScreen(
                onAnalytics = { navController.navigate(MonitoringRouteConfig.analytics) },
                onTabs = { navController.navigate(MonitoringRouteConfig.tabsShell) },
            )
        }
        composable(MonitoringRouteConfig.tabsShell) {
            TabsShellScreen(
                onAnalytics = { navController.navigate(MonitoringRouteConfig.analytics) },
                onSettings = { navController.navigate(MonitoringRouteConfig.settings) },
                onTimeline = { navController.navigate(MonitoringRouteConfig.timeline) },
            )
        }
        composable(MonitoringRouteConfig.webviewBridge) {
            WebViewBridgeScreen { target ->
                navController.navigate(
                    if (MonitoringRouteConfig.declaredRoutes.contains(target)) target
                    else MonitoringRouteConfig.resolveNativeHandoffTargetRoutePath()
                )
            }
        }
    }

    val strictAllowed = if (managerAllowedRoutes.isEmpty()) listOf("__revrag_no_routes__") else managerAllowedRoutes
    EmbedProviderComposable(
        currentScreen = currentRoute,
        appUserId = MonitoringConfig.appUserId,
        visibilityConfig = EmbedButtonVisibilityConfig(
            allowedScreens = strictAllowed,
            excludedScreens = listOf(MonitoringRouteConfig.splash),
        ),
        navController = navController,
    )
}

@Composable
private fun SplashScreen(onContinue: () -> Unit) {
    DisposableEffect(Unit) {
        SemanticRegistry.set(
            MonitoringRouteConfig.splash,
            SemanticSnapshot(
                title = "RevRag Kotlin Monitoring",
                buttons = listOf(SemanticBuilders.button("Start", kind = "elevated")),
                texts = listOf("RevRag Kotlin Monitoring", "Monitoring Active (Silent Mode)"),
            ),
        )
        onDispose { }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("RevRag Kotlin Monitoring", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Monitoring Active (Silent Mode)",
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )
        Button(onClick = onContinue) { Text("Start") }
    }
}

@Composable
private fun WelcomeScreen(
    bootstrap: ServerBootstrapSnapshot?,
    onGoForm: () -> Unit,
    onGoBanner: () -> Unit,
    onGoCheckout: () -> Unit,
    onWebView: () -> Unit,
    onAnalytics: () -> Unit,
    onSettings: () -> Unit,
    onTimeline: () -> Unit,
    onDrawerShell: () -> Unit,
    onTabsShell: () -> Unit,
) {
    DisposableEffect(Unit) {
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
                texts = listOf(
                    "Welcome",
                    "Go to Registration Form",
                    "View Content Banner",
                    "Checkout & Payment",
                    "Open Analytics Dashboard",
                ),
            ),
        )
        onDispose { }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineSmall)
        BootstrapStatusCard(initial = bootstrap)
        DebugCaptureStatusCard()
        SnapshotNetworkDebugCard()
        Button(onClick = onGoForm, modifier = Modifier.fillMaxWidth()) { Text("Go to Registration Form") }
        androidx.compose.material3.TextButton(onClick = onGoBanner, modifier = Modifier.fillMaxWidth()) {
            Text("View Content Banner")
        }
        androidx.compose.material3.FilledTonalButton(onClick = onGoCheckout, modifier = Modifier.fillMaxWidth()) {
            Text("Checkout & Payment")
        }
        androidx.compose.material3.FilledTonalButton(onClick = onAnalytics, modifier = Modifier.fillMaxWidth()) {
            Text("Open Analytics Dashboard")
        }
        androidx.compose.material3.OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open Automation Settings")
        }
        androidx.compose.material3.OutlinedButton(onClick = onTimeline, modifier = Modifier.fillMaxWidth()) {
            Text("View Incident Timeline")
        }
        androidx.compose.material3.OutlinedButton(onClick = onDrawerShell, modifier = Modifier.fillMaxWidth()) {
            Text("Open Drawer Navigation Demo")
        }
        androidx.compose.material3.OutlinedButton(onClick = onTabsShell, modifier = Modifier.fillMaxWidth()) {
            Text("Open Bottom Tabs Demo")
        }
        androidx.compose.material3.FilledTonalButton(onClick = onWebView, modifier = Modifier.fillMaxWidth()) {
            Text("POC: Transfer Native -> WebView Agent")
        }
    }
}

@Composable
private fun BootstrapStatusCard(initial: ServerBootstrapSnapshot?) {
    var snap by remember { mutableStateOf(initial) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Refresh the bootstrap snapshot every 4 s, parity with Flutter.
        while (true) {
            delay(4_000)
            snap = withContext(Dispatchers.IO) { ServerBootstrapLoader.load() }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Server bootstrap", style = MaterialTheme.typography.titleSmall)
            val ok = snap?.error == null
            Text(
                "Status: " + when {
                    snap == null -> "loading…"
                    !ok -> "error: ${snap!!.error}"
                    else -> "ok elapsed=${snap!!.elapsedMs}ms"
                }
            )
            snap?.let {
                Text("Discovered: routes=${it.discoveredRouteCount} edges=${it.discoveredEdgeCount}")
                Text("Manual: routes=${it.manualRouteCount} edges=${it.manualEdgeCount}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        if (refreshing) return@OutlinedButton
                        refreshing = true
                    },
                ) { Text(if (refreshing) "Refreshing…" else "Refresh") }
            }
            if (refreshing) {
                LaunchedEffect(refreshing) {
                    snap = withContext(Dispatchers.IO) { ServerBootstrapLoader.load() }
                    refreshing = false
                }
            }
        }
    }
}

@Composable
private fun DebugCaptureStatusCard() {
    val status by ScreenCaptureStore.status.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Capture", style = MaterialTheme.typography.titleSmall)
            Text("Captures: ${status.captureCount}")
            Text("Last screen: ${status.lastScreen ?: "-"}")
            Text("JSON: ${status.lastJsonPath ?: "(pending)"}")
            Text("Screenshot: ${status.lastScreenshotPath ?: "(pending)"}")
        }
    }
}

@Composable
private fun SnapshotNetworkDebugCard() {
    val state by OfflineQueue.state.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Snapshot Network Debug", style = MaterialTheme.typography.titleSmall)
            Text(
                "Mode: " + when {
                    state.offline && state.simulateBackend -> "Offline + simulated"
                    state.offline -> "Offline (queueing)"
                    else -> "Online"
                }
            )
            Text("Queue: snapshots=${state.snapshotQueueSize}, analytics=${state.analyticsQueueSize}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { OfflineQueue.setOffline(true) }) {
                    Text("Go Offline")
                }
                androidx.compose.material3.FilledTonalButton(onClick = {
                    OfflineQueue.setOffline(false)
                    OfflineQueue.flushNow()
                }) { Text("Online + Flush") }
            }
        }
    }
}

@Composable
private fun FormScreen(onSubmit: () -> Unit) {
    val autofill = MonitoringConfig.autofill
    var name by remember { mutableStateOf(if (autofill) "Demo User" else "") }
    var phone by remember { mutableStateOf(if (autofill) "9999999999" else "") }
    var email by remember { mutableStateOf(if (autofill) "demo@revrag.ai" else "") }
    var password by remember { mutableStateOf(if (autofill) "Hunter2!" else "") }

    DisposableEffect(name, phone, email, password) {
        SemanticRegistry.set(
            MonitoringRouteConfig.form,
            SemanticSnapshot(
                title = "Registration Form",
                fields = listOf(
                    SemanticBuilders.field("Full name", name),
                    SemanticBuilders.field("Phone", phone),
                    SemanticBuilders.field("Email", email),
                    SemanticBuilders.field("Password", password, secureOverride = true),
                ),
                buttons = listOf(SemanticBuilders.button("Submit", kind = "elevated")),
                texts = listOf("Registration Form"),
            ),
        )
        onDispose { }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Registration Form", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            phone,
            { phone = it },
            label = { Text("Phone") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            email,
            { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text("Submit") }
    }
}

@Composable
private fun BannerScreen(onCheckout: () -> Unit) {
    val items = remember { (1..8).map { "Offer Card #$it" } }
    DisposableEffect(items) {
        SemanticRegistry.set(
            MonitoringRouteConfig.banner,
            SemanticSnapshot(
                title = "Content Banner",
                lists = items.map { SemanticBuilders.listItem(it) },
                buttons = listOf(SemanticBuilders.button("Checkout & Payment", kind = "elevated")),
                texts = listOf("Content Banner") + items,
            ),
        )
        onDispose { }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Content Banner", style = MaterialTheme.typography.headlineSmall)
        items.forEach { label ->
            Card(modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.padding(12.dp)) }
        }
        Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) { Text("Checkout & Payment") }
    }
}

@Composable
private fun CheckoutScreen(onPay: () -> Unit) {
    var amount by remember { mutableStateOf("4999") }
    var coupon by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    DisposableEffect(amount, coupon, card) {
        SemanticRegistry.set(
            MonitoringRouteConfig.checkout,
            SemanticSnapshot(
                title = "Payment Checkout",
                fields = listOf(
                    SemanticBuilders.field("Amount", amount),
                    SemanticBuilders.field("Coupon", coupon),
                    SemanticBuilders.field("Card", card),
                ),
                buttons = listOf(SemanticBuilders.button("Pay Now", kind = "elevated")),
                texts = listOf("Payment Checkout"),
            ),
        )
        onDispose { }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Payment Checkout", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            amount,
            { amount = it },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(coupon, { coupon = it }, label = { Text("Coupon") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            card,
            { card = it },
            label = { Text("Card") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) { Text("Pay Now") }
    }
}

@Composable
private fun SuccessScreen(onBackHome: () -> Unit) {
    DisposableEffect(Unit) {
        SemanticRegistry.set(
            MonitoringRouteConfig.success,
            SemanticSnapshot(
                title = "Form Submitted Successfully",
                buttons = listOf(SemanticBuilders.button("Back to Welcome", kind = "elevated")),
                texts = listOf("Form Submitted Successfully"),
            ),
        )
        onDispose { }
    }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text("Form Submitted Successfully", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onBackHome, modifier = Modifier.padding(top = 12.dp)) { Text("Back to Welcome") }
    }
}

@Composable
private fun AnalyticsScreen(onSettings: () -> Unit, onTimeline: () -> Unit, onWebview: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var criticalOnly by remember { mutableStateOf(false) }
    val cards = when (tab) {
        0 -> if (criticalOnly) listOf("Checkout Drop 17.2% (critical)", "OTP Timeout 8.4% (critical)")
        else listOf("Checkout Drop 17.2% (critical)", "OTP Timeout 8.4% (critical)", "Banner CTR +2.1%")
        1 -> listOf("P95 API latency 684ms", "P99 widget render 410ms", "Network retries 12")
        else -> listOf("Transcript confidence 93%", "Context precision 89%", "Route-match misses 4")
    }
    DisposableEffect(tab, criticalOnly, cards) {
        SemanticRegistry.set(
            MonitoringRouteConfig.analytics,
            SemanticSnapshot(
                title = "Analytics Dashboard",
                checkboxes = listOf(SemanticBuilders.checkbox("Critical only", criticalOnly)),
                lists = cards.map { SemanticBuilders.listItem(it) },
                buttons = listOf(
                    SemanticBuilders.button("Open Settings", kind = "outlined"),
                    SemanticBuilders.button("View Timeline", kind = "filled_tonal"),
                    SemanticBuilders.button("Transfer -> WebView", kind = "outlined"),
                ),
                texts = listOf("Analytics Dashboard", "Funnels", "Latency", "Quality") + cards,
            ),
        )
        onDispose { }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Analytics Dashboard", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Funnels") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Latency") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Quality") })
        }
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Critical only")
            Switch(checked = criticalOnly, onCheckedChange = { criticalOnly = it }, modifier = Modifier.padding(start = 8.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cards.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) { Text(item, modifier = Modifier.padding(12.dp)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Open Settings") }
            androidx.compose.material3.FilledTonalButton(onClick = onTimeline, modifier = Modifier.weight(1f)) { Text("View Timeline") }
            androidx.compose.material3.OutlinedButton(onClick = onWebview, modifier = Modifier.weight(1f)) { Text("Transfer -> WebView") }
        }
    }
}

@Composable
private fun SettingsScreen(onTimeline: () -> Unit) {
    var autoEscalation by remember { mutableStateOf(true) }
    var notifySlack by remember { mutableStateOf(true) }
    var confidence by remember { mutableStateOf(0.75f) }
    DisposableEffect(autoEscalation, notifySlack, confidence) {
        SemanticRegistry.set(
            MonitoringRouteConfig.settings,
            SemanticSnapshot(
                title = "Automation Settings",
                checkboxes = listOf(
                    SemanticBuilders.checkbox("Auto escalation", autoEscalation, kind = "switch"),
                    SemanticBuilders.checkbox("Slack notifications", notifySlack, kind = "switch"),
                ),
                fields = listOf(
                    SemanticBuilders.field(
                        "Confidence Threshold",
                        "${(confidence * 100).toInt()}%",
                        kind = "slider",
                        secureOverride = false,
                    ),
                ),
                buttons = listOf(SemanticBuilders.button("Go To Incident Timeline", kind = "elevated")),
                texts = listOf(
                    "Automation Settings",
                    "Auto escalation",
                    "Slack notifications",
                    "Advanced Conditions",
                ),
            ),
        )
        onDispose { }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Automation Settings", style = MaterialTheme.typography.headlineSmall)
        Row { Text("Auto escalation"); Switch(checked = autoEscalation, onCheckedChange = { autoEscalation = it }) }
        Row { Text("Slack notifications"); Switch(checked = notifySlack, onCheckedChange = { notifySlack = it }) }
        Text("Confidence Threshold ${(confidence * 100).toInt()}%")
        androidx.compose.material3.Slider(value = confidence, onValueChange = { confidence = it }, valueRange = 0.4f..0.95f)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Advanced Conditions")
                Text("A: Route mismatch + high latency")
                Text("B: Error burst > 5 in 2 min")
                Text("C: Intent drift confidence < 60%")
            }
        }
        Button(onClick = onTimeline, modifier = Modifier.fillMaxWidth()) { Text("Go To Incident Timeline") }
    }
}

@Composable
private fun TimelineScreen(onDrawerShell: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    val steps = listOf(
        "Event Detected - Checkout latency crossed 600ms threshold",
        "Context Snapshot - Widget tree + screenshot collected",
        "Routing Decision - Escalated to payment QA squad",
        "Resolution - Rollback complete",
    )
    DisposableEffect(step) {
        SemanticRegistry.set(
            MonitoringRouteConfig.timeline,
            SemanticSnapshot(
                title = "Incident Timeline",
                lists = steps.map { SemanticBuilders.listItem(it) },
                buttons = listOf(
                    SemanticBuilders.button("Next", kind = "text"),
                    SemanticBuilders.button("Back", kind = "text"),
                    SemanticBuilders.button("Open Drawer Navigation Demo", kind = "elevated"),
                ),
                texts = listOf("Incident Timeline") + steps,
            ),
        )
        onDispose { }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Incident Timeline", style = MaterialTheme.typography.headlineSmall)
        steps.forEachIndexed { idx, item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("${if (idx == step) "->" else "  "} $item", modifier = Modifier.padding(12.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = { step = (step + 1).coerceAtMost(3) }) { Text("Next") }
            androidx.compose.material3.TextButton(onClick = { step = (step - 1).coerceAtLeast(0) }) { Text("Back") }
        }
        Button(onClick = onDrawerShell, modifier = Modifier.fillMaxWidth()) { Text("Open Drawer Navigation Demo") }
    }
}

@Composable
private fun DrawerShellScreen(onAnalytics: () -> Unit, onTabs: () -> Unit) {
    DisposableEffect(Unit) {
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
                texts = listOf(
                    "Drawer Navigation Hub",
                    "Open the drawer from top-left and navigate to connected routes.",
                ),
            ),
        )
        onDispose { }
    }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Drawer Navigation Hub", style = MaterialTheme.typography.headlineSmall)
        Text("Open the drawer from top-left and navigate to connected routes.")
        androidx.compose.material3.OutlinedButton(onClick = onAnalytics, modifier = Modifier.fillMaxWidth()) { Text("Analytics") }
        androidx.compose.material3.OutlinedButton(onClick = onTabs, modifier = Modifier.fillMaxWidth()) { Text("Bottom Tabs Demo") }
    }
}

@Composable
private fun TabsShellScreen(onAnalytics: () -> Unit, onSettings: () -> Unit, onTimeline: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    DisposableEffect(index) {
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
                texts = listOf("Bottom Tabs Demo", "Home", "Reports", "Profile"),
            ),
        )
        onDispose { }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        when (index) {
            0 -> Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Home Tab", style = MaterialTheme.typography.titleMedium)
                Text("Overview and quick actions")
            }
            1 -> Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Reports Tab", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onAnalytics) { Text("Open Analytics") }
                androidx.compose.material3.OutlinedButton(onClick = onTimeline) { Text("Open Timeline") }
            }
            else -> Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Profile Tab", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onSettings) { Text("Settings") }
            }
        }
        TabRow(selectedTabIndex = index) {
            Tab(selected = index == 0, onClick = { index = 0 }, text = { Text("Home") })
            Tab(selected = index == 1, onClick = { index = 1 }, text = { Text("Reports") })
            Tab(selected = index == 2, onClick = { index = 2 }, text = { Text("Profile") })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewBridgeScreen(onNativeHandoff: (String) -> Unit) {
    val context = LocalContext.current
    var micStatus by remember {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) "granted" else "needed")
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micStatus = if (granted) "granted" else "denied"
        EmbedSDK.event(
            EventKeys.ANALYTICS_DATA,
            mapOf(
                "event_name" to if (granted) "microphone_permission_allowed" else "microphone_permission_denied",
                "route" to MonitoringRouteConfig.webviewBridge,
                "flow" to MonitoringConfig.flowName,
            ),
        )
    }
    LaunchedEffect(Unit) {
        if (micStatus == "needed") {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        SemanticRegistry.set(
            MonitoringRouteConfig.webviewBridge,
            SemanticSnapshot(
                title = "WebView Bridge",
                buttons = listOf(SemanticBuilders.button("Back to native", kind = "elevated")),
                texts = listOf("WebView Bridge", "Microphone status: $micStatus"),
            ),
        )
        onDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mic permission status banner — parity with Flutter's status banner.
        val bannerText = when (micStatus) {
            "granted" -> "Microphone permission: GRANTED"
            "denied" -> "Microphone permission: DENIED — open settings to enable mic"
            else -> "Microphone permission: REQUESTING…"
        }
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bannerText, style = MaterialTheme.typography.bodyMedium)
                if (micStatus == "denied") {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) { Text("Open settings") }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            val req = request ?: return
                            val hasMic = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                req.grant(req.resources)
                                EmbedSDK.event(
                                    EventKeys.ANALYTICS_DATA,
                                    mapOf(
                                        "event_name" to "microphone_permission_allowed",
                                        "route" to MonitoringRouteConfig.webviewBridge,
                                    ),
                                )
                            } else {
                                req.deny()
                                EmbedSDK.event(
                                    EventKeys.ANALYTICS_DATA,
                                    mapOf(
                                        "event_name" to "microphone_permission_denied",
                                        "route" to MonitoringRouteConfig.webviewBridge,
                                    ),
                                )
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val pending = AgentHandoffController.consumePendingForWeb() ?: return
                            val json = JSONObject(pending).toString()
                            view?.evaluateJavascript(
                                "(function(){var detail=$json;if(typeof window.onNativeTransfer==='function'){window.onNativeTransfer(detail);}window.dispatchEvent(new CustomEvent('revrag:bridge',{detail:detail}));})();",
                                null,
                            )
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface fun postMessage(raw: String) {
                            if (raw.length > 32 * 1024) return
                            val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
                            if (payload.optInt("version", -1) != 1) return
                            val envelope = if (payload.optString("action") == "transferToNativeAgent")
                                (payload.optJSONObject("payload") ?: JSONObject()).toMap()
                            else payload.toMap()
                            if ((envelope["direction"] as? String) != "webview_to_native") return
                            val target = (envelope["target"] as? String)
                                ?: MonitoringRouteConfig.resolveNativeHandoffTargetRoutePath()
                            AgentHandoffController.transferFromWeb(envelope)
                            trackAgentHandoff(envelope)
                            onNativeHandoff(target)
                        }
                    }, "NativeBridge")
                    if (MonitoringConfig.webViewUrl.isNotBlank()) {
                        if (MonitoringConfig.allowedWebViewHost.isNotBlank()) {
                            val host = runCatching { android.net.Uri.parse(MonitoringConfig.webViewUrl).host }.getOrNull()
                            if (!host.equals(MonitoringConfig.allowedWebViewHost, true)) {
                                loadDataWithBaseURL(
                                    null,
                                    "<html><body>Blocked untrusted WebView host.</body></html>",
                                    "text/html", "UTF-8", null,
                                )
                                return@apply
                            }
                        }
                        loadUrl(MonitoringConfig.webViewUrl)
                    } else {
                        loadDataWithBaseURL(
                            null,
                            """<html><body style="font-family:sans-serif;padding:16px;"><h3>RevRag WebView Bridge</h3>
                            <button onclick='NativeBridge.postMessage(JSON.stringify({version:1,direction:"webview_to_native",timestamp:Date.now(),source:"/webview-bridge",target:"/welcome",trigger_route:"/webview-bridge",flow:"monitoring_demo",app_user_id:"kotlin_monitoring_demo_user",data:{reason:"user_completed_webview"}}))'>Back to native</button>
                            </body></html>""".trimIndent(),
                            "text/html", "UTF-8", null,
                        )
                    }
                }
            })
        }
    }
}

private fun buildAgentHandoffPayload(
    direction: String,
    source: String,
    target: String,
    triggerRoute: String,
    data: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = mapOf(
    "version" to 1,
    "direction" to direction,
    "source" to source,
    "target" to target,
    "trigger_route" to triggerRoute,
    "timestamp" to System.currentTimeMillis(),
    "flow" to MonitoringConfig.flowName,
    "app_user_id" to MonitoringConfig.appUserId,
    "data" to data,
)

private fun trackAgentHandoff(payload: Map<String, Any?>) {
    EmbedSDK.event(EventKeys.CUSTOM_EVENT, mapOf("event" to "agent_handoff") + payload)
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val out = mutableMapOf<String, Any?>()
    keys().forEach { out[it] = opt(it) }
    return out
}
