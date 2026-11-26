package com.xern.xrnwal.data

import android.content.Context
import android.os.Build
import com.xern.xrnwal.BuildConfig
import com.xern.xrnwal.work.SyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class SyncStatus(
    val address: String? = null,
    val user: String = "",
    val auto: Boolean = true,
    val photos: Boolean = true,
    val running: Boolean = false,
    val syncedAt: Long = 0,
    val error: String? = null,
    val conflict: SyncOutcome.Conflict? = null,
    /** The server turned the saved login down: nothing is sent until the password is entered again. */
    val signInAgain: Boolean = false,
) {
    val connected: Boolean get() = address != null
}

/** Runs the engine for the app: one sync at a time, after changes, and reporting to the screen. */
class SyncManager(context: Context, private val repo: Repository, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val store = SyncStore(app)
    private val lock = Mutex()
    private var login: ServerLogin? = store.login()

    private val state = MutableStateFlow(
        SyncStatus(
            login?.address,
            login?.user.orEmpty(),
            store.auto,
            store.photos,
            syncedAt = store.load().syncedAt,
            signInAgain = store.refusal?.signInAgain == true,
        ),
    )
    val status: StateFlow<SyncStatus> = state

    private val wallet = object : SyncedWallet {
        override suspend fun document() = repo.syncDocument()
        override suspend fun replaceWith(body: String, expected: String) = repo.replaceIfUnchanged(body, expected)
        override fun photosIn(body: String) = repo.photosIn(body)
        override fun photoFile(name: String) = repo.photoFile(name)
    }

    val autoSyncing: Boolean get() = login != null && store.auto

    /** Syncs when the app opens and a few seconds after anything changes. */
    @OptIn(FlowPreview::class)
    fun start() {
        SyncWorker.apply(app, autoSyncing)
        scope.launch {
            repo.snapshot.debounce(CHANGE_DELAY_MILLIS).collect { if (autoSyncing) syncNow() }
        }
    }

    private fun engine(login: ServerLogin) =
        SyncEngine(HomeServer(login, Build.MODEL.take(MAX_DEVICE_NAME)), wallet, store, { store.photos })

    /**
     * Runs [work] against the server. Returns what went wrong, or null. It runs in the app's scope,
     * not the caller's: a screen can close at any moment, and work like keeping the server's copy
     * must not stop halfway because of it. A caller that goes away only stops waiting.
     */
    private suspend fun attempt(login: ServerLogin, work: suspend (SyncEngine) -> SyncOutcome): Exception? =
        scope.async(Dispatchers.IO) {
            lock.withLock {
                // The saved login, turned down: sending it again would only count one more wrong password.
                val saved = login === this@SyncManager.login
                if (saved && store.refusal?.signInAgain == true) return@withLock ServerFailure(401)
                state.update { it.copy(running = true, error = null) }
                var failure: Exception? = null
                var conflict: SyncOutcome.Conflict? = null
                try {
                    conflict = work(engine(login)) as? SyncOutcome.Conflict
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Beyond the server failing, a copy this phone can't read. Nothing was replaced.
                    failure = e
                } finally {
                    if (saved) noteAnswer(failure)
                    state.update {
                        it.copy(
                            running = false,
                            conflict = conflict,
                            syncedAt = store.load().syncedAt,
                            error = failure?.let(::describe),
                            signInAgain = store.refusal?.signInAgain == true,
                        )
                    }
                }
                failure
            }
        }.await()

    /** Remembers a refused login, and forgets it once the server lets this phone in. */
    private fun noteAnswer(failure: Exception?) {
        val refusal = Refusal.of(failure, System.currentTimeMillis())
        if (refusal != null) store.refusal = refusal else if (failure == null) store.refusal = null
    }

    private fun describe(failure: Exception): String = when (failure) {
        is ServerFailure -> failure.message.orEmpty()
        is UnusableBackup -> "The server's copy can't be used here: ${failure.message}. Nothing on this phone changed."
        else -> "The server's copy could not be used. Nothing on this phone changed."
    }

    /**
     * Returns true when the sync should be tried again later, as with a phone that's offline. Unless
     * the person [asked] for it, it leaves a server alone that turned this phone's login down.
     */
    suspend fun syncNow(asked: Boolean = false): Boolean {
        val login = login ?: return false
        if (!asked && store.holdsBack(System.currentTimeMillis())) return false
        val failure = attempt(login) { it.sync() }
        val again = (failure as? ServerFailure)?.temporary == true
        if (again && autoSyncing) SyncWorker.retrySoon(app)
        return again
    }

    /** Tries the login before keeping it. Returns what went wrong, or null. */
    suspend fun connect(address: String, user: String, password: String): String? {
        val login = ServerLogin(ServerAddress.clean(address), user.trim(), password)
        if (!ServerAddress.isValid(login.address, emulator = BuildConfig.DEBUG)) {
            return "Enter the https:// address of your home server."
        }
        if (login.user.isEmpty() || password.isEmpty()) return "Enter the user name and password."
        // Whatever an earlier connection left behind says nothing about this server.
        withContext(Dispatchers.IO) { store.save(SyncMeta()) }
        attempt(login) { it.sync() }?.let { return describe(it) }
        withContext(Dispatchers.IO) {
            store.saveLogin(login)
            store.refusal = null
        }
        this.login = login
        state.update { it.copy(address = login.address, user = login.user, signInAgain = false) }
        SyncWorker.apply(app, autoSyncing)
        return null
    }

    /**
     * Takes the password again after the server turned the saved one down, keeping everything else:
     * the same server, and the copy this phone matched there. Returns what went wrong, or null.
     */
    suspend fun signInAgain(password: String): String? {
        val saved = login ?: return null
        if (password.isEmpty()) return "Enter the password."
        val login = saved.copy(password = password)
        attempt(login) { it.sync() }?.let { return describe(it) }
        withContext(Dispatchers.IO) {
            store.saveLogin(login)
            store.refusal = null
        }
        this.login = login
        state.update { it.copy(signInAgain = false) }
        return null
    }

    fun disconnect() {
        login = null
        store.forget()
        SyncWorker.apply(app, false)
        state.value = SyncStatus(auto = store.auto, photos = store.photos)
    }

    fun setAuto(on: Boolean) {
        store.auto = on
        state.update { it.copy(auto = on) }
        SyncWorker.apply(app, autoSyncing)
    }

    fun setPhotos(on: Boolean) {
        store.photos = on
        state.update { it.copy(photos = on) }
    }

    fun dismissConflict() = state.update { it.copy(conflict = null) }

    /** Takes the choice and gets on with it, so the dialog that asked can close straight away. */
    fun resolve(useServer: Boolean) {
        val login = login ?: return
        dismissConflict()
        scope.launch { attempt(login) { if (useServer) it.keepServer() else it.keepPhone() } }
    }

    /** Throws [ServerFailure] when the server can't be reached. */
    suspend fun earlierCopies(): List<EarlierCopy> = withContext(Dispatchers.IO) {
        if (store.refusal?.signInAgain == true) throw ServerFailure(401)
        HomeServer(login ?: throw ServerFailure(0), Build.MODEL.take(MAX_DEVICE_NAME)).earlierCopies()
    }

    suspend fun restoreEarlier(id: String) {
        val login = login ?: return
        attempt(login) { it.restoreEarlier(id) }
    }

    /**
     * Runs [change], which replaces this phone's data on purpose, such as restoring a file. While
     * connected, the next sync then asks which copy to keep: see [SyncEngine.replaceHere]. No sync runs
     * during it, so none can send the result up before this phone has forgotten what it matched, and
     * like [attempt] it runs in the app's scope, so a screen closing can't stop it halfway.
     */
    suspend fun replaceHere(change: suspend () -> Unit) {
        scope.async(Dispatchers.IO) {
            lock.withLock {
                val login = login
                if (login == null) change() else engine(login).replaceHere(change)
            }
        }.await()
    }

    /**
     * Runs [work] unless a sync is running, and says whether it ran. For tidying that can wait, like
     * clearing out unused photos: a download picks which photos to fetch before its bills land, so a
     * photo it means to use can look unused in between.
     */
    suspend fun unlessSyncing(work: suspend () -> Unit): Boolean {
        if (!lock.tryLock()) return false
        try {
            work()
        } finally {
            lock.unlock()
        }
        return true
    }

    private companion object {
        const val CHANGE_DELAY_MILLIS = 5_000L
        const val MAX_DEVICE_NAME = 40
    }
}
