package com.lqpicker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lqpicker.Hero
import com.lqpicker.HeroClass

/** Bảng màu phẳng, không gradient/bóng đổ để nhẹ cho máy yếu. */
object Pal {
    val bg = Color(0xFF0F1218)
    val surface = Color(0xFF181D27)
    val surface2 = Color(0xFF212838)
    val text = Color(0xFFE8ECF4)
    val sub = Color(0xFF8E98AD)
    val gold = Color(0xFFF5B63A)
    val ally = Color(0xFF3B82F6)
    val enemy = Color(0xFFEF4444)
    val good = Color(0xFF34D399)
    val warn = Color(0xFFF59E0B)
}

@Composable
fun LQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.gold,
            onPrimary = Pal.bg,
            background = Pal.bg,
            surface = Pal.surface,
            onSurface = Pal.text,
            onBackground = Pal.text
        ),
        content = content
    )
}

fun classColor(c: HeroClass): Color = when (c) {
    HeroClass.TANK -> Color(0xFF3F7D5C)
    HeroClass.WARRIOR -> Color(0xFFB4673A)
    HeroClass.ASSASSIN -> Color(0xFF8B4BA8)
    HeroClass.MAGE -> Color(0xFF3B6FD0)
    HeroClass.MARKSMAN -> Color(0xFFB8922B)
    HeroClass.SUPPORT -> Color(0xFF2F8F9D)
}

fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return if (parts.size >= 2) {
        "${parts[0].first()}${parts[1].first()}".uppercase()
    } else {
        name.take(2).replaceFirstChar { it.uppercase() }
    }
}

/** Avatar tướng vẽ bằng màu + chữ viết tắt (không tải ảnh). */
@Composable
fun HeroBadge(hero: Hero?, size: Dp, border: Color = Color.Transparent, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (hero != null) classColor(hero.cls) else Pal.surface2)
            .border(BorderStroke(2.dp, border), shape),
        contentAlignment = Alignment.Center
    ) {
        if (hero != null) {
            Text(
                text = initials(hero.name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp
            )
        } else {
            Text(text = "+", color = Pal.sub, fontSize = (size.value * 0.4f).sp)
        }
    }
}
