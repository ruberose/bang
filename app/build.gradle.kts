/**
 * 앱 모듈 빌드 파일.
 * SDK 버전, 패키지 이름, 사용하는 라이브러리를 정한다.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.0부터 Compose 컴파일러는 이 플러그인으로 켠다.
    alias(libs.plugins.kotlin.compose)
}

android {
    // 패키지 이름(네임스페이스). 출시 후에는 바꿀 수 없으니 출시 전에 확정할 것.
    namespace = "com.kukuku.bang"
    // compileSdk: 컴파일할 때 사용할 안드로이드 API 버전 (최신 안정 버전)
    compileSdk = 36

    defaultConfig {
        // 플레이스토어에서 앱을 구분하는 고유 ID
        applicationId = "com.kukuku.bang"
        // CLAUDE.md 규칙: 최소 SDK 26 (Android 8.0)
        minSdk = 26
        // targetSdk: 이 버전의 동작 방식에 맞춰 앱이 테스트되었다는 선언
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            // 릴리스 빌드 최적화(코드 축소)는 8단계 "릴리스 빌드 설정"에서 켤 예정.
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
        // Jetpack Compose 사용
        compose = true
    }
}

kotlin {
    compilerOptions {
        // 위 compileOptions의 자바 버전과 맞춘다.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM: 아래 Compose 라이브러리들의 버전을 한 번에 맞춰준다.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // 단위 테스트(JUnit) - 2단계에서 GameEngine 판정 로직 테스트에 사용
    testImplementation(libs.junit)

    // @Preview 미리보기 도구는 디버그 빌드에만 포함 (릴리스 앱 용량 절약)
    debugImplementation(libs.androidx.ui.tooling)
}
