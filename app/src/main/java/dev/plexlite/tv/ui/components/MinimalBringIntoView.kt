package dev.plexlite.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls only as much as needed to show the focused item (plus [margin] px on each side), instead of
 * Compose for TV's default pivot that moves focused items towards the middle of the container.
 */
@OptIn(ExperimentalFoundationApi::class)
class MinimalBringIntoView(private val margin: Float = 0f) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = offset - margin
        val end = offset + size + margin
        return when {
            start >= 0f && end <= containerSize -> 0f
            start < 0f || end - start > containerSize -> start
            else -> end - containerSize
        }
    }
}
