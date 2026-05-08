package ai.revrag.monitoring.kotlin

object AgentHandoffController {
    private var latestFromNative: Map<String, Any?>? = null
    private var latestFromWeb: Map<String, Any?>? = null
    private var pendingForWeb: Map<String, Any?>? = null

    fun transferFromNative(payload: Map<String, Any?>) {
        latestFromNative = payload
        pendingForWeb = payload
    }

    fun transferFromWeb(payload: Map<String, Any?>) {
        latestFromWeb = payload
    }

    fun consumePendingForWeb(): Map<String, Any?>? {
        val payload = pendingForWeb
        pendingForWeb = null
        return payload
    }

    fun latestNative(): Map<String, Any?>? = latestFromNative
    fun latestWeb(): Map<String, Any?>? = latestFromWeb
}

