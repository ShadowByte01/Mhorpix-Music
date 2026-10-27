package com.music.mhorpix.ui.player

import com.music.mhorpix.playback.PlaybackPosition

import android.view.SoundEffectConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.mhorpix.data.model.Song
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Poster-inspired palette
private val DeepBlue = Color(0xFF3B4CA0)
private val Cream = Color(0xFFD9D2C8)
private val LcdGreen = Color(0xFFA8D86E)
private val LcdTextDark = Color(0xFF003366)
private val ChromeLight = Color(0xFFE8ECF1)
private val ChromeMid = Color(0xFFD0D6DE)
private val ChromeDark = Color(0xFFC0C5CE)
private val Lavender = Color(0xFFB8A9E8)
private val AquaBlue = Color(0xFF1E9FFF)
private val VinylBlack = Color(0xFF0A0A0A)
private val VinylGroove = Color(0xFF1A1A1A)
private val TextWhite = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF1A1A2E)

/**
 * A skeuomorphic vinyl turntable Now Playing screen. When Y2K mode is on, the
 * player transforms into a realistic record player: a chrome chassis with the
 * Mhorpix three-wave logo etched into it, a spinning black vinyl record with
 * concentric grooves, a metallic tonearm, and retro transport buttons.
 */
@Composable
fun Y2kCdPlayer(
    song: Song,
    isPlaying: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val pos = position.positionMs
    val fraction = remember(pos, durationMs) {
        if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    // Vinyl spin — slow rotation when playing
    val transition = rememberInfiniteTransition(label = "vinyl")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing), // 3s per rotation (33⅓ RPM)
            RepeatMode.Restart,
        ),
        label = "spin",
    )
    val vinylAngle = if (isPlaying) rotation else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Lavender.copy(alpha = 0.9f),
                            Color(0xFFD4C5F9),
                            Lavender,
                            Color(0xFF9B7FBF),
                        ),
                    ),
                )
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent,
                            ChromeLight.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { view.playSoundEffect(SoundEffectConstants.CLICK); onBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextWhite)
                }
                Text("M H O R P I X", color = ChromeLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                IconButton(onClick = { view.playSoundEffect(SoundEffectConstants.CLICK); onOpenMenu() }) {
                    Icon(Icons.Rounded.MoreVert, "Menu", tint = TextWhite)
                }
            }

            // ── Turntable ──
            Turntable(
                thumbnailUrl = song.thumbnailUrl,
                isPlaying = isPlaying,
                rotation = vinylAngle,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f),
            )

            // ── Song info ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    song.title,
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    color = TextWhite.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Action row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionIcon(Icons.Rounded.Mic, "Mic") { view.playSoundEffect(SoundEffectConstants.CLICK) }
                ActionIcon(Icons.Rounded.Equalizer, "EQ") { view.playSoundEffect(SoundEffectConstants.CLICK) }
                ActionIcon(Icons.Rounded.Download, "Download") { view.playSoundEffect(SoundEffectConstants.CLICK) }
                ActionIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") { view.playSoundEffect(SoundEffectConstants.CLICK) }
            }

            // ── Progress bar ──
            VinylProgressBar(
                fraction = fraction,
                onSeekFraction = onSeekFraction,
                currentMs = pos,
                totalMs = durationMs,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Transport buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportIcon(Icons.Rounded.Shuffle, "Shuffle") {
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                }
                TransportIcon(Icons.Rounded.SkipPrevious, "Previous") {
                    view.playSoundEffect(SoundEffectConstants.CLICK); onPrevious()
                }
                // Center play/pause — large circle with thin border
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .drawBehind {
                            val r = size.minDimension / 2f
                            val c = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(ChromeLight, ChromeMid, ChromeDark),
                                    center = c,
                                    radius = r,
                                ),
                                radius = r,
                                center = c,
                            )
                            drawCircle(ChromeDark, r - 2.dp.toPx(), c, style = Stroke(width = 2.dp.toPx()))
                            // Top shine
                            drawCircle(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height * 0.4f,
                                ),
                                radius = r,
                                center = c,
                            )
                        }
                        .clickable {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = DeepBlue,
                        modifier = Modifier.size(30.dp),
                    )
                }
                TransportIcon(Icons.Rounded.SkipNext, "Next") {
                    view.playSoundEffect(SoundEffectConstants.CLICK); onNext()
                }
                TransportIcon(Icons.Rounded.Repeat, "Repeat") {
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                }
            }
        }
    }
}

