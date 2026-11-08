import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Signing material stays local: it is never committed. */
val releaseKeystore = file("release.jks")
val keystoreProperties = Properties().apply {
    val props = rootProject.file("keystore.properties")
    if (props.exists()) {
        props.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = releaseKeystore.exists() &&
    (keystoreProperties.getProperty("storePassword")?.isNotBlank() == true)

android {
    namespace = "hey.skipad"
    compileSdk = 37

    defaultConfig {
        applicationId = "hey.skipad"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.3"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias", "skipad")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Without local signing material the release build falls back to
                // the debug key so `assembleRelease` still works for contributors.
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

/**
 * Keeps assets/changelog.md in sync with the repository CHANGELOG.md so every
 * build ships its own release notes and the About screen can show them offline.
 */
val copyChangelog = tasks.register("copyChangelog") {
    val source = rootProject.file("CHANGELOG.md")
    val target = file("src/main/assets/changelog.md")
    outputs.file(target)
    doLast {
        if (source.exists()) {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copyChangelog)
}
