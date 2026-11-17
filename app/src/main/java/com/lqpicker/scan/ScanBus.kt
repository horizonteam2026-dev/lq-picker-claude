package com.lqpicker.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lqpicker.Lane

class ScanResult(
    val allyIds: List<String>,
    val enemyIds: List<String>,
    val stamp: Long
)

/** Cầu nối trạng thái giữa service quét màn hình và giao diện. Chỉ ghi từ luồng chính. */
object ScanBus {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")
    var latest by mutableStateOf<ScanResult?>(null)

    /** Vị trí người chơi sẽ đánh. Dùng chung cho app và bong bóng nổi. Chỉ ghi từ luồng chính. */
    var lane by mutableStateOf<Lane?>(null)
}
