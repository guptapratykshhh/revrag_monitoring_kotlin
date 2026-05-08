plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.revrag.monitoring.kotlin"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.revrag.monitoring.kotlin"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "REVRAG_EMBED_API_KEY", "\"key_embed_revrag_test_key\"")
        // Default points at the Android emulator's host loopback; override
        // with `-PREVRAG_EMBED_URL=http://<lan-ip>:8000` for real devices.
        val embedUrl = (project.findProperty("REVRAG_EMBED_URL") as? String)?.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8000"
        buildConfigField("String", "REVRAG_EMBED_URL", "\"$embedUrl\"")
        buildConfigField("String", "REVRAG_DEMO_FLOW_NAME", "\"monitoring_demo\"")
        buildConfigField("String", "REVRAG_DEMO_APP_USER_ID", "\"kotlin_monitoring_demo_user\"")
        buildConfigField("String", "REVRAG_DEMO_WEBVIEW_URL", "\"\"")
        buildConfigField("String", "REVRAG_DEMO_WEBVIEW_ROUTE_PATH", "\"\"")
        buildConfigField("String", "REVRAG_DEMO_NATIVE_HANDOFF_TARGET_ROUTE_PATH", "\"\"")
        buildConfigField("String", "REVRAG_DEMO_START_ROUTE", "\"/welcome\"")
        buildConfigField("int", "REVRAG_DEMO_SERVER_BOOTSTRAP_TIMEOUT_MS", "6000")
        buildConfigField("String", "REVRAG_DEMO_WEBVIEW_ALLOWED_HOST", "\"\"")
        buildConfigField("boolean", "REVRAG_DEMO_PREFETCH_ROUTE_COMPONENTS", "true")
        buildConfigField("boolean", "REVRAG_DEMO_SNAPSHOT_OFFLINE", "false")
        buildConfigField("int", "REVRAG_DEMO_SNAPSHOT_AUTO_RECOVER_MS", "0")
        buildConfigField("boolean", "REVRAG_DEMO_SNAPSHOT_SIMULATE_BACKEND", "false")
        buildConfigField("boolean", "REVRAG_DEMO_AUTOFILL", "false")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Sign the release variant with the debug keystore so the APK is installable
    // on any device without requiring a production keystore.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    // Resolved from includeBuild("../embed-native") in settings.gradle.kts.
    implementation("ai.revrag:android:1.0.5")

    testImplementation("junit:junit:4.13.2")
}

