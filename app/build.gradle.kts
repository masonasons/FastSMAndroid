plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "me.masonasons.fastsm"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.masonasons.fastsm"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["oauthScheme"] = "fastsm"
        manifestPlaceholders["oauthHost"] = "oauth"
    }

    // Release signing is driven by gradle.properties / env vars so the
    // keystore and its password are never committed. If the properties are
    // missing, the release build falls back to debug signing — useful for
    // local testing but rejected by Play Store.
    signingConfigs {
        create("release") {
            val storeFilePath = (project.findProperty("FASTSM_RELEASE_STORE_FILE") as String?)
                ?: System.getenv("FASTSM_RELEASE_STORE_FILE")
            val storePwd = (project.findProperty("FASTSM_RELEASE_STORE_PASSWORD") as String?)
                ?: System.getenv("FASTSM_RELEASE_STORE_PASSWORD")
            val keyAliasValue = (project.findProperty("FASTSM_RELEASE_KEY_ALIAS") as String?)
                ?: System.getenv("FASTSM_RELEASE_KEY_ALIAS")
            val keyPwd = (project.findProperty("FASTSM_RELEASE_KEY_PASSWORD") as String?)
                ?: System.getenv("FASTSM_RELEASE_KEY_PASSWORD")

            if (storeFilePath != null && storePwd != null && keyAliasValue != null && keyPwd != null) {
                storeFile = file(storeFilePath)
                storePassword = storePwd
                keyAlias = keyAliasValue
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only attach the release signing config when all credentials were
            // resolved — otherwise the build system complains about null.
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Rename output APKs so the file you hand out is `FastSM.apk`, not
    // `app-release.apk`. Debug builds still get a suffix so both artifacts
    // can live side-by-side in the outputs dir.
    applicationVariants.all {
        val buildTypeName = buildType.name
        outputs.all {
            val suffix = if (buildTypeName == "release") "" else "-debug"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "FastSM$suffix.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
