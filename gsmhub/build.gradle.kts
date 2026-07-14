import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.gsminulx.gsmwatch"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.gsminulx.gsmwatch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // DataGSM OAuth (Client ID 는 local.properties, redirect 는 고정 커스텀 스킴)
        val clientId = (localProps.getProperty("DATAGSM_CLIENT_ID") ?: "").replace("\"", "")
        buildConfigField("String", "DATAGSM_CLIENT_ID", "\"$clientId\"")
        val redirectUri = (localProps.getProperty("DATAGSM_REDIRECT_URI") ?: "gsmhub://oauth2redirect").replace("\"", "")
        buildConfigField("String", "DATAGSM_REDIRECT_URI", "\"$redirectUri\"")
        // confidential 클라이언트라 토큰 교환에 secret 이 필요할 수 있어 PKCE 와 함께 전송(있을 때만)
        val clientSecret = (localProps.getProperty("DATAGSM_CLIENT_SECRET") ?: "").replace("\"", "")
        buildConfigField("String", "DATAGSM_CLIENT_SECRET", "\"$clientSecret\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // :core 의 java.time 사용을 위해 코어 라이브러리 디슈가링 (minSdk 24)
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.browser)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
