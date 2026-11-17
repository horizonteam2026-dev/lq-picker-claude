package com.lqpicker

import android.content.Context

/**
 * Danh sách tướng người chơi đã sở hữu, lưu trên máy.
 * null = chưa thiết lập: coi như có đủ tướng.
 */
object OwnedPrefs {
    private const val FILE = "lq_prefs"
    private const val KEY = "owned"

    fun load(ctx: Context): Set<String>? = try {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!p.contains(KEY)) null else HashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
    } catch (_: Exception) {
        null
    }

    fun save(ctx: Context, ids: Set<String>?) {
        try {
            val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            if (ids == null) e.remove(KEY) else e.putStringSet(KEY, HashSet(ids))
            e.apply()
        } catch (_: Exception) {
        }
    }

    fun filter(all: List<Hero>, owned: Set<String>?): List<Hero> =
        if (owned == null) all else all.filter { it.id in owned }
}
