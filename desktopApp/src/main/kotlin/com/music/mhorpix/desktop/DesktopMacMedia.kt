package com.music.mhorpix.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * macOS media key and Now Playing integration via the MediaRemote private
 * framework — the same one Spotify, VLC and IINA use. This is the macOS
 * counterpart of [DesktopWindowsMedia] (SMTC) and [DesktopMprisController]
 * (Linux MPRIS): the player hands it state, it hands back button presses.
 *
 * Publishes the current song to the macOS lock-screen widget, the volume
 * flyout's media card, and the Now Playing widget, and receives the media
 * keys (F7/F8/F9, headphone buttons, Bluetooth controls, etc.) so the
 * system-wide play/pause/next/previous reach the player.
 *
 * Absent on non-macOS, everything here is a no-op.
 */
internal object DesktopMacMedia {

    private val started = AtomicBoolean(false)

    private val shutdownHookAdded = AtomicBoolean(false)

    @Volatile
    private var controller: Controller? = null

    /** What a media key does, filled in by the player. */
    internal class Controller(
        val onPlay: () -> Unit,
        val onPause: () -> Unit,
        val onNext: () -> Unit,
        val onPrevious: () -> Unit,
        val onStop: () -> Unit,
    )

    // ─────────────────────────── CoreFoundation (JNA) ──────────────────────

    /**
     * The subset of CoreFoundation's C API used to build the now-playing
     * dictionary that MediaRemote consumes.
     */
    private interface CoreFoundation : Library {
        // CFString
        fun CFStringCreateWithCString(
            alloc: Pointer?,
            cStr: ByteArray,
            encoding: Int,
        ): Pointer?

        // CFNumber
        fun CFNumberCreate(
            alloc: Pointer?,
            theType: Int,
            valuePtr: Pointer,
        ): Pointer?

        // CFDictionary
        fun CFDictionaryCreateMutable(
            alloc: Pointer?,
            capacity: Int,
            keyCallBacks: Pointer?,
            valueCallBacks: Pointer?,
        ): Pointer?

        fun CFDictionarySetValue(
            dict: Pointer,
            key: Pointer,
            value: Pointer,
        )

        fun CFRelease(cf: Pointer)
    }

    private const val kCFStringEncodingUTF8 = 0x08000100
    private const val kCFNumberFloat64Type = 13
    private const val kCFNumberSInt32Type = 3

    // MediaRemote command IDs (from reverse-engineering; these are stable
    // across macOS releases — Spotify, VLC and others rely on them).
    private const val MR_COMMAND_PLAY = 0
    private const val MR_COMMAND_PAUSE = 1
    private const val MR_COMMAND_TOGGLE_PLAY_PAUSE = 2
    private const val MR_COMMAND_NEXT_TRACK = 4
    private const val MR_COMMAND_PREVIOUS_TRACK = 5
    private const val MR_COMMAND_STOP = 8

    // MediaRemote playback states.
    private const val MR_PLAYBACK_STATE_PAUSED = 0
    private const val MR_PLAYBACK_STATE_PLAYING = 1

    /**
     * The MediaRemote private framework. The functions declared here are the
     * stable, widely-used subset that has not changed since macOS 10.15.
     */
    private interface MediaRemote : Library {
        /** Set the now-playing dictionary. Pass a CFDictionary, or NULL to clear. */
        fun MRMediaRemoteSetNowPlayingInfo(info: Pointer?): Int

        /** Set the playback state (0 = paused, 1 = playing). */
        fun MRMediaRemoteSetPlaybackState(state: Int): Int

        /** Set the elapsed time in seconds. */
        fun MRMediaRemoteSetElapsedTime(seconds: Double): Int

        /**
         * Register a C callback to receive media key / command events.
         * The signature is: void callback(int command, void *context).
         * Returns the registration token (or 0 on failure).
         */
        fun MRMediaRemoteRegisterForCommandNotifications(
            callback: MediaCommandCallback?,
            context: Pointer?,
        ): Int

        fun MRMediaRemoteUnregisterForCommandNotifications(token: Int): Int
    }

    /** The C callback type for media command notifications. */
    private fun interface MediaCommandCallback : Callback {
        fun invoke(command: Int, context: Pointer?)
    }

    private val cf: CoreFoundation? by lazy {
        if (!DesktopPlatform.isMac) null
        else runCatching { Native.load("CoreFoundation", CoreFoundation::class.java) }.getOrNull()
    }

    private val mr: MediaRemote? by lazy {
        if (!DesktopPlatform.isMac) null
        else runCatching {
            Native.load(
                "/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote",
                MediaRemote::class.java,
            )
        }.getOrNull()
    }

    private val available: Boolean by lazy {
        if (!DesktopPlatform.isMac) return@lazy false
        mr != null && cf != null
    }

