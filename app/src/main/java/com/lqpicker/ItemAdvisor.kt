package com.lqpicker

/**
 * Gợi ý LOẠI trang bị theo đội hình hai bên.
 * Chỉ nêu loại (giáp, kháng phép, xuyên giáp...), không dùng tên trang bị cụ thể,
 * để tránh sai khi game đổi trang bị. Hàm thuần, không phụ thuộc Android.
 */
object ItemAdvisor {

    fun advise(self: Hero?, allies: List<Hero>, enemies: List<Hero>): List<String> {
        if (enemies.isEmpty() && allies.isEmpty()) return emptyList()
        val out = ArrayList<String>()

        val eP = enemies.count { it.dmg == Dmg.P }
        val eM = enemies.count { it.dmg == Dmg.M }
        if (eP >= 3) out.add("Đối thủ nhiều sát thương vật lý ($eP): ưu tiên trang bị giáp")
        if (eM >= 3) out.add("Đối thủ nhiều sát thương phép ($eM): ưu tiên trang bị kháng phép")
        if (enemies.count { it.t.sus >= 2 } >= 2) {
            out.add("Đối thủ có nhiều hồi máu: cân nhắc trang bị giảm hồi")
        }
        if (enemies.count { it.t.burst >= 3 } >= 2) {
            out.add("Đối thủ bùng nổ mạnh: cân nhắc trang bị khiên hoặc hồi máu")
        }
        if (enemies.count { it.t.mob >= 3 } >= 2) {
            out.add("Đối thủ cơ động: cân nhắc trang bị tốc chạy để đuổi hoặc thoát")
        }
        if (self != null && self.dmg == Dmg.P && enemies.any { it.t.tank >= 2 }) {
            out.add("Bạn đánh vật lý, đối thủ có đỡ đòn: ưu tiên trang bị xuyên giáp")
        }
        if (allies.isNotEmpty() && allies.sumOf { it.t.tank } < 3) {
            out.add("Đội thiếu tiền tuyến: đồng đội nên lên trang bị hỗ trợ tiền tuyến")
        }
        return out.take(4)
    }
}
