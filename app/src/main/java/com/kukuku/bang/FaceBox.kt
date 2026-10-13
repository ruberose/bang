package com.kukuku.bang

import kotlin.math.max

/**
 * 카메라 이미지 안에서 얼굴이 있는 위치 (화면 크기와 무관한 0~1 비율 좌표).
 *
 * 좌표 기준: "똑바로 세우고(회전 보정) 거울처럼 좌우 반전한" 카메라 이미지.
 * 셀카 미리보기 화면이 거울처럼 보이므로, 같은 기준으로 맞춰야 화면 위 얼굴과 겹친다.
 *
 * @property cx 얼굴 중심의 가로 위치 (0 = 이미지 왼쪽 끝, 1 = 오른쪽 끝)
 * @property cy 얼굴 중심의 세로 위치 (0 = 위, 1 = 아래)
 * @property width 얼굴 너비 (이미지 너비 대비 비율)
 * @property imageAspect 이미지 가로/세로 비율 (세로로 든 폰이면 보통 3/4 = 0.75)
 */
data class FaceBox(val cx: Float, val cy: Float, val width: Float, val imageAspect: Float)

/**
 * 카메라 한 프레임에서 얻은 얼굴 정보.
 * @property angleDeg 좌우 기울기(도, ML Kit headEulerAngleZ 원래 값)
 * @property box 얼굴 위치
 */
data class FaceSample(val angleDeg: Float, val box: FaceBox)

/**
 * 화면 위의 원 (픽셀 단위). Compose와 무관한 순수 데이터라 단위 테스트에 쓸 수 있다.
 * @property x 중심 가로 위치
 * @property y 중심 세로 위치
 * @property r 반지름
 */
data class ScreenCircle(val x: Float, val y: Float, val r: Float)

/** 카메라 이미지 좌표 ↔ 화면 좌표 변환과 흔들림 보정 (순수 계산) */
object FaceMapping {

    /**
     * 얼굴 위치를 화면 픽셀 좌표의 원으로 바꾼다.
     *
     * 셀카 미리보기는 FILL_CENTER(화면을 꽉 채우고 넘치는 부분은 잘라냄)로 그려진다.
     * 그래서 같은 방식으로 계산해야 한다:
     * 1. 이미지를 화면을 "꽉 채울 만큼" 확대한다 (가로/세로 배율 중 큰 쪽).
     * 2. 가운데 맞춤 → 넘친 만큼 양쪽으로 잘려 나간다(offset이 음수).
     *
     * @param viewW 화면(미리보기) 너비(px)
     * @param viewH 화면(미리보기) 높이(px)
     */
    fun toScreen(face: FaceBox, viewW: Float, viewH: Float): ScreenCircle {
        // 이미지 크기를 (가로 = aspect, 세로 = 1) 단위로 생각하면 계산이 간단해진다.
        val scale = max(viewW / face.imageAspect, viewH / 1f)
        val shownW = face.imageAspect * scale
        val shownH = scale
        val offsetX = (viewW - shownW) / 2f
        val offsetY = (viewH - shownH) / 2f
        return ScreenCircle(
            x = offsetX + face.cx * shownW,
            y = offsetY + face.cy * shownH,
            r = face.width * shownW / 2f,
        )
    }

    /**
     * 흔들림 보정 (지수 이동 평균).
     * 얼굴 인식 결과는 프레임마다 몇 픽셀씩 떨린다. 새 값을 그대로 쓰면 원이 덜덜 떨려 보이므로
     * "이전 위치에서 새 위치 쪽으로 [alpha] 비율만큼만" 움직인다.
     * alpha가 클수록 빠르게 따라가고(덜 부드러움), 작을수록 부드럽지만 늦게 따라간다.
     *
     * @param prev 이전 값. 없으면(처음 보이는 얼굴) 새 값을 그대로 쓴다.
     */
    fun smooth(prev: FaceBox?, next: FaceBox, alpha: Float): FaceBox {
        if (prev == null) return next
        fun lerp(a: Float, b: Float) = a + (b - a) * alpha
        return FaceBox(
            cx = lerp(prev.cx, next.cx),
            cy = lerp(prev.cy, next.cy),
            width = lerp(prev.width, next.width),
            imageAspect = next.imageAspect,
        )
    }
}
