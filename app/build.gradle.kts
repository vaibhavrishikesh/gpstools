import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from keystore.properties (gitignored) so the upload key
// + passwords stay out of source control. When the file is absent (e.g. a CI box
// without the key) the release signingConfig is simply not applied and the build
// produces an unsigned artifact — debug builds are unaffected.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.gpstools.camera"
    compileSdk = 35

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.gpstools.camera"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply the release signing config when keystore.properties is present so
            // assembleRelease / bundleRelease emit a signed APK / AAB directly.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // Enabled so the billing flow (US-016) can expose a DEBUG-only
        // "simulate purchase" affordance for emulator verification without a
        // configured Play Console product. Off by default in AGP 8.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.ads)
    // play-services-ads (US-015) pulls in full Guava, which makes Gradle dedupe
    // CameraX's `listenablefuture:1.0` to the empty stub — leaving ListenableFuture
    // off the COMPILE classpath. Declaring Guava directly keeps the real class on
    // the compile classpath so CameraX's ProcessCameraProvider API resolves.
    implementation(libs.guava)
    // Google Play Billing (US-016) — one-time IAP to remove ads + unlock premium.
    implementation(libs.billing.ktx)
    // EXIF GPS metadata (US-014) — write machine-readable lat/long/altitude/timestamp
    // into the saved JPEG. The platform android.media.ExifInterface can't write to a
    // FileDescriptor pre-API24-reliably; the androidx backport does and adds GPS helpers.
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.androidx.ui.tooling)
}
