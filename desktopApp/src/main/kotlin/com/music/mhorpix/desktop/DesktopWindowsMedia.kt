package com.music.mhorpix.desktop

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Windows System Media Transport Controls (SMTC) and hardware media key integration.
 *
 * Catches hardware media keys (Fn + F-keys, dedicated media buttons, Bluetooth
 * headphones) via a Low-Level Keyboard Hook (WH_KEYBOARD_LL) and WinRT System
 * Media Transport Controls (SMTC).
 *
 * Implemented using JNA so it runs on all Windows 10/11 machines without requiring
 * any external MSVC or MinGW compiled DLLs.
 */
internal object DesktopWindowsMedia {

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
        val onPlayPause: (() -> Unit)? = null,
    )

    private val available: Boolean get() = DesktopPlatform.isWindows

    // WinRT ComBase library interface
    private interface ComBase : com.sun.jna.Library {
        fun RoInitialize(initType: Int): Int
        fun RoUninitialize()
        fun WindowsCreateString(sourceString: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
        fun RoGetActivationFactory(activatableClassId: Pointer?, iid: Pointer, factory: PointerByReference): Int
    }

    private val combase: ComBase? by lazy {
        if (!DesktopPlatform.isWindows) null
        else runCatching { Native.load("combase", ComBase::class.java) }.getOrNull()
    }

    private interface QueryInterfaceCallback : Callback {
        fun invoke(thisPtr: Pointer, riid: Pointer, ppvObject: PointerByReference): Int
    }
    private interface AddRefReleaseCallback : Callback {
        fun invoke(thisPtr: Pointer): Int
    }
    private interface InvokeCallback : Callback {
        fun invoke(thisPtr: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    // Keep strong references to prevent GC from freeing native callbacks
    private var eventHandlerQi: QueryInterfaceCallback? = null
    private var eventHandlerAddRef: AddRefReleaseCallback? = null
    private var eventHandlerRelease: AddRefReleaseCallback? = null
    private var eventHandlerInvoke: InvokeCallback? = null
    private var eventHandlerComObject: Memory? = null
    private var eventHandlerVtable: Memory? = null

    private var keyboardHook: WinUser.HHOOK? = null
    private var keyboardHookProc: WinUser.LowLevelKeyboardProc? = null

    private var smtcPointer: Pointer? = null
    private var smtcWindow: WinDef.HWND? = null
    private var smtcWndProc: WinUser.WindowProc? = null
    private const val SMTC_CLASS_NAME = "MhorpixSmtcWindow"

    private var mediaThread: Thread? = null
    private var mediaThreadId: Int = 0

    private val lastTriggerTime = AtomicLong(0)

    fun start(controller: Controller) {
        this.controller = controller
        if (!available || !started.compareAndSet(false, true)) return

        mediaThread = Thread({
            mediaThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
            initSmtcAndHook()
            val msg = WinUser.MSG()
            val user32 = User32.INSTANCE
            while (started.get()) {
                val result = user32.GetMessage(msg, null, 0, 0)
                if (result <= 0) break
                user32.TranslateMessage(msg)
                user32.DispatchMessage(msg)
            }
            cleanupSmtcAndHook()
        }, "mhorpix-windows-media").apply {
            isDaemon = true
            start()
        }

        if (shutdownHookAdded.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread(::stop, "mhorpix-smtc-stop"))
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        controller = null
        if (mediaThreadId != 0) {
            User32.INSTANCE.PostThreadMessage(mediaThreadId, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }
        mediaThread?.join(1000)
        mediaThread = null
    }

    private fun initSmtcAndHook() {
        val user32 = User32.INSTANCE

        // 1. Install Low-Level Keyboard Hook
        keyboardHookProc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
            if (nCode >= 0) {
                val msg = wParam.toInt()
                if (msg == WinUser.WM_KEYDOWN || msg == WinUser.WM_SYSKEYDOWN) {
                    val vk = info.vkCode
                    when (vk) {
                        0xB3, 0xFA -> triggerDebounced(BUTTON_PLAY)        // VK_MEDIA_PLAY_PAUSE, VK_PLAY
                        0xB0 -> triggerDebounced(BUTTON_NEXT)              // VK_MEDIA_NEXT_TRACK
                        0xB1 -> triggerDebounced(BUTTON_PREVIOUS)          // VK_MEDIA_PREV_TRACK
                        0xB2 -> triggerDebounced(BUTTON_STOP)              // VK_MEDIA_STOP
                    }
                }
            }
            user32.CallNextHookEx(keyboardHook, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(info.pointer)))
        }
        keyboardHook = user32.SetWindowsHookEx(
            WinUser.WH_KEYBOARD_LL,
            keyboardHookProc,
            Kernel32.INSTANCE.GetModuleHandle(null),
            0,
        )

        // 2. Install WinRT SystemMediaTransportControls
        runCatching {
            val cb = combase ?: return@runCatching
            // RO_INIT_SINGLETHREADED = 0 (STA apartment required for window-associated WinRT objects)
            cb.RoInitialize(0)

            val str = "Windows.Media.SystemMediaTransportControls"
            val hstrRef = PointerByReference()
            if (cb.WindowsCreateString(WString(str), str.length, hstrRef) != 0) return@runCatching
            val hstr = hstrRef.value

            val interopIid = GUID.fromString("{ddb0472d-c911-4a1f-86d9-dc3d71a95f5a}")
            val factoryRef = PointerByReference()
            val hrFact = cb.RoGetActivationFactory(hstr, interopIid.pointer, factoryRef)
            cb.WindowsDeleteString(hstr)
            if (hrFact != 0 || factoryRef.value == null) return@runCatching

            val interop = factoryRef.value
            val interopVtable = interop.getPointer(0)
            val getForWindowFunc = Function.getFunction(interopVtable.getPointer(6L * Native.POINTER_SIZE))

            val wndProc = object : WinUser.WindowProc {
                override fun callback(hwnd: WinDef.HWND, uMsg: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT {
                    return user32.DefWindowProc(hwnd, uMsg, wParam, lParam)
                }
            }
            smtcWndProc = wndProc

            val hInst = Kernel32.INSTANCE.GetModuleHandle(null)
            val wndClass = WinUser.WNDCLASSEX().apply {
                cbSize = size()
                lpfnWndProc = wndProc
                hInstance = hInst
                lpszClassName = SMTC_CLASS_NAME
            }
            user32.RegisterClassEx(wndClass)

            smtcWindow = user32.CreateWindowEx(
                0,
                SMTC_CLASS_NAME,
                "Mhorpix",
                0,
                0, 0, 0, 0,
                null, null, hInst, null
            )

            val smtcIid = GUID.fromString("{99fa3ff4-1742-42a6-902e-087d41f965ec}")
            val smtcRef = PointerByReference()
            val hrGet = getForWindowFunc.invokeInt(arrayOf(interop, smtcWindow?.pointer, smtcIid.pointer, smtcRef))
            if (hrGet != 0 || smtcRef.value == null) return@runCatching
            val smtc = smtcRef.value
            smtcPointer = smtc

            val smtcVtable = smtc.getPointer(0)
            // put_IsEnabled(true) -> index 11
            Function.getFunction(smtcVtable.getPointer(11L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, 1))
            // put_IsPlayEnabled(true) -> index 13
            Function.getFunction(smtcVtable.getPointer(13L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, 1))
            // put_IsPauseEnabled(true) -> index 15
            Function.getFunction(smtcVtable.getPointer(15L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, 1))
            // put_IsStopEnabled(true) -> index 17
            Function.getFunction(smtcVtable.getPointer(17L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, 1))
            // put_IsPreviousEnabled(true) -> index 25
            Function.getFunction(smtcVtable.getPointer(25L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, 1))
            // put_IsNextEnabled(true) -> index 27
            Function.getFunction(smtcVtable.getPointer(27L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, 1))

            // Build ITypedEventHandler COM object
            val vt = Memory(4L * Native.POINTER_SIZE)
            val obj = Memory(Native.POINTER_SIZE.toLong())
            obj.setPointer(0, vt)

            val qi = object : QueryInterfaceCallback {
                override fun invoke(thisPtr: Pointer, riid: Pointer, ppvObject: PointerByReference): Int {
                    ppvObject.value = obj
                    return 0
                }
            }
            val addRef = object : AddRefReleaseCallback {
                override fun invoke(thisPtr: Pointer): Int = 1
            }
            val rel = object : AddRefReleaseCallback {
                override fun invoke(thisPtr: Pointer): Int = 1
            }
            val inv = object : InvokeCallback {
                override fun invoke(thisPtr: Pointer, sender: Pointer?, args: Pointer?): Int {
                    if (args != null) {
                        val argsVt = args.getPointer(0)
                        val getButtonFunc = Function.getFunction(argsVt.getPointer(6L * Native.POINTER_SIZE))
                        val btnRef = IntByReference()
                        if (getButtonFunc.invokeInt(arrayOf(args, btnRef)) == 0) {
                            when (btnRef.value) {
                                0 -> triggerDebounced(BUTTON_PLAY)
                                1 -> triggerDebounced(BUTTON_PAUSE)
                                2 -> triggerDebounced(BUTTON_STOP)
                                6 -> triggerDebounced(BUTTON_NEXT)
                                7 -> triggerDebounced(BUTTON_PREVIOUS)
                            }
                        }
                    }
                    return 0
                }
            }

            eventHandlerQi = qi
            eventHandlerAddRef = addRef
            eventHandlerRelease = rel
            eventHandlerInvoke = inv
            eventHandlerVtable = vt
            eventHandlerComObject = obj

            vt.setPointer(0L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(qi))
            vt.setPointer(1L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(addRef))
            vt.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(rel))
            vt.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(inv))

            // add_ButtonPressed -> index 32
            val tokenRef = LongByReference()
            Function.getFunction(smtcVtable.getPointer(32L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, obj, tokenRef))
        }.onFailure {
            DesktopTrackLog.log("SMTC initialization warning: ${it.message}")
        }
    }

    private fun cleanupSmtcAndHook() {
        val user32 = User32.INSTANCE

        keyboardHook?.let {
            user32.UnhookWindowsHookEx(it)
            keyboardHook = null
        }
        keyboardHookProc = null

        smtcPointer?.let {
            val vtable = it.getPointer(0)
            runCatching {
                Function.getFunction(vtable.getPointer(11L * Native.POINTER_SIZE)).invokeInt(arrayOf(it, 0))
                Function.getFunction(vtable.getPointer(2L * Native.POINTER_SIZE)).invokeInt(arrayOf(it))
            }
            smtcPointer = null
        }

        smtcWindow?.let {
            user32.DestroyWindow(it)
            smtcWindow = null
        }
        runCatching { user32.UnregisterClass(SMTC_CLASS_NAME, Kernel32.INSTANCE.GetModuleHandle(null)) }
        smtcWndProc = null

        eventHandlerComObject = null
        eventHandlerVtable = null
        eventHandlerQi = null
        eventHandlerAddRef = null
        eventHandlerRelease = null
        eventHandlerInvoke = null

        runCatching { combase?.RoUninitialize() }
    }

    private fun triggerDebounced(button: Int) {
        val now = System.currentTimeMillis()
        val last = lastTriggerTime.get()
        if (now - last > 180) {
            lastTriggerTime.set(now)
            java.awt.EventQueue.invokeLater {
                onButton(button)
            }
        }
    }

    /** Publishes the current track. Called on every change the shell would show. */
    fun publish(state: DesktopPlaybackState) {
        if (!started.get()) return
        val smtc = smtcPointer ?: return
        val cb = combase ?: return
        val song = state.song

        runCatching {
            val smtcVtable = smtc.getPointer(0)
            // put_PlaybackStatus -> index 7: 3 = Playing, 4 = Paused
            val status = if (state.isPlaying) 3 else 4
            Function.getFunction(smtcVtable.getPointer(7L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, status))

            // get_DisplayUpdater -> index 8
            val updaterRef = PointerByReference()
            if (Function.getFunction(smtcVtable.getPointer(8L * Native.POINTER_SIZE)).invokeInt(arrayOf(smtc, updaterRef)) == 0 && updaterRef.value != null) {
                val updater = updaterRef.value
                val updaterVtable = updater.getPointer(0)

                // put_Type(1) -> index 7 (MediaPlaybackType_Music = 1)
                Function.getFunction(updaterVtable.getPointer(7L * Native.POINTER_SIZE)).invokeInt(arrayOf(updater, 1))

                // get_MusicProperties -> index 12
                val musicRef = PointerByReference()
                if (Function.getFunction(updaterVtable.getPointer(12L * Native.POINTER_SIZE)).invokeInt(arrayOf(updater, musicRef)) == 0 && musicRef.value != null) {
                    val music = musicRef.value
                    val musicVtable = music.getPointer(0)

                    val title = song?.title.orEmpty()
                    val artist = song?.artist.orEmpty()
                    val album = song?.albumName.orEmpty()

                    val titleHstrRef = PointerByReference()
                    if (cb.WindowsCreateString(WString(title), title.length, titleHstrRef) == 0) {
                        Function.getFunction(musicVtable.getPointer(7L * Native.POINTER_SIZE)).invokeInt(arrayOf(music, titleHstrRef.value))
                        cb.WindowsDeleteString(titleHstrRef.value)
                    }

                    val artistHstrRef = PointerByReference()
                    if (cb.WindowsCreateString(WString(artist), artist.length, artistHstrRef) == 0) {
                        Function.getFunction(musicVtable.getPointer(11L * Native.POINTER_SIZE)).invokeInt(arrayOf(music, artistHstrRef.value))
                        cb.WindowsDeleteString(artistHstrRef.value)
                    }

                    if (album.isNotEmpty()) {
                        val music2Iid = GUID.fromString("{036c0e3b-1bc2-44bc-8e53-d501557016cf}")
                        val music2Ref = PointerByReference()
                        val qiFunc = Function.getFunction(musicVtable.getPointer(0L * Native.POINTER_SIZE))
                        if (qiFunc.invokeInt(arrayOf(music, music2Iid.pointer, music2Ref)) == 0 && music2Ref.value != null) {
                            val music2 = music2Ref.value
                            val music2Vtable = music2.getPointer(0)
                            val albumHstrRef = PointerByReference()
                            if (cb.WindowsCreateString(WString(album), album.length, albumHstrRef) == 0) {
                                Function.getFunction(music2Vtable.getPointer(6L * Native.POINTER_SIZE)).invokeInt(arrayOf(music2, albumHstrRef.value))
                                cb.WindowsDeleteString(albumHstrRef.value)
                            }
                            Function.getFunction(music2Vtable.getPointer(2L * Native.POINTER_SIZE)).invokeInt(arrayOf(music2))
                        }
                    }

                    Function.getFunction(musicVtable.getPointer(2L * Native.POINTER_SIZE)).invokeInt(arrayOf(music))
                }

                // Update() -> index 17
                Function.getFunction(updaterVtable.getPointer(17L * Native.POINTER_SIZE)).invokeInt(arrayOf(updater))
                // Release updater
                Function.getFunction(updaterVtable.getPointer(2L * Native.POINTER_SIZE)).invokeInt(arrayOf(updater))
            }
        }.onFailure {
            DesktopTrackLog.log("SMTC update error: ${it.message}")
        }
    }

    /** Called from the native side when a media key or the shell's card is used. */
    @JvmStatic
    fun onButton(button: Int) {
        val target = controller ?: return
        when (button) {
            BUTTON_PLAY -> {
                if (target.onPlayPause != null) target.onPlayPause.invoke()
                else target.onPlay()
            }
            BUTTON_PAUSE -> target.onPause()
            BUTTON_NEXT -> target.onNext()
            BUTTON_PREVIOUS -> target.onPrevious()
            BUTTON_STOP -> target.onStop()
        }
    }

    const val BUTTON_PLAY = 0
    const val BUTTON_PAUSE = 1
    const val BUTTON_NEXT = 2
    const val BUTTON_PREVIOUS = 3
    const val BUTTON_STOP = 4
}
