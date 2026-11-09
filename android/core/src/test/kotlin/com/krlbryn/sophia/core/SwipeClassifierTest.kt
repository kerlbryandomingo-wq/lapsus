package com.krlbryn.sophia.core

import com.krlbryn.sophia.core.SwipeClassifier.Touch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeClassifierTest {
    private val d = 2.75f // a typical phone density

    @Test fun quickLeftSwipe() = assertEquals(SwipeDirection.LEFT, SwipeClassifier.classify(Touch(-160 * d, 10 * d, 220, d)))

    @Test fun quickRightSwipe() = assertEquals(SwipeDirection.RIGHT, SwipeClassifier.classify(Touch(140 * d, -20 * d, 300, d)))

    @Test fun verticalScrollIsIgnored() = assertNull(SwipeClassifier.classify(Touch(60 * d, 400 * d, 250, d)))

    @Test fun diagonalIsIgnored() = assertNull(SwipeClassifier.classify(Touch(150 * d, 90 * d, 250, d)))

    @Test fun shortDragIsIgnored() = assertNull(SwipeClassifier.classify(Touch(50 * d, 0f, 120, d)))

    @Test fun slowDragIsIgnored() = assertNull(SwipeClassifier.classify(Touch(200 * d, 0f, 900, d)))

    @Test fun textSelectionIsIgnored() = assertNull(SwipeClassifier.classify(Touch(200 * d, 0f, 300, d, longPress = true)))

    @Test fun pinchIsIgnored() = assertNull(SwipeClassifier.classify(Touch(200 * d, 0f, 300, d, multiTouch = true)))

    @Test fun horizontalPageScrollIsIgnored() =
        assertNull(SwipeClassifier.classify(Touch(-200 * d, 0f, 300, d, pageScrolledHorizontally = true)))

    @Test fun densityIndependent() {
        assertEquals(SwipeDirection.RIGHT, SwipeClassifier.classify(Touch(100f, 0f, 200, 1f)))
        assertNull(SwipeClassifier.classify(Touch(100f, 0f, 200, 3f)))
    }
}
