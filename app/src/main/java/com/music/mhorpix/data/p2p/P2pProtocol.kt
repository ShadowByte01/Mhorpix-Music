package com.music.mhorpix.data.p2p

import kotlinx.serialization.Serializable

// ─────────────────────────── wire-protocol messages ────────────────────────
// Every frame is a single line of JSON (terminated by \n). The host is the
// authority: it owns the room state, applies incoming control messages, and
// broadcasts the full state to every member after each change.

@Serializable
sealed class P2pFrame {
    /** A guest → host join request. */
    @Serializable
    data class Join(val deviceId: String, val name: String) : P2pFrame()

    /** Host → everyone: here is the full room state. */
    @Serializable
    data class State(val room: P2pRoom) : P2pFrame()

    /** Guest → host: play / resume. */
    @Serializable
    data class Play(val positionMs: Long = 0, val anchorMs: Long = 0) : P2pFrame()

    /** Guest → host: pause. */
    @Serializable
    data class Pause(val positionMs: Long = 0) : P2pFrame()

    /** Guest → host: seek. */
    @Serializable
    data class Seek(val positionMs: Long) : P2pFrame()

    /** Guest → host: go to the next track (or the most-voted one). */
    @Serializable
    data object Next : P2pFrame()

    /** Guest → host: add a track to the queue. */
    @Serializable
    data class QueueAdd(val track: P2pTrack) : P2pFrame()

    /** Guest → host: vote for a track. */
    @Serializable
    data class Vote(val videoId: String, val voterId: String) : P2pFrame()

    /** Guest → host / host → all: a chat message. */
    @Serializable
    data class Chat(val from: String, val text: String, val atMs: Long) : P2pFrame()

    /** Guest → host: toggle autoplay. */
    @Serializable
    data class SetAutoplay(val enabled: Boolean) : P2pFrame()

    /** Guest → host: I'm leaving. */
    @Serializable
    data class Leave(val deviceId: String) : P2pFrame()

    /** Host → guest: you've been kicked. */
    @Serializable
    data class Kick(val deviceId: String) : P2pFrame()
}

// ─────────────────────────── room state ─────────────────────────────────────

@Serializable
data class P2pRoom(
    val code: String,
    val hostName: String,
    val hostDeviceId: String,
    val members: List<P2pMember> = emptyList(),
    val queue: List<P2pTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val anchorMs: Long = 0,
    val autoplay: Boolean = true,
    val votes: Map<String, List<String>> = emptyMap(), // videoId → list of voterIds
    val chat: List<P2pChatMessage> = emptyList(),
    val updatedAtMs: Long = 0,
) {
    val currentTrack: P2pTrack? get() = queue.getOrNull(currentIndex)
    val inRoom: Boolean get() = code.isNotBlank()
}

@Serializable
data class P2pMember(
    val deviceId: String,
    val name: String,
    val isHost: Boolean = false,
    val connected: Boolean = true,
)

@Serializable
data class P2pTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
)

@Serializable
data class P2pChatMessage(
    val from: String,
    val text: String,
    val atMs: Long,
)

// ─────────────────────────── connection info (encoded in the QR) ────────────

@Serializable
data class P2pConnectionInfo(
    val code: String,
    val host: String,
    val port: Int,
) {
    /** Encoded as "code|host:port" for the QR payload. */
    fun encode(): String = "$code|$host:$port"

    companion object {
        fun decode(raw: String): P2pConnectionInfo? {
            val pipe = raw.indexOf('|')
            if (pipe < 0) return null
            val code = raw.substring(0, pipe)
            val rest = raw.substring(pipe + 1)
            val colon = rest.lastIndexOf(':')
            if (colon < 0) return null
            val host = rest.substring(0, colon)
            val port = rest.substring(colon + 1).toIntOrNull() ?: return null
            return P2pConnectionInfo(code, host, port)
        }
    }
}
