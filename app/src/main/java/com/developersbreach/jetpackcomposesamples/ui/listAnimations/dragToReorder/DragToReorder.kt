package com.developersbreach.jetpackcomposesamples.ui.listAnimations.dragToReorder

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import com.developersbreach.jetpackcomposesamples.ui.listAnimations.model.ShoesArticle
import com.developersbreach.jetpackcomposesamples.ui.listAnimations.model.SlideState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.IndexOutOfBoundsException
import kotlin.math.roundToInt
import kotlin.math.sign

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.dragToReorder(
    shoesArticle: ShoesArticle,
    shoesArticles: MutableList<ShoesArticle>,
    itemHeight: Int,
    updateSlideState: (shoesArticle: ShoesArticle, slideState: SlideState) -> Unit,
    onDrag: () -> Unit,
    onStopDrag: (currentIndex: Int, destinationIndex: Int) -> Unit,
): Modifier = this.composed {

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val offset = pointerInput(Unit) {
        // Wrap in a coroutine scope to use suspend functions for touch events and animation.
        coroutineScope {
            while (true) {

                // Wait for a touch down event.
                val pointerId = awaitPointerEventScope { awaitFirstDown().id }

                // Interrupt any ongoing animation of other items.
                offsetX.stop()
                offsetY.stop()

                val shoesArticleIndex = shoesArticles.indexOf(shoesArticle)
                val offsetToSlide = itemHeight / 4
                var numberOfItems = 0
                var previousNumberOfItems: Int
                var listOffset = 0

                // Wait for drag events.
                awaitPointerEventScope {
                    drag(pointerId) { change ->
                        onDrag()

                        val horizontalDragOffset = offsetX.value + change.positionChange().x
                        launch {
                            offsetX.snapTo(horizontalDragOffset)
                        }

                        val verticalDragOffset = offsetY.value + change.positionChange().y
                        launch {
                            offsetY.snapTo(verticalDragOffset)
                            val offsetSign = offsetY.value.sign.toInt()
                            previousNumberOfItems = numberOfItems
                            numberOfItems = calculateNumberOfSlidItems(
                                offsetY.value * offsetSign,
                                itemHeight,
                                offsetToSlide,
                                previousNumberOfItems
                            )

                            if (previousNumberOfItems > numberOfItems) {
                                updateSlideState(
                                    shoesArticles[shoesArticleIndex + previousNumberOfItems * offsetSign],
                                    SlideState.NONE
                                )
                            } else if (numberOfItems != 0) {
                                try {
                                    updateSlideState(
                                        shoesArticles[shoesArticleIndex + numberOfItems * offsetSign],
                                        if (offsetSign == 1) SlideState.UP else SlideState.DOWN
                                    )
                                } catch (e: IndexOutOfBoundsException) {
                                    numberOfItems = previousNumberOfItems
                                    Timber.e("DragToReorder - Item is outside or at the edge")
                                }
                            }
                            listOffset = numberOfItems * offsetSign
                        }
                        // Consume the gesture event, not passed to external
                        change.consumePositionChange()
                    }
                }
                launch {
                    offsetX.animateTo(0f)
                }
                launch {
                    offsetY.animateTo(itemHeight * numberOfItems * offsetY.value.sign)
                    onStopDrag(shoesArticleIndex, shoesArticleIndex + listOffset)
                }
            }
        }
    }
        .offset {
            // Use the animating offset value here.
            IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt())
        }
    offset
}

private fun calculateNumberOfSlidItems(
    offsetY: Float,
    itemHeight: Int,
    offsetToSlide: Int,
    previousNumberOfItems: Int
): Int {
    val numberOfItemsInOffset = (offsetY / itemHeight).toInt()
    val numberOfItemsPlusOffset = ((offsetY + offsetToSlide) / itemHeight).toInt()
    val numberOfItemsMinusOffset = ((offsetY - offsetToSlide - 1) / itemHeight).toInt()
    return when {
        offsetY - offsetToSlide - 1 < 0 -> 0
        numberOfItemsPlusOffset > numberOfItemsInOffset -> numberOfItemsPlusOffset
        numberOfItemsMinusOffset < numberOfItemsInOffset -> numberOfItemsInOffset
        else -> previousNumberOfItems
    }
}