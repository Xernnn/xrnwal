package com.xern.xrnwallet.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import android.os.SystemClock
import androidx.compose.animation.core.tween
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
/** A bill opens straight into its edit page, as one tap is all it should take to change it. */
fun NavHostController.openBill(id: Long) = navigate("edit?id=$id")

const val NO_DATE = Long.MIN_VALUE

// Pages cross-fade quickly, the way the reference app's do (about 70 ms there), rather than sliding
// across over a quarter of a second: a slide reads as waiting for the page, a quick fade as the page
// simply being there. The new page also grows the last 2% into place, so it doesn't look pasted on.
private val pageIn = tween<Float>(130, easing = LinearOutSlowInEasing)
private val pageOut = tween<Float>(90, easing = FastOutLinearInEasing)

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
                        enterTransition = { fadeIn(pageIn) + scaleIn(pageIn, initialScale = 0.98f) },
                        exitTransition = { fadeOut(pageOut) },
                        popEnterTransition = { fadeIn(pageIn) },
                        popExitTransition = { fadeOut(pageOut) + scaleOut(pageOut, targetScale = 0.98f) },
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

private val FAB_SIZE = 56.dp
private val FAB_RISE = 26.dp // how far the + sits above the bar's top edge
private val CRADLE_GAP = 6.dp

/**
 * The bar's outline: a rectangle with a round notch cut into its top edge, wide enough to hold the +
 * with a ring of page showing round it, and soft shoulders where the notch meets the edge.
 */
private class CradleShape(private val radius: Float, private val centreY: Float, private val shoulder: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        // Where the notch's circle crosses the top edge, and the angle (from straight right, clockwise,
        // as y grows downwards) it crosses at on the left: the arc runs under the + from there round
        // to the right. Each end is trimmed a few degrees down the circle, and a shoulder curve
        // eases the edge into it, so the notch has no sharp corner.
        val half = kotlin.math.sqrt((radius * radius - centreY * centreY).coerceAtLeast(0f))
        val cross = Math.toDegrees(kotlin.math.asin((centreY / radius).coerceIn(-1f, 1f).toDouble())).toFloat()
        val trim = 8f
        val startDeg = 180f + cross - trim
        val sweep = -(180f + 2 * cross - 2 * trim)
        val startRad = Math.toRadians(startDeg.toDouble())
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(cx - half - shoulder, 0f)
            quadraticTo(cx - half, 0f, cx + radius * kotlin.math.cos(startRad).toFloat(), centreY + radius * kotlin.math.sin(startRad).toFloat())
            arcTo(Rect(cx - radius, centreY - radius, cx + radius, centreY + radius), startDeg, sweep, forceMoveTo = false)
            quadraticTo(cx + half, 0f, cx + half + shoulder, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val density = LocalDensity.current
    val cradle = remember(density) {
        with(density) {
            CradleShape(
                radius = (FAB_SIZE / 2 + CRADLE_GAP).toPx(),
                centreY = (FAB_SIZE / 2 - FAB_RISE).toPx(),
                shoulder = 10.dp.toPx(),
            )
        }
    }
    Box {
        Surface(color = MaterialTheme.colorScheme.surface, shape = cradle) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(70.dp)) {
                BarItem("Home", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance, tab == 0, Modifier.weight(1f)) { onSelect(0) }
                BarItem("Reports", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard, tab == 1, Modifier.weight(1f)) { onSelect(1) }
                Spacer(Modifier.weight(1f))
                BarItem("Accounts", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, tab == 2, Modifier.weight(1f)) { onSelect(2) }
                // Material's filled face fills only the hair, so Mine's is a disc behind the line face.
                BarItem("Mine", null, Icons.Outlined.Face, tab == 3, Modifier.weight(1f)) { onSelect(3) }
            }
        }
        val source = remember { MutableInteractionSource() }
        val scale = pressedScale(source, depth = 0.86f)
        val tick = rememberTick()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -FAB_RISE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(FAB_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(source, ripple()) { tick(); onAdd() }
                .semantics { contentDescription = "Add bill" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(30.dp)) }
    }
}

/**
 * A tab: a line icon, and when chosen the same icon filled in the accent with its lines drawn dark
 * over it, which reads on a light bar as well as a dark one. The choice hops as it lands.
 */
@Composable
private fun BarItem(label: String, filled: ImageVector?, outlined: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tick = rememberTick()
    val hop = remember { Animatable(1f) }
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected) {
            hop.animateTo(1.18f, spring(stiffness = Spring.StiffnessHigh))
            hop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
        wasSelected = selected
    }
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(140),
        label = "fill",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, null) {
                if (!selected) tick()
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = hop.value; scaleY = hop.value }.size(28.dp), contentAlignment = Alignment.Center) {
            if (filled != null) {
                Icon(filled, null, tint = fill, modifier = Modifier.size(28.dp))
            } else {
                Box(Modifier.size(21.dp).clip(CircleShape).background(fill))
            }
            Icon(
                outlined,
                null,
                tint = if (selected) Color(0xFF1B1B1E) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            label,
            Modifier.padding(top = 2.dp),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
