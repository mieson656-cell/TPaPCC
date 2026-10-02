plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="com.tpappcc"; compileSdk=36
    defaultConfig { applicationId="com.tpappcc"; minSdk=26; targetSdk=36; versionCode=2; versionName="0.3.0" }
}
dependencies {
    implementation("io.github.webrtc-sdk:android:150.7871.01")
}
