package com.music.mhorpix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

// Poster colors
private val DeepBlue = Color(0xFF3B4CA0)
private val Cream = Color(0xFFD9D2C8)
private val ChromeLight = Color(0xFFE8ECF1)
private val ChromeDark = Color(0xFFC0C5CE)
private val Lavender = Color(0xFFB8A9E8)

/**
 * The transition overlay that plays when Y2K mode is toggled on. Shows a
 * spinning CD disc with a rotating clock hand over a cream poster background
 * with "BACK TO THE 90s" text, then calls [onFinished] after ~2.5 seconds.
 */
@Composable
fun Y2kTransitionOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
) {
    if (!visible) return

    var showText by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "cd-spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "rotation",
    )

    LaunchedEffect(Unit) {
        delay(300)
        showText = true
        delay(2200)
        onFinished()
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(400)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Cream)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(DeepBlue.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(size.width * 0.3f, size.height * 0.4f),
                            radius = size.minDimension * 0.6f,
                        ),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Lavender.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width * 0.7f, size.height * 0.6f),
                            radius = size.minDimension * 0.5f,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // Spinning CD disc with clock hand
            Box(
                modifier = Modifier.drawBehind {
                    val r = 120f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ChromeLight, ChromeDark, ChromeLight),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                    val rainbow = listOf(
                        Color(0xFFFF0040), Color(0xFFFF8000), Color(0xFFFFE000),
                        Color(0xFF00E040), Color(0xFF00B0FF), Color(0xFF4000FF),
                        Color(0xFF8000FF),
                    )
                    rainbow.forEachIndexed { i, col ->
                        drawCircle(
                            color = col.copy(alpha = 0.3f),
                            radius = r * (0.9f - i * 0.01f),
                            center = c,
                            style = Stroke(width = 3f),
                        )
                    }
                    drawCircle(ChromeDark, 15f, c)
                },
            ) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    val cx = this.size.width / 2f
                    val cy = this.size.height / 2f
                    val handLen = 70f
                    val rad = Math.toRadians(rotation.toDouble()).toFloat()
                    drawLine(
                        color = DeepBlue,
                        start = Offset(cx, cy),
                        end = Offset(cx + handLen * kotlin.math.cos(rad), cy + handLen * kotlin.math.sin(rad)),
                        strokeWidth = 5f,
                    )
                    drawCircle(DeepBlue, 8f, Offset(cx, cy))
                }
            }

            AnimatedVisibility(visible = showText, enter = fadeIn(tween(400))) {
                Text(
                    "BACK TO THE 90s",
                    color = DeepBlue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.offset(y = 180.dp),
                )
            }
        }
    }
}
