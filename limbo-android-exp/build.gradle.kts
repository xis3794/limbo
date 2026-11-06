// The experimental Kotlin/Compose UI for Limbo.
//
// This module is deliberately *separate* from :limbo-android-lib so the
// GTK/SDL/VNC main paths stay buildable without a Kotlin or Compose dependency
// graph. The legacy Java code talks to it through
// com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge.
//
// The three plugins come from the root buildscript classpath
// (build.gradle.kts), which is why no versions are repeated here.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.max2idea.android.limbo.ui.exp"

    // AGP 9 resolves the integer 37 to the "android-37.0" platform hash, which
    // is the only API 37 revision the SDK ships (there is no android-37).
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // Miuix 0.9.4 declares minSdkVersion 24 in its own manifest.
        // No targetSdk on purpose: this is a library module, and with
        // android.newDsl=false the legacy DSL hides the targetSdk property.
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }
}

kotlin {
    compilerOptions {
        // Must stay in step with compileOptions above, otherwise AGP fails the
        // "inconsistent JVM-target compatibility" check.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":limbo-android-lib"))

    // Miuix (top.yukonga.miuix.kmp) 0.9.4 pulls in the JetBrains Compose 1.12
    // foundation it was built against, so no separate Compose BOM is needed.
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")

    // The Compose *activity* integration is not a transitive dependency of
    // Miuix; ComponentActivity + setContent come from here.
    implementation("androidx.activity:activity-compose:1.10.1")
}
