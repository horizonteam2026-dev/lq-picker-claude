package com.lqpicker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lqpicker.DraftViewModel
import com.lqpicker.Hero
import com.lqpicker.Lane
import com.lqpicker.PickTarget
import com.lqpicker.Suggestion
import com.lqpicker.Team
import com.lqpicker.TourSteps
import com.lqpicker.scan.ScanBus

@Composable
fun DraftScreen(vm: DraftViewModel) {
    val target = vm.picker
    val scanned = ScanBus.latest
    LaunchedEffect(scanned) {
        if (scanned != null) vm.applyScan(scanned)
    }
    when {
        target != null -> HeroPicker(vm, target)
        vm.showOwned -> OwnedScreen(vm)
        else -> DraftContent(vm)
    }
}

@Composable
private fun DraftContent(vm: DraftViewModel) {
    val suggestions = vm.suggestions
    val listState = rememberLazyListState()
    // Vị trí các mục trong danh sách, để cuộn tới đúng phần hướng dẫn đang nói.
    val itemsIdx = if (vm.itemTips.isNotEmpty()) 6 + (if (vm.notes.isNotEmpty()) 1 else 0) else -1
    val titleIdx = 6 + (if (vm.notes.isNotEmpty()) 1 else 0) + (if (vm.itemTips.isNotEmpty()) 1 else 0)
    LaunchedEffect(vm.tourStep) {
        val key = TourSteps.all.getOrNull(vm.tourStep)?.key ?: return@LaunchedEffect
        val idx = when (key) {
            "coach" -> 1
            "scan" -> 2
            "lane" -> 3
            "team" -> 4
            "bans" -> 5
            "items" -> itemsIdx
            "suggest" -> titleIdx
            else -> -1
        }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") { Header(vm) }
        item(key = "coach") { Box(Modifier.fillMaxWidth().tourTarget("coach")) { CoachCard(vm) } }
        item(key = "scan") { Box(Modifier.fillMaxWidth().tourTarget("scan")) { ScanCard() } }
        item(key = "lane") {
            Box(Modifier.fillMaxWidth().tourTarget("lane")) {
                LaneRow(selected = vm.lane, onSelect = vm::selectLane)
            }
        }
        item(key = "teams") { Box(Modifier.fillMaxWidth().tourTarget("team")) { TeamsCard(vm) } }
        item(key = "bans") { Box(Modifier.fillMaxWidth().tourTarget("bans")) { BansRow(vm) } }
        if (vm.notes.isNotEmpty()) {
            item(key = "notes") {
                Panel(title = "PHÂN TÍCH ĐỘI HÌNH") {
                    vm.notes.forEach { Reason("•", Pal.gold, it) }
                }
            }
        }
        if (vm.itemTips.isNotEmpty()) {
            item(key = "items") {
                Panel(title = "TRANG BỊ GỢI Ý", modifier = Modifier.tourTarget("items")) {
                    vm.itemTips.forEach { Reason("▸", Pal.good, it) }
                }
            }
        }
        item(key = "title") {
            Box(Modifier.fillMaxWidth().tourTarget("suggest")) {
                Label(
                    text = "GỢI Ý CHO BẠN" + (vm.lane?.let { " · ${it.label}" } ?: ""),
                    color = Pal.gold
                )
            }
        }
        if (!vm.hasAnyPick) {
            item(key = "hint") {
                InfoCard("Chọn vị trí, rồi chọn tướng của bạn và đội hình. Gợi ý sẽ xuất hiện khi có ít nhất một tướng được chọn.")
            }
        } else if (suggestions.isEmpty()) {
            item(key = "empty") {
                InfoCard("Không còn tướng phù hợp trong danh sách bạn có. Thử bỏ chọn vị trí, bỏ bớt tướng cấm, hoặc cập nhật tướng bạn sở hữu.")
            }
        } else {
            itemsIndexed(suggestions, key = { _, s -> s.hero.id }) { index, s ->
                SuggestionCard(rank = index + 1, s = s)
            }
        }
    }
}

@Composable
private fun Header(vm: DraftViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Gợi ý chọn tướng", color = Pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Bạn tự nhập tướng, app chỉ gợi ý", color = Pal.sub, fontSize = 12.sp)
        }
        Pill(text = "?", selected = false, onClick = vm::startTour)
        Spacer(Modifier.width(6.dp))
        Pill(text = "Tướng của tôi", selected = false, onClick = vm::openOwned)
        Spacer(Modifier.width(6.dp))
        Pill(text = "Làm lại", selected = false, onClick = vm::reset)
    }
}

