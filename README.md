# RevRag Monitoring Kotlin

Standalone Android monitoring demo app (Kotlin/Compose), parallel to `revrag_monitoring` (Flutter).

## Goal

Replicate Auto-DOM monitoring features from Flutter:

- Dynamic route discovery and route-edge publishing (`app_routes_catalog`)
- Screen-open analytics
- Semantic widget-tree snapshot publishing (`widget_tree_snapshot`)
- Platform allowlist-driven embed visibility
- Native <-> WebView handoff envelope (`agent_handoff`)

while keeping SDK-level dynamic behavior in `embed-native`.

## Current status

- App scaffold created as a separate folder (not inside `embed-native/examples`)
- Monitoring navigation/screens implemented
- Dynamic route + edge publishing implemented
- Semantic snapshot event publishing implemented
- WebView bridge fallback page + handoff event implemented

## Config (BuildConfig fields)

- `REVRAG_EMBED_API_KEY`
- `REVRAG_EMBED_URL`
- `REVRAG_DEMO_FLOW_NAME`
- `REVRAG_DEMO_APP_USER_ID`
- `REVRAG_DEMO_WEBVIEW_URL`

