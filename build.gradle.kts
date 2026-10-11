/**
 * 최상위(루트) 빌드 파일.
 * 실제 설정은 app/build.gradle.kts 에서 하고, 여기서는 사용할 플러그인 버전만 "선언"한다.
 * apply false = 루트에는 적용하지 않고 하위 모듈이 골라 쓰게 한다는 뜻.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
