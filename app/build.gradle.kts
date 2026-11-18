plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val signingStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val signingStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val signingKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

if (!signingStorePath.isNullOrBlank()) {
    require(!signingStorePassword.isNullOrBlank()) { "ANDROID_KEYSTORE_PASSWORD is required when ANDROID_KEYSTORE_PATH is set." }
    require(!signingKeyAlias.isNullOrBlank()) { "ANDROID_KEY_ALIAS is required when ANDROID_KEYSTORE_PATH is set." }
    require(!signingKeyPassword.isNullOrBlank()) { "ANDROID_KEY_PASSWORD is required when ANDROID_KEYSTORE_PATH is set." }
    require(java.io.File(signingStorePath).isFile) { "Android signing keystore was not found at ANDROID_KEYSTORE_PATH." }
}

android {
    namespace = "com.vikram.mindkeep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vikram.mindkeep"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("versionName").orElse("0.1.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // CI supplies the same upload key to both variants. Local builds retain
    // Android's normal debug signing when the environment variables are absent.
    if (!signingStorePath.isNullOrBlank()) {
        signingConfigs {
            create("ci") {
                storeFile = file(signingStorePath)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("ci")
            }
            getByName("release") {
                signingConfig = signingConfigs.getByName("ci")
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
    } else {
        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
