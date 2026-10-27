package com.music.mhorpix.data.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Device-based identity: if the user isn't signed in, they get a persistent
 * identity named after their device. The deviceId is a UUID stored in prefs;
 * the name is the device's model name (e.g. "Pixel 8").
 */
object P2pDeviceIdentity {
    fun get(context: Context): Identity {
        val prefs = context.getSharedPreferences("mhorpix_p2p", Context.MODE_PRIVATE)
        val id = prefs.getString("device_id", null) ?: run {
            val new = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", new).apply()
            new
        }
        val name = prefs.getString("device_name", null) ?: run {
            val model = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
                ?.takeIf { it.isNotBlank() }
                ?: android.os.Build.MODEL
            prefs.edit().putString("device_name", model).apply()
            model
        }
        return Identity(id, name)
    }

    data class Identity(val id: String, val name: String)
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * The P2P Listen Together controller. Owns the room state and the transport.
 *
 * As host: opens a [ServerSocket] on a random port, advertises it via NSD,
 * accepts guest connections, applies incoming control messages, and broadcasts
 * the full room state after every change.
 *
 * As guest: discovers the host via NSD (or connects directly from a QR),
 * opens a [Socket], and relays control messages to the host while receiving
 * state updates.
 */
object P2pController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    enum class Mode { IDLE, HOST, GUEST }
    enum class Connection { DISCONNECTED, CONNECTING, LIVE }

    private val _mode = MutableStateFlow(Mode.IDLE)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _connection = MutableStateFlow(Connection.DISCONNECTED)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _room = MutableStateFlow<P2pRoom?>(null)
    val room: StateFlow<P2pRoom?> = _room.asStateFlow()

    private var identity: P2pDeviceIdentity.Identity? = null
    val deviceId: String get() = identity?.id ?: ""

    // Host-side state
    private var serverSocket: ServerSocket? = null
    private val guestSockets = ConcurrentHashMap<String, Socket>() // deviceId â†’ socket
    private var nsdManager: NsdManager? = null
    private var nsdService: NsdServiceInfo? = null
    private var hostRoom: P2pRoom? = null

    // Guest-side state
    private var guestSocket: Socket? = null

