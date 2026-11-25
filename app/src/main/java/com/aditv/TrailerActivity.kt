package com.aditv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Bande-annonce plein écran DANS ADI TV : écran Android dédié avec un simple WebView plein écran (aucune couche
 * Compose autour). Intégré dans l'interface Compose, le lecteur YouTube ne donnait que le son sur Fire TV.
 * OK : pause / lecture · Menu : ouvrir dans l'appli YouTube · Retour : revenir à la fiche.
 */
class TrailerActivity : Activity() {
    private var web: WebView? = null
    private var custom: View? = null
    private lateinit var root: FrameLayout
    private lateinit var hint: TextView
    private val main = Handler(Looper.getMainLooper())
    private val key by lazy { intent.getStringExtra(EXTRA_KEY).orEmpty().filter { it.isLetterOrDigit() || it == '_' || it == '-' } }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val w = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false; isFocusableInTouchMode = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                // Pas d'icône « lecture » grise par-dessus la vidéo.
                override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                // Si le lecteur passe en plein écran natif, on affiche sa vue vidéo par-dessus.
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    custom?.let { root.removeView(it) }
                    custom = view
                    root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    hint.bringToFront()
                }
                override fun onHideCustomView() { custom?.let { root.removeView(it) }; custom = null }
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun state(s: Int) { main.post { if (s == 0) finish() } }
                @JavascriptInterface fun error(@Suppress("UNUSED_PARAMETER") code: Int) { main.post { openInYouTube() } }
            }, "AdiTv")
        }
        web = w
        root.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        hint = TextView(this).apply {
            text = "OK : pause / lecture  ·  Menu ≡ : ouvrir dans YouTube  ·  Retour : revenir"
            setTextColor(0xFFC3CED8.toInt()); textSize = 14f
            setBackgroundColor(0xB0000000.toInt()); setPadding(24, 10, 24, 10)
        }
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 48 })
        main.postDelayed({ hint.visibility = View.GONE }, 5_000)
        setContentView(root)
        val html = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}#p{position:fixed;top:0;left:0;width:100vw;height:100vh}</style></head>
<body><div id="p"></div><script>
var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);
var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$key',
playerVars:{autoplay:1,controls:0,rel:0,modestbranding:1,playsinline:1,iv_load_policy:3,fs:0,disablekb:1,hl:'fr'},
events:{onReady:function(e){e.target.playVideo();},onStateChange:function(e){AdiTv.state(e.data)},onError:function(e){AdiTv.error(e.data)}}});}
function toggle(){if(!player)return;if(player.getPlayerState()==1)player.pauseVideo();else player.playVideo();}
</script></body></html>"""
        w.loadDataWithBaseURL("https://www.aditv.app/", html, "text/html", "utf-8", null)
    }

    private fun openInYouTube() {
        com.aditv.ui.common.Trailer.open(this, key)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.repeatCount == 0) { web?.evaluateJavascript("toggle()", null); hint.visibility = View.VISIBLE; main.postDelayed({ hint.visibility = View.GONE }, 3_000) }
                return true
            }
            KeyEvent.KEYCODE_MENU -> { openInYouTube(); return true }
            KeyEvent.KEYCODE_BACK -> { finish(); return true }
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() { super.onPause(); web?.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        web?.apply { stopLoading(); loadUrl("about:blank"); (parent as? ViewGroup)?.removeView(this); destroy() }
        web = null
        super.onDestroy()
    }

    companion object { const val EXTRA_KEY = "youtube_key" }
}
