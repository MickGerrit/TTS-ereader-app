import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Readium 3.x is published as one coordinated release; keep the modules in step. */
val readiumVersion = "3.1.1"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        // Readium 3.x is built against Java 17.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.core:core-ktx:1.13.1")

                // Readium — the EPUB engine (TECHNICALPRD §2, Spike B).
                // shared: publication model and Locators. streamer: opens the file.
                // navigator: the paginated rendering surface.
                implementation("org.readium.kotlin-toolkit:readium-shared:$readiumVersion")
                implementation("org.readium.kotlin-toolkit:readium-streamer:$readiumVersion")
                implementation("org.readium.kotlin-toolkit:readium-navigator:$readiumVersion")

                // EpubNavigatorFragment is a Fragment, so the reader surface is
                // hosted rather than drawn by Compose.
                implementation("androidx.fragment:fragment-ktx:1.8.5")
                implementation("androidx.fragment:fragment-compose:1.8.5")
                implementation("androidx.appcompat:appcompat:1.7.0")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "nl.lector"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.lector"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Unsigned for now: there is no keystore to sign with yet, and the
            // point of building release today is that shrinking does not break it.
        }
    }

    compileOptions {
        // Readium needs java.time and friends on API levels below 26.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // The EPUB parser logs skipped files via android.util.Log; let the stub
        // return defaults instead of throwing "not mocked" in JVM tests.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "nl.lector.res"
}
