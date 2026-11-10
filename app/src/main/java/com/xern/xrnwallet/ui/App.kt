package com.xern.xrnwallet.ui

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xern.xrnwallet.MainActivity
import com.xern.xrnwallet.WalletApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate

fun NavHostController.addBill(date: LocalDate? = null) = navigate("edit?date=${date?.toEpochDay() ?: NO_DATE}")
fun NavHostController.openBill(id: Long) = navigate("bill/$id")

const val NO_DATE = Long.MIN_VALUE

private val pageSlide = tween<IntOffset>(260, easing = FastOutSlowInEasing)

/** Far longer than any slide, so a stuck animation can't leave the app ignoring touches. */
private const val SLIDE_LIMIT_MILLIS = 1_500L

/**
 * Drops touches while two pages are on screen. One landing then hits a page on its way in or out, so a
 * second tap on Save would write twice, and a second tap on a tile would open its page twice. It checks
 * on each touch instead of covering the screen, because a cover only appears on the next frame.
 */
private fun Modifier.ignoreTouchesDuringSlides(nav: NavHostController) = pointerInput(nav) {
    var slidingSince = 0L
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (nav.visibleEntries.value.size > 1) {
                val now = SystemClock.uptimeMillis()
                if (slidingSince == 0L) slidingSince = now
                if (now - slidingSince < SLIDE_LIMIT_MILLIS) event.changes.forEach { it.consume() }
            } else {
                slidingSince = 0L
            }
        }
    }
}

private fun NavGraphBuilder.idRoute(route: String, content: @Composable (Long) -> Unit) =
    composable("$route/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
        content(it.arguments!!.getLong("id"))
    }

/** A screen that edits an existing row when given an id and creates one when it is not. */
private fun NavGraphBuilder.editRoute(route: String, content: @Composable (Long) -> Unit) =
    composable("$route?id={id}", listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
        content(it.arguments!!.getLong("id"))
    }

@Composable
fun WalletRoot(activity: MainActivity) {
    val repo = (LocalContext.current.applicationContext as WalletApp).repository
    val settings by repo.store.settings.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val ui = remember { AppUi(LocalDate.now(), settings.periodRules) }
    val scope = rememberCoroutineScope()
    val undo = remember { BillUndo(repo, scope) }

    WalletTheme(settings) {
        CompositionLocalProvider(
            LocalRepo provides repo,
            LocalNav provides nav,
            LocalAppUi provides ui,
            LocalBillUndo provides undo,
        ) {
            val lockShown = settings.locked && activity.locked
            // With no lock set the session counts as unlocked, so choosing a first PIN does not lock you out.
            LaunchedEffect(settings.locked, activity.locked) {
                if (!settings.locked && activity.locked) activity.unlock()
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // The pages stay composed so navigation survives unlocking, and so a file shared while
                // locked is waiting afterwards. Hiding them from accessibility keeps bills unreadable.
                Box(if (lockShown) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                    NavHost(
                        navController = nav,
                        startDestination = "main",
                        modifier = Modifier.ignoreTouchesDuringSlides(nav),
                        // Both pages move together, side by side, so opening one reads as turning to it.
                        enterTransition = { slideInHorizontally(pageSlide) { it } },
                        exitTransition = { slideOutHorizontally(pageSlide) { -it } },
                        popEnterTransition = { slideInHorizontally(pageSlide) { -it } },
                        popExitTransition = { slideOutHorizontally(pageSlide) { it } },
                    ) { routes() }
                }
                UndoBar(undo, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 100.dp))
                if (lockShown) LockScreen(activity, onUnlock = activity::unlock)
            }
            LaunchedEffect(activity.inboxRevision) {
                if (activity.inboxRevision > 0) nav.navigate("import")
            }
            LaunchedEffect(activity.addBillRevision) {
                if (activity.addBillRevision > 0) nav.addBill()
            }
            // Waits for the database, so someone updating the app with records already in it never sees this.
            LaunchedEffect(Unit) {
                if (settings.welcomeSeen) return@LaunchedEffect
                val loaded = repo.snapshot.first { it.ledgers.isNotEmpty() }
                repo.store.update { it.copy(welcomeSeen = true) }
                if (loaded.bills.isEmpty()) nav.navigate("welcome")
            }
        }
    }
}

