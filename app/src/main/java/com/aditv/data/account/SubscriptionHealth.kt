package com.aditv.data.account

import com.aditv.data.db.AccountEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Santé de l'abonnement, d'après le dernier contrôle auprès du fournisseur. */
data class SubscriptionHealth(val level: Level, val title: String, val detail: String?, val daysLeft: Long?) {
    enum class Level { OK, SOON, EXPIRED, PROBLEM, UNKNOWN }
    val needsAttention get() = level == Level.SOON || level == Level.EXPIRED || level == Level.PROBLEM

    companion object {
        const val WARN_DAYS = 7

        fun assess(a: AccountEntity, nowMs: Long = System.currentTimeMillis()): SubscriptionHealth {
            val conns = a.maxConnections?.let { m -> "$m connexion${if (m > 1) "s" else ""} simultanée${if (m > 1) "s" else ""}" + (a.activeConnections?.let { " · $it en cours" } ?: "") }
            val status = a.status?.trim()?.lowercase()
            if (status != null && status !in setOf("active", "actif", "enabled")) {
                return SubscriptionHealth(Level.PROBLEM, "Abonnement « ${a.status} » selon le fournisseur", conns, null)
            }
            val exp = a.expiresAtEpochSec ?: return SubscriptionHealth(if (status == null) Level.UNKNOWN else Level.OK,
                if (status == null) "État non communiqué par le fournisseur" else "Abonnement actif (sans date de fin communiquée)", conns, null)
            val leftMs = exp * 1000 - nowMs
            val days = Math.floorDiv(leftMs, 86_400_000L)
            val date = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(exp * 1000))
            return when {
                leftMs <= 0 -> SubscriptionHealth(Level.EXPIRED, "Abonnement expiré depuis le $date", conns, days)
                days < 1 -> SubscriptionHealth(Level.SOON, "Votre abonnement expire aujourd'hui", "Fin le $date", 0)
                days <= WARN_DAYS -> SubscriptionHealth(Level.SOON, "Votre abonnement expire dans $days jour${if (days > 1) "s" else ""}", "Fin le $date", days)
                else -> SubscriptionHealth(Level.OK, "Abonnement actif · $days jours restants", "Jusqu'au $date" + (conns?.let { " · $it" } ?: ""), days)
            }
        }
    }
}
