package com.lqpicker.scan

import com.lqpicker.Hero
import com.lqpicker.foldVi

/** Một dòng chữ OCR: tâm khung chữ (cx, cy) và chiều cao h, đều theo pixel màn hình. */
class OcrLine(val text: String, val cx: Float, val cy: Float, val h: Float)

class MatchResult(val allyIds: List<String>, val enemyIds: List<String>)

/**
 * Khớp chữ OCR với tên tướng theo bố cục thật của màn chọn tướng Liên Quân:
 *  - Cột TRÁI (đồng đội): mỗi hàng gồm [tên người chơi, tên tướng, danh hiệu].
 *  - Cột PHẢI (đối thủ): mỗi hàng gồm [tên tướng màu đỏ, danh hiệu].
 *  - Vùng GIỮA là lưới chọn tướng của chính bạn: BỎ QUA hoàn toàn.
 * Hàm thuần, không phụ thuộc Android.
 */
object NameMatcher {
    const val LEFT_MAX = 0.27f
    const val RIGHT_MIN = 0.76f

    /** Hàng cách nhau > ngưỡng này (x chiều cao chữ) thì tính là hàng người chơi khác. */
    private const val ROW_GAP = 1.85f

    private class Key(val id: String, val key: String)

    private fun norm(s: String): String = s.foldVi().filter { it.isLetterOrDigit() }

    fun match(lines: List<OcrLine>, heroes: List<Hero>, screenWidth: Float): MatchResult {
        val keys = heroes.map { Key(it.id, norm(it.name)) }
        val left = lines.filter { it.cx < screenWidth * LEFT_MAX }
        val right = lines.filter { it.cx > screenWidth * RIGHT_MIN }
        val allies = scanZone(left, keys, skipPlayerName = true)
        val enemies = scanZone(right, keys, skipPlayerName = false).filter { it !in allies }
        return MatchResult(allies, enemies)
    }

    private fun scanZone(zone: List<OcrLine>, keys: List<Key>, skipPlayerName: Boolean): List<String> {
        if (zone.isEmpty()) return emptyList()
        val sorted = zone.sortedBy { it.cy }

        // Dòng đầu của mỗi hàng 3 dòng ở cột trái là tên người chơi: không nhận làm tên tướng
        // (phòng khi người chơi đặt nick trùng tên tướng).
        val ignored = HashSet<OcrLine>()
        if (skipPlayerName) {
            val medianH = sorted.map { it.h }.sorted()[sorted.size / 2].coerceAtLeast(1f)
            var cluster = ArrayList<OcrLine>()
            fun flush() {
                if (cluster.size >= 3) ignored.add(cluster[0])
                cluster = ArrayList()
            }
            for (l in sorted) {
                if (cluster.isNotEmpty() && l.cy - cluster.last().cy > medianH * ROW_GAP) flush()
                cluster.add(l)
            }
            flush()
        }

        val out = ArrayList<String>()
        for (l in sorted) {
            if (l in ignored) continue
            for (k in matchLine(l.text, keys)) if (k.id !in out) out.add(k.id)
        }
        return out
    }

    private fun matchLine(text: String, keys: List<Key>): List<Key> {
        val tokens = text.foldVi()
            .split(Regex("[^\\p{L}\\p{N}']+"))
            .filter { it.isNotEmpty() }
        val found = ArrayList<Key>()
        var i = 0
        while (i < tokens.size) {
            var matched: Key? = null
            var used = 1
            for (size in minOf(3, tokens.size - i) downTo 1) {
                val joined = tokens.subList(i, i + size).joinToString("").filter { it.isLetterOrDigit() }
                // Chỉ cho phép sai 1 ký tự với từ đơn; tên nhiều từ ghép lại phải khớp chính xác
                // (tránh "tư lệnh" -> "tulenh" khớp nhầm "Tulen").
                val k = find(joined, keys, wholeLine = tokens.size == size, allowFuzzy = size == 1)
                if (k != null) {
                    matched = k
                    used = size
                    break
                }
            }
            if (matched != null) {
                found.add(matched)
                i += used
            } else {
                i++
            }
        }
        return found
    }

    private fun find(word: String, keys: List<Key>, wholeLine: Boolean, allowFuzzy: Boolean): Key? {
        if (word.length < 3) return null
        keys.firstOrNull { it.key == word }?.let {
            // Tên ngắn (<4 ký tự) chỉ nhận khi cả dòng chỉ có tên đó.
            return if (it.key.length >= 4 || wholeLine) it else null
        }
        if (allowFuzzy && word.length >= 5) {
            var best: Key? = null
            var bestD = 2
            for (k in keys) {
                if (k.key.length < 5 || kotlin.math.abs(k.key.length - word.length) > 1) continue
                val d = editDistance(word, k.key, 1)
                if (d < bestD) {
                    bestD = d
                    best = k
                }
            }
            return best
        }
        return null
    }

    /** Levenshtein, dừng sớm khi vượt [limit] (trả về limit + 1). */
    internal fun editDistance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