private fun NavGraphBuilder.routes() {
    composable("main") { MainShell() }
    composable("welcome") { WelcomeScreen() }
    composable(
        "edit?id={id}&date={date}&type={type}",
        listOf(
            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
            navArgument("date") { type = NavType.LongType; defaultValue = NO_DATE },
            navArgument("type") { type = NavType.IntType; defaultValue = 0 },
        ),
    ) {
        val args = it.arguments!!
        BillEditScreen(args.getLong("id"), args.getLong("date"), args.getInt("type"))
    }
    idRoute("bill") { BillDetailScreen(it) }
    composable(
        "filter?category={category}&start={start}&end={end}&type={type}",
        listOf(
            navArgument("category") { type = NavType.LongType; defaultValue = 0L },
            navArgument("start") { type = NavType.LongType; defaultValue = NO_DATE },
            navArgument("end") { type = NavType.LongType; defaultValue = NO_DATE },
            navArgument("type") { type = NavType.IntType; defaultValue = -1 },
        ),
    ) {
        val args = it.arguments!!
        FilterScreen(args.getLong("category"), args.getLong("start"), args.getLong("end"), args.getInt("type"))
    }
    composable("calendar") { CalendarScreen() }

    editRoute("account/edit") { AccountEditScreen(it) }
    idRoute("account") { AccountDetailScreen(it) }

    composable("budget") { BudgetScreen() }
    composable("savings") { SavingsScreen() }
    composable("savings/new/{type}") { SavingsEditScreen(it.arguments!!.getString("type")!!) }
    idRoute("savings/plan") { SavingsDetailScreen(it) }
    composable("recurring") { RecurringScreen() }
    editRoute("recurring/edit") { RecurringEditScreen(it) }
    idRoute("recurring/view") { RecurringDetailScreen(it) }

    composable("ledgers") { LedgersScreen() }
    editRoute("ledger/edit") { LedgerEditScreen(it) }
    idRoute("ledger") { LedgerDetailScreen(it) }
    composable("categories") { CategoriesScreen() }
    composable("category/new/{type}") { CategoryEditScreen(0, it.arguments!!.getString("type")!!.toInt(), null) }
    composable("category/sub/{type}/{parent}") {
        CategoryEditScreen(0, it.arguments!!.getString("type")!!.toInt(), it.arguments!!.getString("parent")!!.toLong())
    }
    idRoute("category/edit") { CategoryEditScreen(it, 0, null) }
    composable("tags") { TagsScreen() }
    composable("checklists") { ChecklistsScreen() }
    idRoute("checklist") { ChecklistDetailScreen(it) }

    composable("appearance") { AppearanceScreen() }
    composable("reminders") { RemindersScreen() }
    editRoute("reminder/edit") { ReminderEditScreen(it) }
    composable("password") { PasswordScreen() }
    composable("exchange") { ExchangeScreen() }
    composable("sync") { BackupScreen() }
    composable("sync/earlier") { EarlierCopiesScreen() }
    composable("export") { ExportScreen() }
    composable("import") { ImportScreen() }
    composable("startofmonth") { StartOfMonthScreen() }
    composable("carryover") { CarryoverScreen() }
    composable("preferences") { PreferencesScreen() }
    composable("cardstyle") { CardStyleScreen() }
    composable("colors") { ColorsScreen() }
    composable("currencies") { CurrenciesScreen() }
    composable("haptics") { HapticsScreen() }
    composable("languages") { NoticeScreen("Languages", "English", "The app is in English for now.") }
    composable("appicons") { AppIconsScreen() }
}

@Composable
private fun MainShell() {
    val ui = LocalAppUi.current
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            when (ui.tab) {
                0 -> HomeScreen()
                1 -> ReportsScreen()
                2 -> AccountsScreen()
                else -> MineScreen()
            }
        }
        BottomBar(ui.tab, onSelect = { ui.tab = it }, onAdd = { nav.addBill() })
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    Box {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(70.dp)) {
                BarItem("Home", Icons.Filled.AccountBalance, tab == 0, Modifier.weight(1f)) { onSelect(0) }
                BarItem("Reports", Icons.Filled.BarChart, tab == 1, Modifier.weight(1f)) { onSelect(1) }
                Spacer(Modifier.weight(1f))
                BarItem("Accounts", Icons.Filled.AccountBalanceWallet, tab == 2, Modifier.weight(1f)) { onSelect(2) }
                BarItem("Mine", Icons.Filled.Person, tab == 3, Modifier.weight(1f)) { onSelect(3) }
            }
        }
        val source = remember { MutableInteractionSource() }
        val scale = pressedScale(source)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(source, ripple(), onClick = onAdd)
                .semantics { contentDescription = "Add bill" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Yellow on white is hard to see, so the choice is a soft pill and the icon stays dark.
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 2.dp),
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

/** A plain page for the settings that have only one choice in this build. */
@Composable
fun NoticeScreen(title: String, current: String, note: String) {
    Page(title) {
        ScrollBody {
            Panel { ListRow(current, chevron = false) }
            Muted(note, Modifier.padding(horizontal = 4.dp))
        }
    }
}
