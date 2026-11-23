package com.example.ui.player.controls

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animasi visual feedback double-tap to seek (gaya YouTube: "-10s" di sisi kiri, "+10s" di sisi kanan).
 */
@Composable
fun DoubleTapSeekFeedback(
    seekSide: String?,
    seekAmount: Int,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = seekSide != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.95f),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (seekSide == "left") Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = if (isFullscreen) 44.dp else 20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (seekSide == "left") {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Mundur",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "-${seekAmount}s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "+${seekAmount}s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Maju",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Modifier untuk menangani gesture interaksi permukaan video:
 * - Single-tap: Toggle visibilitas kontrol overlay.
 * - Double-tap: Seek forward / rewind 10 detik sesuai sisi layar yang diketuk.
 */
fun Modifier.playerGestureHandler(
    onTap: () -> Unit,
    onDoubleTapSeek: (isRightSide: Boolean) -> Unit
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = { onTap() },
        onDoubleTap = { offset ->
            val isRight = offset.x > size.width / 2
            onDoubleTapSeek(isRight)
        }
    )
}
