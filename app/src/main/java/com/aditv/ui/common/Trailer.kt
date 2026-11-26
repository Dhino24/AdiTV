package com.aditv.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Bande-annonce dans l'appli YouTube officielle de la Fire TV (image et son garantis). Le lecteur YouTube intégré
 * (WebView) ne montre pas l'image sur Fire TV — seulement le son — : il ne sert plus qu'en dernier recours.
 * Retour sur la télécommande : revient dans ADI TV.
 */
object Trailer {
    /** Appli YouTube Fire TV, YouTube pour Android TV, puis tout ce qui ouvre un lien YouTube. */
    private val packages = listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube")

    private fun intents(key: String): List<Intent> = packages.flatMap { pkg ->
        listOf(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")), Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")))
            .map { it.setPackage(pkg) }
    } + Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))

    private fun first(context: Context, key: String): Intent? =
        intents(key).firstOrNull { it.resolveActivity(context.packageManager) != null }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canOpen(context: Context): Boolean = first(context, "aditv-test") != null

    fun open(context: Context, key: String): Boolean = runCatching { context.startActivity(first(context, key) ?: return false); true }.getOrDefault(false)

    /** Recherche dans l'appli YouTube (résumés de match) : recherche native de l'appli TV, sinon page de résultats. */
    private fun searchIntents(q: String): List<Intent> {
        val url = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        return packages.flatMap { pkg ->
            listOf(
                Intent(Intent.ACTION_SEARCH).putExtra(android.app.SearchManager.QUERY, q).putExtra("query", q),
                Intent(Intent.ACTION_VIEW, url),
            ).map { it.setPackage(pkg) }
        }
    }

    fun canSearch(context: Context): Boolean = searchIntents("aditv").any { it.resolveActivity(context.packageManager) != null }

    fun search(context: Context, q: String): Boolean = runCatching {
        val i = searchIntents(q).firstOrNull { it.resolveActivity(context.packageManager) != null } ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
