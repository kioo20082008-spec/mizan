import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase config is read from local.properties (gitignored) or environment
// variables so the repo/CI can build without any secrets, and the cloud-backup
// feature simply stays disabled until the values are supplied. The manual
// FirebaseOptions init in CloudBackup means the google-services plugin (which
// hard-fails on a missing google-services.json) is not used.
val firebaseProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun firebaseConfig(propKey: String, envKey: String): String =
    (firebaseProps.getProperty(propKey) ?: System.getenv(envKey) ?: "").trim()

android {
    namespace = "com.mizan.money"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mizan.money"
        minSdk = 26
        targetSdk = 34
        // GITHUB_RUN_NUMBER increments on every workflow run (GitHub Actions sets
        // it automatically), so every APK GitHub Actions builds gets a distinct,
        // strictly increasing versionCode without needing full git history in CI.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exposed via BuildConfig for CloudBackup. Blank values mean "cloud
        // backup not configured" — the UI hides the feature instead of crashing.
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseConfig("firebase.projectId", "FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseConfig("firebase.appId", "FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseConfig("firebase.apiKey", "FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${firebaseConfig("firebase.storageBucket", "FIREBASE_STORAGE_BUCKET")}\"")
        buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", "\"${firebaseConfig("firebase.messagingSenderId", "FIREBASE_MESSAGING_SENDER_ID")}\"")
        // Google Sign-In server (web) client id — from the Firebase console's
        // Authentication > Google > Web SDK configuration. Blank hides the
        // Google button and leaves only email/password sign-in.
        buildConfigField("String", "FIREBASE_GOOGLE_CLIENT_ID", "\"${firebaseConfig("firebase.googleServerClientId", "FIREBASE_GOOGLE_CLIENT_ID")}\"")
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use a real release keystore when available, otherwise fall back to the
            // debug config so the release build still assembles (e.g. in CI).
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    // Provides androidx.lifecycle.compose.LocalLifecycleOwner, the non-deprecated
    // replacement for androidx.compose.ui.platform.LocalLifecycleOwner.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Daily background check for upcoming bill reminders (BillReminderWorker) —
    // WorkManager survives process death/reboot, unlike a plain coroutine/alarm.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    // Optional cloud backup/restore to Firestore (see CloudBackup). Initialized
    // manually from BuildConfig values, so no google-services plugin/json is
    // required and the app builds and runs unchanged when unconfigured.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // Google Sign-In via Credential Manager (the replacement for the deprecated
    // GoogleSignInClient). Produces an ID token that is exchanged for a
    // Firebase session; also works without the google-services plugin.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for local unit tests: the Android "mockable" jar ships
    // stubbed org.json methods that throw, which would break BackupManagerTest.
    testImplementation("org.json:json:20240303")
    // Robolectric lets a real in-memory Room DB run on the JVM in unit tests.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Instrumented Compose UI test placeholder (not run in CI).
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
