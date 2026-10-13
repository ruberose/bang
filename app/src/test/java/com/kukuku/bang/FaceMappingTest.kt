package com.kukuku.bang

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FaceMapping] 좌표 변환 테스트.
 * 기준 상황: 3:4 세로 카메라 이미지(aspect 0.75)를 1080×2400 화면에 FILL_CENTER로 표시.
 * → 세로가 꽉 차도록 2400배 확대, 가로는 1800px이 되어 양쪽 360px씩 잘린다.
 */
class FaceMappingTest {

    private val eps = 0.01f
    private val viewW = 1080f
    private val viewH = 2400f

    @Test
    fun `이미지 정중앙은 화면 정중앙`() {
        val c = FaceMapping.toScreen(FaceBox(0.5f, 0.5f, 0.2f, 0.75f), viewW, viewH)
        assertEquals(540f, c.x, eps)
        assertEquals(1200f, c.y, eps)
    }

    @Test
    fun `가로는 넘친 만큼 잘려서 이미지 왼쪽 끝은 화면 밖이다`() {
        val c = FaceMapping.toScreen(FaceBox(0f, 0f, 0.2f, 0.75f), viewW, viewH)
        assertEquals(-360f, c.x, eps)   // (1080 - 1800) / 2
        assertEquals(0f, c.y, eps)       // 세로는 딱 맞아서 잘리지 않음
    }

    @Test
    fun `얼굴 너비가 화면 기준 반지름으로 바뀐다`() {
        // 이미지 너비의 20% = 표시 너비 1800px의 20% = 360px → 반지름 180px
        val c = FaceMapping.toScreen(FaceBox(0.5f, 0.5f, 0.2f, 0.75f), viewW, viewH)
        assertEquals(180f, c.r, eps)
    }

    @Test
    fun `화면이 이미지보다 넓적하면 위아래가 잘린다`() {
        // 3:4 이미지를 1000×1000 정사각 화면에: 가로 맞춤 배율 1333 → 높이 1333, 위아래 166.7씩 잘림
        val c = FaceMapping.toScreen(FaceBox(0.5f, 0f, 0.2f, 0.75f), 1000f, 1000f)
        assertEquals(500f, c.x, eps)
        assertEquals(-166.67f, c.y, 0.1f)
    }

    @Test
    fun `흔들림 보정 - 처음 값은 그대로, 이후엔 일부만 따라간다`() {
        val a = FaceBox(0.5f, 0.5f, 0.2f, 0.75f)
        assertEquals(a, FaceMapping.smooth(null, a, 0.4f))

        val b = FaceBox(1.0f, 0.5f, 0.2f, 0.75f)
        val s = FaceMapping.smooth(a, b, 0.4f)
        assertEquals(0.7f, s.cx, 0.0001f)   // 0.5 + (1.0 - 0.5) * 0.4
        assertEquals(0.5f, s.cy, 0.0001f)
    }
}
