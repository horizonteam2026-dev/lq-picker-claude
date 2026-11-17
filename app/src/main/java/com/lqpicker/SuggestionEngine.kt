package com.lqpicker

/**
 * Chấm điểm các tướng ứng viên dựa trên chỉ số của đội hình hai bên.
 * Là heuristic, không phải thống kê tỉ lệ thắng. Hàm thuần, không phụ thuộc Android.
 */
object SuggestionEngine {

    private class Acc {
        var total = 0
        val pros = ArrayList<Pair<Int, String>>()
        val cons = ArrayList<Pair<Int, String>>()

        fun add(points: Int, reason: String) {
            total += points
            when {
                points > 0 -> pros.add(points to reason)
                points < 0 -> cons.add(points to reason)
            }
        }
    }

    /** Độ tin cậy của gợi ý tuỳ số tướng đã biết: càng ít thông tin càng thấp. */
    fun confidence(allyCount: Int, enemyCount: Int): String {
        val n = allyCount + enemyCount
        return when {
            n == 0 -> "thấp"
            n <= 3 -> "trung bình"
            else -> "khá"
        }
    }

    private fun buildSummary(h: Hero, lane: Lane?, pros: List<String>, cons: List<String>): String {
        val sb = StringBuilder()
        if (pros.isEmpty()) {
            sb.append("Lựa chọn cân bằng")
            if (lane != null) sb.append(" cho vị trí ${lane.label}")
            sb.append(".")
        } else {
            sb.append("Nên chọn vì ")
            sb.append(pros.take(2).joinToString("; ") { it.replaceFirstChar { c -> c.lowercase() } })
            sb.append(".")
        }
        if (cons.isNotEmpty()) {
            sb.append(" Lưu ý: ").append(cons.first().replaceFirstChar { c -> c.lowercase() }).append(".")
        }
        return sb.toString()
    }

    /** Nhận xét ngắn về đội hình hai bên (tối đa 5 dòng). */
    fun analyze(allies: List<Hero>, enemies: List<Hero>): List<String> {
        val out = ArrayList<String>()
        if (allies.size >= 2) {
            if (allies.sumOf { it.t.tank } < 3) out.add("Đội bạn thiếu đỡ đòn.")
            if (allies.sumOf { it.t.cc } < 4) out.add("Đội bạn thiếu khống chế.")
            if (allies.none { it.dmg == Dmg.M }) out.add("Đội bạn chưa có sát thương phép.")
            if (allies.none { it.dmg == Dmg.P }) out.add("Đội bạn chưa có sát thương vật lý.")
        }
        if (enemies.size >= 2) {
            if (enemies.sumOf { it.t.tank } >= 6) out.add("Đối thủ nhiều đỡ đòn: ưu tiên tướng xuyên giáp.")
            if (enemies.count { it.t.burst >= 3 } >= 2) out.add("Đối thủ nhiều sát thương bùng nổ: cần tanker hoặc hồi phục.")
            if (enemies.count { it.t.mob >= 3 } >= 2) out.add("Đối thủ nhiều tướng cơ động: khống chế rất giá trị.")
            if (enemies.size >= 3 && enemies.count { it.dmg == Dmg.M } >= 3) out.add("Đối thủ nặng sát thương phép: cân nhắc trang bị giáp phép.")
            if (enemies.size >= 3 && enemies.count { it.dmg == Dmg.P } >= 4) out.add("Đối thủ nặng sát thương vật lý: cân nhắc trang bị giáp.")
        }
        return out.take(5)
    }

