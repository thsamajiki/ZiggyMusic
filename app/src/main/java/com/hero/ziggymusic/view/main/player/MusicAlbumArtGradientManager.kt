package com.hero.ziggymusic.view.main.player

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.core.graphics.toColorInt
import com.hero.ziggymusic.R
import androidx.core.graphics.drawable.toDrawable

class MusicAlbumArtGradientManager(private val context: Context) {
    fun applyGradients(
        bitmap: Bitmap,
        albumBackground: View,
    ) {
        Palette.from(bitmap).generate { palette ->
            // 앨범아트에서 주요 컬러 추출 (없으면 fallback)
            val fallbackDominant = "#2B2B2B".toColorInt()
            val dominant = palette?.getDominantColor(fallbackDominant) ?: fallbackDominant
            val vibrant = palette?.getVibrantColor(dominant) ?: dominant
            val muted = palette?.getMutedColor(dominant) ?: dominant
            val topColor = darken(vibrant, 0.70f)
            val midColor = darken(muted, 0.55f)
            val bottomColor = darken(dominant, 0.78f)
            // (1) 세로 스크림: TOP -> BOTTOM
            val verticalScrim = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(topColor, midColor, bottomColor)
            ).apply {
                alpha = 235
            }
            // (2) 라디얼 비네팅: 가운데는 비교적 밝고, 가장자리로 갈수록 어두워짐
            val vignette = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                // 가운데는 투명에 가깝게, 바깥은 검정으로
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(Color.BLACK, 0),
                    ColorUtils.setAlphaComponent(Color.BLACK, 170)
                )
                // 중앙 기준 (정중앙 ~ 살짝 상단)
                setGradientCenter(0.5f, 0.45f)
                // 반지름
                gradientRadius = dpToPx(420f)
            }
            // (3) 레이어 합성 (스크림 + 비네팅) -> 가장 아래에 검은색 배경 추가
            val blackBackground =
                ContextCompat.getColor(context, R.color.dark_black).toDrawable() // 투명 방지용 검은색
            // 순서 중요: 검은배경 -> 세로 그라데이션 -> 비네팅
            val layer = LayerDrawable(arrayOf(blackBackground, verticalScrim, vignette))

            albumBackground.post {
                albumBackground.background = null
                if (context is Activity) {
                    val containerPlayer = context.findViewById<View>(R.id.containerPlayer)
                    // 부모 컨테이너에 그라데이션 적용 (상태바 영역까지 덮으려면 window 배경도 설정)
                    containerPlayer?.background = layer
                    try {
                        // stateful drawable로 인한 렌더링/공유 문제를 대부분 해결
                        val windowBackground = layer.constantState?.newDrawable(context.resources)?.mutate() ?: layer
                        context.window.setBackgroundDrawable(windowBackground)
                    } catch (e: Exception) {
                        Log.w("MusicAlbumArtGradient", "Failed to set window background: ${e.message}")
                    }
                } else {
                    // non-Activity context인 경우 폴백 적용
                    albumBackground.background = layer
                    Log.w(
                        "MusicAlbumArtGradient",
                        "Context is not Activity; applied gradient to albumBackground"
                    )
                }
            }
        }
    }

    private fun darken(color: Int, factor: Float = 0.8f): Int {
        return ColorUtils.blendARGB(color, Color.BLACK, 1f - factor)
    }

    private fun lighten(color: Int, factor: Float = 0.8f): Int {
        return ColorUtils.blendARGB(color, Color.WHITE, 1f - factor)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
