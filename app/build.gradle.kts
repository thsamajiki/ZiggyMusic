import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.devtools.ksp)
}

android {
    namespace = "com.hero.ziggymusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hero.ziggymusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                val pathToSuperpowered = System.getenv("PATH_TO_SUPERPOWERED")
                    ?: (project.findProperty("PATH_TO_SUPERPOWERED") as String?)
                    ?: file("src/main/jniLibs").absolutePath
                arguments += "-DPATH_TO_SUPERPOWERED=$pathToSuperpowered"

                val spKey = System.getenv("SUPERPOWERED_LICENSE_KEY")
                    ?: (project.findProperty("SUPERPOWERED_LICENSE_KEY") as String?)
                    ?: run {
                        // 프로젝트 루트의 local.secrets.properties를 선택적으로 읽는 방식 (C안)
                        val f = rootProject.file("local.secrets.properties")
                        if (f.exists()) {
                            val p = Properties()
                            f.inputStream().use { p.load(it) }
                            p.getProperty("SUPERPOWERED_LICENSE_KEY")
                        } else null
                    }

                // release에서 키 없으면 실패시키는 게 현업적으로 안전
                if (spKey.isNullOrBlank()) {
                    throw GradleException(
                        "SUPERPOWERED_LICENSE_KEY is missing. " +
                                "Set env var / ~/.gradle/gradle.properties / local.secrets.properties"
                    )
                }

                arguments += "-DSUPERPOWERED_LICENSE_KEY=$spKey"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isShrinkResources = false
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isShrinkResources = false
            isMinifyEnabled = false
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
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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

    implementation(libs.gson)
    implementation(libs.lifecycle.viewmodel.compose.android)

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

    implementation(libs.lottie)
}

kapt {
    correctErrorTypes = true
}
