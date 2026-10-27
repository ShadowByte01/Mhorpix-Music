package com.music.mhorpix.ui.components

import android.view.SoundEffectConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = 6.2831855f

/** When true, the whole app renders in the nostalgic Y2K + Frutiger Aero style. */
val LocalY2kMode = staticCompositionLocalOf { false }

// ─────────────────────────── Y2K + Frutiger Aero palette ────────────────────
// Silver chrome, Aqua blue, AND the glossy water/nature greens of Frutiger Aero
// (Windows Vista glass, 2006 wallpapers, water-droplet bubbles).

object Y2kPalette {
    // Chrome / silver
    val ChromeDark = Color(0xFFA0A8B4)
    val ChromeMid = Color(0xFFD0D6DE)
    val ChromeLight = Color(0xFFF8FAFC)
    val White = Color(0xFFFFFFFF)

    // Aqua (Y2K blue)
    val Aqua = Color(0xFF1E9FFF)
    val AquaLight = Color(0xFF7FD4FF)
    val AquaDeep = Color(0xFF0066CC)

    // Frutiger Aero (water / nature / glass)
    val AeroMint = Color(0xFFB0F0D0)
    val AeroCyan = Color(0xFFA0E8F0)
    val AeroSky = Color(0xFF80D0FF)
    val AeroGreen = Color(0xFF60D880)
    val AeroLeaf = Color(0xFF90E8A0)
    val AeroDeep = Color(0xFF008060)

    // Holographic
    val Purple = Color(0xFF8B5FD6)
    val Pink = Color(0xFFFF6EC7)

    // Text
    val TextDark = Color(0xFF1A1A2E)
    val TextLight = Color(0xFFE8F0FF)
    val LcdText = Color(0xFF003366)  // LCD blue-black
    val LcdBg = Color(0xFFB8E8D0)   // LCD green-cyan

    // Panels
    val PanelBg = Color(0xFFF0F8FF)  // alice blue
    val GlossyWhite = Color(0xE6FFFFFF)
    val Shadow = Color(0x40000000)
}

// ─────────────────────────── Frutiger Aero background ────────────────────────

/**
 * The signature Frutiger Aero backdrop: a soft aurora gradient (mint → cyan →
 * sky blue) with scattered, glossy water droplets that float slowly. This is
 * the 2006 Windows wallpaper look — wet, bright, nature.
 */
@Composable
fun FrutigerAeroBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aero")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Restart),
        label = "drift",
    )
    val droplets = remember { List(12) { Droplet.random() } }
    Box(modifier = modifier.drawBehind {
        // Aurora gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Y2kPalette.AeroMint,
                    Y2kPalette.AeroCyan,
                    Y2kPalette.AeroSky,
                    Y2kPalette.AeroMint.copy(alpha = 0.8f),
                ),
            ),
        )
        // Water droplets — glossy circles with radial gradient highlights
        droplets.forEachIndexed { i, d ->
            val t = (drift + d.phase) % 1f
            val x = (d.x + sin((t * TWO_PI).toDouble()) * d.amp).toFloat()
            val y = (d.y + sin(((t + d.phase) * TWO_PI).toDouble()) * d.amp * 0.5f).toFloat()
            val r = d.radius
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent,
                    ),
                    center = Offset(x, y),
                    radius = r,
                ),
                center = Offset(x, y),
                radius = r,
            )
            // Droplet highlight (small bright dot)
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = r * 0.15f,
                center = Offset(x - r * 0.25f, y - r * 0.25f),
            )
        }
    })
}

/** Animated water droplets only (no gradient) — for overlaying on top of the
 *  aurora background drawn in the paintBackdrop. */
@Composable
fun FrutigerAeroDroplets(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "droplets")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Restart),
        label = "drift2",
    )
    val droplets = remember { List(12) { Droplet.random() } }
    Box(modifier = modifier.drawBehind {
        droplets.forEachIndexed { i, d ->
            val t = (drift + d.phase) % 1f
            val x = (d.x + sin((t * TWO_PI).toDouble()) * d.amp).toFloat()
            val y = (d.y + sin(((t + d.phase) * TWO_PI).toDouble()) * d.amp * 0.5f).toFloat()
            val r = d.radius
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent,
                    ),
                    center = Offset(x, y),
                    radius = r,
                ),
                center = Offset(x, y),
                radius = r,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = r * 0.15f,
                center = Offset(x - r * 0.25f, y - r * 0.25f),
            )
        }
    })
}

