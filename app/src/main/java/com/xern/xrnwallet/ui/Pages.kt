package com.xern.xrnwallet.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Far enough that nobody reaches an end: about 830 years of months either way. */
private const val PAGE_COUNT = 20_000
private const val MIDDLE_PAGE = PAGE_COUNT / 2
private const val TURN_MILLIS = 300

/** Most of a page has to be dragged by hand before it would turn without this. */
private const val TURNS_AFTER = 0.3f

/**
 * Sideways pages with no first or last, like turning through a book: each page follows the finger and
 * the next one comes in beside it. A page is named by a value such as a month, and [move] steps from one
 * value to the next.
 */
@Stable
class EndlessPages<T>(start: T, private val move: (T, Int) -> T, private val scope: CoroutineScope) {
    private var origin by mutableStateOf(start)

    var pager by mutableStateOf(newPager())
        private set

    /** The page showing now, which changes once the next one is more than halfway in. */
    val current: T get() = at(pager.currentPage)

    fun at(page: Int): T = move(origin, page - MIDDLE_PAGE)

    /** Quick presses add up, because each one counts from where the pages are heading. */
    fun turn(by: Int) {
        scope.launch {
            pager.animateScrollToPage(
                pager.targetPage + by,
                animationSpec = tween(TURN_MILLIS, easing = FastOutSlowInEasing),
            )
        }
    }

    /** Goes straight to a value chosen from a list, instead of turning through everything between. */
    fun jumpTo(value: T) {
        origin = value
        pager = newPager()
    }

    private fun newPager() = PagerState(MIDDLE_PAGE) { PAGE_COUNT }
}

@Composable
fun <T> rememberEndlessPages(start: T, move: (T, Int) -> T): EndlessPages<T> {
    val scope = rememberCoroutineScope()
    return remember { EndlessPages(start, move, scope) }
}

@Composable
fun <T> EndlessPager(pages: EndlessPages<T>, modifier: Modifier = Modifier, page: @Composable (T) -> Unit) {
    val tick = rememberTick()
    LaunchedEffect(pages.pager) {
        snapshotFlow { pages.pager.settledPage }.drop(1).collect { tick() }
    }
    HorizontalPager(
        pages.pager,
        modifier,
        verticalAlignment = Alignment.Top,
        flingBehavior = PagerDefaults.flingBehavior(pages.pager, snapPositionalThreshold = TURNS_AFTER),
    ) { index ->
        val value = pages.at(index)
        key(value) { page(value) }
    }
}
