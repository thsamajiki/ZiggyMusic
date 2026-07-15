import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.aboutlibraries)
}

extensions.configure<ApplicationExtension> {
    namespace = "com.hero.ziggymusic"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.hero.ziggymusic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"

                cppFlags += listOf("-std=c++17", "-O3")

            }
        }
    }

    buildTypes {
        getByName("debug") {
            isShrinkResources = false
            isMinifyEnabled = false
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        prefab = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testjunit)
    androidTestImplementation(libs.espresso.core)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler)
    implementation(libs.palette)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.elastic.view)

    implementation(libs.otto)

    // ViewModel
    implementation(libs.bundles.lifecycle)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    // Media3
    implementation(libs.bundles.media3)

    implementation(libs.oboe)
}

kapt {
    correctErrorTypes = true
}
