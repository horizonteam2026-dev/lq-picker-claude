package com.lqpicker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lqpicker.DraftViewModel
import com.lqpicker.TourSteps

/** Vị trí (toạ độ cửa sổ) của các phần tử đã đánh dấu để hướng dẫn khoanh sáng. */
object TourRegistry {
    val bounds = mutableStateMapOf<String, Rect>()
}

/** Đánh dấu một phần tử để hướng dẫn có thể khoanh sáng. Không đổi giao diện. */
fun Modifier.tourTarget(key: String): Modifier = onGloballyPositioned { coords ->
    TourRegistry.bounds[key] = coords.boundsInRoot()
}

/**
 * Lớp hướng dẫn: làm tối màn hình, chừa một ô sáng quanh phần tử đang nói tới.
 * Lớp này không nhận chạm, nên người dùng vẫn bấm được tướng khi đang ở bước chọn tướng.
 * Chỉ gọi khi vm.tourStep >= 0.
 */
@Composable
fun TourOverlay(vm: DraftViewModel) {
    val idx = vm.tourStep.coerceIn(0, TourSteps.all.lastIndex)
    val step = TourSteps.all[idx]
    val rect = TourRegistry.bounds[step.key]

    var last by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(rect) {
        if (rect != null) last = rect
    }

    val spec = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    val left by animateFloatAsState(targetValue = last.left, animationSpec = spec, label = "l")
    val top by animateFloatAsState(targetValue = last.top, animationSpec = spec, label = "t")
    val right by animateFloatAsState(targetValue = last.right, animationSpec = spec, label = "r")
    val bottom by animateFloatAsState(targetValue = last.bottom, animationSpec = spec, label = "b")
    val holeAlpha by animateFloatAsState(
        targetValue = if (rect != null) 1f else 0f,
        animationSpec = tween(300),
        label = "a"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "p"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenPx = with(LocalDensity.current) { maxHeight.toPx() }
        // Đặt thẻ hướng dẫn ở nửa còn lại để không che phần đang khoanh sáng.
        val targetLow = rect != null && rect.center.y > screenPx / 2f
        val cardAlign = when {
            rect == null -> Alignment.Center
            targetLow -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color(0xD90A0D12))
            if (holeAlpha > 0f) {
                val pad = 8.dp.toPx()
                val radius = CornerRadius(14.dp.toPx())
                val tl = Offset(left - pad, top - pad)
                val sz = Size(right - left + pad * 2, bottom - top + pad * 2)
                drawRoundRect(
                    color = Color.Black,
                    topLeft = tl,
                    size = sz,
                    cornerRadius = radius,
                    alpha = holeAlpha,
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = Pal.gold,
                    topLeft = tl,
                    size = sz,
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx()),
                    alpha = holeAlpha * (0.4f + 0.6f * pulse)
                )
            }
        }

        Column(
            Modifier
                .align(cardAlign)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Pal.surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BƯỚC ${idx + 1}/${TourSteps.all.size}",
                    color = Pal.gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (idx < TourSteps.all.lastIndex) {
                    Text(
                        "Bỏ qua",
                        color = Pal.sub,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable(onClick = vm::skipTour)
                            .padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = idx,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 5 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 5 })
                },
                label = "tour"
            ) { i ->
                val s = TourSteps.all[i]
                Column {
                    Text(s.title, color = Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(s.body, color = Pal.text, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressDots(current = idx, total = TourSteps.all.size)
                Spacer(Modifier.weight(1f))
                Pill(
                    text = if (idx == TourSteps.all.lastIndex) "Xong" else "Tiếp →",
                    selected = true,
                    onClick = vm::nextTour
                )
            }
        }
    }
}

@Composable
private fun ProgressDots(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val active = i == current
            val w by animateDpAsState(
                targetValue = if (active) 14.dp else 5.dp,
                animationSpec = tween(250),
                label = "dot"
            )
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .width(w)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= current) Pal.gold else Pal.surface2)
            )
        }
    }
}

/** Màn hình chúc may mắn sau khi hoàn tất hướng dẫn. Chỉ gọi khi vm.celebrate = true. */
@Composable
fun CelebrationOverlay(vm: DraftViewModel) {
    val tr = rememberInfiniteTransition(label = "cel")
    val fall by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(3200, easing = LinearEasing)),
        label = "fall"
    )
    val bob by tr.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "bob"
    )

    Box(Modifier.fillMaxSize().background(Color(0xE60A0D12))) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 24) {
                val x = ((i * 37) % 100) / 100f * size.width
                val p = (fall + i / 24f) % 1f
                val y = p * size.height
                val color = when (i % 3) {
                    0 -> Pal.gold
                    1 -> Pal.good
                    else -> Pal.ally
                }
                drawCircle(
                    color = color,
                    radius = (4 + i % 4).dp.toPx(),
                    center = Offset(x, y),
                    alpha = 0.85f
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Pal.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "★",
                color = Pal.gold,
                fontSize = 56.sp,
                modifier = Modifier.offset(y = bob.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Chúc bạn may mắn!", color = Pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bình tĩnh, chơi hết mình và mang chiến thắng về cho cả đội nhé!",
                color = Pal.sub,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Pill(text = "Vào trận thôi!", selected = true, onClick = vm::dismissCelebrate)
        }
    }
}
