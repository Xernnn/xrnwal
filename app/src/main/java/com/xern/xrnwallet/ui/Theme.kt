package com.xern.xrnwallet.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.xern.xrnwallet.R
import com.xern.xrnwallet.data.ALL_LEDGERS
import com.xern.xrnwallet.data.Repository
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.domain.Currencies
import com.xern.xrnwallet.domain.Money
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.PeriodRules
import java.time.LocalDate

val LocalRepo = staticCompositionLocalOf<Repository> { error("No repository") }
val LocalNav = staticCompositionLocalOf<NavHostController> { error("No navigation") }
val LocalAppUi = staticCompositionLocalOf<AppUi> { error("No app state") }

/**
 * State that outlives a single screen: the periods chosen on Home and Reports. Both open on the
 * accounting month holding today, which with a later start of month can still be last month.
 */
class AppUi(today: LocalDate, rules: PeriodRules) {
    var tab by mutableIntStateOf(0)
    var homePeriod by mutableStateOf<Period>(Period.Month(rules.monthOf(today)))
    var reportPeriod by mutableStateOf<Period>(Period.Month(rules.monthOf(today)))
}

/** Data held still, for drawing pages with no database behind them: the debug build's previews. */
class FixedData(val snapshot: Snapshot, val settings: Settings)

val LocalFixedData = staticCompositionLocalOf<FixedData?> { null }

@Composable
fun snap(): Snapshot = LocalFixedData.current?.snapshot ?: LocalRepo.current.snapshot.collectAsStateWithLifecycle().value

@Composable
fun prefs(): Settings = LocalFixedData.current?.settings ?: LocalRepo.current.store.settings.collectAsStateWithLifecycle().value

@Composable
fun fmt(): Fmt {
    val settings = prefs()
    return remember(settings) { Fmt(settings) }
}

class Fmt(private val settings: Settings) {
    val format = settings.moneyFormat
    val base = settings.baseCurrency

    fun symbol(code: String): String =
        if (code == base && !settings.showBaseSymbol) "" else Currencies.symbol(code)

    fun money(cents: Long, code: String = base): String = format.money(cents, symbol(code))
    fun signed(cents: Long, code: String = base): String = format.signed(cents, symbol(code))
    fun plain(cents: Long): String = (if (cents < 0) "-" else "") + format.digits(cents)

    /** An amount typed in [code], read the way this number format writes one. See [Money.parse]. */
    fun parse(text: String, code: String = base): Long? = Money.parse(text, format.decimalChar, Money.wholeUnits(code))
    val expenseColor = Color(settings.expenseColor)
    val incomeColor = Color(settings.incomeColor)
    val expenseArrowColor = Color(settings.expenseArrowColor)
    val incomeArrowColor = Color(settings.incomeArrowColor)
    val expenseArrow = if (settings.arrowsReversed) "↓" else "↑"
    val incomeArrow = if (settings.arrowsReversed) "↑" else "↓"
}

val accents: List<Pair<String, Long>> = listOf(
    "Sun" to 0xFFFFD633,
    "Sky" to 0xFF4F9DFF,
    "Mint" to 0xFF4CD08A,
    "Rose" to 0xFFFF7EB6,
    "Amber" to 0xFFFF9F45,
)

@Composable
fun WalletTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        Settings.THEME_LIGHT -> false
        Settings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val accent = Color(accents.getOrElse(settings.accent) { accents[0] }.second)
    val colors = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent,
            onSecondary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF1E1E20),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2C2C2F),
            onSurfaceVariant = Color(0xFFC2C2C8),
            outline = Color(0xFF3C3C40),
            surfaceContainer = Color(0xFF2C2C2F),
            surfaceContainerHigh = Color(0xFF242427),
            surfaceContainerHighest = Color(0xFF2C2C2F),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent,
            onSecondary = Color.Black,
            background = Color(0xFFF4F4F6),
            onBackground = Color(0xFF17171A),
            surface = Color.White,
            onSurface = Color(0xFF17171A),
            surfaceVariant = Color(0xFFEDEDF0),
            onSurfaceVariant = Color(0xFF5C5C63),
            outline = Color(0xFFDDDDE1),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFEDEDF0),
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = walletType) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}

/**
 * MiSans Latin by Xiaomi, one unmodified file per weight. Its terms need the credit on the Mine page
 * kept. It runs heavier than its names, so Bold draws Semibold and ExtraBold draws Bold. Covers
 * Vietnamese.
 */
val MiSans = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_demibold, FontWeight.SemiBold),
    Font(R.font.misans_semibold, FontWeight.Bold),
    Font(R.font.misans_bold, FontWeight.ExtraBold),
    Font(R.font.misans_heavy, FontWeight.Black),
)

/** MiSans for text drawn straight onto a canvas, which the theme can't reach. */
@Composable
fun rememberCanvasTypeface(): android.graphics.Typeface? {
    val context = LocalContext.current
    return remember { ResourcesCompat.getFont(context, R.font.misans_regular) }
}

/**
 * Sizes alone leave lines touching, because Compose trims the space a font keeps above and below.
 * Line height is relative, so a text with its own size keeps the same room.
 */
private fun roomy(size: Int, lines: Float, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = MiSans,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lines.em,
    letterSpacing = 0.1.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val walletType = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = MiSans),
        displayMedium = displayMedium.copy(fontFamily = MiSans),
        displaySmall = displaySmall.copy(fontFamily = MiSans),
        headlineLarge = headlineLarge.copy(fontFamily = MiSans),
        headlineMedium = headlineMedium.copy(fontFamily = MiSans),
        headlineSmall = headlineSmall.copy(fontFamily = MiSans),
        titleLarge = roomy(23, 1.3f, FontWeight.Bold),
        titleMedium = roomy(19, 1.35f, FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = MiSans),
        bodyLarge = roomy(18, 1.4f),
        bodyMedium = roomy(16, 1.4f),
        bodySmall = roomy(14, 1.4f),
        labelLarge = roomy(17, 1.35f, FontWeight.Medium),
        labelMedium = labelMedium.copy(fontFamily = MiSans),
        labelSmall = labelSmall.copy(fontFamily = MiSans),
    )
}

/** The ledger Home is showing, or the first ledger when the stored choice no longer exists. */
fun Snapshot.activeLedger(settings: Settings): Long =
    if (settings.activeLedger == ALL_LEDGERS || ledgerById.containsKey(settings.activeLedger)) {
        settings.activeLedger
    } else {
        ALL_LEDGERS
    }
