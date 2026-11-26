package com.xern.xrnwal

import android.app.AppOpsManager
import com.xern.xrnwal.work.OemAutostart
import com.xern.xrnwal.work.RestartIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the Reminders page says can keep a reminder from ringing after a restart. */
class RestartIssueTest {
    @Test
    fun theRemindersPageSaysWhenAutostartOrTheBatteryKeepsAReminderFromRinging() {
        // HyperOS turns Autostart off at every install; then a reminder stays silent after a reboot
        // until the app is opened, and nothing said so.
        assertEquals(listOf(RestartIssue.AUTOSTART_OFF, RestartIssue.BATTERY_OPTIMISED), RestartIssue.of(true, false, false))
        assertEquals(listOf(RestartIssue.AUTOSTART_OFF), RestartIssue.of(true, false, true))
        assertEquals(listOf(RestartIssue.BATTERY_OPTIMISED), RestartIssue.of(true, null, false))
        assertEquals(emptyList<RestartIssue>(), RestartIssue.of(true, true, true))
        assertEquals("nothing to ring, nothing to say", emptyList<RestartIssue>(), RestartIssue.of(false, false, false))
    }

    @Test
    fun hyperOsAutostartReadsAsOnOffOrUnknown() {
        assertEquals(true, OemAutostart.opAllows(AppOpsManager.MODE_ALLOWED))
        assertEquals(false, OemAutostart.opAllows(AppOpsManager.MODE_IGNORED))
        assertEquals(false, OemAutostart.opAllows(AppOpsManager.MODE_ERRORED))
        assertNull(OemAutostart.opAllows(AppOpsManager.MODE_DEFAULT))
        assertNull(OemAutostart.opAllows(null))
    }

    @Test
    fun theManifestLetsTheAppAskForTheBatteryExemption() {
        // Without it Android's question never shows, and the row opens nothing.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in manifest)
    }
}
