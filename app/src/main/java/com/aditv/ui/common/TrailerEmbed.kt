package com.aditv.ui.common

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Commande du lecteur intégré (pause / lecture) depuis la télécommande. */
class TrailerControl { internal var web: WebView? = null
    fun toggle() { web?.evaluateJavascript("if(window.player){var s=player.getPlayerState(); if(s==1){player.pauseVideo()}else{player.playVideo()}}", null) }
}

/**
 * Bande-annonce TMDB jouée dans ADI TV avec le lecteur YouTube officiel (IFrame), la seule intégration autorisée
 * par YouTube (aucune extraction de flux). [muted] + [loop] : vidéo de fond. Si la vidéo refuse l'intégration,
 * [onError] permet de basculer vers l'application YouTube.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerEmbed(
    videoId: String, muted: Boolean, loop: Boolean, modifier: Modifier = Modifier,
    control: TrailerControl? = null, onPlaying: () -> Unit = {}, onEnded: () -> Unit = {}, onError: () -> Unit = {},
) {
    val playing = rememberUpdatedState(onPlaying)
    val ended = rememberUpdatedState(onEnded)
    val error = rememberUpdatedState(onError)
    val id = videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    androidx.compose.runtime.DisposableEffect(Unit) { VideoHole.embeds++; onDispose { VideoHole.embeds-- } }
    key(id) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // La télécommande reste à ADI TV (le WebView ne prend jamais le focus).
                    isFocusable = false; isFocusableInTouchMode = false
                    // Transparent + accélération matérielle : la vidéo (affichée derrière la fenêtre sur Fire TV) reste visible.
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    val main = Handler(Looper.getMainLooper())
                    addJavascriptInterface(object {
                        @JavascriptInterface fun state(s: Int) { main.post { if (s == 1) playing.value() else if (s == 0 && !loop) ended.value() } }
                        @JavascriptInterface fun error(@Suppress("UNUSED_PARAMETER") code: Int) { main.post { error.value() } }
                    }, "AdiTv")
                    val html = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div><script>
var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);
var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',
playerVars:{autoplay:1,mute:${if (muted) 1 else 0},controls:0,rel:0,modestbranding:1,playsinline:1,iv_load_policy:3,fs:0,disablekb:1,cc_load_policy:0,hl:'fr'${if (loop) ",loop:1,playlist:'$id'" else ""}},
events:{onReady:function(e){${if (muted) "e.target.mute();" else ""}e.target.playVideo();},
onStateChange:function(e){AdiTv.state(e.data)},onError:function(e){AdiTv.error(e.data)}}});}
</script></body></html>"""
                    loadDataWithBaseURL("https://www.aditv.app/", html, "text/html", "utf-8", null)
                    control?.web = this
                }
            },
            onRelease = { w -> control?.let { if (it.web === w) it.web = null }; w.stopLoading(); w.loadUrl("about:blank"); w.destroy() },
            modifier = modifier,
        )
    }
}
