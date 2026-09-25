plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jimgrok.anchorwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jimgrok.anchorwatch"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
}

fun writePngFromB64(name: String, dest: File, size: Int? = null) {
    val raw = java.util.Base64.getDecoder().decode(file("icons/$name.b64").readText().trim())
    dest.parentFile.mkdirs()
    if (size == null) {
        dest.writeBytes(raw)
        return
    }
    val src = javax.imageio.ImageIO.read(raw.inputStream())
        ?: error("Could not decode icons/$name.b64 as PNG")
    val scaled = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = scaled.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC
    )
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
    )
    g.drawImage(src, 0, 0, size, size, null)
    g.dispose()
    javax.imageio.ImageIO.write(scaled, "png", dest)
}

tasks.register("decodeLauncherIcons") {
    doLast {
        listOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        ).forEach { (folder, px) ->
            writePngFromB64("ic_launcher_xxxhdpi.png", file("src/main/res/mipmap-$folder/ic_launcher.png"), px)
            writePngFromB64("ic_launcher_xxxhdpi.png", file("src/main/res/mipmap-$folder/ic_launcher_round.png"), px)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("decodeLauncherIcons")
}
