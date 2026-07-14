import java.util.Properties // 추가됨: Properties 클래스 임포트
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.gsminulx.gsmwatch"

    // 36 버전 경고가 뜨더라도 무시하시고 안정적인 35 버전을 쓰시는 것을 권장합니다.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gsminulx.gsmwatch"
        minSdk = 30
        targetSdk = 36

        versionCode = 12
        versionName = "0.6.2"

        // 👉 [수정] 혹시 모를 따옴표(")를 강제로 모두 지워버린 후 감싸기!
        val dataGsmApiKey = (localProperties.getProperty("DATA_GSM_API_KEY") ?: "").replace("\"", "")
        buildConfigField("String", "DATA_GSM_API_KEY", "\"$dataGsmApiKey\"")

        // 👉 [수정] 여기도 마찬가지!
        val gsmApiKey = (localProperties.getProperty("GSM_API_KEY") ?: "").replace("\"", "")
        buildConfigField("String", "GSM_API_KEY", "\"$gsmApiKey\"")

        // 👉 [추가] D-Day 데이터를 받아오기 위한 디스코드 봇 서버 API 주소
        val botApiUrl = (localProperties.getProperty("BOT_API_URL") ?: "http://127.0.0.1:8080").replace("\"", "")
        buildConfigField("String", "BOT_API_URL", "\"$botApiUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // :core 가 코어 라이브러리 디슈가링을 사용하므로 소비 모듈도 활성화해야 함
        isCoreLibraryDesugaringEnabled = true
    }

    // 에러를 유발했던 kotlinOptions 블록 삭제 완료

    useLibrary("wear-sdk")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    // wear-compose 스택을 1.5.6 으로 통일 (foundation/material3 와 정합). navigation 은 SwipeDismissableNavHost 에 사용.
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.wear.compose.navigation)
    // Wear Tile (앱 미실행 상태에서 남은시간 글랜스)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.concurrent.futures)
}

