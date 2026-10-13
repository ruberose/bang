package com.kukuku.bang

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

/** 로그 태그 */
private const val TAG = "FaceCamera"

/**
 * 전면 카메라 미리보기(셀카 화면) + 얼굴 기울기 인식.
 *
 * 하는 일:
 * 1. CameraX로 전면 카메라를 켜서 [PreviewView]에 셀카 화면을 보여준다.
 * 2. 같은 카메라의 프레임을 ImageAnalysis로 받아 ML Kit 얼굴 인식에 넘긴다.
 * 3. 인식된 얼굴의 좌우 기울기(headEulerAngleZ)와 위치를 [onFace]로 알려준다. 얼굴이 없으면 null.
 *
 * 개인정보: 프레임은 메모리에서 분석만 하고 바로 버린다(close). 저장하거나 전송하지 않는다.
 * ML Kit 번들 모델이라 인식도 기기 안에서만 일어난다.
 *
 * 카메라는 화면 수명(LifecycleOwner)에 묶여 있어서, 앱이 백그라운드로 가면 자동으로 꺼지고
 * 돌아오면 다시 켜진다. 이 컴포저블이 화면에서 사라지면 카메라를 완전히 해제한다.
 *
 * @param onFace (얼굴 정보 또는 null, 프레임을 받은 순간의 System.nanoTime()). 메인 스레드에서 호출된다.
 * @param onError 카메라를 켤 수 없을 때(전면 카메라 없음 등) 호출된다.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun FaceCamera(
    modifier: Modifier = Modifier,
    onFace: (FaceSample?, Long) -> Unit,
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // rememberUpdatedState: 카메라 콜백은 한 번 등록되면 계속 살아 있으므로,
    // 화면이 다시 그려져 onFace 함수가 바뀌어도 항상 "최신" 함수를 부르게 한다.
    val currentOnFace by rememberUpdatedState(onFace)
    val currentOnError by rememberUpdatedState(onError)

    // 셀카 미리보기 뷰 (기존 안드로이드 View라서 AndroidView로 Compose 안에 넣는다)
    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE(TextureView): Compose의 다른 그림과 겹쳐 그리기에 안정적이다.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // 화면을 꽉 채우고 넘치는 부분은 잘라낸다 (셀카 앱과 같은 방식)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // 참고: 전면 카메라 미리보기는 PreviewView가 자동으로 좌우 반전(거울)해 준다.
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        // 프레임 분석 전용 스레드 하나. 메인 스레드에서 분석하면 게임 화면이 버벅인다.
        val analysisExecutor = Executors.newSingleThreadExecutor()

        // 얼굴 인식기: 빠른 모드, 각도만 필요하므로 눈/입 위치 같은 부가 기능은 끈다.
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)   // 화면에서 너무 작은 얼굴(뒤에 지나가는 사람)은 무시
                .build()
        )

        var provider: ProcessCameraProvider? = null
        var disposed = false

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            // 카메라 준비가 끝나기 전에 화면이 사라졌으면 아무것도 켜지 않는다.
            if (disposed) return@addListener
            val p = providerFuture.get()
            provider = p

            // 미리보기와 분석 이미지를 "같은 4:3 비율"로 맞춘다.
            // 비율이 다르면 두 이미지가 잘리는 범위가 달라서, 분석에서 찾은 얼굴 위치를
            // 화면에 옮겼을 때 실제 얼굴과 어긋난다.
            val sameRatio = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(sameRatio)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // KEEP_ONLY_LATEST: 분석이 밀리면 오래된 프레임은 버리고 최신 것만 분석 → 지연 최소화
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(sameRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                // 프레임을 받은 순간의 시각. 인식 처리 시간(수십 ms)만큼 판정이 늦어지지 않게
                // 인식 "결과" 시각이 아니라 프레임 "도착" 시각을 입력 시각으로 쓴다.
                val frameTime = System.nanoTime()
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                // rotationDegrees: 폰을 세로로 들었을 때 이미지를 몇 도 돌려야 똑바로 서는지
                val rotation = imageProxy.imageInfo.rotationDegrees
                val image = InputImage.fromMediaImage(mediaImage, rotation)
                // ML Kit가 돌려주는 얼굴 위치는 "똑바로 세운" 이미지 기준이다.
                // 90/270도 회전이면 가로/세로가 뒤바뀌므로 그에 맞춰 크기를 정한다.
                val uprightW: Float
                val uprightH: Float
                if (rotation == 90 || rotation == 270) {
                    uprightW = imageProxy.height.toFloat(); uprightH = imageProxy.width.toFloat()
                } else {
                    uprightW = imageProxy.width.toFloat(); uprightH = imageProxy.height.toFloat()
                }
                detector.process(image)
                    // 결과 콜백은 기본으로 메인 스레드에서 실행된다 → 게임 상태를 바로 바꿔도 안전
                    .addOnSuccessListener { faces ->
                        // 여러 얼굴이 잡히면 가장 큰(= 가장 가까운) 얼굴을 플레이어로 본다.
                        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        val sample = face?.let {
                            val b = it.boundingBox
                            FaceSample(
                                angleDeg = it.headEulerAngleZ,
                                box = FaceBox(
                                    // 분석 이미지는 반전되지 않은 원본이고 미리보기는 거울 반전이므로
                                    // 가로 위치를 뒤집어(1 - x) 미리보기 화면 기준으로 맞춘다.
                                    cx = 1f - b.exactCenterX() / uprightW,
                                    cy = b.exactCenterY() / uprightH,
                                    width = b.width() / uprightW,
                                    imageAspect = uprightW / uprightH,
                                ),
                            )
                        }
                        currentOnFace(sample, frameTime)
                    }
                    .addOnFailureListener { currentOnFace(null, frameTime) }
                    // 성공/실패와 상관없이 프레임을 반드시 닫아야 다음 프레임이 들어온다.
                    .addOnCompleteListener { imageProxy.close() }
            }

            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // 전면 카메라가 없거나 다른 앱이 카메라를 쓰는 중일 때
                Log.w(TAG, "카메라 연결 실패", e)
                currentOnError("카메라를 켤 수 없어요")
            }
        }, ContextCompat.getMainExecutor(context))

        // 화면에서 사라질 때: 카메라 끄기 → 인식기 닫기 → 스레드 정리
        onDispose {
            disposed = true
            provider?.unbindAll()
            detector.close()
            analysisExecutor.shutdown()
        }
    }
}
