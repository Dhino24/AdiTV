package com.aditv.playback

/** Demande de lecture. L'URL contient des secrets : jamais journalisée ni stockée. */
class PlaybackRequest(
    val url: String,
    val title: String,
    val subtitle: String?,
    val isLive: Boolean,
    val startPositionMs: Long?,
    /** Contenu pour la progression (null pour le direct). */
    val contentId: String?,
    val kind: String,
    val seriesItemId: String?,
) {
    override fun toString() = "PlaybackRequest($title, live=$isLive, start=$startPositionMs)"
}

data class TrackOption(val groupIndex: Int, val trackIndex: Int, val label: String, val selected: Boolean)
