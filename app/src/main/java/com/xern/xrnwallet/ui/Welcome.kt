package com.xern.xrnwallet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavHostController
import com.xern.xrnwallet.data.Snapshot

/** One of the first things to do in the app. It ticks itself off from the data, so it can't go stale. */
class FirstStep(
    val title: String,
    val detail: String,
    val done: (Snapshot) -> Boolean,
    val open: (NavHostController) -> Unit,
)

val firstSteps = listOf(
    FirstStep(
        "Add your bank or card",
        "Cash is already set up. Add anywhere else you keep money.",
        { it.accounts.size > 1 },
    ) { it.navigate("account/edit") },
    FirstStep(
        "Add your first bill",
        "A bill is anything you spent or earned. Use the + button any time.",
        { it.bills.isNotEmpty() },
    ) { it.addBill() },
    FirstStep(
        "Set a budget",
        "Optional. Choose how much to spend each month.",
        { it.budgets.isNotEmpty() },
    ) { it.navigate("budget") },
)

/** What to do first. Each step opens its page and comes back here, so the list works as a checklist. */
@Composable
fun WelcomeScreen() {
    val s = snap()
    val nav = LocalNav.current
    val context = LocalContext.current
    val icon = remember {
        val choice = AppIcons.choices[AppIcons.current(context)]
        ResourcesCompat.getDrawable(context.resources, choice.icon, context.theme)?.toBitmap(192, 192)?.asImageBitmap()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) Image(icon, null, Modifier.size(68.dp))
                Text(
                    "Welcome to xrnwallet",
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "See where your money goes. Your records stay on this phone unless you choose to back them up.",
                    Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            SectionTitle("Start here")
            Panel {
                firstSteps.forEachIndexed { index, step ->
                    if (index > 0) Divider()
                    StepRow(index + 1, step, step.done(s)) { step.open(nav) }
                }
            }
            SectionTitle("Already have records?")
            Panel {
                ListRow("Import from another app", subtitle = "Use a CSV file exported from it") { nav.navigate("import") }
                Divider()
                ListRow("Restore a backup", subtitle = "From a file or your own server") { nav.navigate("sync") }
            }
        }
        PrimaryButton("Get started", Modifier.fillMaxWidth().padding(16.dp)) { nav.leavePage() }
    }
}

@Composable
private fun StepRow(number: Int, step: FirstStep, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, "Done", Modifier.size(20.dp), tint = Color.Black)
            } else {
                Text(number.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge)
            Muted(step.detail, Modifier.padding(top = 3.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
    }
}