// ──────────────────────────── Turntable ────────────────────────────────────

@Composable
private fun Turntable(
    thumbnailUrl: String?,
    isPlaying: Boolean,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    // The chassis: a rounded-square chrome panel with the Mhorpix 3-wave logo
    // etched into the top-left corner. The vinyl sits on top, spinning.
    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                // Chrome chassis gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(ChromeLight, ChromeMid, ChromeDark, ChromeMid),
                    ),
                )
                // Subtle texture noise (fine dots)
                drawRect(ChromeDark.copy(alpha = 0.05f))

                // ── Mhorpix 3-wave logo etched into top-left ──
                val logoX = 24f
                val logoY = 24f
                val waveW = 30f
                val waveH = 6f
                val gap = 4f
                for (i in 0..2) {
                    val y = logoY + i * (waveH + gap)
                    // Draw three curved horizontal lines (sound waves)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(logoX, y + waveH / 2f)
                        cubicTo(
                            logoX + waveW * 0.25f, y,
                            logoX + waveW * 0.75f, y + waveH,
                            logoX + waveW, y + waveH / 2f,
                        )
                    }
                    drawPath(path, ChromeDark.copy(alpha = 0.4f), style = Stroke(width = 2.5f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // ── Vinyl record (rotating) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .rotate(rotation)
                    .drawBehind {
                        val r = size.minDimension / 2f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        // Black vinyl base
                        drawCircle(VinylBlack, r, c)
                        // Concentric grooves
                        for (i in 1..20) {
                            drawCircle(
                                VinylGroove,
                                r * (0.3f + i * 0.033f),
                                c,
                                style = Stroke(width = 0.5f),
                            )
                        }
                        // Rainbow sheen (iridescent, like light hitting vinyl)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFFF0040).copy(alpha = 0.05f),
                                    Color(0xFF00B0FF).copy(alpha = 0.05f),
                                    Color.Transparent,
                                ),
                                center = Offset(c.x + r * 0.3f, c.y - r * 0.3f),
                                radius = r,
                            ),
                            radius = r,
                            center = c,
                        )
                        // Center: subtle dark recess where the label sits
                        drawCircle(VinylGroove, r * 0.3f, c)
                        drawCircle(VinylBlack, r * 0.29f, c)
                        // Spindle hole
                        drawCircle(ChromeDark, r * 0.03f, c)
                    },
            ) {
                // Album art — perfectly centered, circular, sized like a real
                // vinyl record label (~28% of the disc diameter). No ring/border;
                // it sits flush inside the black vinyl, rotating with the disc.
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.28f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                }
            }

            // ── Tonearm (static, doesn't rotate) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val pivotX = size.width * 0.88f
                        val pivotY = size.height * 0.12f
                        val tipX = size.width * 0.52f
                        val tipY = size.height * 0.52f
                        // Tonearm shaft (chrome)
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(ChromeLight, ChromeDark, ChromeLight),
                                start = Offset(pivotX, pivotY),
                                end = Offset(tipX, tipY),
                            ),
                            start = Offset(pivotX, pivotY),
                            end = Offset(tipX, tipY),
                            strokeWidth = 6.dp.toPx(),
                        )
                        // Pivot (chrome circle)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(ChromeLight, ChromeDark),
                                center = Offset(pivotX, pivotY),
                                radius = 10.dp.toPx(),
                            ),
                            radius = 10.dp.toPx(),
                            center = Offset(pivotX, pivotY),
                        )
                        // Headshell (small rectangle at the tip)
                        drawCircle(ChromeDark, 5.dp.toPx(), Offset(tipX, tipY))
                    },
            )
        }
    }
}

// ──────────────────────────── small action icons ──────────────────────────

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, desc, tint = TextWhite.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TransportIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, desc, tint = TextWhite, modifier = Modifier.size(26.dp))
    }
}

// ──────────────────────────── vinyl progress bar ───────────────────────────

@Composable
private fun VinylProgressBar(
    fraction: Float,
    onSeekFraction: (Float) -> Unit,
    currentMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(currentMs), color = TextWhite.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(formatTime(totalMs), color = TextWhite.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat()
                            if (w > 0) onSeekFraction((change.position.x / w).coerceIn(0f, 1f))
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height / 2f
                drawLine(ChromeDark, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                drawLine(TextWhite, Offset(0f, y), Offset(size.width * fraction, y), 3.dp.toPx())
                drawCircle(TextWhite, 7.dp.toPx(), Offset(size.width * fraction, y))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}
