package ai.revrag.monitoring.kotlin

import android.content.Context
import android.content.SharedPreferences
import ai.revrag.embed.android.EmbedSDK
import ai.revrag.embed.core.events.EventKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mirrors the Flutter SDK's `widget_tree_snapshot_queue` /
 * `analytics_event_queue` behavior: when the demo is in offline mode (or when
 * the SDK could not flush yet), `WIDGET_TREE_SNAPSHOT` and `ANALYTICS_DATA`
 * events are persisted to SharedPreferences and retried every
 * `RETRY_INTERVAL_MS` until offline mode is turned off.
 */
object OfflineQueue {
    private const val PREFS = "revrag_offline_queue"
    private const val KEY_OFFLINE = "offline_mode"
    private const val KEY_SIMULATE = "simulate_backend"
    private const val KEY_SNAPSHOTS = "widget_tree_snapshot_queue"
    private const val KEY_ANALYTICS = "analytics_event_queue"
    private const val RETRY_INTERVAL_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryJob: Job? = null
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(OfflineState())
    val state: StateFlow<OfflineState> = _state

    data class OfflineState(
        val offline: Boolean = false,
        val simulateBackend: Boolean = false,
        val snapshotQueueSize: Int = 0,
        val analyticsQueueSize: Int = 0,
    )

    fun initialize(context: Context, defaultOffline: Boolean, simulateBackend: Boolean) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs!!.contains(KEY_OFFLINE)) prefs!!.edit().putBoolean(KEY_OFFLINE, defaultOffline).apply()
        prefs!!.edit().putBoolean(KEY_SIMULATE, simulateBackend).apply()
        publishState()
        startRetryLoop()
    }

    fun setOffline(offline: Boolean) {
        prefs?.edit()?.putBoolean(KEY_OFFLINE, offline)?.apply()
        publishState()
        if (!offline) flushNow()
    }

    fun isOffline(): Boolean = prefs?.getBoolean(KEY_OFFLINE, false) ?: false

    private fun isSimulating(): Boolean = prefs?.getBoolean(KEY_SIMULATE, false) ?: false

    /**
     * Returns true if the event should be SUPPRESSED (handled by the queue).
     * If false, the caller must dispatch it through the SDK as usual.
     */
    fun maybeQueue(eventKey: EventKeys, payload: Map<String, Any?>): Boolean {
        if (!isOffline()) return false
        if (isSimulating()) return true
        val storeKey = when (eventKey) {
            EventKeys.WIDGET_TREE_SNAPSHOT -> KEY_SNAPSHOTS
            EventKeys.ANALYTICS_DATA -> KEY_ANALYTICS
            else -> return false
        }
        val arr = JSONArray(prefs?.getString(storeKey, "[]") ?: "[]")
        val record = JSONObject().apply {
            put("event", eventKey.name)
            put("queued_at", System.currentTimeMillis())
            put("payload", JSONObject(payload.toJsonSafeMap()))
        }
        arr.put(record)
        prefs?.edit()?.putString(storeKey, arr.toString())?.apply()
        publishState()
        return true
    }

    fun flushNow() {
        scope.launch { flushAll() }
    }

    private suspend fun flushAll() {
        if (isOffline()) return
        flushQueue(KEY_SNAPSHOTS)
        flushQueue(KEY_ANALYTICS)
        publishState()
    }

    private fun flushQueue(storeKey: String) {
        val raw = prefs?.getString(storeKey, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        if (arr.length() == 0) return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val eventName = obj.optString("event")
            if (eventName.isBlank()) continue
            val key = runCatching { EventKeys.valueOf(eventName) }.getOrNull() ?: continue
            val payload = obj.optJSONObject("payload") ?: continue
            val map = payload.toMutableMap()
            map["__queued_at"] = obj.optLong("queued_at")
            map["__source"] = "offline_flush"
            EmbedSDK.event(key, map)
        }
        prefs?.edit()?.putString(storeKey, "[]")?.apply()
    }

    private fun startRetryLoop() {
        retryJob?.cancel()
        retryJob = scope.launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                if (!isOffline()) flushAll()
            }
        }
    }

    private fun publishState() {
        val snapshotsCount = JSONArray(prefs?.getString(KEY_SNAPSHOTS, "[]") ?: "[]").length()
        val analyticsCount = JSONArray(prefs?.getString(KEY_ANALYTICS, "[]") ?: "[]").length()
        _state.value = OfflineState(
            offline = isOffline(),
            simulateBackend = isSimulating(),
            snapshotQueueSize = snapshotsCount,
            analyticsQueueSize = analyticsCount,
        )
    }

    private fun JSONObject.toMutableMap(): MutableMap<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        keys().forEach { out[it] = opt(it) }
        return out
    }

    private fun Map<String, Any?>.toJsonSafeMap(): Map<String, Any?> = mapValues { (_, v) -> normalize(v) }

    private fun normalize(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject(v.entries.associate { (k, vv) -> k.toString() to normalize(vv) })
        is List<*> -> JSONArray(v.map { normalize(it) })
        is Number, is Boolean, is String -> v
        else -> v.toString()
    }
}
