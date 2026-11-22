plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release-Signatur: verschlüsselter Schlüssel liegt im Repo (signing/zdf-release.p12),
// das Passwort kommt aus der Umgebung (GitHub-Secret ZDF_SIGNING_PASSWORD).
// Gleiche Signatur = Updates lassen sich über die alte Version installieren.
val signingPassword: String? = System.getenv("ZDF_SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }
val signingFile = rootProject.file("signing/zdf-release.p12")

android {
    namespace = "de.zdf.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.zdf.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("ZDF_VERSION_CODE") ?: "100").toInt()
        versionName = System.getenv("ZDF_VERSION_NAME") ?: "1.1.0"
        buildConfigField("String", "UPDATE_REPO", "\"holle1235/zdf_2\"")
    }

    signingConfigs {
        if (signingPassword != null && signingFile.exists()) {
            create("release") {
                storeFile = signingFile
                storeType = "PKCS12"
                storePassword = signingPassword
                keyAlias = "zdf"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
