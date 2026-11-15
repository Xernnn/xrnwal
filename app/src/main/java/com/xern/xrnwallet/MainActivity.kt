package com.xern.xrnwallet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xern.xrnwallet.ui.WalletRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : FragmentActivity() {
    /** True until the lock screen is passed. Ignored when no lock is set. */
    var locked by mutableStateOf(true)
        private set

    /** Bumped each time another app shares a file here, so the import screen can open. */
    var inboxRevision by mutableIntStateOf(0)
        private set

    /** Bumped each time a widget's add button is pressed, so the new bill page can open. */
    var addBillRevision by mutableIntStateOf(0)
        private set

    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideFromRecentsWhileLocked()
        // A recreated activity gets its old intent again, which must not repeat the action.
        if (savedInstanceState == null) receive(intent)
        setContent { WalletRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    override fun onStart() {
        super.onStart()
        com.xern.xrnwallet.domain.Dates.use24Hour = android.text.format.DateFormat.is24HourFormat(this)
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MILLIS) locked = true
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        locked = false
    }

    /**
     * Recents sits outside the lock screen, so with a lock set it must not keep a picture of these pages.
     * Android 13 can leave out just that picture. Older versions can only block all screenshots.
     */
    private fun hideFromRecentsWhileLocked() {
        val settings = (application as WalletApp).repository.store.settings
        lifecycleScope.launch {
            settings.map { it.locked }.distinctUntilChanged().collect { lockSet ->
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> setRecentsScreenshotEnabled(!lockSet)
                    lockSet -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    /** Keeps a shared file byte for byte, so nothing is lost before the user reviews the import. */
    private fun receive(intent: Intent?) {
        if (intent?.action == ACTION_ADD_BILL) addBillRevision++
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Parcelable::class.java) as? Uri ?: return
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = File(filesDir, "inbox").apply { mkdirs() }
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(folder, "received.csv").outputStream().use { input.copyTo(it) }
                    } != null
                }.getOrDefault(false)
            }
            if (saved) {
                inboxRevision++
            } else {
                Toast.makeText(this@MainActivity, "Could not read the shared file", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_ADD_BILL = "com.xern.xrnwallet.ADD_BILL"
        private const val RELOCK_AFTER_MILLIS = 60_000L
    }
}
