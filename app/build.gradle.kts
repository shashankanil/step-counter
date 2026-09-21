import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun configString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val apiUrl = local.getProperty("API_BASE_URL", "").trim()

android {
    namespace = "dev.stepcounter"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.stepcounter"
        minSdk = 26
        targetSdk = 37
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", configString(local.getProperty("GOOGLE_WEB_CLIENT_ID", "").trim()))
        buildConfigField("String", "API_BASE_URL", configString(apiUrl.ifBlank { "http://10.0.2.2:3000" }))
        buildConfigField("boolean", "API_CONFIGURED", apiUrl.isNotBlank().toString())
        versionCode = 2
        versionName = "0.2.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
