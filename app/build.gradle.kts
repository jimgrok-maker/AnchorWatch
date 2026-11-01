plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional release signing. Supply all of KEYSTORE_PATH, KEYSTORE_PASSWORD and KEY_ALIAS
// (KEY_PASSWORD defaults to the keystore password) as environment variables or Gradle
// properties (-P / ~/.gradle/gradle.properties). Nothing secret is stored in this repo.
fun signingValue(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("KEYSTORE_PATH")
val releaseKeystorePassword = signingValue("KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("KEY_ALIAS")
val hasReleaseKeystore =
    releaseKeystorePath != null && releaseKeystorePassword != null && releaseKeyAlias != null

android {
    namespace = "com.jimgrok.anchorwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jimgrok.anchorwatch"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.0.13"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = signingValue("KEY_PASSWORD") ?: releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // WARNING: without a release keystore (see KEYSTORE_PATH above) the release APK is
            // signed with the publicly-known DEBUG key. That is fine for sideloading and CI
            // artifacts, but such an APK must not be published to a store, and updates only
            // install over builds signed with the same key.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("org.osmdroid:osmdroid-android:6.1.20")

    testImplementation("junit:junit:4.13.2")
}
