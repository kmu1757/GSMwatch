plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.gsminulx.gsmwatch"

    compileSdk = 37

    defaultConfig {
        applicationId = "com.gsminulx.gsmwatch"
        minSdk = 30
        targetSdk = 36

        versionCode = 14
        versionName = "0.8.0"
        // NEIS 키/봇 주소 등 API 설정은 :core 의 BuildConfig 가 담당한다(이 모듈은 사용하지 않음).
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

    useLibrary("wear-sdk")

    testOptions {
        // Robolectric + Roborazzi 스크린샷 테스트(에뮬레이터 없이 JVM 에서 화면 렌더링)
        unitTests { isIncludeAndroidResources = true }
    }

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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.ui.test.junit4)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    // wear-compose 스택(material3/foundation/navigation)은 같은 버전으로 통일. navigation 은 SwipeDismissableNavHost 에 사용.
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.wear.compose.navigation)
    // 과목명 입력: 시스템 키보드/음성/필기 (RemoteInput)
    implementation(libs.wear.input)
    // Wear Tile (앱 미실행 상태에서 남은시간 글랜스) — Material3TileService + ProtoLayout Material3
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.concurrent.futures)
}

