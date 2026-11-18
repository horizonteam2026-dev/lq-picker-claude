package com.lqpicker

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.lqpicker.scan.ScanBus
import com.lqpicker.scan.ScanResult

/** SELF = tướng của bạn; ALLY = đồng đội; ENEMY = đối thủ; BAN = tướng bị cấm. */
enum class Team { SELF, ALLY, ENEMY, BAN }

/** Ô đang được chọn tướng. Với BAN, index = -1 nghĩa là thêm tướng cấm mới. */
data class PickTarget(val team: Team, val index: Int)

class DraftViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx: Application = app
    val heroes: List<Hero> = HeroRepository.load(app)
    private val byId: Map<String, Hero> = heroes.associateBy { it.id }

    /** null = chưa thiết lập (coi như có đủ tướng). */
    var owned by mutableStateOf<Set<String>?>(OwnedPrefs.load(app))
        private set
    var self by mutableStateOf<String?>(null)
        private set
    /** Ô 0 là bạn (đã tách sang self), 4 ô đồng đội còn lại. */
    var allies by mutableStateOf<List<String?>>(List(ALLY_SLOTS) { null })
        private set
    var enemies by mutableStateOf<List<String?>>(List(ENEMY_SLOTS) { null })
        private set
    var bans by mutableStateOf<List<String>>(emptyList())
        private set
    var picker by mutableStateOf<PickTarget?>(null)
        private set
    var showOwned by mutableStateOf(false)
        private set

    val lane: Lane? get() = ScanBus.lane

    fun hero(id: String?): Hero? = if (id == null) null else byId[id]

    val ownedHeroes: List<Hero> get() = OwnedPrefs.filter(heroes, owned)

    private fun takenIds(): Set<String> {
        val s = HashSet<String>()
        self?.let { s.add(it) }
        allies.forEach { if (it != null) s.add(it) }
        enemies.forEach { if (it != null) s.add(it) }
        s.addAll(bans)
        return s
    }

    /** Tướng có thể gợi ý: đã sở hữu, đúng vị trí, chưa bị chọn hay cấm. */
    private fun pool(): List<Hero> {
        val taken = takenIds()
        val l = lane
        return ownedHeroes.filter { it.id !in taken && (l == null || l in it.lanes) }
    }

    /** Tướng có thể chọn cho ô hiện tại. Ô "Bạn" chỉ hiện tướng đã sở hữu. */
    fun availableFor(target: PickTarget): List<Hero> {
        val current = currentId(target)
        val taken = takenIds()
        val source = if (target.team == Team.SELF) ownedHeroes else heroes
        return source.filter { it.id !in taken || it.id == current }
    }

    fun currentId(target: PickTarget): String? = when (target.team) {
        Team.SELF -> self
        Team.ALLY -> allies.getOrNull(target.index)
        Team.ENEMY -> enemies.getOrNull(target.index)
        Team.BAN -> bans.getOrNull(target.index)
    }

    val suggestions: List<Suggestion> by derivedStateOf {
        SuggestionEngine.suggest(
            pool = pool(),
            allies = allies.mapNotNull { hero(it) },
            enemies = enemies.mapNotNull { hero(it) },
            lane = lane,
            taken = takenIds()
        )
    }

    val notes: List<String> by derivedStateOf {
        SuggestionEngine.analyze(allies.mapNotNull { hero(it) }, enemies.mapNotNull { hero(it) })
    }

    val itemTips: List<String> by derivedStateOf {
        ItemAdvisor.advise(hero(self), allies.mapNotNull { hero(it) }, enemies.mapNotNull { hero(it) })
    }

    val hasAnyPick: Boolean
        get() = self != null || allies.any { it != null } || enemies.any { it != null }

    fun selectLane(l: Lane?) {
        ScanBus.lane = if (ScanBus.lane == l) null else l
    }

    fun openPicker(target: PickTarget) {
        picker = target
    }

    fun closePicker() {
        picker = null
    }

    fun openOwned() {
        showOwned = true
    }

    fun closeOwned() {
        showOwned = false
        // Rời màn hình tướng khi đang ở bước hướng dẫn thì chuyển sang bước kế tiếp.
        if (tourStep >= 0 && TourSteps.all[tourStep].screen == TourScreen.OWNED) nextTour()
    }

    /** Lưu danh sách tướng đã sở hữu. null = có tất cả. */
    fun saveOwned(ids: Set<String>?) {
        owned = ids
        OwnedPrefs.save(appCtx, ids)
    }

    private var lastScan = 0L

    /** Điền đội hình từ kết quả quét. Không ghi đè nếu quét không thấy tướng nào. */
    fun applyScan(r: ScanResult) {
        if (r.stamp == lastScan) return
        lastScan = r.stamp
        val a = r.allyIds.filter { byId.containsKey(it) }.take(ALLY_SLOTS)
        val e = r.enemyIds.filter { byId.containsKey(it) && it !in a }.take(ENEMY_SLOTS)
        if (a.isNotEmpty()) allies = List(ALLY_SLOTS) { a.getOrNull(it) }
        if (e.isNotEmpty()) enemies = List(ENEMY_SLOTS) { e.getOrNull(it) }
    }

    fun pick(heroId: String) {
        val t = picker ?: return
        when (t.team) {
            Team.SELF -> self = heroId
            Team.ALLY -> allies = allies.toMutableList().also { it[t.index] = heroId }
            Team.ENEMY -> enemies = enemies.toMutableList().also { it[t.index] = heroId }
            Team.BAN -> bans = if (t.index in bans.indices) {
                bans.toMutableList().also { it[t.index] = heroId }
            } else if (bans.size < MAX_BANS) {
                bans + heroId
            } else bans
        }
        picker = null
    }

    fun clearCurrent() {
        val t = picker ?: return
        when (t.team) {
            Team.SELF -> self = null
            Team.ALLY -> allies = allies.toMutableList().also { it[t.index] = null }
            Team.ENEMY -> enemies = enemies.toMutableList().also { it[t.index] = null }
            Team.BAN -> if (t.index in bans.indices) {
                bans = bans.toMutableList().also { it.removeAt(t.index) }
            }
        }
        picker = null
    }

    fun removeBan(index: Int) {
        if (index in bans.indices) bans = bans.toMutableList().also { it.removeAt(index) }
    }

    fun reset() {
        self = null
        allies = List(ALLY_SLOTS) { null }
        enemies = List(ENEMY_SLOTS) { null }
        bans = emptyList()
        picker = null
    }

    /** Hướng dẫn lần đầu. -1 = đang tắt. Bước đầu tiên là chọn tướng sở hữu. */
    var tourStep by mutableIntStateOf(-1)
        private set

    /** Màn hình chúc may mắn sau khi hoàn tất hướng dẫn. */
    var celebrate by mutableStateOf(false)
        private set

    fun startTour() = goTourStep(0)

    fun nextTour() {
        val next = tourStep + 1
        if (next > TourSteps.all.lastIndex) finishTour(show = true) else goTourStep(next)
    }

    fun skipTour() = finishTour(show = false)

    fun dismissCelebrate() {
        celebrate = false
    }

    private fun goTourStep(i: Int) {
        tourStep = i
        picker = null
        showOwned = TourSteps.all[i].screen == TourScreen.OWNED
    }

    private fun finishTour(show: Boolean) {
        tourStep = -1
        showOwned = false
        picker = null
        celebrate = show
        OwnedPrefs.setTourDone(appCtx, true)
    }

    // Đặt sau khai báo các trạng thái trên để không bị ghi đè khi khởi tạo.
    init {
        if (!OwnedPrefs.isTourDone(appCtx)) startTour()
    }

    companion object {
        const val ALLY_SLOTS = 4
        const val ENEMY_SLOTS = 5
        const val MAX_BANS = 8
    }
}
