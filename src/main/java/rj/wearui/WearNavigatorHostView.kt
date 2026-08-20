package rj.wearui

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import java.util.ArrayList
import java.util.LinkedHashMap

/**
 * Route host that binds [SwipeDismissNavigator] entries to ordinary Views. Its retained current and
 * previous route Views make a committed dismiss visually continuous and preserve stable IDs.
 */
class WearNavigatorHostView : PredictiveBackHostView {
    private val ownedNavigator = SwipeDismissNavigator()
    private val specsByToken = LinkedHashMap<String, rj.wearui.ScreenSpec>()
    private var defaultFactory: rj.wearui.WearScreenFactory? = null

    private val routedFactory = object : rj.wearui.WearScreenFactory {
        override fun create(context: Context, entry: rj.wearui.ScreenEntry): View {
            val spec = specsByToken[entry.screenToken]
            val factory = spec?.factory ?: defaultFactory
                ?: throw IllegalStateException("No screen factory registered for ${entry.screenToken}")
            return factory.create(context, entry).also { view ->
                if (view.contentDescription == null && spec?.contentDescription != null) {
                    view.contentDescription = spec.contentDescription
                }
            }
        }
    }

    constructor(context: Context) : super(context) { initializeNavigator() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initializeNavigator() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initializeNavigator() }

    private fun initializeNavigator() {
        super.setNavigator(ownedNavigator)
        super.setScreenFactory(routedFactory)
        isFocusable = true
        contentDescription = "Screen navigation"
    }

    fun navigator(): SwipeDismissNavigator = ownedNavigator

    fun setDefaultScreenFactory(factory: rj.wearui.WearScreenFactory?) {
        defaultFactory = factory
        renderCurrent()
    }

    fun registerScreen(screenToken: String, spec: rj.wearui.ScreenSpec) {
        specsByToken[screenToken] = spec
        if (ownedNavigator.current?.screenToken == screenToken) renderCurrent()
    }

    fun unregisterScreen(screenToken: String) {
        specsByToken.remove(screenToken)
    }

    fun push(entry: rj.wearui.ScreenEntry) {
        ownedNavigator.push(entry)
    }

    fun pop(): Boolean = if (ownedNavigator.canPop()) dismissCurrent() else false
    fun canPop(): Boolean = ownedNavigator.canPop()

    /** Activates an app-owned token with the supplied native view factory specification. */
    fun activateScreen(screenToken: String, spec: rj.wearui.ScreenSpec) {
        registerScreen(screenToken, spec)
        val existing = ownedNavigator.current
        if (existing?.screenToken == screenToken) {
            renderCurrent()
            return
        }
        push(rj.wearui.ScreenEntry(id = screenToken, screenToken = screenToken, title = spec.contentDescription))
    }

    /** Removes an inactive registered factory. The current screen is only popped through dismissal. */
    fun deactivateScreen(screenToken: String) {
        if (ownedNavigator.current?.screenToken != screenToken) unregisterScreen(screenToken)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also { saved ->
            saved.userSwipe = isUserSwipeEnabled()
            for (entry in ownedNavigator.entries()) {
                saved.entries.add(Bundle().apply {
                    putString("id", entry.id)
                    putString("token", entry.screenToken)
                    putCharSequence("title", entry.title)
                    putBundle("args", entry.arguments)
                })
            }
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        setUserSwipeEnabled(state.userSwipe)
        ownedNavigator.clear()
        for (bundle in state.entries) {
            val id = bundle.getString("id") ?: continue
            val token = bundle.getString("token") ?: id
            ownedNavigator.push(
                rj.wearui.ScreenEntry(
                    id = id,
                    screenToken = token,
                    title = bundle.getCharSequence("title"),
                    arguments = bundle.getBundle("args")
                )
            )
        }
        renderCurrent()
    }

    private class SavedState : BaseSavedState {
        var userSwipe = true
        val entries = ArrayList<Bundle>()

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            userSwipe = source.readInt() != 0
            source.readTypedList(entries, Bundle.CREATOR)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (userSwipe) 1 else 0)
            out.writeTypedList(entries)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
