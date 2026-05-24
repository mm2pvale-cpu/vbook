package com.kyant.backdrop.catalog.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.data.Book
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

@Composable
fun LiquidGlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOffset: Offset,
    backdrop: Backdrop,
    book: Book?,
    onRename: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentBook by androidx.compose.runtime.rememberUpdatedState(book)
    var activeBook by remember { mutableStateOf<Book?>(null) }
    LaunchedEffect(currentBook) {
        if (currentBook != null) {
            activeBook = currentBook
        }
    }

    AnimatedVisibility(
        visible = expanded,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            // Full screen overlay overlaying parent's Box bounds to capture backpress/dismiss taps
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismissRequest()
                    }
            ) {
                val cornerRadius = 18.dp

                Box(
                    modifier = Modifier
                        .offset {
                            val menuWidthPx = 180.dp.toPx()
                            val menuHeightPx = 110.dp.toPx()

                            val rawX = anchorOffset.x - menuWidthPx + 32.dp.toPx()
                            val posX = rawX.coerceIn(16.dp.toPx(), screenWidthPx - menuWidthPx - 16.dp.toPx())

                            val rawY = anchorOffset.y + 36.dp.toPx()
                            val posY = rawY.coerceIn(16.dp.toPx(), screenHeightPx - menuHeightPx - 16.dp.toPx())

                            IntOffset(posX.roundToInt(), posY.roundToInt())
                        }
                        .animateEnterExit(
                            enter = scaleIn(
                                androidx.compose.animation.core.tween(durationMillis = 150, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                            ) + fadeIn(androidx.compose.animation.core.tween(150)),
                            exit = scaleOut(
                                androidx.compose.animation.core.tween(durationMillis = 150),
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                            ) + fadeOut(androidx.compose.animation.core.tween(150))
                        )
                        .width(180.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(cornerRadius) },
                            effects = {
                                vibrancy()
                                blur(18f.dp.toPx())
                                lens(16f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color(0xFF141A24).copy(alpha = 0.55f))
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .padding(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Rename action
                        LiquidMenuItem(
                            onClick = {
                                activeBook?.let { onRename(it) }
                                onDismissRequest()
                            },
                            backdrop = backdrop,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    tint = Color(0xFF0091FF) // Branding accent color
                                )
                            },
                            text = "Rename Book"
                        )

                        // Delete action
                        LiquidMenuItem(
                            onClick = {
                                activeBook?.let { onDelete(it) }
                                onDismissRequest()
                            },
                            backdrop = backdrop,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF3B30) // Red warning coloration
                                )
                            },
                            text = "Delete"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidMenuItem(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: @Composable () -> Unit,
    text: String
) {
    // Utilize the repo's actual custom LiquidButton logic to capture organic tactile animation
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp)),
        isInteractive = true,
        surfaceColor = Color.White.copy(alpha = 0.04f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(18.dp)) {
                icon()
            }
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = (-0.2).sp
            )
        }
    }
}
