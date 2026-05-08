package ai.revrag.monitoring.kotlin

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

data class CatalogPayload(
    val routes: List<String> = emptyList(),
    val edges: List<Map<String, String>> = emptyList(),
    val source: String? = null,
)

data class ServerBootstrapSnapshot(
    val discovered: CatalogPayload = CatalogPayload(),
    val manual: CatalogPayload = CatalogPayload(),
    val fetchedAt: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0L,
    val error: String? = null,
) {
    val prioritizedRoutes: List<String> = (manual.routes + discovered.routes).distinct()
    val discoveredRouteCount: Int get() = discovered.routes.size
    val discoveredEdgeCount: Int get() = discovered.edges.size
    val manualRouteCount: Int get() = manual.routes.size
    val manualEdgeCount: Int get() = manual.edges.size
}

object ServerBootstrapLoader {
    suspend fun load(): ServerBootstrapSnapshot {
        val started = System.currentTimeMillis()
        return try {
            val discovered = fetchCatalog("discovered")
            val manual = fetchCatalog("manual")
            ServerBootstrapSnapshot(
                discovered = discovered,
                manual = manual,
                fetchedAt = System.currentTimeMillis(),
                elapsedMs = System.currentTimeMillis() - started,
            )
        } catch (t: Throwable) {
            ServerBootstrapSnapshot(
                error = t.message ?: t::class.java.simpleName,
                elapsedMs = System.currentTimeMillis() - started,
            )
        }
    }

    private fun fetchCatalog(catalogKind: String): CatalogPayload {
        val base = MonitoringConfig.embedUrl.trimEnd('/')
        if (base.isBlank()) return CatalogPayload()
        val encodedUser = URLEncoder.encode(MonitoringConfig.appUserId, "UTF-8")
        val encodedFlow = URLEncoder.encode(MonitoringConfig.flowName, "UTF-8")
        val url = URL("$base/embedded-agent/route-catalog/$encodedUser?catalog_kind=$catalogKind&flow=$encodedFlow")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = MonitoringConfig.serverBootstrapTimeoutMs
            readTimeout = MonitoringConfig.serverBootstrapTimeoutMs
            setRequestProperty("X-Revrag-Embedded-Key", MonitoringConfig.apiKey)
            setRequestProperty("Accept", "application/json")
        }
        return runCatching {
            conn.inputStream.bufferedReader().use { reader ->
                parseCatalog(JSONArray(reader.readText()), catalogKind)
            }
        }.getOrElse { CatalogPayload() }
    }

    private fun parseCatalog(rows: JSONArray, catalogKind: String): CatalogPayload {
        val routeSet = linkedSetOf<String>()
        val edgeSet = linkedSetOf<Pair<String, String>>()
        var source: String? = null
        for (idx in 0 until rows.length()) {
            val row = rows.optJSONObject(idx) ?: continue
            val catalog = row.optJSONObject("catalog") ?: JSONObject()
            val rowSource = catalog.optString("source", "").takeIf { it.isNotBlank() }
            // Drop SDK-published rows when reading manual catalog (parity with Flutter SDK guard).
            if (catalogKind == "manual" && rowSource == "material_app.routes") continue
            if (source == null) source = rowSource
            val routes = catalog.optJSONArray("routes") ?: JSONArray()
            for (j in 0 until routes.length()) {
                val route = routes.optString(j).trim()
                if (route.isNotEmpty()) routeSet.add(route)
            }
            val edges = catalog.optJSONArray("route_edges") ?: JSONArray()
            for (j in 0 until edges.length()) {
                val edge = edges.optJSONObject(j) ?: continue
                val src = edge.optString("source").trim()
                val dst = edge.optString("target").trim()
                if (src.isNotEmpty() && dst.isNotEmpty() && src != dst) {
                    edgeSet.add(src to dst)
                }
            }
        }
        return CatalogPayload(
            routes = routeSet.toList(),
            edges = edgeSet.map { (s, t) -> mapOf("source" to s, "target" to t) },
            source = source,
        )
    }
}
