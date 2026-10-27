import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.library)
}

// local.properties 의 API 키/봇 주소를 BuildConfig 로 주입 (:app 과 동일하게 따옴표 제거)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.gsminulx.gsmwatch.core"
    compileSdk = 37

    defaultConfig {
        // 워치(minSdk 30)와 폰(minSdk 24) 양쪽 소비 모듈을 지원하려면 라이브러리 minSdk 는 24
        minSdk = 24

        val dataGsmApiKey = (localProperties.getProperty("DATA_GSM_API_KEY") ?: "").replace("\"", "")
        buildConfigField("String", "DATA_GSM_API_KEY", "\"$dataGsmApiKey\"")

        val botApiUrl = (localProperties.getProperty("BOT_API_URL") ?: "http://127.0.0.1:8080").replace("\"", "")
        buildConfigField("String", "BOT_API_URL", "\"$botApiUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 24 에서 java.time 사용 위해 코어 라이브러리 디슈가링 활성화
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
