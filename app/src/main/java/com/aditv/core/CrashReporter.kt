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
            write(
                "Fermeture inattendue sans erreur Java",
                "ADITV était à l'écran et s'est fermée sans erreur Java : plantage natif (VLC, décodeur vidéo) " +
                    "ou mémoire reprise par Fire OS.\n\nDernier état connu :\n$last",
            )
            marker.delete()
        }
        if (!handlerSet) { handlerSet = true; setHandler() }
        val main = Handler(Looper.getMainLooper())
        main.postDelayed(object : Runnable {
            override fun run() { heartbeat(); main.postDelayed(this, 20_000) }
        }, 5_000)
    }

    private fun setHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread, error); File(dir, "foreground.marker").delete() }
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

    private fun heartbeat() = runCatching { File(dir, "heartbeat.txt").writeText(snapshot()) }

    private fun memory(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        val sys = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo().also { sys?.getMemoryInfo(it) }
        return "Mémoire Java : $used / ${rt.maxMemory() / 1_048_576} Mo · système libre : ${mi.availMem / 1_048_576} Mo" +
            (if (mi.lowMemory) " (mémoire basse signalée)" else "")
    }

    private fun snapshot(): String = buildString {
        appendLine("ADITV ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine(runCatching { memory() }.getOrDefault("Mémoire : N/D"))
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
