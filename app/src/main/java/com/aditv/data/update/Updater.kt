package com.aditv.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aditv.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Version publiée : numéro, nom, notes, et l'APK de CET appareil (Cube ou Stick). */
data class UpdateInfo(val versionCode: Int, val versionName: String, val notes: String, val url: String, val sha256: String, val size: Long)

/**
 * Mise à jour automatique d'ADI TV depuis le dépôt GitHub public (fichier `updates/latest.json` + APK).
 * Vérifiée avant installation (taille + SHA-256 publiés) ; l'installation reste confirmée à l'écran par Android
 * (« Installer »), ADI TV ne peut pas s'installer seul. Aucune donnée personnelle n'est envoyée.
 */
class Updater(private val context: Context, private val http: OkHttpClient, private val manifestUrl: String = MANIFEST) {
    companion object {
        const val MANIFEST = "https://raw.githubusercontent.com/dhino24/aditv/claude/new-session-9assu0/updates/latest.json"
        /** Variante de l'appareil : « cube » ou « stick » (même APK que celui installé). */
        val FLAVOR: String = BuildConfig.FLAVOR

        fun parse(json: String, flavor: String = FLAVOR): UpdateInfo? = runCatching {
            val o = JSONObject(json)
            val apk = o.getJSONObject(flavor)
            UpdateInfo(o.getInt("versionCode"), o.getString("versionName"), o.optString("notes"), apk.getString("url"), apk.getString("sha256").lowercase(), apk.optLong("size"))
        }.getOrNull()
    }

    /** Version plus récente que celle installée, sinon null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(manifestUrl).header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) null else parse(r.body.string()) }
            ?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Téléchargement (avec progression 0..1) puis vérification SHA-256 ; renvoie le fichier prêt à installer. */
    suspend fun download(info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "ADITV-${info.versionName}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Téléchargement impossible (HTTP ${r.code}).")
            val total = info.size.takeIf { it > 0 } ?: r.body.contentLength()
            r.body.byteStream().use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024); var read: Int; var done = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        os.write(buf, 0, read); md.update(buf, 0, read); done += read
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        if (hash != info.sha256) { out.delete(); throw IllegalStateException("Fichier corrompu (empreinte différente) : mise à jour annulée.") }
        out
    }

    /** Android 8+ : ADI TV doit être autorisée à installer des applis (une seule fois). */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Ouvre l'autorisation « Installer des applis inconnues » pour ADI TV (Fire OS 7) ; false si l'écran n'existe pas. */
    fun openInstallPermission(): Boolean = Build.VERSION.SDK_INT >= 26 && runCatching {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    /** Ouvre l'installeur Android sur l'APK vérifié. */
    fun install(apk: File) {
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(context, context.packageName + ".updates", apk) else Uri.fromFile(apk)
        @Suppress("DEPRECATION")
        val i = Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        context.startActivity(i)
    }
}
