plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "com.hero.ziggymusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hero.ziggymusic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // release와 동일한 최적화 조건으로 로컬 성능을 측정한다.
        create("benchmark") {
            initWith(getByName("release"))

            // 로컬 설치용. 실제 release 서명키는 사용하지 않음
            signingConfig = signingConfigs.getByName("debug")

            // debug 앱과 동시에 설치하려면 사용
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"

            matchingFallbacks += listOf("release")
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
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    // Media3
    implementation(libs.bundles.media3)

}
