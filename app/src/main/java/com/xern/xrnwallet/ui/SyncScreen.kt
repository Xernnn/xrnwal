package com.xern.xrnwallet.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xern.xrnwallet.WalletApp
import com.xern.xrnwallet.data.EarlierCopy
import com.xern.xrnwallet.data.ServerFailure
import com.xern.xrnwallet.data.SyncManager
import com.xern.xrnwallet.data.SyncOutcome
import com.xern.xrnwallet.data.SyncStatus
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun syncManager(): SyncManager = (LocalContext.current.applicationContext as WalletApp).sync

private val whenFormat = DateTimeFormatter.ofPattern("MMM d h:mm a", Locale.ENGLISH)

private fun whenText(millis: Long): String =
    if (millis == 0L) "Never"
    else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(whenFormat)

private fun whenText(updated: String): String = runCatching {
    OffsetDateTime.parse(updated).atZoneSameInstant(ZoneId.systemDefault()).format(whenFormat)
}.getOrDefault(updated)

private fun hostOf(address: String): String =
    runCatching { URI(address).host }.getOrNull() ?: address

@Composable
fun HomeServerSection() {
    val sync = syncManager()
    val status by sync.status.collectAsStateWithLifecycle()
    if (status.connected) ConnectedPanel(sync, status) else ConnectPanel(sync)
    status.conflict?.let { ConflictDialog(sync, it) }
}

@Composable
private fun ConnectPanel(sync: SyncManager) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Home server", style = MaterialTheme.typography.titleMedium)
            WalletField(address, { address = it }, Modifier.fillMaxWidth(), "https://your-server/xrnwallet", plain = KeyboardType.Uri)
            WalletField(user, { user = it }, Modifier.fillMaxWidth(), "User name", plain = KeyboardType.Ascii)
            WalletField(password, { password = it }, Modifier.fillMaxWidth(), "Password", password = true)
            problem?.let { Muted(it) }
            PrimaryButton(if (busy) "Connecting…" else "Connect", Modifier.fillMaxWidth(), enabled = !busy) {
                busy = true
                problem = null
                scope.launch {
                    problem = sync.connect(address, user, password)
                    busy = false
                }
            }
        }
    }
    Muted("Keeps a copy of this phone's data on your own server, instead of Google Drive.")
}

@Composable
private fun ConnectedPanel(sync: SyncManager, status: SyncStatus) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    Panel {
        ListRow("Home server", hostOf(status.address.orEmpty()), subtitle = status.user, chevron = false)
        Divider()
        SwitchRow("Enable automatic syncing", status.auto, onChange = sync::setAuto)
        Divider()
        ListRow("Last sync time", whenText(status.syncedAt), chevron = false)
        Divider()
        SwitchRow("Sync photo", status.photos, onChange = sync::setPhotos)
        Divider()
        ListRow("Earlier copies", subtitle = "Go back to a copy the server kept") { nav.navigate("sync/earlier") }
        Divider()
        ListRow("Disconnect") { leaving = true }
    }
    status.error?.let { Muted(it) }
    PrimaryButton(if (status.running) "Syncing…" else "Sync now", Modifier.fillMaxWidth(), enabled = !status.running) {
        scope.launch { sync.syncNow() }
    }
    Muted("The server keeps your last 20 changes and one copy a day for 60 days. The app lock stays on this phone.")
    if (leaving) {
        ConfirmDialog(
            "Disconnect from the server?",
            "This phone stops syncing and forgets the login. Its data and the server's copy both stay.",
            confirm = "Disconnect",
            onDismiss = { leaving = false },
        ) {
            leaving = false
            sync.disconnect()
        }
    }
}

/** Both choices are safe: the side not chosen is filed on the server, under Earlier copies. */
@Composable
private fun ConflictDialog(sync: SyncManager, conflict: SyncOutcome.Conflict) {
    DialogCard(onDismiss = sync::dismissConflict) {
        Text(
            "This phone and the server differ",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        Muted(
            "The server's copy is from ${whenText(conflict.updated)}. Choose which to keep. Your other phones " +
                "follow the one you keep, and the other stays on the server under Earlier copies.",
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Use the server's copy", Modifier.fillMaxWidth()) { sync.resolve(useServer = true) }
        Spacer(Modifier.height(8.dp))
        GhostButton("Use this phone's copy", Modifier.fillMaxWidth()) { sync.resolve(useServer = false) }
        TextButton(sync::dismissConflict, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Not now", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun EarlierCopiesScreen() {
    val sync = syncManager()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copies by remember { mutableStateOf<List<EarlierCopy>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<EarlierCopy?>(null) }
    LaunchedEffect(Unit) {
        try {
            copies = sync.earlierCopies()
        } catch (e: ServerFailure) {
            problem = e.message
        }
    }
    Page("Earlier copies") {
        ScrollBody {
            val list = copies
            when {
                problem != null -> Muted(problem.orEmpty())
                list == null -> Muted("Loading…")
                list.isEmpty() -> EmptyNote("No earlier copies yet")
                else -> Panel {
                    list.forEachIndexed { index, copy ->
                        if (index > 0) Divider()
                        ListRow(whenText(copy.updated), subtitle = billCount(copy.bills)) { chosen = copy }
                    }
                }
            }
        }
    }
    chosen?.let { copy ->
        ConfirmDialog(
            "Go back to this copy?",
            "This phone will match the copy from ${whenText(copy.updated)}. What it holds now goes to the server first, so you can come back to it.",
            confirm = "Go back",
            onDismiss = { chosen = null },
        ) {
            chosen = null
            scope.launch {
                sync.restoreEarlier(copy.id)
                val status = sync.status.value
                val done = status.error == null && status.conflict == null
                Toast.makeText(context, if (done) "Copy restored" else status.error ?: "Sort out the sync first", Toast.LENGTH_LONG).show()
                if (done) nav.leavePage()
            }
        }
    }
}
