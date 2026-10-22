package com.music.mhorpix.ui.screens.p2p

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.music.mhorpix.data.p2p.P2pConnectionInfo
import com.music.mhorpix.data.p2p.P2pController
import com.music.mhorpix.data.p2p.P2pRoom
import com.music.mhorpix.data.p2p.P2pTrack

// ──────────────────────────── main P2P screen ────────────────────────────

/**
 * The P2P Listen Together entry screen. Shows Create Room / Join Room when
 * idle, or the room view when in a session.
 */
@Composable
fun P2pListenTogetherScreen(
    onDismiss: () -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mode by P2pController.mode.collectAsState()
    val room by P2pController.room.collectAsState()
    val connection by P2pController.connection.collectAsState()

    if (mode == P2pController.Mode.IDLE || room == null) {
        P2pLandingScreen(onCreate = {
            P2pController.createRoom(context)
        }, onJoinByCode = { code ->
            P2pController.joinRoomByCode(context, code)
        }, onJoinByQr = onOpenScanner, onDismiss = onDismiss, modifier = modifier)
    } else {
        P2pRoomView(room = room!!, connection = connection, onLeave = {
            P2pController.leaveRoom(context)
            onDismiss()
        }, modifier = modifier)
    }
}

// ──────────────────────────── landing (create / join) ───────────────────

@Composable
private fun P2pLandingScreen(
    onCreate: () -> Unit,
    onJoinByCode: (String) -> Unit,
    onJoinByQr: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text("P2P Listen Together", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Groups,
                    contentDescription = "Listen Together",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("Listen together, no server needed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(40.dp))

        if (showCreate) {
            CreatingRoomView(onDone = { showCreate = false })
        } else if (showJoin) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Enter Room Code", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase().take(6) },
                    textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OptionButton(text = "Scan QR", icon = Icons.Filled.QrCode2, onClick = onJoinByQr)
                    OptionButton(
                        text = "Join",
                        icon = Icons.Rounded.PlayArrow,
                        enabled = codeInput.length == 6,
                        onClick = { onJoinByCode(codeInput); showJoin = false },
                    )
                }
            }
        } else {
            OptionButton(
                text = "Create Room",
                icon = Icons.Rounded.Groups,
                primary = true,
                onClick = { showCreate = true },
            )
            Spacer(Modifier.height(12.dp))
            OptionButton(
                text = "Join Room",
                icon = Icons.Rounded.PlayArrow,
                onClick = { showJoin = true },
            )
        }
    }
}

@Composable
private fun CreatingRoomView(onDone: () -> Unit) {
    val context = LocalContext.current
    val room by P2pController.room.collectAsState()
    val connection by P2pController.connection.collectAsState()

    if (room != null && connection == P2pController.Connection.LIVE) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Room ${room!!.code}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            val info = P2pController.getConnectionInfo()
            if (info != null) {
                val bmp = remember(info.encode()) { generateQrBitmap(info.encode(), 400) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Room QR",
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Scan or share the code: ${room!!.code}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            OptionButton(text = "Enter Room", icon = Icons.Rounded.PlayArrow, primary = true, onClick = onDone)
        }
    } else {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(120.dp)) {
            CircularProgressIndicator()
        }
    }
}

// ──────────────────────────── room view ───────────────────────────────────

@Composable
private fun P2pRoomView(
    room: P2pRoom,
    connection: P2pController.Connection,
    onLeave: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var chatInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(room.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onLeave) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Leave")
            }
        }
        Spacer(Modifier.height(8.dp))
        // Members
        Text("Members (${room.members.size})", style = MaterialTheme.typography.titleSmall)
        Text(room.members.joinToString(", ") { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        // Now playing
        room.currentTrack?.let { track ->
            NowPlayingCard(track, isPlaying = room.isPlaying, onPlayPause = {
                P2pController.togglePlay()
            }, onNext = { P2pController.skipNext() })
            Spacer(Modifier.height(16.dp))
        }

        // Queue with voting
        if (room.queue.isNotEmpty()) {
            Text("Queue", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(room.queue.withIndex().toList()) { (idx, track) ->
                    QueueRow(
                        track = track,
                        isCurrent = idx == room.currentIndex,
                        voteCount = room.votes[track.videoId]?.size ?: 0,
                        onVote = { P2pController.voteFor(track.videoId, P2pController.deviceId) },
                    )
                }
                items(room.chat) { msg ->
                    Text("${msg.from}: ${msg.text}", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Queue is empty. Add songs to start.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Chat input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            IconButton(onClick = {
                if (chatInput.isNotBlank()) {
                    P2pController.sendChat("you", chatInput)
                    chatInput = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun NowPlayingCard(track: P2pTrack, isPlaying: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
        Text(track.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, "Next")
            }
        }
    }
}

@Composable
private fun QueueRow(track: P2pTrack, isCurrent: Boolean, voteCount: Int, onVote: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onVote) {
            Icon(Icons.Outlined.HowToVote, "Vote")
        }
        Text("$voteCount", modifier = Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ──────────────────────────── shared UI bits ──────────────────────────────

@Composable
private fun OptionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, text, tint = fg)
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontWeight = FontWeight.Medium)
    }
}

// ──────────────────────────── QR generation ──────────────────────────────

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return runCatching {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    }.getOrNull()
}
