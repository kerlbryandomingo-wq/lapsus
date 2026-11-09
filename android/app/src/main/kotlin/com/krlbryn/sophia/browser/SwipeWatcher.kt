package com.krlbryn.sophia.browser

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import com.krlbryn.sophia.core.SwipeClassifier
import com.krlbryn.sophia.core.SwipeDirection
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Watches a WebView's touch stream and reports deliberate horizontal swipes. It never consumes events.
 * Gestures that begin as a long press (text selection), use two fingers (zoom), or scroll the page
 * sideways are ignored; the final decision is made by [SwipeClassifier].
 */
internal class SwipeWatcher(private val webView: WebView, private val onSwipe: (SwipeDirection) -> Unit) {
    private val touchSlop = ViewConfiguration.get(webView.context).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var startScrollX = 0
    private var multiTouch = false
    private var moved = false
    private var longPress = false
    private var tracking = false

    fun onTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                downTime = e.eventTime
                startScrollX = webView.scrollX
                multiTouch = false
                moved = false
                longPress = false
                tracking = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_MOVE -> if (tracking && !moved) {
                if (hypot(e.x - downX, e.y - downY) > touchSlop) {
                    moved = true
                    // Holding still before moving is how text selection starts.
                    if (e.eventTime - downTime >= longPressMs) longPress = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return
                tracking = false
                val direction = SwipeClassifier.classify(
                    SwipeClassifier.Touch(
                        dxPx = e.x - downX,
                        dyPx = e.y - downY,
                        durationMs = e.eventTime - downTime,
                        density = webView.resources.displayMetrics.density,
                        multiTouch = multiTouch,
                        longPress = longPress || (!moved && e.eventTime - downTime >= longPressMs),
                        pageScrolledHorizontally = abs(webView.scrollX - startScrollX) > touchSlop,
                    ),
                )
                if (direction != null) onSwipe(direction)
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
    }
}
