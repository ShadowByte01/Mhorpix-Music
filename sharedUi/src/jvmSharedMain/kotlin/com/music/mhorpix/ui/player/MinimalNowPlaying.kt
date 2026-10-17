package com.music.mhorpix.ui.player

import com.music.mhorpix.playback.PlaybackPosition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** Selects the minimal "Now Playing" layout. Provided from the platform side. */
val LocalMinimalMode = androidx.compose.runtime.staticCompositionLocalOf { false }

/** The current playback volume, 0..1. Provided from the platform side. */
val LocalPlayerVolume = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** Called when the user drags the volume ring. */
val LocalOnVolumeChange = androidx.compose.runtime.staticCompositionLocalOf<(Float) -> Unit> { {} }

// ────────────────────────────────────────────────────────── colors ──

private val Surface = Color(0xFFF0F0F0)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF888888)
private val RingTrack = Color(0xFFD0D0D0)
private val RingProgress = Color(0xFF2A2A2A)
private val ArtOuter = Color(0xFFE8E8E8)
private val ArtInner = Color(0xFF1A1A1A)
private val ButtonOutline = Color(0xFFD0D0D0)

// ────────────────────────────────────────────────────────── main ──

/**
 * A clean, neumorphic minimal player — the "Minimal" mode layout.
 *
 * Light off-white surface with soft shadows. The circular album art sits in
 * the centre of a 210-degree volume ring with + and − buttons at top and
 * bottom. The seek bar is a separate horizontal line below. Transport buttons
 * (prev / pause / next) are at the very bottom. No glass, no blur, no lens —
 * the lightest render the app can produce, and fast on every device.
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
    modifier: Modifier = Modifier,
) {
    val volume = LocalPlayerVolume.current
    val onVolumeChange = LocalOnVolumeChange.current
    val pos = position.positionMs
    val fraction = remember(pos, durationMs) {
        if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .padding(4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Text("Back", color = TextPrimary, fontSize = 16.sp)
                }
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Rounded.Menu, "Menu", tint = TextPrimary)
                }
            }

            // ── Metadata (left-aligned) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    song.artist,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.title,
                    color = TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Circular volume ring + album art ──
            VolumeRingArt(
                thumbnailUrl = song.thumbnailUrl,
                volume = volume,
                onVolumeChange = onVolumeChange,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            )

            // ── Timeline / seek bar ──
            SeekBar(
                fraction = fraction,
                onSeekFraction = onSeekFraction,
                currentMs = pos,
                totalMs = durationMs,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ── Transport buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(
                    icon = Icons.Rounded.SkipPrevious,
                    desc = "Previous",
                    isPrimary = false,
                    onClick = onPrevious,
                )
                CircleButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    desc = if (isPlaying) "Pause" else "Play",
                    isPrimary = true,
                    onClick = onPlayPause,
                )
                CircleButton(
                    icon = Icons.Rounded.SkipNext,
                    desc = "Next",
                    isPrimary = false,
                    onClick = onNext,
                )
            }
        }
    }
}

// ────────────────────────────────────────────── volume ring + art ──

/**
 * A 210-degree volume arc with + at the top and − at the bottom. The album
 * art sits in the centre as a grey circle with a black inner circle. Dragging
 * the arc (or the dot) changes the volume; tapping +/− nudges it by 10%.
 */
@Composable
private fun VolumeRingArt(
    thumbnailUrl: String?,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The arc starts at 165° and sweeps 210° clockwise, so the gaps are at
    // the top (≈ 12 o'clock, for the +) and bottom (≈ 6 o'clock, for the −).
    val startAngle = 165f
    val sweepAngle = 210f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                            val dist = hypot(dx, dy)
                            if (dist < 10f) return@detectDragGestures
                            var angle = (atan2(dy, dx) * 180f / kotlin.math.PI.toFloat())
                            if (angle < 0) angle += 360f
                            val rel = ((angle - startAngle + 360f) % 360f) / sweepAngle
                            if (rel in 0f..1f) onVolumeChange(rel.coerceIn(0f, 1f))
                        },
                    )
                },
        ) {
            val stroke = 4.dp.toPx()
            val inset = stroke + 8.dp.toPx()
            val diameter = minOf(size.width, size.height) - inset * 2
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            // Background track
            drawArc(
                color = RingTrack,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            // Volume progress
            drawArc(
                color = RingProgress,
                startAngle = startAngle,
                sweepAngle = sweepAngle * volume,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            // Volume dot
            val handleAngle = startAngle + sweepAngle * volume
            val rad = Math.toRadians(handleAngle.toDouble()).toFloat()
            val r = diameter / 2f
            drawCircle(
                color = RingProgress,
                radius = stroke * 1.4f,
                center = Offset(size.width / 2f + r * cos(rad), size.height / 2f + r * sin(rad)),
            )
        }

        // + button (top)
        IconButton(
            onClick = { onVolumeChange((volume + 0.1f).coerceAtMost(1f)) },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text("+", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        // − button (bottom)
        IconButton(
            onClick = { onVolumeChange((volume - 0.1f).coerceAtLeast(0f)) },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("−", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // Album art: grey outer circle + black inner circle + thumbnail
        Box(
            modifier = Modifier
                .fillMaxSize(0.62f)
                .clip(CircleShape)
                .background(ArtOuter)
                .shadow(8.dp, CircleShape, ambientColor = Color(0xFFCCCCCC)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.82f)
                    .clip(CircleShape)
                    .background(ArtInner),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────── seek bar ──

@Composable
private fun SeekBar(
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
            Text(formatTime(currentMs), color = TextSecondary, fontSize = 12.sp)
            Text(formatTime(totalMs), color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
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
                val trackY = size.height / 2f
                drawLine(
                    color = RingTrack,
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = 3.dp.toPx(),
                )
                val progressX = size.width * fraction
                drawLine(
                    color = RingProgress,
                    start = Offset(0f, trackY),
                    end = Offset(progressX, trackY),
                    strokeWidth = 3.dp.toPx(),
                )
                drawCircle(
                    color = RingProgress,
                    radius = 7.dp.toPx(),
                    center = Offset(progressX, trackY),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────── transport button ──

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    val sizeDp = if (isPrimary) 60.dp else 48.dp
    val bgColor = if (isPrimary) Color(0xFF1A1A1A) else Surface
    val iconTint = if (isPrimary) Color.White else TextPrimary
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (isPrimary) Modifier.shadow(4.dp, CircleShape)
                else Modifier.shadow(2.dp, CircleShape, ambientColor = Color(0xFFDDDDDD)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!isPrimary) {
            Canvas(Modifier.size(sizeDp)) {
                drawCircle(
                    color = ButtonOutline,
                    radius = (size.minDimension / 2f) - 1.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        IconButton(onClick = onClick, modifier = Modifier.size(sizeDp)) {
            Icon(icon, desc, tint = iconTint, modifier = Modifier.size(if (isPrimary) 28.dp else 22.dp))
        }
    }
}

// ────────────────────────────────────────────────────────── helpers ──

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun hypot(a: Float, b: Float): Float = kotlin.math.hypot(a, b)
private fun atan2(a: Float, b: Float): Float = kotlin.math.atan2(a, b)
private fun cos(a: Float): Float = kotlin.math.cos(a)
private fun sin(a: Float): Float = kotlin.math.sin(a)
