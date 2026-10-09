package com.tuntech.supertvstreamcast.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring

/** Shared timing so every screen moves with the same rhythm. Durations are milliseconds. */
object TvMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    const val Micro = 120
    const val Control = 180
    const val Card = 240
    const val Screen = 360
    const val Modal = 420
    const val Splash = 700
    /** Delay between neighbours in a staggered reveal. */
    const val Stagger = 55
    fun <T> selection() = spring<T>(dampingRatio = 0.72f, stiffness = 520f)
    fun <T> press() = spring<T>(dampingRatio = 0.58f, stiffness = 700f)
}
