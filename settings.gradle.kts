/**
 * Gradle 설정 파일.
 * - 플러그인과 라이브러리를 어디서 내려받을지(저장소)를 정한다.
 * - 이 프로젝트에 포함될 모듈(:app)을 등록한다.
 */
pluginManagement {
    repositories {
        // google(): 안드로이드 공식 플러그인/라이브러리 (AGP, Compose, AdMob 등)
        google()
        // mavenCentral(): Kotlin, JUnit 등 일반 자바/코틀린 라이브러리
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 모듈별로 저장소를 따로 선언하지 못하게 막아서, 의존성 출처를 이 파일 한 곳에서 관리한다.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Bang"
include(":app")
