package com.aditv.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class CrashReporterTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun javaCrashIsRecordedWithoutUrlsOrSecrets() {
        CrashReporter.install(ctx); CrashReporter.clear()
        Redactor.registerSecret("MonMotDePasse")
        CrashReporter.event("Lecture direct · Media3")
        CrashReporter.event("Ouverture de http://serveur.exemple:8080/live/moi/MonMotDePasse/12.ts")
        CrashReporter.record(Thread.currentThread(), IllegalStateException("échec sur https://serveur.exemple/get.php?username=moi&password=MonMotDePasse"))
        val r = CrashReporter.reports().single()
        assertTrue(r.title.contains("IllegalStateException"))
        assertTrue("derniers événements", r.text.contains("Lecture direct · Media3"))
        assertFalse(r.text.contains("serveur.exemple"))
        assertFalse(r.text.contains("MonMotDePasse"))
        assertTrue(r.text.contains("Mémoire Java"))
        CrashReporter.clear()
        assertTrue(CrashReporter.reports().isEmpty())
    }

    @Test fun closingWhileOnScreenIsDetectedAtNextLaunch() {
        CrashReporter.install(ctx); CrashReporter.clear()
        CrashReporter.event("Lecture direct · VLC")
        CrashReporter.foreground(true) // ADITV à l'écran, puis le processus disparaît (plantage natif, mémoire)
        CrashReporter.install(ctx)     // lancement suivant
        val r = CrashReporter.reports().single()
        assertTrue(r.title.contains("Fermeture inattendue"))
        assertTrue(r.text.contains("Lecture direct · VLC"))
        // Passage normal en arrière-plan : pas de faux plantage.
        CrashReporter.clear()
        CrashReporter.foreground(true); CrashReporter.foreground(false)
        CrashReporter.install(ctx)
        assertEquals(0, CrashReporter.reports().size)
        assertFalse(File(ctx.filesDir, "crash/foreground.marker").exists())
    }

    @Test fun fatalErrorWithoutFullReportIsStillShown() {
        CrashReporter.install(ctx); CrashReporter.clear()
        File(ctx.filesDir, "crash/fatal.txt").writeText("20/11 23:48:10 java.lang.OutOfMemoryError (fil « DefaultDispatcher-worker-2 »)")
        CrashReporter.foreground(true)
        CrashReporter.install(ctx)
        val r = CrashReporter.reports().single()
        assertTrue(r.title, r.title.contains("OutOfMemoryError"))
        assertTrue(r.text.contains("Journal système"))
        assertFalse(File(ctx.filesDir, "crash/fatal.txt").exists())
    }
}
