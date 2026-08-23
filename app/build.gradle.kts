import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.oss.licenses)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
    id("kotlin-parcelize")
}

android {
    namespace = "com.tk.quicksearch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tk.quicksearch"
        minSdk = 24
        targetSdk = 36
        versionCode = 78
        versionName = "4.1.1"
        manifestPlaceholders["profileCaptureExported"] =
            providers.gradleProperty("profileCapture").orElse("false").get().toBoolean()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            isDefault = true
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            resValue("string", "app_name", "QS Debug")
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    adbOptions {
        installOptions("--user", "0")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    androidResources {
        localeFilters +=
            listOf(
                "en",
                "ar",
                "de",
                "el",
                "es",
                "fr",
                "hi",
                "it",
                "pt-rBR",
                "ru",
                "te",
                "tr",
                "zh-rCN",
            )
    }
}

android {
    applicationVariants.all {
        if (name == "standardRelease") {
            outputs.all {
                (this as BaseVariantOutputImpl).outputFileName = "app-release.apk"
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

baselineProfile {
    // Dependencies ship their own profiles. Keep our generated profile focused on Quick Search
    // so startup DEX layout is not inflated by captured framework and library implementation code.
    filter {
        include("com.tk.quicksearch.**")
    }
}

dependencies {
    baselineProfile(project(":benchmark"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.material.color.utilities)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    "standardImplementation"(libs.play.review.ktx)
    "standardImplementation"(libs.play.app.update)
    "standardImplementation"(libs.play.app.update.ktx)
    implementation(libs.libphonenumber)
    implementation(libs.reorderable)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Gradle 8.13+ validates implicit task input/output dependencies.
// The OSS licenses plugin’s cleanup task consumes the generated dependencies file,
// so we explicitly wire the task dependency to keep builds deterministic.
tasks.matching { it.name == "debugOssLicensesCleanUp" }.configureEach {
    dependsOn("debugOssDependencyTask")
}
