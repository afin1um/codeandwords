plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.codeandwords"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        // В Kotlin-скриптах (kts) используется знак равенства "="
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.example.codeandwords"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("org.postgresql:postgresql:42.7.9")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Room (Локальная база данных)
    implementation ("androidx.room:room-runtime:2.6.1")
    annotationProcessor ("androidx.room:room-compiler:2.6.1")

    implementation("com.airbnb.android:lottie:6.3.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}