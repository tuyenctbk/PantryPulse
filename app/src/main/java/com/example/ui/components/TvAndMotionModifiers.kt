package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Detects whether the app is currently running on an Android TV / Leanback device.
 */
fun Context.isTelevisionDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return isTvMode || hasLeanback
}

@Composable
fun rememberIsAndroidTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTelevisionDevice() }
}

/**
 * Adds crisp, high-visibility D-pad focus ring & subtle spring lift for Android TV
 * and external keyboard navigation, without drawing an unfocused hairline border.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(18.dp),
    scaleOnFocus: Float = 1.02f,
    makeFocusable: Boolean = false
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tv_focus_scale"
    )
    val focusRingColor = MaterialTheme.colorScheme.primary

    val base = this
        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isFocused) {
                Modifier.border(
                    width = 2.5.dp,
                    color = focusRingColor,
                    shape = shape
                )
            } else {
                Modifier
            }
        )

    if (makeFocusable) base.focusable() else base
}

/**
 * Adds tactile spring press-down scaling without intercepting downstream click/ripple events.
 */
fun Modifier.pressScaleEffect(
    pressedScale: Float = 0.97f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                isPressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                isPressed = false
            }
        }
}
