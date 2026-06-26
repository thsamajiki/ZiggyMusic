package com.hero.ziggymusic.view.main.player

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import com.hero.ziggymusic.R
import androidx.core.graphics.drawable.toDrawable

class MusicTrackAlbumArtGradientManager(private val context: Context) {
    fun applyGradients(
        bitmap: Bitmap,
        albumBackground: View,
        onVisualizerColorReady: ((Int) -> Unit)? = null,
    ) {
        Palette.from(bitmap).generate { palette ->
            val fallbackDominant = "#2B2B2B".toColorInt()
            val dominant = palette?.getDominantColor(fallbackDominant) ?: fallbackDominant
            val vibrant = palette?.getVibrantColor(dominant) ?: dominant
            val muted = palette?.getMutedColor(dominant) ?: dominant

            val topColor = darken(vibrant, 0.70f)
            val midColor = darken(muted, 0.55f)
            val bottomColor = darken(dominant, 0.78f)
            val gradientLayer = createGradientLayer(topColor, midColor, bottomColor)
            val visualizerColor = createVisualizerColor(topColor, midColor, bottomColor)

            albumBackground.post {
                onVisualizerColorReady?.invoke(visualizerColor)

                val activity = context as? Activity
                if (activity == null) {
                    albumBackground.crossfadeTo(gradientLayer)
                    Log.w(TAG, "Context is not Activity; applied gradient to albumBackground")
                    return@post
                }

                albumBackground.background = null
                activity.findViewById<View>(R.id.containerPlayer)?.crossfadeTo(gradientLayer)
                activity.crossfadeWindowTo(gradientLayer)
                activity.makeStatusBarTransparent()
            }
        }
    }

    fun resetToDarkBackground(albumBackground: View, animate: Boolean = false) {
        albumBackground.post {
            val darkColor = ContextCompat.getColor(context, R.color.dark_black)
            val darkBackground = darkColor.toDrawable()

            val activity = context as? Activity
            if (activity == null) {
                if (animate) {
                    albumBackground.crossfadeTo(darkBackground)
                } else {
                    albumBackground.background = darkBackground.newDrawableInstance()
                }
                return@post
            }

            albumBackground.background = null
            val containerPlayer = activity.findViewById<View>(R.id.containerPlayer)
            if (animate) {
                containerPlayer?.crossfadeTo(darkBackground)
                activity.crossfadeWindowTo(darkBackground)
                activity.makeStatusBarTransparent()
            } else {
                containerPlayer?.background = darkBackground.newDrawableInstance()
                activity.window.setBackgroundDrawable(darkBackground.newDrawableInstance())
                activity.makeStatusBarTransparent()
            }
        }
    }

