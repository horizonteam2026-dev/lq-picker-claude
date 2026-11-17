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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lqpicker.DraftViewModel
import com.lqpicker.Hero
import com.lqpicker.Lane
import com.lqpicker.foldVi

/** Đánh dấu các tướng bạn đang có. Gợi ý chỉ lấy trong số này. */
@Composable
fun OwnedScreen(vm: DraftViewModel) {
    BackHandler(onBack = vm::closeOwned)

    var query by remember { mutableStateOf("") }
    var laneFilter by remember { mutableStateOf<Lane?>(null) }

    val owned = vm.owned
    val total = vm.heroes.size
    val ownedCount = owned?.size ?: total
    val lf = laneFilter
    val q = query.foldVi().trim()
    val list = vm.heroes.filter { h ->
        (q.isEmpty() || h.searchKey.contains(q)) && (lf == null || lf in h.lanes)
    }

    fun isOn(id: String): Boolean = owned == null || id in owned

    fun toggle(id: String) {
        val base: Set<String> = owned ?: vm.heroes.map { it.id }.toSet()
        vm.setOwned(if (id in base) base - id else base + id)
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
            Pill(text = "← Quay lại", selected = false, onClick = vm::closeOwned)
            Spacer(Modifier.weight(1f))
            Pill(text = "Chọn hết", selected = false, onClick = { vm.setOwned(null) })
            Spacer(Modifier.width(6.dp))
            Pill(text = "Bỏ hết", selected = false, onClick = { vm.setOwned(emptySet()) })
        }
        Text("Tướng của tôi", color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Đã có $ownedCount/$total tướng. Gợi ý chỉ chọn trong số này. Chạm tướng để bật/tắt.",
            color = Pal.sub,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Tìm tướng") },
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
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.id }) { h ->
                OwnTile(h, on = isOn(h.id)) { toggle(h.id) }
            }
        }
    }
}

@Composable
private fun OwnTile(hero: Hero, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .alpha(if (on) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeroBadge(hero, 56.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (on) "✓ ${hero.name}" else hero.name,
            color = if (on) Pal.good else Pal.sub,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
