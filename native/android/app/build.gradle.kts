import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

// Local credentials stay outside Git; CI injects the same identity from Secrets.
val releaseProperties = Properties().apply {
    rootProject.file(".signing/release.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun releaseCredential(environment: String, property: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: releaseProperties.getProperty(property)?.takeIf { it.isNotBlank() }
val releaseStoreFile = releaseCredential("ANDROID_RELEASE_STORE_FILE", "storeFile")?.let { rootProject.file(it) }
val releaseStorePassword = releaseCredential("ANDROID_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseCredential("ANDROID_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseCredential("ANDROID_RELEASE_KEY_PASSWORD", "keyPassword")
val releaseSigningReady = releaseStoreFile?.isFile == true &&
    listOf(releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.zoomzhao.racingclub"
    compileSdk = 36
    defaultConfig { applicationId = "com.zoomzhao.racingclub"; minSdk = 26; targetSdk = 36; versionCode = 7; versionName = "0.1.6"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        if (releaseSigningReady) create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
// An unsigned or debug-signed fallback must never be presented as an installable release.
val verifyReleaseSigning by tasks.registering {
    doLast { check(releaseSigningReady) { "Release signing is missing. Configure .signing/release.properties or ANDROID_RELEASE_* environment variables." } }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM tests; android.jar only ships throwing stubs.
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