    fun init(context: Context) {
        identity = P2pDeviceIdentity.get(context)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ host â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Creates a room asynchronously â€” the server socket + NSD registration
     *  run on Dispatchers.IO so the UI thread is never blocked. */
    fun createRoom(context: Context, initialTrack: P2pTrack? = null) {
        if (_mode.value != Mode.IDLE) return
        scope.launch {
            val me = identity ?: P2pDeviceIdentity.get(context).also { identity = it }
            val code = generateCode()
            _mode.value = Mode.HOST
            _connection.value = Connection.CONNECTING
            runCatching {
                // Port 0 = let the OS pick a free port (instant, no collision risk)
                serverSocket = ServerSocket(0)
                val port = serverSocket!!.localPort
                nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                val service = NsdServiceInfo().apply {
                    serviceName = "mhorpix-$code"
                    serviceType = "_mhorpix._tcp."
                    setPort(port)
                }
                nsdService = service
                runCatching { nsdManager?.registerService(service, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
                val room = P2pRoom(
                    code = code,
                    hostName = me.name,
                    hostDeviceId = me.id,
                    members = listOf(P2pMember(me.id, me.name, isHost = true)),
                    queue = initialTrack?.let { listOf(it) } ?: emptyList(),
                    currentIndex = if (initialTrack != null) 0 else -1,
                    isPlaying = false,
                    positionMs = 0,
                    anchorMs = System.currentTimeMillis(),
                    autoplay = true,
                )
                hostRoom = room
                _room.value = room
                _connection.value = Connection.LIVE
                scope.launch { acceptLoop() }
            }.onFailure {
                _mode.value = Mode.IDLE
                _connection.value = Connection.DISCONNECTED
            }
        }
    }

    private fun acceptLoop() {
        val server = serverSocket ?: return
        while (!server.isClosed) {
            runCatching {
                val socket = server.accept()
                scope.launch { handleGuest(socket) }
            }
        }
    }

    private fun handleGuest(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
        var guestDeviceId: String? = null
        try {
            while (socket.isConnected && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val frame = runCatching { json.decodeFromString<P2pFrame>(line) }.getOrNull() ?: continue
                when (frame) {
                    is P2pFrame.Join -> {
                        guestDeviceId = frame.deviceId
                        guestSockets[frame.deviceId] = socket
                        updateHostRoom { room ->
                            room.copy(members = room.members.filter { it.deviceId != frame.deviceId } +
                                P2pMember(frame.deviceId, frame.name, isHost = false))
                        }
                        sendToGuest(socket, P2pFrame.State(hostRoom!!))
                    }
                    is P2pFrame.Play -> updateHostRoom { it.copy(isPlaying = true, positionMs = frame.positionMs, anchorMs = System.currentTimeMillis()) }
                    is P2pFrame.Pause -> updateHostRoom { it.copy(isPlaying = false, positionMs = frame.positionMs) }
                    is P2pFrame.Seek -> updateHostRoom { it.copy(positionMs = frame.positionMs, anchorMs = System.currentTimeMillis()) }
                    is P2pFrame.Next -> playNext()
                    is P2pFrame.QueueAdd -> updateHostRoom { it.copy(queue = it.queue + frame.track) }
                    is P2pFrame.Vote -> updateHostRoom { room ->
                        val voters = (room.votes[frame.videoId] ?: emptyList())
                        val updated = if (frame.voterId in voters) voters - frame.voterId else voters + frame.voterId
                        room.copy(votes = room.votes + (frame.videoId to updated))
                    }
                    is P2pFrame.Chat -> updateHostRoom { it.copy(chat = (it.chat + P2pChatMessage(frame.from, frame.text, frame.atMs)).takeLast(100)) }
                    is P2pFrame.SetAutoplay -> updateHostRoom { it.copy(autoplay = frame.enabled) }
                    is P2pFrame.Leave -> {
                        updateHostRoom { room ->
                            room.copy(members = room.members.filter { it.deviceId != frame.deviceId })
                        }
                        break
                    }
                    is P2pFrame.Kick -> {}
                    is P2pFrame.State -> {}
                }
            }
        } catch (_: Exception) {
        } finally {
            guestDeviceId?.let { id ->
                guestSockets.remove(id)
                updateHostRoom { room -> room.copy(members = room.members.filter { it.deviceId != id }) }
            }
            runCatching { socket.close() }
        }
    }

    private fun updateHostRoom(transform: (P2pRoom) -> P2pRoom) {
        val current = hostRoom ?: return
        val updated = transform(current).copy(updatedAtMs = System.currentTimeMillis())
        hostRoom = updated
        _room.value = updated
        broadcastState()
    }

    private fun playNext() {
        val current = hostRoom ?: return
        val nextIndex = pickNextIndex(current)
        if (nextIndex >= 0) {
            updateHostRoom { it.copy(currentIndex = nextIndex, isPlaying = true, positionMs = 0, anchorMs = System.currentTimeMillis(), votes = emptyMap()) }
        } else {
            updateHostRoom { it.copy(isPlaying = false) }
        }
    }

    private fun pickNextIndex(room: P2pRoom): Int {
        if (room.queue.isEmpty()) return -1
        // Most-voted track wins; ties broken by queue order.
        val mostVoted = room.votes.maxByOrNull { it.value.size }
        if (mostVoted != null && mostVoted.value.isNotEmpty()) {
            val idx = room.queue.indexOfFirst { it.videoId == mostVoted.key }
            if (idx >= 0) return idx
        }
        // Otherwise, the next track in queue order.
        return (room.currentIndex + 1).coerceAtMost(room.queue.lastIndex)
    }

    private fun broadcastState() {
        val room = hostRoom ?: return
        val frame = P2pFrame.State(room)
        val text = json.encodeToString(P2pFrame.serializer(), frame)
        guestSockets.values.toList().forEach { socket ->
            runCatching {
                val w = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
                w.println(text)
            }
        }
    }

    private fun sendToGuest(socket: Socket, frame: P2pFrame) {
        runCatching {
            val text = json.encodeToString(P2pFrame.serializer(), frame)
            PrintWriter(OutputStreamWriter(socket.getOutputStream()), true).println(text)
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ guest â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun joinRoom(info: P2pConnectionInfo, context: Context): Result<Unit> {
        if (_mode.value != Mode.IDLE) return Result.failure(IllegalStateException("Already in a session"))
        val me = identity ?: P2pDeviceIdentity.get(context).also { identity = it }
        _mode.value = Mode.GUEST
        _connection.value = Connection.CONNECTING
        scope.launch {
            runCatching {
                val socket = Socket(info.host, info.port)
                guestSocket = socket
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
                writer.println(json.encodeToString(P2pFrame.serializer(), P2pFrame.Join(me.id, me.name)))
                _connection.value = Connection.LIVE
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (socket.isConnected && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    val frame = runCatching { json.decodeFromString<P2pFrame>(line) }.getOrNull() ?: continue
                    if (frame is P2pFrame.State) {
                        _room.value = frame.room
                    }
                }
            }.onFailure {
                _connection.value = Connection.DISCONNECTED
                _mode.value = Mode.IDLE
            }
        }
        return Result.success(Unit)
    }

    fun joinRoomByCode(context: Context, code: String) {
        // For code-based joining, discover via NSD.
        val me = identity ?: P2pDeviceIdentity.get(context).also { identity = it }
        _mode.value = Mode.GUEST
        _connection.value = Connection.CONNECTING
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        var listener: NsdManager.DiscoveryListener? = null
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _connection.value = Connection.DISCONNECTED }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceName == "mhorpix-$code") {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, code: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val info = P2pConnectionInfo(code, si.host.hostAddress ?: return, si.port)
                            joinRoom(info, context)
                            listener?.let { runCatching { nsdManager?.stopServiceDiscovery(it) } }
                        }
                    })
                }
            }
            override fun onServiceLost(si: NsdServiceInfo) {}
        }
        listener?.let { runCatching { nsdManager?.discoverServices("_mhorpix._tcp.", NsdManager.PROTOCOL_DNS_SD, it) } }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ controls (guest â†’ host) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun sendToHost(frame: P2pFrame) {
        val socket = guestSocket ?: return
        runCatching {
            val text = json.encodeToString(P2pFrame.serializer(), frame)
            PrintWriter(OutputStreamWriter(socket.getOutputStream()), true).println(text)
        }
    }

    fun play() = sendToHost(P2pFrame.Play(0, System.currentTimeMillis()))
    fun pause() = sendToHost(P2pFrame.Pause(0))
    fun seek(ms: Long) = sendToHost(P2pFrame.Seek(ms))
    fun next() = sendToHost(P2pFrame.Next)
    fun addTrack(track: P2pTrack) = sendToHost(P2pFrame.QueueAdd(track))
    fun voteFor(videoId: String, voterId: String) = sendToHost(P2pFrame.Vote(videoId, voterId))
    fun sendChat(from: String, text: String) = sendToHost(P2pFrame.Chat(from, text, System.currentTimeMillis()))
    fun setAutoplay(enabled: Boolean) = sendToHost(P2pFrame.SetAutoplay(enabled))

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ host controls (host â†’ local state) â”€â”€â”€â”€â”€â”€â”€

    fun hostPlay() = updateHostRoom { it.copy(isPlaying = true, anchorMs = System.currentTimeMillis()) }
    fun hostPause() = updateHostRoom { it.copy(isPlaying = false) }
    fun hostSeek(ms: Long) = updateHostRoom { it.copy(positionMs = ms, anchorMs = System.currentTimeMillis()) }
    fun hostNext() = playNext()
    fun hostAddTrack(track: P2pTrack) = updateHostRoom { it.copy(queue = it.queue + track) }
    fun hostVoteFor(videoId: String, voterId: String) = updateHostRoom { room ->
        val voters = (room.votes[videoId] ?: emptyList())
        val updated = if (voterId in voters) voters - voterId else voters + voterId
        room.copy(votes = room.votes + (videoId to updated))
    }
    fun hostSendChat(from: String, text: String) = updateHostRoom { it.copy(chat = (it.chat + P2pChatMessage(from, text, System.currentTimeMillis())).takeLast(100)) }
    fun hostSetAutoplay(enabled: Boolean) = updateHostRoom { it.copy(autoplay = enabled) }

    /** Convenience: route a control to the right path based on mode. */
    fun togglePlay() { if (_mode.value == Mode.HOST) { if (_room.value?.isPlaying == true) hostPause() else hostPlay() } else play() }
    fun seekTo(ms: Long) { if (_mode.value == Mode.HOST) hostSeek(ms) else seek(ms) }
    fun skipNext() { if (_mode.value == Mode.HOST) hostNext() else next() }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ leave / teardown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun leaveRoom(context: Context) {
        val me = identity
        if (_mode.value == Mode.GUEST) {
            me?.let { sendToHost(P2pFrame.Leave(it.id)) }
            runCatching { guestSocket?.close() }
            guestSocket = null
        }
        if (_mode.value == Mode.HOST) {
            // Tell all guests the room is closing.
            val room = hostRoom
            if (room != null) {
                guestSockets.values.toList().forEach { socket ->
                    runCatching { socket.close() }
                }
            }
            guestSockets.clear()
            runCatching { serverSocket?.close() }
            serverSocket = null
            runCatching { nsdService?.let { nsdManager?.unregisterService(registrationListener) } }
            nsdService = null
            hostRoom = null
        }
        _mode.value = Mode.IDLE
        _connection.value = Connection.DISCONNECTED
        _room.value = null
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getConnectionInfo(): P2pConnectionInfo? {
        val room = hostRoom ?: return null
        val port = serverSocket?.localPort ?: return null
        val host = getLocalIpAddress() ?: return null
        return P2pConnectionInfo(room.code, host, port)
    }

    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun getLocalIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<InetAddress>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(si: NsdServiceInfo) {}
        override fun onRegistrationFailed(si: NsdServiceInfo, code: Int) {}
        override fun onServiceUnregistered(si: NsdServiceInfo) {}
        override fun onUnregistrationFailed(si: NsdServiceInfo, code: Int) {}
    }
}
