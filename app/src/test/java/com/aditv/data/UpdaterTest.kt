package com.aditv.data

import com.aditv.data.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UpdaterTest {
    private val json = """{"versionCode":99,"versionName":"9.9.9","notes":"Nouveautés",
      "cube":{"url":"https://x/ADITV-cube.apk","sha256":"ABC","size":10},"stick":{"url":"https://x/ADITV-stick.apk","sha256":"def","size":20}}"""

    @Test fun picksTheApkOfThisDevice() {
        val cube = Updater.parse(json, "cube")!!
        assertEquals(99, cube.versionCode); assertEquals("https://x/ADITV-cube.apk", cube.url); assertEquals("abc", cube.sha256)
        assertEquals("https://x/ADITV-stick.apk", Updater.parse(json, "stick")!!.url)
    }

    @Test fun rejectsBrokenManifest() {
        assertNull(Updater.parse("{}", "cube"))
        assertNull(Updater.parse("pas du json", "stick"))
    }
}
