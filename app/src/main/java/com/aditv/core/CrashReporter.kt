package com.aditv.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.aditv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Enregistreur de plantages, local à la Stick (rien n'est envoyé nulle part).
 *
 * - Erreur Java/Kotlin non rattrapée : rapport écrit (pile d'appels, derniers événements, mémoire) puis fermeture normale.
 * - Fermeture sans erreur Java alors qu'ADITV était à l'écran (plantage natif VLC/décodeur, ou mémoire reprise par
 *   Fire OS) : détectée au lancement suivant grâce à un marqueur, avec le dernier état connu (relevé toutes les 20 s).
 * Les rapports ne contiennent ni adresse de flux ni accès : les URLs sont retirées et le Redactor masque le reste.
 */
object CrashReporter {
    private const val MAX_REPORTS = 5
    private const val MAX_EVENTS = 40
    private lateinit var dir: File
    private var appContext: Context? = null
    private val events = ArrayDeque<String>()
    private val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)
    @Volatile private var installed = false
    @Volatile private var handlerSet = false
    /** Réserve libérée au moment d'une erreur fatale : de quoi écrire le rapport même en manque de mémoire. */
    @Volatile private var reserve: ByteArray? = ByteArray(1 shl 20)

    private val url = Regex("""(?i)\b(?:https?|rtmp|rtsp|udp)://\S+""")
    fun clean(s: String): String = Redactor.redact(url.replace(s, "<adresse masquée>"))

    fun install(context: Context) {
        appContext = context.applicationContext
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        installed = true
        // Lancement précédent terminé pendant qu'ADITV était à l'écran, sans erreur Java enregistrée.
        val marker = File(dir, "foreground.marker")
        if (marker.exists()) {
            val last = runCatching { File(dir, "heartbeat.txt").readText() }.getOrDefault("(aucun relevé)")
            val fatal = File(dir, "fatal.txt").takeIf { it.exists() }?.readText()
            val blocked = File(dir, "watchdog.txt").takeIf { it.exists() }?.readText()
            write(
                if (fatal != null) "Erreur fatale (rapport incomplet) : ${fatal.substringAfter(' ').substringAfter(' ')}" else "Fermeture inattendue sans erreur Java",
                (fatal?.let { "Erreur Java au moment de la fermeture, rapport complet impossible (manque de mémoire probable) : $it\n\n" }
                    ?: "ADITV était à l'écran et s'est fermée sans erreur Java : plantage natif (VLC, décodeur vidéo), " +
                        "blocage de l'interface ou mémoire reprise par Fire OS.\n\n") +
                    (blocked?.let { "Interface bloquée avant la fermeture :\n$it\n\n" } ?: "") +
                    "Dernier état connu :\n$last\n\nJournal système d'ADITV :\n${systemLog()}",
            )
            marker.delete(); File(dir, "fatal.txt").delete()
        }
        File(dir, "watchdog.txt").delete()
        if (!handlerSet) { handlerSet = true; setHandler() }
        val main = Handler(Looper.getMainLooper())
        main.postDelayed(object : Runnable {
            override fun run() { heartbeat(); main.postDelayed(this, 10_000) }
        }, 3_000)
        startWatchdog()
    }

    private fun setHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            reserve = null
            // Ligne minimale d'abord (survit à un manque de mémoire), puis le rapport complet.
            runCatching { File(dir, "fatal.txt").writeText("${time.format(Date())} ${error.javaClass.name} (fil « ${thread.name} »)") }
            runCatching { record(thread, error); File(dir, "fatal.txt").delete(); File(dir, "foreground.marker").delete() }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Pour les tests : écrit le rapport d'une erreur comme le ferait le gestionnaire global. */
    internal fun record(thread: Thread, error: Throwable) {
        val sw = StringWriter(); error.printStackTrace(PrintWriter(sw))
        write("${error.javaClass.simpleName} (fil « ${thread.name} »)", "${sw.toString().take(12_000)}\n${snapshot()}")
    }

    /** Événement récent (écran ouvert, lecture, erreur) : jamais d'URL ni d'accès. */
    fun event(text: String) {
        val line = time.format(Date()) + "  " + clean(text).take(200)
        synchronized(events) { events.addLast(line); while (events.size > MAX_EVENTS) events.removeFirst() }
    }

    /** ADITV à l'écran (marqueur posé) ou en arrière-plan (marqueur retiré : Fire OS peut alors la fermer normalement). */
    fun foreground(visible: Boolean) {
        if (!installed) return
        runCatching { File(dir, "foreground.marker").let { if (visible) it.writeText("1") else it.delete() } }
        if (visible) heartbeat()
    }

    /** Erreur rattrapée mais inattendue (tâche de fond) : notée sans fermer l'appli. */
    fun caught(where: String, error: Throwable) {
        event("Erreur rattrapée ($where) : ${error.javaClass.simpleName}: ${error.message}")
        AppLog.w("Erreur rattrapée ($where)", error)
    }

    private fun heartbeat() = runCatching { File(dir, "heartbeat.txt").writeText("Relevé du ${time.format(Date())}\n" + snapshot()) }

    @Volatile private var watchdog: Thread? = null
    @Volatile private var mainTick = 0L

    /** Interface bloquée plus de 5 s (Fire OS ferme alors l'appli) : pile du fil principal notée tout de suite. */
    private fun startWatchdog() {
        if (watchdog != null) return
        val main = Handler(Looper.getMainLooper())
        watchdog = Thread({
            var reported = false
            while (true) {
                val sent = System.currentTimeMillis()
                main.post { mainTick = System.currentTimeMillis() }
                try { Thread.sleep(5_000) } catch (_: InterruptedException) { return@Thread }
                if (mainTick < sent) {
                    if (!reported) {
                        reported = true
                        val stack = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { "  at $it" }
                        runCatching { File(dir, "watchdog.txt").writeText("${time.format(Date())} — interface bloquée depuis plus de 5 s\n$stack") }
                        event("Interface bloquée plus de 5 s")
                    }
                } else if (reported) {
                    reported = false
                    runCatching { File(dir, "watchdog.txt").delete() }
                    event("Interface débloquée")
                }
            }
        }, "aditv-watchdog").apply { isDaemon = true; start() }
    }

    /** Lignes du journal Android émises par ADITV (plantage natif, mémoire) : lisibles par l'appli elle-même. */
    private fun systemLog(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "2000").redirectErrorStream(true).start()
        val keep = Regex("(?i)fatal|signal|sigsegv|sigabrt|tombstone|outofmemory|out of memory|lowmemory|anr|libc|libvlc|vlc|mediacodec|omx|art\\s|aditv|crash|abort|died|killing")
        val lines = p.inputStream.bufferedReader().readLines().filter { keep.containsMatchIn(it) }.takeLast(60)
        p.destroy()
        lines.joinToString("\n").ifEmpty { "(aucune ligne utile)" }
    }.getOrDefault("(journal illisible)")

    private fun memory(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        val sys = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo().also { sys?.getMemoryInfo(it) }
        return "Mémoire Java : $used / ${rt.maxMemory() / 1_048_576} Mo · système libre : ${mi.availMem / 1_048_576} Mo" +
            (if (mi.lowMemory) " (mémoire basse signalée)" else "")
    }

    /** État de l'appli ajouté aux relevés (synchronisation en cours…), fourni par l'application. */
    @Volatile var extraState: () -> String? = { null }

    private fun snapshot(): String = buildString {
        appendLine("ADITV ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine(runCatching { memory() }.getOrDefault("Mémoire : N/D"))
        runCatching { extraState() }.getOrNull()?.let { appendLine(it) }
        appendLine("Derniers événements :")
        synchronized(events) { events.forEach { appendLine("  $it") } }
    }

    private fun write(title: String, body: String) = runCatching {
        val f = File(dir, "crash-${System.currentTimeMillis()}.txt")
        f.writeText("${time.format(Date())} — $title\n\n${clean(body)}")
        reportFiles().drop(MAX_REPORTS).forEach { it.delete() }
    }

    private fun reportFiles(): List<File> =
        if (!installed) emptyList() else dir.listFiles { f -> f.name.startsWith("crash-") }.orEmpty().sortedByDescending { it.name }

    data class Report(val title: String, val text: String)

    /** Rapports enregistrés, du plus récent au plus ancien. */
    fun reports(): List<Report> = reportFiles().mapNotNull { f -> runCatching { f.readText() }.getOrNull()?.let { Report(it.lineSequence().first(), it) } }

    fun clear() = reportFiles().forEach { it.delete() }
}
