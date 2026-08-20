package rj.wearui

import android.content.Context
import android.util.AttributeSet
import java.util.ArrayList

/**
 * State-only navigation stack for swipe-to-dismiss route hosts.
 * A route is never removed until [pop] is called after the host's dismissal transition completes.
 */
class SwipeDismissNavigator {
    private val entries = ArrayList<rj.wearui.ScreenEntry>()
    private val listeners = ArrayList<rj.wearui.WearScreenListener>()
    private var pendingDismissal = false
    private var pendingCurrent: rj.wearui.ScreenEntry? = null

    constructor()
    constructor(context: Context)
    constructor(context: Context, attrs: AttributeSet?)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)

    val current: rj.wearui.ScreenEntry?
        get() = entries.lastOrNull()
    val previous: rj.wearui.ScreenEntry?
        get() = if (entries.size > 1) entries[entries.size - 2] else null

    fun push(entry: rj.wearui.ScreenEntry) {
        val before = current
        pendingDismissal = false
        pendingCurrent = null
        // Promoting an existing stable entry preserves its associated host state instead of cloning it.
        val existing = entries.indexOfFirst { it.id == entry.id }
        if (existing >= 0) entries.removeAt(existing)
        entries.add(entry)
        notifyListeners(current, before)
    }

    /** Removes the foreground only after a completed gesture/transition. */
    fun pop(): Boolean {
        if (!canPop()) return false
        val outgoing = current
        entries.removeAt(entries.lastIndex)
        pendingDismissal = false
        pendingCurrent = null
        notifyListeners(current, outgoing)
        return true
    }

    fun canPop(): Boolean = entries.size > 1

    fun setUserSwipeEnabled(enabled: Boolean) {
        userSwipeEnabled = enabled
        if (!enabled) cancelDismissal()
    }

    fun isUserSwipeEnabled(): Boolean = userSwipeEnabled
    private var userSwipeEnabled = true

    fun addListener(listener: rj.wearui.WearScreenListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: rj.wearui.WearScreenListener) {
        listeners.remove(listener)
    }

    /** Marks a dismiss interaction as active without mutating stack state. */
    fun beginDismissal(): Boolean {
        if (!userSwipeEnabled || !canPop() || pendingDismissal) return false
        pendingDismissal = true
        pendingCurrent = current
        return true
    }

    fun isDismissalPending(): Boolean = pendingDismissal

    /** Cancels an interactive dismiss and leaves both retained entries intact. */
    fun cancelDismissal() {
        pendingDismissal = false
        pendingCurrent = null
    }

    /** Called by a host only after its outgoing view has fully left the screen. */
    fun completeDismissal(): Boolean {
        if (!pendingDismissal) return false
        return pop()
    }

    fun clear() {
        val before = current
        entries.clear()
        pendingDismissal = false
        pendingCurrent = null
        if (before != null) notifyListeners(null, before)
    }

    fun entries(): List<rj.wearui.ScreenEntry> = ArrayList(entries)

    private fun notifyListeners(now: rj.wearui.ScreenEntry?, before: rj.wearui.ScreenEntry?) {
        val copy = ArrayList(listeners)
        for (listener in copy) listener.onScreenChanged(now, before)
    }
}
