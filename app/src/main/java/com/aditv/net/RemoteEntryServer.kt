package com.aditv.net

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saisie depuis le téléphone : la TV sert elle-même, sur le réseau local, une petite page où l'on tape
 * (ou colle) ses accès. Rien ne passe par Internet.
 *
 * Sécurité :
 * - écoute uniquement sur l'adresse locale de la TV (jamais 0.0.0.0), port aléatoire ;
 * - le QR code contient un jeton aléatoire de 128 bits ; sans lui, la page demande le code à 4 chiffres affiché à l'écran ;
 * - 5 essais faux au total (jeton ou code) ferment le serveur ;
 * - usage unique : fermé dès la première saisie reçue, et au bout de [lifetimeMs] dans tous les cas ;
 * - requêtes bornées (en-têtes et corps 8 Ko), aucune valeur reçue n'est journalisée.
 * La page est en HTTP simple sur le Wi-Fi de la maison : c'est dit à l'écran.
 */
class RemoteEntryServer(
    val form: Form,
    private val bindAddress: InetAddress,
    private val lifetimeMs: Long = 10 * 60_000L,
    random: SecureRandom = SecureRandom(),
) {
    /** Champs demandés sur le téléphone. */
    data class Field(val name: String, val label: String, val placeholder: String = "", val secret: Boolean = false, val type: String = "text")
    /** [readOnlyText] : page de consultation seule (rapport de plantage), sans champ ni envoi. */
    data class Form(val title: String, val intro: String, val fields: List<Field>, val readOnlyText: String? = null) {
        companion object {
            fun report(text: String) = Form(
                "Rapport de plantage",
                "Sélectionnez tout le texte (appui long), copiez-le et collez-le dans la conversation. Il ne contient ni adresse de flux ni accès.",
                emptyList(), text,
            )
            val LOGIN = Form(
                "Connexion à votre abonnement",
                "Saisissez les accès donnés par votre fournisseur. Ils sont envoyés directement à la TV, sur votre Wi-Fi.",
                listOf(
                    Field("server", "Adresse du serveur", "https://exemple.fournisseur:8080", type = "url"),
                    Field("username", "Identifiant", "Identifiant fourni"),
                    Field("password", "Mot de passe", "", secret = true),
                ),
            )
            val FOOTBALL = Form(
                "Clé API-Football",
                "Collez la clé de votre compte api-football.com (tableau de bord, « API Key »).",
                listOf(Field("value", "Clé API-Football", "Coller ici", secret = true)),
            )
            val FOOTBALL_DATA = Form(
                "Clé football-data.org",
                "Collez la clé reçue par e-mail après l'inscription gratuite sur football-data.org (« API Token »).",
                listOf(Field("value", "Clé football-data.org", "Coller ici", secret = true)),
            )
            /** Copie depuis une autre TV de la maison (envoyée par ADI TV, pas saisie au téléphone). */
            val TRANSFER = Form(
                "Recevoir la configuration d'une autre TV",
                "Sur votre autre TV ADI TV : Réglages → « Copier vers une autre TV », puis saisissez l'adresse et le code affichés ici.",
                listOf(Field("payload", "Configuration", "", secret = true)),
            )
            val TMDB = Form(
                "Clé TMDB",
                "Collez la clé API (v3) ou le jeton d'accès en lecture (v4) de votre compte themoviedb.org.",
                listOf(Field("value", "Clé ou jeton TMDB", "Coller ici", secret = true)),
            )
        }
    }

    sealed interface State {
        data object Starting : State
        /** [url] contient le jeton (pour le QR code) ; [shortUrl] est à taper à la main, avec [pin]. */
        data class Ready(val url: String, val shortUrl: String, val pin: String) : State
        /** Valeurs reçues : à consommer une fois, puis [consume]. */
        class Received(val values: Map<String, String>) : State { override fun toString() = "Received(${values.keys})" }
        data class Closed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state.asStateFlow()

    private val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    val pin: String = "%04d".format(random.nextInt(10_000))
    @Volatile private var failures = 0
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var closed = false
    @Volatile private var timer: Thread? = null

    fun start(): State {
        if (socket != null || closed) return _state.value
        return try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(bindAddress, 0), 4)
            socket = s
            val host = bindAddress.hostAddress
            val base = "http://$host:${s.localPort}/"
            val ready = State.Ready(base + "?t=" + token, base, pin)
            _state.value = ready
            thread(name = "aditv-remote-entry", isDaemon = true) { loop(s) }
            timer = thread(name = "aditv-remote-entry-timer", isDaemon = true) {
                try { Thread.sleep(lifetimeMs) } catch (_: InterruptedException) { return@thread }
                close("Délai dépassé : la saisie depuis le téléphone est fermée.")
            }
            ready
        } catch (e: Exception) {
            State.Closed("Impossible d'ouvrir la saisie sur le réseau local (${e.javaClass.simpleName}).").also { _state.value = it; closed = true }
        }
    }

    /** Ferme le serveur. Les valeurs déjà reçues restent dans l'état jusqu'à [consume]. */
    fun close(reason: String = "Saisie fermée.") {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        if (Thread.currentThread() !== timer) timer?.interrupt()
        if (_state.value !is State.Received) _state.value = State.Closed(reason)
    }

    /** Oublie les valeurs reçues une fois utilisées. */
    fun consume() { if (_state.value is State.Received) _state.value = State.Closed("Saisie reçue.") }

    private fun loop(s: ServerSocket) {
        while (!closed) {
            val client = try { s.accept() } catch (_: Exception) { break }
            try { client.use { handle(it) } } catch (_: Exception) { /* requête invalide : ignorée */ }
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
        val path = target.substringBefore('?')
        val query = if ('?' in target) decode(target.substringAfter('?')) else emptyMap()
        val form = if (headers["content-type"]?.startsWith("application/x-www-form-urlencoded") == true) decode(String(body, Charsets.UTF_8)) else emptyMap()
        return Request(parts[0], path, query, form)
    }

    private fun decode(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate {
        val k = it.substringBefore('=')
        val v = it.substringAfter('=', "")
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    private fun same(a: String?, b: String): Boolean =
        a != null && MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun fail(out: OutputStream, page: (Int) -> String) {
        failures++
        val left = MAX_FAILURES - failures
        // Fermeture avant la réponse : le téléphone ne peut plus rien tenter une fois la réponse reçue.
        if (left <= 0) {
            close("Trop d'essais faux : la saisie depuis le téléphone est fermée.")
            respond(out, 403, Html.page("Saisie fermée", "<p>Trop d'essais. Relancez la saisie depuis la TV.</p>"))
        } else respond(out, 403, page(left))
    }

    private fun handle(client: Socket) {
        client.soTimeout = 10_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val req = read(input) ?: return respond(out, 400, Html.page("Requête invalide", "<p>Requête invalide.</p>"))
        if (req.path != "/") return respond(out, 404, Html.page("Introuvable", "<p>Page introuvable.</p>"))
        when (req.method) {
            "GET" -> {
                val t = req.query["t"]
                val p = req.query["pin"]
                when {
                    t == null && p == null -> respond(out, 200, Html.pinPage(null))
                    same(t, token) || same(p, pin) -> respond(out, 200, form.readOnlyText?.let { Html.readOnlyPage(form, it) } ?: Html.formPage(form, token))
                    else -> fail(out) { Html.pinPage("Code incorrect. Encore $it essai(s).") }
                }
            }
            "POST" -> {
                if (!same(req.form["t"], token)) return fail(out) { Html.pinPage("Lien expiré ou incorrect. Encore $it essai(s).") }
                val values = form.fields.associate { f -> f.name to (req.form[f.name] ?: "").let { if (f.secret) it else it.trim() } }
                if (values.values.all { it.isBlank() }) return respond(out, 200, Html.formPage(form, token, "Rien n'a été saisi."))
                _state.value = State.Received(values)
                close("Saisie reçue.")
                respond(out, 200, Html.page("Reçu", "<p class=\"ok\">C'est reçu. Regardez la TV.</p><p>Cette page ne sert plus : vous pouvez la fermer.</p>"))
            }
            else -> respond(out, 405, Html.page("Méthode refusée", "<p>Méthode refusée.</p>"))
        }
    }

    private fun respond(out: OutputStream, code: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Method Not Allowed" }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    companion object {
        const val MAX_FAILURES = 5
        private const val MAX_BYTES = 8 * 1024

        /** Adresse IPv4 privée de la TV sur le réseau local (Wi-Fi ou Ethernet), ou null hors réseau local. */
        fun localAddress(): InetAddress? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { n -> if (n.name.startsWith("wlan") || n.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()

        /** Adresse d'écoute utilisée par l'écran (remplacée par la boucle locale dans les tests d'interface). */
        @Volatile var addressProvider: () -> InetAddress? = ::localAddress
    }
}

/** Pages servies au téléphone : autonomes, sans script ni ressource externe. */
private object Html {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    fun page(title: String, body: String) = """<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer"><title>ADI TV · ${esc(title)}</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#07090D;color:#F5F7FA;font:16px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;padding:28px 20px}
main{max-width:460px;margin:0 auto}.brand{font-weight:700;letter-spacing:.14em;color:#00D7D7;font-size:13px}
h1{font-size:24px;margin:8px 0 6px}p{color:#A7B2BF;margin:6px 0 16px}
label{display:block;font-size:14px;color:#A7B2BF;margin:16px 0 6px}
input{width:100%;padding:14px;border-radius:10px;border:1px solid #1D2631;background:#0E131A;color:#F5F7FA;font-size:17px}
input:focus{outline:none;border-color:#00D7D7;box-shadow:0 0 0 3px rgba(0,215,215,.25)}
button{width:100%;margin-top:22px;padding:15px;border:0;border-radius:999px;background:#F5F7FA;color:#07090D;font-size:17px;font-weight:600}
.pin{letter-spacing:.5em;text-align:center;font-size:26px}.err{color:#FF4D5E}.ok{color:#00D7D7;font-size:20px;font-weight:600}
.note{font-size:13px;color:#7D8896;margin-top:22px}
</style></head><body><main><div class="brand">ADI TV</div><h1>${esc(title)}</h1>
$body
<p class="note">Page servie par votre TV sur votre réseau local, sans passer par Internet. Elle se ferme après l'envoi.</p>
</main></body></html>"""

    fun pinPage(error: String?) = page(
        "Code de la TV",
        (error?.let { "<p class=\"err\">${esc(it)}</p>" } ?: "<p>Saisissez le code à 4 chiffres affiché sur la TV.</p>") +
            """<form method="get" action="/"><label for="pin">Code</label>
<input id="pin" class="pin" name="pin" inputmode="numeric" pattern="[0-9]{4}" maxlength="4" autocomplete="off" required autofocus>
<button type="submit">Continuer</button></form>""",
    )

    fun readOnlyPage(form: RemoteEntryServer.Form, text: String) = page(
        form.title,
        "<p>${esc(form.intro)}</p><pre style=\"white-space:pre-wrap;word-break:break-word;background:#0E131A;border:1px solid #1D2631;" +
            "border-radius:10px;padding:12px;font-size:12px;color:#F5F7FA;user-select:text\">${esc(text)}</pre>",
    )

    fun formPage(form: RemoteEntryServer.Form, token: String, error: String? = null) = page(
        form.title,
        (error?.let { "<p class=\"err\">${esc(it)}</p>" } ?: "") + "<p>${esc(form.intro)}</p>" +
            "<form method=\"post\" action=\"/\" autocomplete=\"off\"><input type=\"hidden\" name=\"t\" value=\"${esc(token)}\">" +
            form.fields.joinToString("") { f ->
                val type = if (f.secret) "password" else f.type
                "<label for=\"${esc(f.name)}\">${esc(f.label)}</label>" +
                    "<input id=\"${esc(f.name)}\" name=\"${esc(f.name)}\" type=\"$type\" placeholder=\"${esc(f.placeholder)}\" " +
                    "autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\">"
            } +
            "<button type=\"submit\">Envoyer à la TV</button></form>",
    )
}