    private var commandToken: Int = 0

    // A strong reference to the callback so the JNA-mediated C callback is not
    // collected while the framework still holds a function pointer to it.
    private var commandCallback: MediaCommandCallback? = null

    fun start(controller: Controller) {
        this.controller = controller
        if (!available || !started.compareAndSet(false, true)) return
        val mr = mr ?: return
        // Register for media-key / command notifications. The callback is kept
        // as a field so it survives for the framework's lifetime.
        commandCallback = MediaCommandCallback { command: Int, _: Pointer? -> onCommand(command) }
        runCatching {
            commandToken = mr.MRMediaRemoteRegisterForCommandNotifications(commandCallback, null)
        }.onFailure {
            DesktopTrackLog.log("macOS media command registration failed: ${it.message}")
        }
        if (shutdownHookAdded.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread(::stop, "mhorpix-macmedia-stop"))
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        val mr = mr ?: return
        if (commandToken != 0) {
            runCatching { mr.MRMediaRemoteUnregisterForCommandNotifications(commandToken) }
            commandToken = 0
        }
        runCatching { mr.MRMediaRemoteSetNowPlayingInfo(null) }
        commandCallback = null
    }

    /** Called from the native side (via the C callback) when a media key is pressed. */
    private fun onCommand(command: Int) {
        val target = controller ?: return
        when (command) {
            MR_COMMAND_PLAY -> target.onPlay()
            MR_COMMAND_PAUSE -> target.onPause()
            MR_COMMAND_TOGGLE_PLAY_PAUSE -> {
                // The player knows whether to play or pause; the system does not.
                // Prefer a toggle — the Controller's caller wires the right one.
                target.onPlay()
            }
            MR_COMMAND_NEXT_TRACK -> target.onNext()
            MR_COMMAND_PREVIOUS_TRACK -> target.onPrevious()
            MR_COMMAND_STOP -> target.onStop()
        }
    }

    /** Push the current song + position to the macOS Now Playing widget. */
    fun publish(state: DesktopPlaybackState) {
        if (!started.get()) return
        val mr = mr ?: return
        val cf = cf ?: return
        val song = state.song
        if (song == null) {
            runCatching { mr.MRMediaRemoteSetNowPlayingInfo(null) }
            return
        }
        runCatching {
            val dict = cf.CFDictionaryCreateMutable(null, 0, null, null) ?: return@runCatching
            try {
                putString(cf, dict, "title", song.title)
                putString(cf, dict, "artist", song.artist.orEmpty())
                putString(cf, dict, "album", song.albumName.orEmpty())
                putDouble(cf, dict, "duration", (state.durationMs / 1000.0).coerceAtLeast(0.0))
                putDouble(cf, dict, "elapsedTime", (state.positionMs / 1000.0).coerceAtLeast(0.0))
                putDouble(cf, dict, "playbackRate", if (state.isPlaying) 1.0 else 0.0)
                mr.MRMediaRemoteSetNowPlayingInfo(dict)
                mr.MRMediaRemoteSetPlaybackState(
                    if (state.isPlaying) MR_PLAYBACK_STATE_PLAYING else MR_PLAYBACK_STATE_PAUSED,
                )
                mr.MRMediaRemoteSetElapsedTime((state.positionMs / 1000.0).coerceAtLeast(0.0))
            } finally {
                cf.CFRelease(dict)
            }
        }.onFailure {
            DesktopTrackLog.log("macOS now-playing publish failed: ${it.message}")
        }
    }

    private fun cfString(cf: CoreFoundation, value: String): Pointer? {
        val bytes = (value + "\u0000").toByteArray(Charsets.UTF_8)
        return cf.CFStringCreateWithCString(null, bytes, kCFStringEncodingUTF8)
    }

    private fun cfDouble(cf: CoreFoundation, value: Double): Pointer? {
        val mem = com.sun.jna.Memory(8)
        mem.setDouble(0, value)
        return cf.CFNumberCreate(null, kCFNumberFloat64Type, mem)
    }

    private fun putString(cf: CoreFoundation, dict: Pointer, key: String, value: String) {
        val k = cfString(cf, key) ?: return
        val v = cfString(cf, value) ?: return
        try {
            cf.CFDictionarySetValue(dict, k, v)
        } finally {
            cf.CFRelease(k)
            cf.CFRelease(v)
        }
    }

    private fun putDouble(cf: CoreFoundation, dict: Pointer, key: String, value: Double) {
        val k = cfString(cf, key) ?: return
        val v = cfDouble(cf, value) ?: return
        try {
            cf.CFDictionarySetValue(dict, k, v)
        } finally {
            cf.CFRelease(k)
            cf.CFRelease(v)
        }
    }
}