/** Hướng dẫn theo bước, có chuyển động nhẹ. Khi đã chọn đủ thì chúc may mắn. */
@Composable
private fun CoachCard(vm: DraftViewModel) {
    val transition = rememberInfiniteTransition(label = "coach")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val (text, color) = when {
        vm.lane == null -> Pair("Chọn vị trí của bạn để bắt đầu.", Pal.sub)
        vm.self == null -> Pair("Chọn tướng của bạn trước, rồi xem gợi ý để chính xác nhất.", Pal.warn)
        else -> Pair("Chúc bạn may mắn, lên top nhé!", Pal.good)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "★",
            color = Pal.gold,
            fontSize = 20.sp,
            modifier = Modifier.offset(y = bob.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LaneRow(selected: Lane?, onSelect: (Lane?) -> Unit) {
    Column {
        Label("VỊ TRÍ CỦA BẠN", Pal.sub)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(Lane.values().toList()) { _, l ->
                Pill(text = l.label, selected = selected == l, onClick = { onSelect(l) })
            }
        }
    }
}

@Composable
private fun TeamsCard(vm: DraftViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.surface)
            .padding(12.dp)
    ) {
        Label("BẠN VÀ ĐỒNG ĐỘI", Pal.ally)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slot(vm.hero(vm.self), Pal.gold, "Bạn", onClick = { vm.openPicker(PickTarget(Team.SELF, 0)) })
            vm.allies.forEachIndexed { i, id ->
                Slot(vm.hero(id), Pal.ally, "Đồng đội", onClick = { vm.openPicker(PickTarget(Team.ALLY, i)) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Label("ĐỐI THỦ", Pal.enemy)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.enemies.forEachIndexed { i, id ->
                Slot(vm.hero(id), Pal.enemy, "Đối thủ", onClick = { vm.openPicker(PickTarget(Team.ENEMY, i)) })
            }
        }
    }
}

@Composable
private fun RowScope.Slot(hero: Hero?, border: Color, label: String, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(10.dp)
    val icon = rememberHeroIcon(hero)
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(if (hero != null) classColor(hero.cls) else Pal.surface2)
                .border(2.dp, border, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Image(
                    bitmap = icon,
                    contentDescription = hero?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                hero != null -> Text(initials(hero.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                onClick != null -> Text("+", color = Pal.sub, fontSize = 18.sp)
                else -> Text("★", color = Pal.gold, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = hero?.name ?: label,
            color = if (hero != null) Pal.text else Pal.sub,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BansRow(vm: DraftViewModel) {
    Column {
        Label("TƯỚNG BỊ CẤM", Pal.sub)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.bans) { i, id ->
                Pill(
                    text = (vm.hero(id)?.name ?: id) + " ×",
                    selected = false,
                    onClick = { vm.removeBan(i) }
                )
            }
            if (vm.bans.size < DraftViewModel.MAX_BANS) {
                item(key = "add_ban") {
                    Pill(
                        text = "+ Cấm tướng",
                        selected = false,
                        onClick = { vm.openPicker(PickTarget(Team.BAN, -1)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(rank: Int, s: Suggestion) {
    val scoreColor = when {
        s.score >= 70 -> Pal.good
        s.score >= 50 -> Pal.gold
        else -> Pal.warn
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#$rank",
                color = Pal.gold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(30.dp)
            )
            HeroBadge(s.hero, 52.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.hero.name, color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    s.hero.cls.label + " · " + s.hero.lanes.joinToString(", ") { it.label },
                    color = Pal.sub,
                    fontSize = 12.sp
                )
            }
            Text("${s.score}", color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Pal.surface2)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(s.score / 100f)
                    .fillMaxHeight()
                    .background(scoreColor)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(s.summary, color = Pal.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        s.pros.forEach { Reason("+", Pal.good, it) }
        s.cons.forEach { Reason("–", Pal.warn, it) }
        if (s.pros.isEmpty() && s.cons.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Chọn thêm tướng để có lý do cụ thể.", color = Pal.sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.surface)
            .padding(12.dp)
    ) {
        Label(title, Pal.sub)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Reason(sign: String, color: Color, text: String) {
    Row(Modifier.padding(top = 6.dp)) {
        Text(sign, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(16.dp))
        Text(text, color = Pal.text, fontSize = 13.sp)
    }
}

@Composable
private fun InfoCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.surface)
            .padding(16.dp)
    ) {
        Text(text, color = Pal.sub, fontSize = 13.sp)
    }
}

@Composable
private fun Label(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
internal fun Pill(text: String, selected: Boolean, onClick: () -> Unit, accent: Color = Pal.gold) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Pal.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) Pal.bg else Pal.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
