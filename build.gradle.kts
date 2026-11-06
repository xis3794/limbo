// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.4.1")

        // Kotlin + Compose compiler are only used by the experimental Compose UI
        // module (limbo-android-exp). Putting them on the root buildscript
        // classpath lets every module opt in with a plain `apply plugin:` and
        // keeps the legacy Java modules completely Kotlin-free.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

apply(plugin = "idea")
configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
    module {
        //Exclude C source code from indexing
        excludeDirs.add(file("limbo-android-lib/src/main/jni"))
        excludeDirs.add(file("limbo-android-lib/build"))
        excludeDirs.add(file("limbo-android-arm/build"))
        excludeDirs.add(file("limbo-android-sparc/build"))
        excludeDirs.add(file("limbo-android-ppc/build"))
        excludeDirs.add(file("limbo-android-x86/build"))
        excludeDirs.add(file("gradle"))
    }
}
