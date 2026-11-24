package com.aditv.data.sport

import com.aditv.core.AppLog
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.FollowEntity
import com.aditv.data.db.ReminderEntity
import com.aditv.data.prefs.AppPrefs
import com.aditv.data.variants.VariantRanking
import com.aditv.data.variants.collapsed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Match trouvé dans le guide pour une équipe ou une compétition suivie. */
data class UpcomingMatch(
    val key: String,
    val title: String,
    val startUtc: Long,
    val endUtc: Long,
    val channelItemId: String,
    val channelName: String,
    val follows: List<String>,
    /** Rediffusion, résumé, magazine : pas de rappel automatique. */
    val notLive: Boolean,
) {
    fun isLive(now: Long) = now in startUtc until endUtc
}

private const val HOUR = 3_600_000L

/**
 * Équipes suivies, matchs à venir (depuis le guide uniquement), rappels automatiques et manuels.
 * Un même match diffusé sur plusieurs chaînes donne un seul rappel, rattaché à la meilleure chaîne :
 * favorite d'abord, puis française, puis meilleure version selon les préférences (VF, qualité).
 */
class SportRepository(
    private val db: AditvDatabase,
    private val prefs: AppPrefs,
    private val scheduler: ReminderScheduler?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.sport()
    private val mutex = Mutex()
    private val _matches = MutableStateFlow<List<UpcomingMatch>>(emptyList())
    /** Derniers matchs trouvés (rafraîchis après chaque actualisation du guide ou changement de suivi). */
    val matches: StateFlow<List<UpcomingMatch>> = _matches

    fun follows(accountId: String): Flow<List<FollowEntity>> = dao.observeFollows(accountId)
    fun upcomingReminders(accountId: String): Flow<List<ReminderEntity>> = dao.observeUpcoming(accountId, clock() - 6 * HOUR)

    /** Premier lancement d'un compte : équipes demandées par Adama, suivies d'office. */
    suspend fun seedDefaults(accountId: String) {
        if (prefs.sportSeeded(accountId)) return
        SportMatcher.PRESETS.filter { it.key in SportMatcher.DEFAULT_FOLLOWS }.forEach { follow(accountId, it) }
        prefs.setSportSeeded(accountId)
    }

    suspend fun follow(accountId: String, p: FollowPreset) =
        dao.upsertFollow(FollowEntity(accountId, p.key, p.name, p.aliases.joinToString("\n"), p.excludes.joinToString("\n"), p.kind, clock()))

    suspend fun unfollow(accountId: String, key: String) = dao.deleteFollow(accountId, key)

    /** Cherche dans le guide (−3 h → +7 j) les programmes des équipes suivies, puis met à jour les rappels automatiques. */
    suspend fun refresh(accountId: String): List<UpcomingMatch> = mutex.withLock {
        val found = runCatching { scan(accountId) }.onFailure { AppLog.w("Recherche des matchs impossible", it) }.getOrDefault(emptyList())
        _matches.value = found
        runCatching { syncAutoReminders(accountId, found) }.onFailure { AppLog.w("Rappels automatiques non mis à jour", it) }
        found
    }

    internal suspend fun scan(accountId: String): List<UpcomingMatch> {
        val now = clock()
        val follows = dao.follows(accountId)
        if (follows.isEmpty()) return emptyList()
        val variantPrefs = prefs.variantPrefs.first()
        val favorites = db.live().favoriteIds(accountId).first().toSet()
        val catNames = db.catalog().categories(accountId, "LIVE").first().associate { it.id to it.name }
        val mappings = db.live().mappings(accountId).associateBy { it.itemId }
        val sources = db.live().sources(accountId).associateBy { it.id }
        // Chaînes regardables par identifiant de guide effectif (association manuelle prioritaire).
        val byEpg = HashMap<String, MutableList<CatalogCard>>()
        db.catalog().allChannels(accountId).forEach { c ->
            val m = mappings[c.id]
            val epg = when { m?.disabled == true -> null; m?.epgChannelId != null -> m.epgChannelId; else -> c.epgChannelId } ?: return@forEach
            byEpg.getOrPut(epg) { mutableListOf() }.add(c)
        }
        val merged = LinkedHashMap<String, UpcomingMatch>()
        for (f in follows) {
            val aliases = f.aliases.lines().filter { it.isNotBlank() }
            val excludes = f.excludes.lines().filter { it.isNotBlank() }
            val patterns = SportMatcher.likePatterns(aliases).ifEmpty { continue }
            val rows = patterns.chunked(4).flatMap { chunk ->
                val p = List(4) { i -> chunk.getOrElse(i) { chunk.first() } }
                dao.programmesMatching(accountId, now - 3 * HOUR, now + 7 * 24 * HOUR, p[0], p[1], p[2], p[3])
            }
            for (p in rows) {
                val candidates = byEpg[p.channelId].orEmpty()
                if (candidates.isEmpty()) continue
                if (!SportMatcher.mentions(p.title, aliases, excludes)) continue
                val sportChannel = candidates.any { c -> SportMatcher.isSportChannel(c.title, c.categoryId?.let(catNames::get)) }
                if (!SportMatcher.isSportContext(p.title, p.category, sportChannel)) continue
                val best = candidates.minWith(compareBy<CatalogCard>(
                    { if (it.id in favorites) 0 else 1 },
                    { if (it.lang == "fr") 0 else 1 },
                    { VariantRanking.cost(it.lang, it.quality, variantPrefs) },
                ))
                val shift = ((sources[p.sourceId]?.offsetMinutes ?: 0) + (mappings[best.id]?.offsetMinutes ?: 0)) * 60_000L
                val start = p.startUtc + shift; val end = p.endUtc + shift
                if (end <= now) continue
                val key = SportMatcher.matchKey(p.title, start)
                val existing = merged[key]
                if (existing == null) merged[key] = UpcomingMatch(key, p.title, start, end, best.id, best.title, listOf(f.name), SportMatcher.isLikelyNotLive(p.title, p.description))
                else if (f.name !in existing.follows) merged[key] = existing.copy(follows = existing.follows + f.name)
            }
        }
        return merged.values.sortedBy { it.startUtc }
    }

    /** Rappels automatiques : créés pour les vrais matchs à venir, retirés s'ils ne sont plus au guide. Jamais recréés après refus. */
    private suspend fun syncAutoReminders(accountId: String, found: List<UpcomingMatch>) {
        val now = clock()
        val lead = prefs.reminderLeadMinutes.first() * 60_000L
        val wanted = found.filter { !it.notLive && it.startUtc - lead > now }
        val keys = wanted.map { it.key }.toSet()
        for (m in wanted) {
            if (dao.reminderByKey(accountId, m.key) != null) continue
            dao.insertReminder(ReminderEntity(
                accountId = accountId, matchKey = m.key, channelItemId = m.channelItemId, channelName = m.channelName, title = m.title,
                startUtc = m.startUtc, endUtc = m.endUtc, notifyAt = m.startUtc - lead, auto = true, followKey = m.follows.firstOrNull(), createdAt = now,
            ))
        }
        dao.autoReminders(accountId).filter { r -> !r.dismissed && r.firedAt == null && r.startUtc > now && r.matchKey !in keys }
            .forEach { r -> dao.deleteReminder(r.id); scheduler?.cancel(r.id) }
        dao.purge(now - 24 * HOUR)
        scheduler?.rescheduleAll()
    }

    /** Un rappel actif existe-t-il pour ce match ? (bouton « ✓ Rappel programmé ») */
    suspend fun isReminded(accountId: String, title: String, startUtc: Long): Boolean =
        dao.reminderByKey(accountId, SportMatcher.matchKey(title, startUtc))?.let { !it.dismissed } ?: false

    /** « Me rappeler » depuis le guide ou une carte de match. Renvoie true si un rappel est désormais actif. */
    suspend fun toggleReminder(accountId: String, title: String, startUtc: Long, endUtc: Long, channelItemId: String, channelName: String): Boolean {
        val key = SportMatcher.matchKey(title, startUtc)
        val existing = dao.reminderByKey(accountId, key)
        if (existing != null && !existing.dismissed) {
            // Un rappel automatique refusé n'est pas recréé au prochain balayage.
            if (existing.auto) dao.dismiss(existing.id) else dao.deleteReminder(existing.id)
            scheduler?.cancel(existing.id)
            return false
        }
        val lead = prefs.reminderLeadMinutes.first() * 60_000L
        val r = ReminderEntity(
            id = existing?.id ?: 0, accountId = accountId, matchKey = key, channelItemId = channelItemId, channelName = channelName, title = title,
            startUtc = startUtc, endUtc = endUtc, notifyAt = maxOf(clock() + 5_000, startUtc - lead), auto = false, followKey = null, createdAt = clock(),
        )
        dao.upsertReminder(r)
        scheduler?.rescheduleAll()
        return true
    }

    suspend fun deleteReminder(id: Long) {
        val r = dao.reminder(id) ?: return
        if (r.auto) dao.dismiss(id) else dao.deleteReminder(id)
        scheduler?.cancel(id)
    }

    /** Chaînes de sport françaises du bouquet : catégorie ou nom de sport, langue/pays français ; une carte par chaîne. */
    suspend fun frenchSportChannels(accountId: String): List<CatalogCard> {
        val catNames = db.catalog().categories(accountId, "LIVE").first().associate { it.id to it.name }
        val variantPrefs = prefs.variantPrefs.first()
        return db.catalog().allChannels(accountId)
            .filter { c ->
                val cat = c.categoryId?.let(catNames::get)
                val french = c.lang == "fr" || (cat != null && FRENCH_CATEGORY.containsMatchIn(cat))
                french && SportMatcher.isSportChannel(c.title, cat)
            }
            .collapsed(variantPrefs).map { it.first }
    }

    private companion object {
        /** Compilée une fois (avant : une expression par chaîne, sur des milliers de chaînes). */
        val FRENCH_CATEGORY = Regex("(?i)(^|[^a-z])(fr|france|french|francais|français)([^a-z]|$)")
    }
}
