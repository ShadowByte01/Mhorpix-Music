package com.music.mhorpix.data

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request from outside the app to open the update dialog immediately.
 *
 * Tapping the system-tray update notification should land on the in-app
 * update sheet. Because MainActivity is singleTask, the intent arrives via
 * two paths (onCreate on a cold start, onNewIntent when the app is already
 * alive), so both paths relay through here and the composition watches this
 * flag -- the same pattern PlayerDeepLink uses for the now-playing sheet.
 */
object UpdateDeepLink {

    /** Set on the notification's tap intent. Nothing else sets it. */
    const val EXTRA_OPEN_UPDATE = "mhorpix.openUpdate"

    private val _pending = MutableStateFlow(false)

    /** Whether a request is outstanding. Cleared by [handled]. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Reads an incoming intent, and reports whether it asked for the update dialog. */
    fun consume(intent: Intent?): Boolean {
        if (intent == null || !intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) return false
        // Clear the extra so a configuration change (rotation, theme flip, ...)
        // does not re-trigger the dialog after the user has already seen it.
        intent.removeExtra(EXTRA_OPEN_UPDATE)
        _pending.value = true
        return true
    }

    /**
     * Called once the update dialog has actually been shown.
     *
     * Must be cleared by the composition that acts on it; leaving it set
     * would re-open the dialog on the next recomposition after dismissal.
     */
    fun handled() {
        _pending.value = false
    }
}
