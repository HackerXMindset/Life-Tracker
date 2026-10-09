plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// GitHub passes its run number here so every build has a higher version code
// than the last, which lets a new APK install over the old one.
val buildNumber: Int = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.lifetracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifetracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.9.$buildNumber"
    }

    signingConfigs {
        // One fixed key for every build, so updates install over the old app
        // and keep your data. The repository is private; see README.
        create("release") {
            storeFile = rootProject.file("keystore/lifetracker.p12")
            storePassword = "lifetracker"
            keyAlias = "lifetracker"
            keyPassword = "lifetracker"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // On-phone database. Never use destructive migrations: future versions must
    // add a Migration so your logged data survives updates.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Reads and writes the folders you pick (Streak, OpenNutriTracker, backups).
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Runs the small background job that copies app usage history every few hours.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Reads steps and sleep from Android's Health Connect.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha12")

    // Tests that run in the cloud build. The org.json copy is needed because
    // the Android one is only a stub outside a real phone.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
