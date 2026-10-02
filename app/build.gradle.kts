import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// CI passes -PversionName and -PversionCode when cutting a release from a tag.
val ciVersionName: String? = (project.findProperty("versionName") as String?)
val ciVersionCode: Int? = (project.findProperty("versionCode") as String?)?.toIntOrNull()

android {
    namespace = "com.sortfold.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sortfold.app"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: 2
        versionName = ciVersionName ?: "1.1.0"

        // Repo injected by CI (-PgithubRepo=owner/name). A blank value would
        // silently break the in-app update check, so the build fails instead.
        val repo = (project.findProperty("githubRepo") as String?) ?: "tukiza7-debug/Sortfold"
        require(repo.isNotBlank() && repo.contains('/')) {
            "githubRepo must be in owner/name form, got: \"$repo\""
        }
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")

        // Short git sha: identifies the exact build in error reports.
        // providers.exec is configuration-cache compatible (no raw ProcessBuilder).
        val sha = try {
            providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                workingDir(rootProject.projectDir)
                isIgnoreExitValue = true
            }.standardOutput.asText.get().trim().ifBlank { "dev" }
        } catch (_: Exception) {
            "dev"
        }
        buildConfigField("String", "GIT_SHA", "\"$sha\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from environment variables set by CI (or a local release.properties).
    // When no keystore is configured, release builds fall back to the debug key so the
    // project always builds; CI sets the real credentials before publishing.
    val releaseProps = Properties().apply {
        val f = rootProject.file("release.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val ksFile = System.getenv("KEYSTORE_FILE") ?: releaseProps.getProperty("keystoreFile")
    val ksAlias = System.getenv("KEY_ALIAS") ?: releaseProps.getProperty("keyAlias")
    val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: releaseProps.getProperty("keystorePassword")
    val keyPass = System.getenv("KEY_PASSWORD") ?: releaseProps.getProperty("keyPassword")
    val hasReleaseKey = ksFile != null && ksAlias != null && ksPass != null && keyPass != null

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(ksFile)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = keyPass
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Universal APK plus per-ABI APKs for the release channel.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions{} is removed in newer Kotlin; the Kotlin 2.2 DSL is the supported form.
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += "GoogleAppIndexingWarning"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)
    implementation(libs.exifinterface)
    implementation(libs.splashscreen)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.work.testing)
}
