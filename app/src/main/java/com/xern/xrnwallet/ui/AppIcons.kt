package com.xern.xrnwallet.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.xern.xrnwallet.MainActivity
import com.xern.xrnwallet.R

/** The launcher icon, chosen by enabling one of the manifest's activity aliases. */
object AppIcons {
    class Choice(val name: String, @DrawableRes val icon: Int)

    val choices = listOf(
        Choice("Lime", R.mipmap.ic_launcher),
        Choice("Gold", R.mipmap.ic_launcher_gold),
        Choice("Orange", R.mipmap.ic_launcher_orange),
        Choice("Rose", R.mipmap.ic_launcher_rose),
        Choice("Violet", R.mipmap.ic_launcher_violet),
        Choice("Blue", R.mipmap.ic_launcher_blue),
        Choice("Teal", R.mipmap.ic_launcher_teal),
        Choice("Slate", R.mipmap.ic_launcher_slate),
    )

    private fun component(context: Context, choice: Choice) =
        ComponentName(context.packageName, MainActivity::class.java.name.substringBeforeLast('.') + ".Icon" + choice.name)

    /** Index of the enabled choice. The first is on until something else has been chosen. */
    fun current(context: Context): Int = choices.indices.firstOrNull {
        context.packageManager.getComponentEnabledSetting(component(context, choices[it])) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: 0

    /**
     * Turns the new alias on before the others go off, so the app always has a launcher entry.
     * Android closes an activity whose alias is disabled, so this reopens the app under the new one.
     * Starting it here, while still in the foreground, keeps background launch limits out of the way.
     */
    fun choose(activity: Activity, index: Int) {
        val manager = activity.packageManager
        manager.setComponentEnabledSetting(
            component(activity, choices[index]),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        choices.filterIndexed { other, _ -> other != index }.forEach {
            manager.setComponentEnabledSetting(
                component(activity, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        activity.finishAndRemoveTask()
        activity.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component(activity, choices[index]))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun AppIconsScreen() {
    val context = LocalContext.current
    val current = remember { AppIcons.current(context) }
    Page("App Icons") {
        ScrollBody {
            AppIcons.choices.withIndex().chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (index, choice) ->
                        val shape = RoundedCornerShape(6.dp)
                        val edge = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .border(if (index == current) 3.dp else 1.dp, edge, shape)
                                .clickable(enabled = index != current) { (context as? Activity)?.let { AppIcons.choose(it, index) } }
                                .padding(8.dp),
                        ) {
                            val picture = remember(choice) {
                                ResourcesCompat.getDrawable(context.resources, choice.icon, context.theme)?.toBitmap(192, 192)?.asImageBitmap()
                            }
                            if (picture != null) Image(picture, choice.name, Modifier.fillMaxSize())
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Muted("*After changing the app icon, the app will exit and restart.")
        }
    }
}