    fun suggest(
        pool: List<Hero>,
        allies: List<Hero>,
        enemies: List<Hero>,
        lane: Lane?,
        taken: Set<String>,
        limit: Int = 8
    ): List<Suggestion> {
        val candidates = pool.filter { it.id !in taken && (lane == null || lane in it.lanes) }

        val aTank = allies.sumOf { it.t.tank }
        val aCc = allies.sumOf { it.t.cc }
        val aEng = allies.sumOf { it.t.eng }
        val aSus = allies.sumOf { it.t.sus }
        val aDps = allies.sumOf { it.t.dps }
        val aMagic = allies.count { it.dmg == Dmg.M }
        val aPhys = allies.count { it.dmg == Dmg.P }
        val hasAllies = allies.isNotEmpty()

        val results = ArrayList<Suggestion>(candidates.size)
        for (c in candidates) {
            val acc = Acc()

            // 1) Nhu cầu đội hình
            if (hasAllies) {
                if (aTank < 3 && c.t.tank >= 2) acc.add(8 + c.t.tank, "Đội đang thiếu đỡ đòn, bạn làm tiền tuyến tốt")
                if (aTank >= 6 && c.t.tank >= 2) acc.add(-6, "Đội đã nhiều đỡ đòn, thêm tanker sẽ thiếu sát thương")
                if (aCc < 4 && c.t.cc >= 2) acc.add(6, "Bổ sung khống chế cho đội")
                if (aEng < 2 && c.t.eng >= 2) acc.add(6, "Đội thiếu người khai giao tranh")
                if (aSus < 2 && c.t.sus >= 2) acc.add(4, "Thêm hồi phục/khiên cho đội")
                if (aDps < 4 && c.t.dps >= 3) acc.add(7, "Đội thiếu sát thương chính liên tục")

                if (allies.size >= 2) {
                    if (aMagic == 0 && c.dmg == Dmg.M) acc.add(10, "Đội toàn sát thương vật lý, cần thêm sát thương phép")
                    if (aPhys == 0 && c.dmg == Dmg.P) acc.add(10, "Đội toàn sát thương phép, cần thêm sát thương vật lý")
                    if (allies.size >= 3 && c.dmg == Dmg.M && aMagic >= 3) acc.add(-10, "Đội đã quá nhiều sát thương phép")
                    if (allies.size >= 3 && c.dmg == Dmg.P && aPhys >= 4) acc.add(-10, "Đội đã quá nhiều sát thương vật lý")
                }
                if (c.cls == HeroClass.MARKSMAN && allies.any { it.cls == HeroClass.MARKSMAN }) {
                    acc.add(-10, "Đội đã có xạ thủ")
                }
                if (c.cls == HeroClass.SUPPORT && allies.any { it.cls == HeroClass.SUPPORT }) {
                    acc.add(-8, "Đội đã có trợ thủ")
                }
            }

            // 2) Khắc chế / bị khắc chế bởi từng đối thủ
            for (e in enemies) {
                if (e.id in c.counters) acc.add(10, "Khắc chế ${e.name}")
                if (e.id in c.weak) acc.add(-10, "Bị ${e.name} khắc chế")
                if (c.id in e.counters) acc.add(-10, "Bị ${e.name} khắc chế")
                if (c.id in e.weak) acc.add(10, "Khắc chế ${e.name}")

                // Ưu thế của bạn
                if (c.t.prc >= 2 && e.t.tank >= 2) acc.add(8, "Xuyên giáp tốt, khắc tanker ${e.name}")
                if (c.t.burst >= 2 && e.t.tank == 0 && e.t.sus <= 1) acc.add(6, "Sát thương bùng nổ hạ nhanh ${e.name} (mỏng máu)")
                if (c.t.cc >= 2 && e.t.mob >= 3) acc.add(6, "Khống chế chặn đường thoát của ${e.name}")
                if (c.t.mob >= 2 && e.t.rng >= 2 && e.t.tank == 0) acc.add(5, "Dễ áp sát ${e.name} (đánh xa, mỏng)")
                if (c.t.tank >= 2 && e.t.burst >= 3 && e.t.prc == 0) acc.add(4, "Chịu được sát thương bùng nổ của ${e.name}")
                if (c.t.sus >= 2 && e.t.burst <= 1 && e.t.dps >= 2) acc.add(3, "Hồi phục lấn át sát thương đều của ${e.name}")

                // Rủi ro bị khắc ngược
                if (e.t.prc >= 2 && c.t.tank >= 2) acc.add(-6, "${e.name} xuyên giáp, tanker khó trụ")
                if (e.t.burst >= 3 && c.t.tank == 0 && c.t.mob <= 1 && c.t.sus <= 1) {
                    acc.add(-6, "${e.name} có thể hạ bạn rất nhanh")
                }
                if (e.t.mob >= 3 && e.t.burst >= 2 && c.t.rng >= 2 && c.t.tank == 0 && c.t.cc == 0) {
                    acc.add(-5, "${e.name} dễ lao vào bạn, bạn thiếu khống chế")
                }
                if (e.t.cc >= 3 && c.t.mob >= 3 && c.t.tank == 0) acc.add(-3, "${e.name} nhiều khống chế, bạn dễ bị bắt")
            }

            // 3) Chọn sớm (chưa thấy đủ đối thủ): ưu tiên tướng linh hoạt, ít bị khắc chế cứng
            if (enemies.size < 3) {
                val flex = c.lanes.size - 1
                if (flex > 0) acc.add(2 * flex, "Chơi được ${c.lanes.size} vị trí nên khó bị đoán và khắc khi chọn sớm")
                if (c.t.sus >= 2 || c.t.mob >= 3 || c.t.tank >= 2) acc.add(3, "Ít bị khắc chế cứng, an toàn khi chọn sớm")
                if (c.t.tank == 0 && c.t.mob <= 1 && c.t.sus <= 1) acc.add(-4, "Mỏng và khó thoát thân: chọn sớm dễ bị đối thủ khắc")
            }

            val score = (50 + acc.total).coerceIn(0, 100)
            val pros = acc.pros.sortedByDescending { it.first }.map { it.second }.distinct().take(3)
            val cons = acc.cons.sortedBy { it.first }.map { it.second }.distinct().take(2)
            results.add(
                Suggestion(
                    hero = c,
                    score = score,
                    summary = buildSummary(c, lane, pros, cons),
                    pros = pros,
                    cons = cons
                )
            )
        }

        return results
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.hero.searchKey })
            .take(limit)
    }
}
