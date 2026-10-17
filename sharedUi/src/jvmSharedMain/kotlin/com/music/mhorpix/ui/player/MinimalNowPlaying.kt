package com.music.mhorpix.ui.player

import com.music.mhorpix.playback.PlaybackPosition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.mhorpix.data.model.Song
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The CompositionLocal that selects the minimal "Now Playing" layout. Provided
 * from the platform side (Android's [AppSettings.minimalMode], the desktop's own
 * appearance flag) — see [com.music.mhorpix.ui.AppUi] / MainActivity.
 */
val LocalMinimalMode = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A clean, minimal full-screen player — the "Minimal" mode layout.
 *
 * Dark, immersive background; left-aligned metadata; a circular album art ringed
 * by a 270-degree arc scrubber with a draggable white handle; circular transport
 * buttons; and a bottom row for lyrics and the queue. Every surface is a
 * solid fill — no glass, no blur, no lens — so this is the lightest render the
 * app can produce.
 */
@Composable
fun MinimalNowPlaying(
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
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val fraction = remember(position.positionMs, durationMs) {
        val pos = position.positionMs
        if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "Back",
                    color = Color.White,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenMenu) {
                    Icon(
                        Icons.Rounded.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                    )
                }
            }

            // ── Metadata (left-aligned) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = song.albumName ?: "",
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Circular album art with arc scrubber ──
            CircularArtScrubber(
                thumbnailUrl = song.thumbnailUrl,
                fraction = fraction,
                onSeekFraction = onSeekFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 8.dp),
            )

            // ── Time labels ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(position.positionMs),
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                )
                Text(
                    text = "-" + formatTime((durationMs - position.positionMs).coerceAtLeast(0L)),
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // ── Transport buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous — outline circle
                TransportButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    isPrimary = false,
                    onClick = onPrevious,
                )
                // Play/Pause — solid circle
                TransportButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    isPrimary = true,
                    onClick = onPlayPause,
                )
                // Next — outline circle
                TransportButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    isPrimary = false,
                    onClick = onNext,
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // ── Bottom bar: lyrics + queue ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenLyrics) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = "Lyrics",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────── circular art + arc scrubber ──

/**
 * The circular album art ringed by a 270-degree arc scrubber. The arc has gaps
 * at top and bottom; a white "+" sits in the top gap and a draggable white dot
 * marks the current position. Dragging the dot (or anywhere on the ring) seeks.
 */
@Composable
private fun CircularArtScrubber(
    thumbnailUrl: String?,
    fraction: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // The arc sweeps 270°, from 135° to 405° (i.e. clockwise from bottom-left
    // around through left, top-gap, right, to bottom-gap).
    val startAngle = 135f
    val sweepAngle = 270f
    // How far the handle has travelled along the arc, 0..1.
    val handleAngleDeg = startAngle + sweepAngle * fraction

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = change.position.x - cx
                            val dy = change.position.y - cy
                            // Convert the touch angle to the arc's frame.
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < 0) angle += 360f
                            // Map the angle onto the 0..1 sweep.
                            val rel = ((angle - startAngle + 360f) % 360f) / sweepAngle
                            if (rel in 0f..1f) {
                                onSeekFraction(rel.coerceIn(0f, 1f))
                            }
                        },
                    )
                },
        ) {
            val strokePx = 6.dp.toPx()
            val diameter = minOf(size.width, size.height) - strokePx * 2
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            // Background arc
            drawArc(
                color = Color(0xFF333333),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            // Progress arc
            drawArc(
                color = Color.White,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            // Handle (white dot at the current position on the arc)
            val rad = Math.toRadians(handleAngleDeg.toDouble()).toFloat()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = diameter / 2f
            val hx = cx + r * cos(rad)
            val hy = cy + r * sin(rad)
            drawCircle(
                color = Color.White,
                radius = strokePx * 1.3f,
                center = androidx.compose.ui.geometry.Offset(hx, hy),
            )
            // Plus sign at the top gap (12 o'clock)
            val plusY = cy - r - strokePx * 1.5f
            val plusLen = 6.dp.toPx()
            drawLine(
                color = Color(0xFF888888),
                start = androidx.compose.ui.geometry.Offset(cx - plusLen, plusY),
                end = androidx.compose.ui.geometry.Offset(cx + plusLen, plusY),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                color = Color(0xFF888888),
                start = androidx.compose.ui.geometry.Offset(cx, plusY - plusLen),
                end = androidx.compose.ui.geometry.Offset(cx, plusY + plusLen),
                strokeWidth = 2.dp.toPx(),
            )
        }
        // Album art (circular, inside the ring)
        val artPad = 20.dp
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Album art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(artPad)
                .clip(CircleShape),
        )
    }
}

// ─────────────────────────────── transport button ──

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    val buttonSize = if (isPrimary) 64.dp else 52.dp
    val borderColor = if (isPrimary) Color.White else Color(0xFF333333)
    val bgColor = if (isPrimary) Color.White else Color.Transparent
    val iconTint = if (isPrimary) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        // Outline ring drawn via Canvas for non-primary buttons
        if (!isPrimary) {
            Canvas(modifier = Modifier.size(buttonSize)) {
                val stroke = 2.dp.toPx()
                drawCircle(
                    color = borderColor,
                    radius = (this.size.minDimension - stroke) / 2f,
                    style = Stroke(width = stroke),
                )
            }
        }
        IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(if (isPrimary) 32.dp else 28.dp),
            )
        }
    }
}

// ─────────────────────────────── helpers ──

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
