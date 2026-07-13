// 1️⃣ import 문은 반드시 파일 맨 첫 줄에 있어야 합니다!
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 2️⃣ 비밀 금고 파일 열기 설정
val properties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
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
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 3️⃣ 서버 주소와 API 키 꺼내오기 (local.properties 값에 붙은 따옴표를 제거 후 감싼다 — :app 과 동일 처리)
        val botApiUrl = (properties.getProperty("BOT_API_URL") ?: "http://127.0.0.1:8080").replace("\"", "")
        buildConfigField("String", "BOT_API_URL", "\"$botApiUrl\"")
        val dataGsmApiKey = (properties.getProperty("DATA_GSM_API_KEY") ?: "").replace("\"", "")
        buildConfigField("String", "DATA_GSM_API_KEY", "\"$dataGsmApiKey\"")
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
    buildFeatures {
        compose = true
        // 4️⃣ BuildConfig 자동 생성
        buildConfig = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}