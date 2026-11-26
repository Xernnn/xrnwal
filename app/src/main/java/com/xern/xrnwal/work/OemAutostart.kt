package com.xern.xrnwal.work

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/**
 * HyperOS's Autostart and Android's battery optimisation, as xrnrule and xrnimg read and fix them.
 * Autostart off, HyperOS doesn't hand the app the restart after a reboot, so a reminder doesn't
 * ring until the app is opened again; HyperOS turns it off at every install. Battery optimised,
 * Android holds back the alarms and jobs of an app that is seldom opened.
 */
object OemAutostart {
    /**
     * Whether Autostart is on, or null where the phone won't say. Only Xiaomi's can be read: MIUI and
     * HyperOS keep it as an app op of their own, which Android's public API has no name for.
     */
    fun enabled(context: Context): Boolean? {
        if ("xiaomi" !in Build.MANUFACTURER.lowercase()) return null
        return runCatching {
            val check = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
            opAllows(check.invoke(context.getSystemService(AppOpsManager::class.java), MIUI_AUTOSTART_OP, Process.myUid(), context.packageName) as? Int)
        }.getOrNull()
    }

    /** An app op's mode as on or off; null for one that leaves it to something else. */
    fun opAllows(mode: Int?): Boolean? = when (mode) {
        AppOpsManager.MODE_ALLOWED -> true
        AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
        else -> null
    }

    /** HyperOS's Autostart list; the app's own system page where that isn't there. */
    fun open(context: Context) {
        val miui = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.MainActivity")),
        )
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        start(context, miui.takeIf { "xiaomi" in Build.MANUFACTURER.lowercase() }.orEmpty() + details)
    }

    fun batteryExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Android's own question, "let xrnwal always run in the background?"; the list of apps where it can't be asked. */
    @SuppressLint("BatteryLife") // installed by hand, never through Play, whose policy this check is
    fun askBattery(context: Context) {
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        start(context, listOf(ask, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
    }

    private fun start(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // The next page.
            } catch (_: SecurityException) {
                // Not exported on this build.
            }
        }
    }

    private const val MIUI_AUTOSTART_OP = 10008
}

/** What can keep a reminder from ringing, each with its fix, as xrnrule's Blocks page shows it. */
enum class RestartIssue(val label: String, val action: String) {
    AUTOSTART_OFF("Autostart off", "Turn on"),
    BATTERY_OPTIMISED("Battery optimised", "Allow"),
    ;

    companion object {
        /** [autostart]: HyperOS's switch, null where the phone won't say. Nothing to warn of with no reminder on. */
        fun of(remindersOn: Boolean, autostart: Boolean?, batteryExempt: Boolean): List<RestartIssue> =
            if (!remindersOn) emptyList()
            else listOfNotNull(AUTOSTART_OFF.takeIf { autostart == false }, BATTERY_OPTIMISED.takeIf { !batteryExempt })
    }
}
