package com.lqpicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Bỏ dấu tiếng Việt và viết thường: "Điêu Thuyền" -> "dieu thuyen". */
fun String.foldVi(): String {
    val s = this.replace('đ', 'd').replace('Đ', 'D')
    val n = Normalizer.normalize(s, Normalizer.Form.NFD)
    val sb = StringBuilder(n.length)
    for (ch in n) {
        if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) sb.append(ch)
    }
    return sb.toString().lowercase()
}

object HeroRepository {

    fun load(context: Context): List<Hero> {
        val text = context.assets.open("heroes.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(text).getJSONArray("heroes")
        val out = ArrayList<Hero>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.getJSONObject("t")
            val name = o.getString("name")
            out.add(
                Hero(
                    id = o.getString("id"),
                    name = name,
                    cls = HeroClass.valueOf(o.getString("cls")),
                    lanes = o.getJSONArray("lanes").toStringList().map { Lane.valueOf(it) },
                    dmg = Dmg.valueOf(o.getString("dmg")),
                    t = Traits(
                        tank = t.optInt("tank"),
                        burst = t.optInt("burst"),
                        dps = t.optInt("dps"),
                        cc = t.optInt("cc"),
                        mob = t.optInt("mob"),
                        sus = t.optInt("sus"),
                        rng = t.optInt("rng"),
                        eng = t.optInt("eng"),
                        push = t.optInt("push"),
                        prc = t.optInt("prc")
                    ),
                    counters = o.optJSONArray("counters").toStringList().toSet(),
                    weak = o.optJSONArray("weak").toStringList().toSet(),
                    searchKey = name.foldVi()
                )
            )
        }
        return out.sortedBy { it.searchKey }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = ArrayList<String>(length())
        for (i in 0 until length()) list.add(getString(i))
        return list
    }
}