    private fun createGradientLayer(topColor: Int, midColor: Int, bottomColor: Int): Drawable {
        val blackBackground = ContextCompat.getColor(context, R.color.dark_black).toDrawable()
        val verticalScrim = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, midColor, bottomColor)
        ).apply {
            alpha = 235
        }
        val vignette = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                ColorUtils.setAlphaComponent(Color.BLACK, 0),
                ColorUtils.setAlphaComponent(Color.BLACK, 170)
            )
            setGradientCenter(0.5f, 0.45f)
            gradientRadius = dpToPx(420f)
        }

        return LayerDrawable(arrayOf(blackBackground, verticalScrim, vignette))
    }

    private fun Activity.crossfadeWindowTo(nextBackground: Drawable) {
        val finalBackground = nextBackground.newDrawableInstance()
        val transition = TransitionDrawable(
            arrayOf(
                window.decorView.background?.newDrawableInstance()
                    ?: ContextCompat.getColor(context, R.color.dark_black).toDrawable(),
                finalBackground
            )
        ).apply {
            isCrossFadeEnabled = false
        }

        window.setBackgroundDrawable(transition)
        transition.startTransition(BACKGROUND_FADE_DURATION_MS)
        window.decorView.postDelayed(
            {
                window.setBackgroundDrawable(finalBackground)
            },
            BACKGROUND_FADE_DURATION_MS.toLong()
        )
    }

    private fun Activity.makeStatusBarTransparent() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
    }

    private fun View.crossfadeTo(nextBackground: Drawable) {
        val finalBackground = nextBackground.newDrawableInstance()
        val transition = TransitionDrawable(
            arrayOf(
                background?.newDrawableInstance()
                    ?: ContextCompat.getColor(context, R.color.dark_black).toDrawable(),
                finalBackground
            )
        ).apply {
            isCrossFadeEnabled = false
        }

        background = transition
        transition.startTransition(BACKGROUND_FADE_DURATION_MS)
        postDelayed(
            {
                if (background === transition) {
                    background = finalBackground
                }
            },
            BACKGROUND_FADE_DURATION_MS.toLong()
        )
    }

    private fun Drawable.newDrawableInstance(): Drawable {
        return constantState?.newDrawable(context.resources)?.mutate() ?: mutate()
    }

    private fun darken(color: Int, factor: Float = 0.8f): Int {
        return ColorUtils.blendARGB(color, Color.BLACK, 1f - factor)
    }

    private fun createVisualizerColor(topColor: Int, midColor: Int, bottomColor: Int): Int {
        val baseColor = ColorUtils.blendARGB(midColor, topColor, 0.35f)
        val backgroundColor = ColorUtils.blendARGB(midColor, bottomColor, 0.35f)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)
        val backgroundLuminance = ColorUtils.calculateLuminance(backgroundColor).toFloat()

        hsl[1] = hsl[1].coerceIn(VISUALIZER_MIN_SATURATION, VISUALIZER_MAX_SATURATION)
        hsl[2] = targetVisualizerLightness(backgroundLuminance)

        if (isWarmHue(hsl[0])) {
            hsl[1] *= VISUALIZER_WARM_SATURATION_FACTOR
        }

        val tunedColor = ColorUtils.blendARGB(
            ColorUtils.HSLToColor(hsl),
            backgroundColor,
            VISUALIZER_BACKGROUND_BLEND_RATIO
        )

        return ensureVisualizerContrast(tunedColor, backgroundColor)
    }

    private fun targetVisualizerLightness(backgroundLuminance: Float): Float {
        return when {
            backgroundLuminance < 0.08f -> 0.58f
            backgroundLuminance < 0.16f -> 0.52f
            backgroundLuminance < 0.28f -> 0.46f
            else -> 0.34f
        }
    }

    private fun ensureVisualizerContrast(color: Int, backgroundColor: Int): Int {
        if (ColorUtils.calculateContrast(color, backgroundColor) >= VISUALIZER_MIN_CONTRAST) {
            return color
        }

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val backgroundLuminance = ColorUtils.calculateLuminance(backgroundColor)

        hsl[2] = if (backgroundLuminance < 0.35) {
            (hsl[2] + VISUALIZER_CONTRAST_LIGHTNESS_STEP).coerceAtMost(0.68f)
        } else {
            (hsl[2] - VISUALIZER_CONTRAST_LIGHTNESS_STEP).coerceAtLeast(0.22f)
        }

        return ColorUtils.HSLToColor(hsl)
    }

    private fun isWarmHue(hue: Float): Boolean {
        return hue <= 45f || hue >= 340f
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    private companion object {
        const val TAG = "MusicAlbumArtGradient"
        const val BACKGROUND_FADE_DURATION_MS = 150
        const val VISUALIZER_MIN_SATURATION = 0.18f
        const val VISUALIZER_MAX_SATURATION = 0.46f
        const val VISUALIZER_WARM_SATURATION_FACTOR = 0.65f
        const val VISUALIZER_BACKGROUND_BLEND_RATIO = 0.12f
        const val VISUALIZER_MIN_CONTRAST = 1.65
        const val VISUALIZER_CONTRAST_LIGHTNESS_STEP = 0.14f
    }
}
