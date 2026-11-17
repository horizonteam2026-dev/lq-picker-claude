package com.lqpicker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lqpicker.DraftViewModel
import com.lqpicker.Hero
import com.lqpicker.Lane
import com.lqpicker.PickTarget
import com.lqpicker.Team
import com.lqpicker.foldVi

@Composable
fun HeroPicker(vm: DraftViewModel, target: PickTarget) {
    BackHandler(onBack = vm::closePicker)

    var query by remember { mutableStateOf("") }
    var laneFilter by remember { mutableStateOf<Lane?>(null) }

    val available = remember(target, vm.allies, vm.enemies, vm.bans) { vm.availableFor(target) }
    val filtered = remember(available, query, laneFilter) {
        val q = query.foldVi().trim()
        val lf = laneFilter
        available.filter { h ->
            (q.isEmpty() || h.searchKey.contains(q)) && (lf == null || lf in h.lanes)
        }
    }

    val title = when (target.team) {
        Team.ALLY -> "Chọn đồng đội"
        Team.ENEMY -> "Chọn đối thủ"
        Team.BAN -> "Chọn tướng bị cấm"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(text = "← Quay lại", selected = false, onClick = vm::closePicker)
            Spacer(Modifier.weight(1f))
            if (vm.currentId(target) != null) {
                Pill(text = "Bỏ chọn ô này", selected = false, onClick = vm::clearCurrent)
            }
        }
        Text(title, color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Tìm tướng (gõ không dấu cũng được)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all") {
                Pill(text = "Tất cả", selected = laneFilter == null, onClick = { laneFilter = null })
            }
            itemsIndexed(Lane.values().toList()) { _, l ->
                Pill(
                    text = l.label,
                    selected = laneFilter == l,
                    onClick = { laneFilter = if (laneFilter == l) null else l }
                )
            }
        }
        if (filtered.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Không tìm thấy tướng phù hợp.", color = Pal.sub, fontSize = 14.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { h ->
                    HeroTile(h) { vm.pick(h.id) }
                }
            }
        }
    }
}

@Composable
private fun HeroTile(hero: Hero, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeroBadge(hero, 56.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            hero.name,
            color = Pal.text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
