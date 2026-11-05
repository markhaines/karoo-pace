import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing never lives in the repo. Locally it comes from keystore.properties (and the
// keystore it points at), both gitignored. In CI the release workflow restores the keystore
// from GitHub secrets and passes everything as KAROO_PACE_* environment variables. With
// neither, only debug builds are available: a release build fails at signing validation.
// See docs/releasing.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps[propKey] as String?)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envKey).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "KAROO_PACE_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KAROO_PACE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KAROO_PACE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KAROO_PACE_KEY_PASSWORD")

// Desk-test switch (cycles every band every 2s). Debug builds only, opt in with
// -PsimulateBands=true. Release builds hard-code false, so a field that goes on the bike
// cannot ship with it on; ReleaseGuardTest checks that in the release unit tests.
val simulateBands = providers.gradleProperty("simulateBands").orElse("false").get()
require(simulateBands == "true" || simulateBands == "false") {
    "simulateBands must be true or false, got '$simulateBands'"
}

android {
    namespace = "com.hainesy.karoopace"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hainesy.karoopace"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            releaseStoreFile?.let { path ->
                storeFile = rootProject.file(path)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "SIMULATE_BANDS", simulateBands)
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "SIMULATE_BANDS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main").java.srcDir("src/main/kotlin")
        getByName("test").java.srcDir("src/test/kotlin")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.karoo.ext)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
}
