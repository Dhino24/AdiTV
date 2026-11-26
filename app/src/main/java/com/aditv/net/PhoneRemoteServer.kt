package com.aditv.net

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Télécommande sur le téléphone : la TV sert, sur le réseau local seulement, une page avec croix directionnelle,
 * OK, retour, lecture, chaînes, volume et recherche au clavier.
 *
 * Sécurité (même esprit que [RemoteEntryServer]) :
 * - écoute uniquement sur l'adresse locale de la TV, port aléatoire ;
 * - jeton aléatoire de 128 bits dans le QR code ; sans lui, code à 4 chiffres affiché sur la TV ;
 * - 5 essais faux ferment le serveur ; fermeture automatique après [idleMs] sans commande ;
 * - requêtes bornées à 4 Ko, aucune commande ni texte journalisé ; la TV n'exécute que des touches et une recherche.
 */
class PhoneRemoteServer(
    private val bindAddress: InetAddress,
    private val onCommand: (String, String?) -> Unit,
    private val onClosed: (String) -> Unit = {},
    private val idleMs: Long = 3 * 3_600_000L,
    random: SecureRandom = SecureRandom(),
) {
    private val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    val pin: String = "%04d".format(random.nextInt(10_000))
    var url: String? = null; private set
    var shortUrl: String? = null; private set
    @Volatile private var failures = 0
    @Volatile private var socket: ServerSocket? = null
    @Volatile var closed = false; private set
    @Volatile private var lastUse = System.currentTimeMillis()

    fun start(): Boolean {
        if (socket != null || closed) return socket != null
        return try {
            val s = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, 0), 8) }
            socket = s
            val base = "http://${bindAddress.hostAddress}:${s.localPort}/"
            shortUrl = base; url = "$base?t=$token"
            thread(name = "aditv-phone-remote", isDaemon = true) { loop(s) }
            thread(name = "aditv-phone-remote-idle", isDaemon = true) {
                while (!closed) {
                    try { Thread.sleep(60_000) } catch (_: InterruptedException) { return@thread }
                    if (System.currentTimeMillis() - lastUse > idleMs) close("Télécommande arrêtée après 3 h sans utilisation.")
                }
            }
            true
        } catch (_: Exception) { closed = true; false }
    }

    fun close(reason: String = "Télécommande arrêtée.") {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        onClosed(reason)
    }

    private fun loop(s: ServerSocket) {
        while (!closed) {
            val client = try { s.accept() } catch (_: Exception) { break }
            thread(isDaemon = true) { try { client.use { handle(it) } } catch (_: Exception) { } }
        }
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val form: Map<String, String>)

    private fun read(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (head.size() > MAX_BYTES) return null
        }
        val lines = head.toString("ISO-8859-1").split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size < 2) return null
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BYTES) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) { val n = input.read(body, read, length - read); if (n < 0) return null; read += n }
        val target = parts[1]
        return Request(parts[0], target.substringBefore('?'), if ('?' in target) decode(target.substringAfter('?')) else emptyMap(),
            if (headers["content-type"]?.startsWith("application/x-www-form-urlencoded") == true) decode(String(body, Charsets.UTF_8)) else emptyMap())
    }

    private fun decode(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate {
        runCatching { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrDefault("" to "")
    }

    private fun same(a: String?, b: String) = a != null && MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun handle(client: Socket) {
        client.soTimeout = 10_000
        val out = client.getOutputStream()
        val req = read(BufferedInputStream(client.getInputStream())) ?: return respond(out, 400, "text/plain", "")
        when {
            req.method == "GET" && req.path == "/" -> {
                val t = req.query["t"]; val p = req.query["pin"]
                when {
                    same(t, token) -> { lastUse = System.currentTimeMillis(); respond(out, 200, "text/html", RemotePage.html(token)) }
                    same(p, pin) -> redirect(out, "/?t=$token")
                    t == null && p == null -> respond(out, 200, "text/html", RemotePage.pin(null))
                    else -> fail(out)
                }
            }
            req.method == "POST" && (req.path == "/k" || req.path == "/s") -> {
                if (!same(req.query["t"], token)) return fail(out)
                lastUse = System.currentTimeMillis()
                if (req.path == "/k") {
                    val k = req.query["k"].orEmpty()
                    if (k in KEYS) onCommand(k, null)
                } else {
                    val q = req.form["q"].orEmpty().trim().take(80)
                    if (q.isNotEmpty()) onCommand("search", q)
                }
                respond(out, 204, "text/plain", "")
            }
            else -> respond(out, 404, "text/plain", "")
        }
    }

    private fun fail(out: OutputStream) {
        failures++
        val left = MAX_FAILURES - failures
        if (left <= 0) {
            close("Trop d'essais faux : télécommande arrêtée.")
            respond(out, 403, "text/html", RemotePage.pin("Trop d'essais. Relancez la télécommande depuis la TV."))
        } else respond(out, 403, "text/html", RemotePage.pin("Code incorrect. Encore $left essai(s)."))
    }

    private fun redirect(out: OutputStream, to: String) {
        out.write("HTTP/1.1 303 See Other\r\nLocation: $to\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun respond(out: OutputStream, code: Int, type: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 403 -> "Forbidden"; else -> "Not Found" }
        val head = "HTTP/1.1 $code $reason\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nX-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; base-uri 'none'\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1)); out.write(body); out.flush()
    }

    companion object {
        const val MAX_FAILURES = 5
        private const val MAX_BYTES = 4 * 1024
        val KEYS = setOf("up", "down", "left", "right", "ok", "back", "play", "menu", "chup", "chdown", "volup", "voldown", "mute", "home", "live", "rew", "fwd")
    }
}

/** Page de télécommande (autonome, sans ressource externe). */
private object RemotePage {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private const val STYLE = """*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}body{margin:0;background:#07090D;color:#F5F7FA;font:16px/1.4 system-ui,-apple-system,Roboto,sans-serif;padding:18px 16px 30px;user-select:none}
main{max-width:420px;margin:0 auto}.brand{font-weight:700;letter-spacing:.14em;color:#00D7D7;font-size:13px;text-align:center;margin-bottom:12px}
.pad{position:relative;width:270px;height:270px;margin:8px auto 18px;border-radius:50%;background:radial-gradient(circle at 50% 40%,#1A2230,#0E131A);box-shadow:0 10px 30px rgba(0,0,0,.6),inset 0 1px 0 rgba(255,255,255,.06)}
.pad button{position:absolute;border:0;background:transparent;color:#C9D3DE;font-size:30px;width:80px;height:80px}
.pad .u{top:4px;left:95px}.pad .d{bottom:4px;left:95px}.pad .l{left:4px;top:95px}.pad .r{right:4px;top:95px}
.pad .ok{left:75px;top:75px;width:120px;height:120px;border-radius:50%;background:#F5F7FA;color:#07090D;font-size:22px;font-weight:700}
.row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:10px 0}
.b{border:0;border-radius:16px;padding:16px 4px;background:#121923;color:#F5F7FA;font-size:15px;font-weight:600}
.b small{display:block;font-size:11px;color:#7D8896;font-weight:500;margin-top:3px}
.b:active,.pad button:active{transform:scale(.94);background:#1F2A38}.pad .ok:active{background:#00D7D7}
form{display:flex;gap:8px;margin-top:16px}input{flex:1;padding:14px;border-radius:14px;border:1px solid #1D2631;background:#0E131A;color:#F5F7FA;font-size:16px}
form button{border:0;border-radius:14px;padding:0 16px;background:#00D7D7;color:#07090D;font-weight:700}
.note{font-size:12px;color:#7D8896;text-align:center;margin-top:18px}.err{color:#FF4D5E}.pin{letter-spacing:.5em;text-align:center;font-size:26px;width:100%}
h1{font-size:22px;text-align:center}"""

    fun html(token: String) = """<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><meta name="referrer" content="no-referrer">
<meta name="apple-mobile-web-app-capable" content="yes"><meta name="theme-color" content="#07090D"><title>ADI TV · Télécommande</title>
<style>$STYLE</style></head><body><main><div class="brand">ADI TV · TÉLÉCOMMANDE</div>
<div class="row"><button class="b" data-k="back">↩<small>Retour</small></button><button class="b" data-k="home">⌂<small>Accueil</small></button><button class="b" data-k="menu">≡<small>Menu</small></button></div>
<div class="pad"><button class="u" data-k="up">▲</button><button class="l" data-k="left">◀</button><button class="ok" data-k="ok">OK</button><button class="r" data-k="right">▶</button><button class="d" data-k="down">▼</button></div>
<div class="row"><button class="b" data-k="rew">⏪<small>−30 s</small></button><button class="b" data-k="play">⏯<small>Lecture</small></button><button class="b" data-k="fwd">⏩<small>+30 s</small></button></div>
<div class="row"><button class="b" data-k="chup">CH ▲<small>Chaîne</small></button><button class="b" data-k="live">●<small>Direct</small></button><button class="b" data-k="chdown">CH ▼<small>Chaîne</small></button></div>
<div class="row"><button class="b" data-k="voldown">🔉<small>Vol −</small></button><button class="b" data-k="mute">🔇<small>Muet</small></button><button class="b" data-k="volup">🔊<small>Vol +</small></button></div>
<form id="f"><input id="q" placeholder="Rechercher un film, une série, une chaîne…" autocomplete="off" enterkeyhint="search"><button>OK</button></form>
<p class="note" id="st">Même Wi-Fi que la TV · rien ne passe par Internet</p></main>
<script>
var T="${esc(token)}",st=document.getElementById('st');
function send(k){if(navigator.vibrate)navigator.vibrate(8);fetch('/k?t='+T+'&k='+k,{method:'POST'}).then(function(r){st.textContent=r.ok?'Connecté à la TV':'La TV a fermé la télécommande : rescannez le QR code';}).catch(function(){st.textContent='TV injoignable (même Wi-Fi ?)';});}
document.querySelectorAll('[data-k]').forEach(function(b){var t=null;
 b.addEventListener('click',function(e){e.preventDefault();send(b.dataset.k);});
 if(['up','down','left','right','volup','voldown'].indexOf(b.dataset.k)>=0){b.addEventListener('touchstart',function(){t=setTimeout(function rep(){send(b.dataset.k);t=setTimeout(rep,160);},450);},{passive:true});
 ['touchend','touchcancel'].forEach(function(ev){b.addEventListener(ev,function(){clearTimeout(t);});});}});
document.getElementById('f').addEventListener('submit',function(e){e.preventDefault();var q=document.getElementById('q');if(!q.value.trim())return;
 fetch('/s?t='+T,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'q='+encodeURIComponent(q.value)}).then(function(){q.value='';q.blur();st.textContent='Recherche envoyée à la TV';});});
</script></body></html>"""

    fun pin(error: String?) = """<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ADI TV · Code</title><style>$STYLE</style></head><body><main><div class="brand">ADI TV</div><h1>Code de la TV</h1>
${error?.let { "<p class=\"err\" style=\"text-align:center\">${esc(it)}</p>" } ?: "<p style=\"text-align:center;color:#A7B2BF\">Tapez le code à 4 chiffres affiché sur la TV.</p>"}
<form method="get" action="/" style="flex-direction:column"><input class="pin" name="pin" inputmode="numeric" pattern="[0-9]{4}" maxlength="4" autocomplete="off" required autofocus>
<button style="padding:14px">Continuer</button></form></main></body></html>"""
}
