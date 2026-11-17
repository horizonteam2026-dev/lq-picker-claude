package com.lqpicker

enum class Lane(val label: String) {
    TOP("Caesar"),
    JUNGLE("Rừng"),
    MID("Giữa"),
    ADC("Rồng"),
    SUP("Trợ thủ")
}

enum class HeroClass(val label: String) {
    TANK("Đỡ đòn"),
    WARRIOR("Đấu sĩ"),
    ASSASSIN("Sát thủ"),
    MAGE("Pháp sư"),
    MARKSMAN("Xạ thủ"),
    SUPPORT("Trợ thủ")
}

/** P = vật lý, M = phép, T = chuẩn. */
enum class Dmg { P, M, T }

/** Các chỉ số 0..3. */
data class Traits(
    val tank: Int,
    val burst: Int,
    val dps: Int,
    val cc: Int,
    val mob: Int,
    val sus: Int,
    val rng: Int,
    val eng: Int,
    val push: Int,
    val prc: Int
)

data class Hero(
    val id: String,
    val name: String,
    val cls: HeroClass,
    val lanes: List<Lane>,
    val dmg: Dmg,
    val t: Traits,
    val counters: Set<String>,
    val weak: Set<String>,
    /** Tên viết thường, bỏ dấu, dùng để tìm kiếm. */
    val searchKey: String
)

class Suggestion(
    val hero: Hero,
    /** 0..100 */
    val score: Int,
    /** Một đoạn lý giải ngắn, dễ đọc trong 15 giây. */
    val summary: String,
    val pros: List<String>,
    val cons: List<String>
)
