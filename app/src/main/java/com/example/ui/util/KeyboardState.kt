package com.example.ui.util

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

@Stable
class KeyboardState {
    var isVisible by mutableStateOf(false)
        internal set

    var isAnimating by mutableStateOf(false)
        internal set

    var imeHeightPx by mutableIntStateOf(0)
        internal set
}

val LocalKeyboardState = staticCompositionLocalOf { KeyboardState() }

/**
 * Dedicated KeyboardState provider using ViewCompat.setWindowInsetsAnimationCallback
 * and ViewCompat.setOnApplyWindowInsetsListener to detect and manage keyboard visibility
 * transitions in a performant way, avoiding frame drops during IME animations.
 */
@Composable
fun ProvideKeyboardState(
    view: View = LocalView.current,
    content: @Composable () -> Unit
) {
    val keyboardState = remember { KeyboardState() }

    DisposableEffect(view) {
        // Listener for static insets changes
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            
            keyboardState.isVisible = isImeVisible
            keyboardState.imeHeightPx = imeHeight
            
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        // Animation callback for smooth 0-lag tracking during keyboard open/close
        val animationCallback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    keyboardState.isAnimating = true
                }
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    keyboardState.isAnimating = true
                }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                val hasImeAnim = runningAnimations.any { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }
                if (hasImeAnim) {
                    keyboardState.isAnimating = true
                    val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    keyboardState.imeHeightPx = imeHeight
                    keyboardState.isVisible = imeHeight > 0
                }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    keyboardState.isAnimating = false
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    if (rootInsets != null) {
                        val isImeVisible = rootInsets.isVisible(WindowInsetsCompat.Type.ime())
                        val imeHeight = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        keyboardState.isVisible = isImeVisible
                        keyboardState.imeHeightPx = imeHeight
                    }
                }
            }
        }

        ViewCompat.setWindowInsetsAnimationCallback(view, animationCallback)

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
        }
    }

    CompositionLocalProvider(LocalKeyboardState provides keyboardState) {
        content()
    }
}