private data class Droplet(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float,
    val amp: Float,
) {
    companion object {
        fun random() = Droplet(
            x = Random.nextFloat() * 1080f,
            y = Random.nextFloat() * 2400f,
            radius = 20f + Random.nextFloat() * 50f,
            phase = Random.nextFloat(),
            amp = 10f + Random.nextFloat() * 30f,
        )
    }
}

private const val PI = 3.14159265358979f

// ─────────────────────────── glossy chrome modifier ──────────────────────────

/** Vertical chrome gradient — dark bottom, bright top, wet-shine highlight. */
fun Modifier.y2kChrome(
    cornerRadius: Int = 20,
    elevation: Int = 6,
): Modifier = this
    .shadow(elevation.dp, RoundedCornerShape(cornerRadius.dp))
    .clip(RoundedCornerShape(cornerRadius.dp))
    .drawBehind {
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Y2kPalette.ChromeLight,
                    Y2kPalette.White,
                    Y2kPalette.ChromeMid,
                    Y2kPalette.ChromeDark,
                ),
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                startY = 0f,
                endY = h * 0.35f,
            ),
        )
    }

/** Translucent aero-glass panel — frosted white with a bright top edge. */
fun Modifier.y2kGlass(
    cornerRadius: Int = 18,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius.dp))
    .drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.5f),
                    Y2kPalette.AeroCyan.copy(alpha = 0.3f),
                ),
            ),
        )
        drawLine(
            color = Color.White,
            start = Offset(0f, 1f),
            end = Offset(size.width, 1f),
            strokeWidth = 2f,
        )
    }

/** Holographic gradient panel — silver, aqua, purple, pink. */
fun Modifier.y2kHolo(cornerRadius: Int = 24): Modifier = this
    .shadow(12.dp, RoundedCornerShape(cornerRadius.dp))
    .clip(RoundedCornerShape(cornerRadius.dp))
    .drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Y2kPalette.ChromeLight,
                    Y2kPalette.AeroSky.copy(alpha = 0.4f),
                    Y2kPalette.Purple.copy(alpha = 0.2f),
                    Y2kPalette.Pink.copy(alpha = 0.15f),
                    Y2kPalette.ChromeMid,
                ),
            ),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.5f, size.height * 0.5f),
            ),
        )
    }

// ─────────────────────────── CD disc modifier ────────────────────────────────

/**
 * Draws a CD/DVD-style rainbow ring around the content. The iridescent
 * rainbow of a polycarbonate disc viewed at an angle.
 */
fun Modifier.y2kCdRing(): Modifier = this.drawBehind {
    val r = minOf(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    // Rainbow ring (7 concentric arcs)
    val rainbow = listOf(
        Color(0xFFFF0040), Color(0xFFFF8000), Color(0xFFFFE000),
        Color(0xFF00E040), Color(0xFF00B0FF), Color(0xFF4000FF), Color(0xFF8000FF),
    )
    rainbow.forEachIndexed { i, color ->
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = r * (0.92f - i * 0.01f),
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
    // Chrome bezel
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Y2kPalette.ChromeLight, Y2kPalette.ChromeDark),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
        style = Stroke(width = 4.dp.toPx()),
    )
}

// ─────────────────────────── Aqua button ─────────────────────────────────────

@Composable
fun Y2kButton(
    text: String,
    icon: ImageVector? = null,
    primary: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val bg = if (primary) {
        Brush.verticalGradient(
            colors = listOf(Y2kPalette.AeroSky, Y2kPalette.Aqua, Y2kPalette.AquaDeep),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Y2kPalette.ChromeLight, Y2kPalette.ChromeMid),
        )
    }
    val fg = if (primary) Y2kPalette.TextLight else Y2kPalette.TextDark
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                drawRect(brush = bg)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.4f,
                    ),
                )
            }
            .clickable {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, text, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/** LCD-style text — blue-black on green-cyan, monospace feel. */
@Composable
fun Y2kLcdText(text: String, modifier: Modifier = Modifier, fontSize: Int = 14) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Y2kPalette.LcdBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = Y2kPalette.LcdText,
            fontSize = fontSize.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Plays the system click sound. */
@Composable
fun y2kClick(): () -> Unit {
    val view = LocalView.current
    return { view.playSoundEffect(SoundEffectConstants.CLICK) }
}
