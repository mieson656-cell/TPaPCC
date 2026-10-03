plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace="com.tpappcc"
    compileSdk=36

    defaultConfig {
        applicationId="com.tpappcc"
        minSdk=26
        targetSdk=36
        versionCode=3
        versionName="0.3.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("io.github.webrtc-sdk:android:150.7871.01")
}
