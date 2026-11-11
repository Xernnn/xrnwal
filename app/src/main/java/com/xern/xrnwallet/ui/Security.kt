package com.xern.xrnwallet.ui

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.xern.xrnwallet.MainActivity
import com.xern.xrnwallet.data.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun canUseBiometric(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

private fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock xrnwallet")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build(),
    )
}

/**
 * With a [length], the PIN is tried as its last digit is typed. Without one, a PIN is being chosen: 4 to
 * 8 digits, then the tick.
 */
@Composable
private fun PinPad(title: String, error: Boolean, length: Int?, onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(length ?: maxOf(AppLock.PIN_MIN, pin.length)) { index ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error -> Color(0xFFFF3B30)
                                index < pin.length -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
        val done = if (length == null) 'k' else ' '
        listOf("123", "456", "789", "${done}0<").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { key ->
                    val usable = key != ' ' && (key != 'k' || pin.length >= AppLock.PIN_MIN)
                    Box(
                        Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (key == ' ') Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = usable) {
                                when {
                                    key == '<' -> pin = pin.dropLast(1)
                                    key == 'k' -> {
                                        onPin(pin)
                                        pin = ""
                                    }
                                    pin.length < (length ?: AppLock.PIN_MAX) -> {
                                        pin += key
                                        if (pin.length == length) {
                                            onPin(pin)
                                            pin = ""
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            '<' -> Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace")
                            'k' -> Icon(
                                Icons.Filled.Check,
                                "Done",
                                tint = if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ' ' -> Unit
                            else -> Text(key.toString(), fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternPad(title: String, error: Boolean, onPattern: (String) -> Unit) {
    val dot = MaterialTheme.colorScheme.onSurface
    val line = if (error) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary
    val pattern by rememberUpdatedState(onPattern)
    var path by remember { mutableStateOf(listOf<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, Modifier.padding(bottom = 22.dp), style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    fun centre(index: Int) = Offset(
                        size.width * (index % 3 * 2 + 1) / 6f,
                        size.height * (index / 3 * 2 + 1) / 6f,
                    )
                    fun hit(point: Offset) = (0..8).firstOrNull { (centre(it) - point).getDistance() < size.width / 8f }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        path = listOfNotNull(hit(down.position))
                        finger = down.position
                        val finished = drag(down.id) { change ->
                            change.consume()
                            finger = change.position
                            hit(change.position)?.let { if (it !in path) path = path + it }
                        }
                        finger = null
                        if (finished && path.size >= 4) pattern(path.joinToString("-"))
                        path = emptyList()
                    }
                },
        ) {
            fun centre(index: Int) = Offset(
                size.width * (index % 3 * 2 + 1) / 6f,
                size.height * (index / 3 * 2 + 1) / 6f,
            )
            path.zipWithNext().forEach { (from, to) -> drawLine(line, centre(from), centre(to), 5.dp.toPx()) }
            val last = path.lastOrNull()
            val tip = finger
            if (last != null && tip != null) drawLine(line, centre(last), tip, 5.dp.toPx())
            (0..8).forEach { drawCircle(if (it in path) line else dot, 9.dp.toPx(), centre(it)) }
        }
        Text(
            if (error) "Wrong pattern, try again" else "Connect at least 4 dots",
            Modifier.padding(top = 16.dp),
            color = if (error) line else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LockScreen(activity: MainActivity, onUnlock: () -> Unit) {
    val settings = prefs()
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var usePattern by remember { mutableStateOf(settings.pinHash == null) }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    // Only this screen changes the lock while it shows, so its fields can be taken from the try whole.
    fun attempt(secret: String, pattern: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            val tried = withContext(Dispatchers.Default) { AppLock.attempt(repo.store.settings.value, pattern, secret) }
            repo.store.update { it.withLockOf(tried.settings) }
            checking = false
            if (tried.opened) onUnlock() else error = true
        }
    }

    LaunchedEffect(Unit) {
        if (settings.biometric && canUseBiometric(activity)) promptBiometric(activity, onUnlock)
    }
    BackHandler { activity.moveTaskToBack(true) }
    Column(
        Modifier
            .fillMaxSize()
            // Takes every touch, so nothing on the page beneath can be reached by tapping blind.
            .pointerInput(Unit) {}
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (usePattern && settings.patternHash != null) {
            PatternPad("Draw your pattern", error) { attempt(it, pattern = true) }
        } else if (settings.pinHash != null) {
            PinPad("Enter your PIN", error, settings.pinLength.coerceIn(AppLock.PIN_MIN, AppLock.PIN_MAX)) { attempt(it, pattern = false) }
        }
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (settings.pinHash != null && settings.patternHash != null) {
                Pill(if (usePattern) "Use PIN" else "Use pattern", false) {
                    usePattern = !usePattern
                    error = false
                }
            }
            if (settings.biometric && canUseBiometric(activity)) {
                Pill("Use biometrics", false) { promptBiometric(activity, onUnlock) }
            }
        }
    }
}

@Composable
fun PasswordScreen() {
    val settings = prefs()
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun finish(secret: String, kind: String) {
        val earlier = first
        if (earlier == null) {
            first = secret
            error = false
            return
        }
        if (earlier != secret) {
            first = null
            error = true
            return
        }
        scope.launch {
            val chosen = withContext(Dispatchers.Default) {
                val current = repo.store.settings.value
                if (kind == "pin") AppLock.withPin(current, secret) else AppLock.withPattern(current, secret)
            }
            repo.store.update { it.withLockOf(chosen) }
            setup = null
            first = null
            error = false
        }
    }

    val kind = setup
    if (kind != null) {
        Page(if (kind == "pin") "Digital password" else "Gesture password", onBack = { setup = null; first = null; error = false }) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                val title = when {
                    error -> "They did not match. Try again"
                    first != null -> "Enter it again to confirm"
                    kind == "pin" -> "Choose a PIN of 4 to 8 digits"
                    else -> "Draw a pattern"
                }
                if (kind == "pin") PinPad(title, error, length = null) { finish(it, kind) } else PatternPad(title, error) { finish(it, kind) }
            }
        }
        return
    }

    Page("Password Protection") {
        ScrollBody {
            Panel {
                SwitchRow("Digital password", settings.pinHash != null) { on ->
                    if (on) setup = "pin" else repo.store.update { it.copy(pinHash = null, biometric = it.biometric && it.patternHash != null) }
                }
                Divider()
                SwitchRow("Gesture password", settings.patternHash != null) { on ->
                    if (on) setup = "pattern" else repo.store.update { it.copy(patternHash = null, biometric = it.biometric && it.pinHash != null) }
                }
                Divider()
                SwitchRow("Biometric unlock", settings.biometric) { on ->
                    when {
                        !on -> repo.store.update { it.copy(biometric = false) }
                        settings.pinHash == null && settings.patternHash == null ->
                            Toast.makeText(context, "Set a PIN or gesture first", Toast.LENGTH_SHORT).show()
                        !canUseBiometric(context) ->
                            Toast.makeText(context, "No biometrics set up on this phone", Toast.LENGTH_SHORT).show()
                        else -> repo.store.update { it.copy(biometric = true) }
                    }
                }
            }
            Muted("Set two methods, so you have a fallback. A forgotten password cannot be recovered, and the app would need clearing.")
            Muted("While a lock is set, widgets hide amounts and the recent apps screen shows no preview of this app.")
        }
    }
}
