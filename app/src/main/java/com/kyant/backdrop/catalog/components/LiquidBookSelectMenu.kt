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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

@Composable
fun LiquidBookSelectMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOffset: Offset,
    backdrop: Backdrop,
    onSelectBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

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
            var animateIn by remember { mutableStateOf(false) }
            LaunchedEffect(expanded) {
                animateIn = true
            }

            AnimatedVisibility(
                visible = animateIn,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                val cornerRadius = 20.dp
                val menuWidth = 220.dp
                val menuHeight = 160.dp

                Box(
                    modifier = modifier
                        .offset {
                            val menuWidthPx = menuWidth.toPx()
                            val menuHeightPx = menuHeight.toPx()

                            // Align near button but prevent boundary overflows
                            val rawX = anchorOffset.x - menuWidthPx + 120.dp.toPx()
                            val posX = rawX.coerceIn(16.dp.toPx(), screenWidthPx - menuWidthPx - 16.dp.toPx())

                            val rawY = anchorOffset.y + 44.dp.toPx()
                            val posY = rawY.coerceIn(16.dp.toPx(), screenHeightPx - menuHeightPx - 16.dp.toPx())

                            IntOffset(posX.roundToInt(), posY.roundToInt())
                        }
                        .width(menuWidth)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(cornerRadius) },
                            effects = {
                                vibrancy()
                                blur(20f.dp.toPx())
                                lens(16f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color(0xFF0F131A).copy(alpha = 0.65f))
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .padding(10.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Seleziona Romanzo",
                            color = Color.White.copy(0.45f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            letterSpacing = 0.5.sp
                        )

                        // Jekyll & Hyde
                        LiquidBookMenuItem(
                            onClick = {
                                onDismissRequest()
                                onSelectBook("JekyllHyde")
                            },
                            backdrop = backdrop,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = "Jekyll Hyde",
                                    tint = Color(0xFF0091FF)
                                )
                            },
                            text = "Dr Jekyll & Mr Hyde"
                        )

                        // Time Machine
                        LiquidBookMenuItem(
                            onClick = {
                                onDismissRequest()
                                onSelectBook("TimeMachine")
                            },
                            backdrop = backdrop,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Time Machine",
                                    tint = Color(0xFF34C759)
                                )
                            },
                            text = "The Time Machine"
                        )

                        // Alice in Wonderland
                        LiquidBookMenuItem(
                            onClick = {
                                onDismissRequest()
                                onSelectBook("Alice")
                            },
                            backdrop = backdrop,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Alice",
                                    tint = Color(0xFFAF52DE)
                                )
                            },
                            text = "Alice in Wonderland"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidBookMenuItem(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: @Composable () -> Unit,
    text: String
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp)),
        isInteractive = true,
        surfaceColor = Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
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
