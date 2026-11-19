package com.aditv.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Bande-annonce : TMDB ne fournit qu'une référence YouTube, pas de flux vidéo lisible directement.
 * On l'ouvre donc dans l'application YouTube de la Fire TV (ou un navigateur) si elle est installée ;
 * sinon le bouton n'est pas affiché. Pas d'extraction de flux YouTube (interdite par ses conditions).
 */
object Trailer {
    private fun intent(key: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canOpen(context: Context): Boolean =
        intent("aditv-test").resolveActivity(context.packageManager) != null

    fun open(context: Context, key: String): Boolean = runCatching { context.startActivity(intent(key)); true }.getOrDefault(false)
}
