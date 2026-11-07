package com.xern.xrnwallet.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sanitizer
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Sanitizer
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeRepairService
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Sanitizer
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.twotone.AccountBalance
import androidx.compose.material.icons.twotone.AccountBalanceWallet
import androidx.compose.material.icons.twotone.AutoStories
import androidx.compose.material.icons.twotone.BeachAccess
import androidx.compose.material.icons.twotone.Bed
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Cake
import androidx.compose.material.icons.twotone.CardGiftcard
import androidx.compose.material.icons.twotone.CardMembership
import androidx.compose.material.icons.twotone.Casino
import androidx.compose.material.icons.twotone.Chair
import androidx.compose.material.icons.twotone.Checkroom
import androidx.compose.material.icons.twotone.ChildCare
import androidx.compose.material.icons.twotone.CleaningServices
import androidx.compose.material.icons.twotone.ContentCut
import androidx.compose.material.icons.twotone.Cookie
import androidx.compose.material.icons.twotone.CreditCard
import androidx.compose.material.icons.twotone.Diamond
import androidx.compose.material.icons.twotone.DirectionsBoat
import androidx.compose.material.icons.twotone.DirectionsBus
import androidx.compose.material.icons.twotone.DirectionsCar
import androidx.compose.material.icons.twotone.Eco
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.EmojiEvents
import androidx.compose.material.icons.twotone.EmojiFoodBeverage
import androidx.compose.material.icons.twotone.FaceRetouchingNatural
import androidx.compose.material.icons.twotone.Fastfood
import androidx.compose.material.icons.twotone.FitnessCenter
import androidx.compose.material.icons.twotone.Flight
import androidx.compose.material.icons.twotone.Handshake
import androidx.compose.material.icons.twotone.Hiking
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.HomeRepairService
import androidx.compose.material.icons.twotone.Hotel
import androidx.compose.material.icons.twotone.Insights
import androidx.compose.material.icons.twotone.Inventory2
import androidx.compose.material.icons.twotone.Lightbulb
import androidx.compose.material.icons.twotone.LocalCafe
import androidx.compose.material.icons.twotone.LocalDrink
import androidx.compose.material.icons.twotone.LocalFireDepartment
import androidx.compose.material.icons.twotone.LocalFlorist
import androidx.compose.material.icons.twotone.LocalGasStation
import androidx.compose.material.icons.twotone.LocalLaundryService
import androidx.compose.material.icons.twotone.LocalParking
import androidx.compose.material.icons.twotone.LocalPizza
import androidx.compose.material.icons.twotone.LocalTaxi
import androidx.compose.material.icons.twotone.Luggage
import androidx.compose.material.icons.twotone.LunchDining
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Medication
import androidx.compose.material.icons.twotone.MonetizationOn
import androidx.compose.material.icons.twotone.MoneyOff
import androidx.compose.material.icons.twotone.Movie
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Paid
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Payments
import androidx.compose.material.icons.twotone.PedalBike
import androidx.compose.material.icons.twotone.Pets
import androidx.compose.material.icons.twotone.PhotoCamera
import androidx.compose.material.icons.twotone.Power
import androidx.compose.material.icons.twotone.RamenDining
import androidx.compose.material.icons.twotone.Receipt
import androidx.compose.material.icons.twotone.Redeem
import androidx.compose.material.icons.twotone.Restaurant
import androidx.compose.material.icons.twotone.Sanitizer
import androidx.compose.material.icons.twotone.Savings
import androidx.compose.material.icons.twotone.School
import androidx.compose.material.icons.twotone.SetMeal
import androidx.compose.material.icons.twotone.ShoppingBag
import androidx.compose.material.icons.twotone.ShoppingCart
import androidx.compose.material.icons.twotone.Shower
import androidx.compose.material.icons.twotone.SouthWest
import androidx.compose.material.icons.twotone.SportsBar
import androidx.compose.material.icons.twotone.SportsEsports
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material.icons.twotone.Storefront
import androidx.compose.material.icons.twotone.Subway
import androidx.compose.material.icons.twotone.Terrain
import androidx.compose.material.icons.twotone.Toys
import androidx.compose.material.icons.twotone.TwoWheeler
import androidx.compose.material.icons.twotone.WineBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's category and account artwork.
 *
 * An icon is stored as a plain string, so bills, backups and sync need no special handling. An emoji
 * on its own means the Style 1 drawing of that subject, "s2:" to "s4:" in front of it means another
 * drawing style, and "e:" keeps the emoji itself. A string with no drawing shows as text.
 */
object Art {
    const val STYLES = 4
    private const val EMOJI = "e:"

    private fun subject(icon: String): String =
        (if (icon.length > 3 && icon[0] == 's' && icon[2] == ':') icon.drop(3) else icon).replace("\uFE0F", "")

    private fun style(icon: String): Int =
        if (icon.length > 3 && icon[0] == 's' && icon[2] == ':') icon[1].digitToIntOrNull() ?: 1 else 1

    fun isEmoji(icon: String): Boolean = icon.startsWith(EMOJI)

    /** True when both name the same icon, whichever way their emoji were typed. */
    fun same(first: String, second: String): Boolean = first.replace("\uFE0F", "") == second.replace("\uFE0F", "")

    /** The text to show when [icon] has no drawing. */
    fun text(icon: String): String = icon.removePrefix(EMOJI)

    fun vector(icon: String): ImageVector? {
        if (isEmoji(icon)) return null
        val set = when (style(icon)) {
            2 -> outlined
            3 -> twoTone
            4 -> rounded
            else -> filled
        }
        return set[subject(icon)]
    }

    fun tint(icon: String): Color? = tints[subject(icon)]?.let { Color(it) }

    /** Icons offered on a Style tab, numbered from 1. */
    fun choices(style: Int): List<String> = offered.map { if (style == 1) it else "s$style:$it" }

    /** Icons offered on the emoji tab. */
    val emojiChoices: List<String> get() = offered.map { EMOJI + it }

    /** The picker tab that shows [icon]: its style less one, or the emoji tab after the styles. */
    fun tabOf(icon: String): Int = if (isEmoji(icon)) STYLES else style(icon) - 1

    private val offered = listOf(
        "🍗", "🍔", "🍕", "🍜", "🍣", "🍱", "🥗",
        "🥕", "🍬", "🍰", "🍺", "☕", "🧃", "🥤", "🍷",
        "🏠", "🛋", "🧺", "🧴", "🧹", "🔌", "💡", "🔧",
        "🛒", "🧾", "🪴", "🐾", "👶", "🧸", "🚿", "🛏",
        "🚌", "🚇", "🚕", "🚗", "⛽", "✈", "🚲", "🛵",
        "🏨", "🗺", "🧳", "🚢", "🏖", "⛰", "🎫", "🅿",
        "💊", "🏋", "🎓", "📚", "✏", "💈", "👕", "👟",
        "👜", "💄", "🎁", "🎬", "🎮", "🎵", "📷", "🎨",
        "💰", "👛", "💳", "🏦", "🪙", "📈", "🐖",
        "🏆", "🧧", "🤝", "🍀", "🔥", "💎", "🧰", "📦",
        "⭐", "💵", "📉", "🏪",
    )

    private val tints: Map<String, Long> = mapOf(
        "🍗" to 0xFFF2994A,
        "🍔" to 0xFFF2994A,
        "🍕" to 0xFFF2994A,
        "🍜" to 0xFFF2994A,
        "🍣" to 0xFFF2994A,
        "🍱" to 0xFFF2994A,
        "🥗" to 0xFF7CB342,
        "🍎" to 0xFF5AA9E6,
        "🥕" to 0xFF7CB342,
        "🍬" to 0xFFEC6FA5,
        "🍰" to 0xFFEC6FA5,
        "🍺" to 0xFFE6B422,
        "☕" to 0xFFE6B422,
        "🧃" to 0xFFE6B422,
        "🥤" to 0xFFE6B422,
        "🍷" to 0xFFE6B422,
        "🏠" to 0xFF5AA9E6,
        "🛋" to 0xFF5AA9E6,
        "🧺" to 0xFF5AA9E6,
        "🧴" to 0xFF5AA9E6,
        "🧹" to 0xFF5AA9E6,
        "🔌" to 0xFF5AA9E6,
        "💡" to 0xFFE6B422,
        "🔧" to 0xFF9AA5B1,
        "🛒" to 0xFFF2994A,
        "🧾" to 0xFF9AA5B1,
        "🪴" to 0xFF7CB342,
        "🐾" to 0xFFF2994A,
        "👶" to 0xFFEC6FA5,
        "🧸" to 0xFFEC6FA5,
        "🚿" to 0xFF5AA9E6,
        "🛏" to 0xFF5AA9E6,
        "🚌" to 0xFF35B8A6,
        "🚇" to 0xFF35B8A6,
        "🚕" to 0xFF35B8A6,
        "🚗" to 0xFF35B8A6,
        "⛽" to 0xFF35B8A6,
        "✈" to 0xFF35B8A6,
        "🚲" to 0xFF35B8A6,
        "🛵" to 0xFF35B8A6,
        "🏨" to 0xFF35B8A6,
        "🗺" to 0xFF35B8A6,
        "🧳" to 0xFF35B8A6,
        "🚢" to 0xFF35B8A6,
        "🏖" to 0xFF35B8A6,
        "⛰" to 0xFF35B8A6,
        "🎫" to 0xFFB388EB,
        "🅿" to 0xFF5AA9E6,
        "💊" to 0xFFEF6C6C,
        "🏋" to 0xFFEF6C6C,
        "🎓" to 0xFFB388EB,
        "📚" to 0xFFB388EB,
        "✏" to 0xFFB388EB,
        "💈" to 0xFFB388EB,
        "👕" to 0xFFEC6FA5,
        "👟" to 0xFFEC6FA5,
        "👜" to 0xFFEC6FA5,
        "💄" to 0xFFEC6FA5,
        "🎁" to 0xFFEF6C6C,
        "🎬" to 0xFFB388EB,
        "🎮" to 0xFFB388EB,
        "🎵" to 0xFFB388EB,
        "📷" to 0xFFB388EB,
        "🎨" to 0xFFB388EB,
        "💰" to 0xFF7CB342,
        "👛" to 0xFF7CB342,
        "💳" to 0xFF5AA9E6,
        "🏦" to 0xFF7CB342,
        "🪙" to 0xFFE6B422,
        "📈" to 0xFF7CB342,
        "🐖" to 0xFFEC6FA5,
        "🐷" to 0xFFEC6FA5,
        "🏆" to 0xFFE6B422,
        "🧧" to 0xFFEF6C6C,
        "🤝" to 0xFFF2994A,
        "🍀" to 0xFF7CB342,
        "🔥" to 0xFFEF6C6C,
        "💎" to 0xFF5AA9E6,
        "🧰" to 0xFF9AA5B1,
        "📦" to 0xFFF2994A,
        "⭐" to 0xFFE6B422,
        "💵" to 0xFF7CB342,
        "📉" to 0xFFEF6C6C,
        "🏪" to 0xFFF2994A,
    )

    private val filled: Map<String, ImageVector> by lazy {
        mapOf(
            "🍗" to Icons.Filled.Restaurant,
            "🍔" to Icons.Filled.LunchDining,
            "🍕" to Icons.Filled.LocalPizza,
            "🍜" to Icons.Filled.RamenDining,
            "🍣" to Icons.Filled.SetMeal,
            "🍱" to Icons.Filled.Fastfood,
            "🥗" to Icons.Filled.Eco,
            "🍎" to Icons.Filled.CreditCard,
            "🥕" to Icons.Filled.SouthWest,
            "🍬" to Icons.Filled.Cookie,
            "🍰" to Icons.Filled.Cake,
            "🍺" to Icons.Filled.SportsBar,
            "☕" to Icons.Filled.LocalCafe,
            "🧃" to Icons.Filled.EmojiFoodBeverage,
            "🥤" to Icons.Filled.LocalDrink,
            "🍷" to Icons.Filled.WineBar,
            "🏠" to Icons.Filled.Home,
            "🛋" to Icons.Filled.Chair,
            "🧺" to Icons.Filled.LocalLaundryService,
            "🧴" to Icons.Filled.Sanitizer,
            "🧹" to Icons.Filled.CleaningServices,
            "🔌" to Icons.Filled.Power,
            "💡" to Icons.Filled.Lightbulb,
            "🔧" to Icons.Filled.Build,
            "🛒" to Icons.Filled.ShoppingCart,
            "🧾" to Icons.Filled.Receipt,
            "🪴" to Icons.Filled.LocalFlorist,
            "🐾" to Icons.Filled.Pets,
            "👶" to Icons.Filled.ChildCare,
            "🧸" to Icons.Filled.Toys,
            "🚿" to Icons.Filled.Shower,
            "🛏" to Icons.Filled.Bed,
            "🚌" to Icons.Filled.DirectionsBus,
            "🚇" to Icons.Filled.Subway,
            "🚕" to Icons.Filled.LocalTaxi,
            "🚗" to Icons.Filled.DirectionsCar,
            "⛽" to Icons.Filled.LocalGasStation,
            "✈" to Icons.Filled.Flight,
            "🚲" to Icons.Filled.PedalBike,
            "🛵" to Icons.Filled.TwoWheeler,
            "🏨" to Icons.Filled.Hotel,
            "🗺" to Icons.Filled.Map,
            "🧳" to Icons.Filled.Luggage,
            "🚢" to Icons.Filled.DirectionsBoat,
            "🏖" to Icons.Filled.BeachAccess,
            "⛰" to Icons.Filled.Terrain,
            "🎫" to Icons.Filled.CardMembership,
            "🅿" to Icons.Filled.LocalParking,
            "💊" to Icons.Filled.Medication,
            "🏋" to Icons.Filled.FitnessCenter,
            "🎓" to Icons.Filled.School,
            "📚" to Icons.Filled.AutoStories,
            "✏" to Icons.Filled.Edit,
            "💈" to Icons.Filled.ContentCut,
            "👕" to Icons.Filled.Checkroom,
            "👟" to Icons.Filled.Hiking,
            "👜" to Icons.Filled.ShoppingBag,
            "💄" to Icons.Filled.FaceRetouchingNatural,
            "🎁" to Icons.Filled.CardGiftcard,
            "🎬" to Icons.Filled.Movie,
            "🎮" to Icons.Filled.SportsEsports,
            "🎵" to Icons.Filled.MusicNote,
            "📷" to Icons.Filled.PhotoCamera,
            "🎨" to Icons.Filled.Palette,
            "💰" to Icons.Filled.Paid,
            "👛" to Icons.Filled.AccountBalanceWallet,
            "💳" to Icons.Filled.CreditCard,
            "🏦" to Icons.Filled.AccountBalance,
            "🪙" to Icons.Filled.MonetizationOn,
            "📈" to Icons.Filled.Insights,
            "🐖" to Icons.Filled.Savings,
            "🐷" to Icons.Filled.Savings,
            "🏆" to Icons.Filled.EmojiEvents,
            "🧧" to Icons.Filled.Redeem,
            "🤝" to Icons.Filled.Handshake,
            "🍀" to Icons.Filled.Casino,
            "🔥" to Icons.Filled.LocalFireDepartment,
            "💎" to Icons.Filled.Diamond,
            "🧰" to Icons.Filled.HomeRepairService,
            "📦" to Icons.Filled.Inventory2,
            "⭐" to Icons.Filled.Star,
            "💵" to Icons.Filled.Payments,
            "📉" to Icons.Filled.MoneyOff,
            "🏪" to Icons.Filled.Storefront,
        )
    }

    private val outlined: Map<String, ImageVector> by lazy {
        mapOf(
            "🍗" to Icons.Outlined.Restaurant,
            "🍔" to Icons.Outlined.LunchDining,
            "🍕" to Icons.Outlined.LocalPizza,
            "🍜" to Icons.Outlined.RamenDining,
            "🍣" to Icons.Outlined.SetMeal,
            "🍱" to Icons.Outlined.Fastfood,
            "🥗" to Icons.Outlined.Eco,
            "🍎" to Icons.Outlined.CreditCard,
            "🥕" to Icons.Outlined.SouthWest,
            "🍬" to Icons.Outlined.Cookie,
            "🍰" to Icons.Outlined.Cake,
            "🍺" to Icons.Outlined.SportsBar,
            "☕" to Icons.Outlined.LocalCafe,
            "🧃" to Icons.Outlined.EmojiFoodBeverage,
            "🥤" to Icons.Outlined.LocalDrink,
            "🍷" to Icons.Outlined.WineBar,
            "🏠" to Icons.Outlined.Home,
            "🛋" to Icons.Outlined.Chair,
            "🧺" to Icons.Outlined.LocalLaundryService,
            "🧴" to Icons.Outlined.Sanitizer,
            "🧹" to Icons.Outlined.CleaningServices,
            "🔌" to Icons.Outlined.Power,
            "💡" to Icons.Outlined.Lightbulb,
            "🔧" to Icons.Outlined.Build,
            "🛒" to Icons.Outlined.ShoppingCart,
            "🧾" to Icons.Outlined.Receipt,
            "🪴" to Icons.Outlined.LocalFlorist,
            "🐾" to Icons.Outlined.Pets,
            "👶" to Icons.Outlined.ChildCare,
            "🧸" to Icons.Outlined.Toys,
            "🚿" to Icons.Outlined.Shower,
            "🛏" to Icons.Outlined.Bed,
            "🚌" to Icons.Outlined.DirectionsBus,
            "🚇" to Icons.Outlined.Subway,
            "🚕" to Icons.Outlined.LocalTaxi,
            "🚗" to Icons.Outlined.DirectionsCar,
            "⛽" to Icons.Outlined.LocalGasStation,
            "✈" to Icons.Outlined.Flight,
            "🚲" to Icons.Outlined.PedalBike,
            "🛵" to Icons.Outlined.TwoWheeler,
            "🏨" to Icons.Outlined.Hotel,
            "🗺" to Icons.Outlined.Map,
            "🧳" to Icons.Outlined.Luggage,
            "🚢" to Icons.Outlined.DirectionsBoat,
            "🏖" to Icons.Outlined.BeachAccess,
            "⛰" to Icons.Outlined.Terrain,
            "🎫" to Icons.Outlined.CardMembership,
            "🅿" to Icons.Outlined.LocalParking,
            "💊" to Icons.Outlined.Medication,
            "🏋" to Icons.Outlined.FitnessCenter,
            "🎓" to Icons.Outlined.School,
            "📚" to Icons.Outlined.AutoStories,
            "✏" to Icons.Outlined.Edit,
            "💈" to Icons.Outlined.ContentCut,
            "👕" to Icons.Outlined.Checkroom,
            "👟" to Icons.Outlined.Hiking,
            "👜" to Icons.Outlined.ShoppingBag,
            "💄" to Icons.Outlined.FaceRetouchingNatural,
            "🎁" to Icons.Outlined.CardGiftcard,
            "🎬" to Icons.Outlined.Movie,
            "🎮" to Icons.Outlined.SportsEsports,
            "🎵" to Icons.Outlined.MusicNote,
            "📷" to Icons.Outlined.PhotoCamera,
            "🎨" to Icons.Outlined.Palette,
            "💰" to Icons.Outlined.Paid,
            "👛" to Icons.Outlined.AccountBalanceWallet,
            "💳" to Icons.Outlined.CreditCard,
            "🏦" to Icons.Outlined.AccountBalance,
            "🪙" to Icons.Outlined.MonetizationOn,
            "📈" to Icons.Outlined.Insights,
            "🐖" to Icons.Outlined.Savings,
            "🐷" to Icons.Outlined.Savings,
            "🏆" to Icons.Outlined.EmojiEvents,
            "🧧" to Icons.Outlined.Redeem,
            "🤝" to Icons.Outlined.Handshake,
            "🍀" to Icons.Outlined.Casino,
            "🔥" to Icons.Outlined.LocalFireDepartment,
            "💎" to Icons.Outlined.Diamond,
            "🧰" to Icons.Outlined.HomeRepairService,
            "📦" to Icons.Outlined.Inventory2,
            "⭐" to Icons.Outlined.Star,
            "💵" to Icons.Outlined.Payments,
            "📉" to Icons.Outlined.MoneyOff,
            "🏪" to Icons.Outlined.Storefront,
        )
    }

    private val twoTone: Map<String, ImageVector> by lazy {
        mapOf(
            "🍗" to Icons.TwoTone.Restaurant,
            "🍔" to Icons.TwoTone.LunchDining,
            "🍕" to Icons.TwoTone.LocalPizza,
            "🍜" to Icons.TwoTone.RamenDining,
            "🍣" to Icons.TwoTone.SetMeal,
            "🍱" to Icons.TwoTone.Fastfood,
            "🥗" to Icons.TwoTone.Eco,
            "🍎" to Icons.TwoTone.CreditCard,
            "🥕" to Icons.TwoTone.SouthWest,
            "🍬" to Icons.TwoTone.Cookie,
            "🍰" to Icons.TwoTone.Cake,
            "🍺" to Icons.TwoTone.SportsBar,
            "☕" to Icons.TwoTone.LocalCafe,
            "🧃" to Icons.TwoTone.EmojiFoodBeverage,
            "🥤" to Icons.TwoTone.LocalDrink,
            "🍷" to Icons.TwoTone.WineBar,
            "🏠" to Icons.TwoTone.Home,
            "🛋" to Icons.TwoTone.Chair,
            "🧺" to Icons.TwoTone.LocalLaundryService,
            "🧴" to Icons.TwoTone.Sanitizer,
            "🧹" to Icons.TwoTone.CleaningServices,
            "🔌" to Icons.TwoTone.Power,
            "💡" to Icons.TwoTone.Lightbulb,
            "🔧" to Icons.TwoTone.Build,
            "🛒" to Icons.TwoTone.ShoppingCart,
            "🧾" to Icons.TwoTone.Receipt,
            "🪴" to Icons.TwoTone.LocalFlorist,
            "🐾" to Icons.TwoTone.Pets,
            "👶" to Icons.TwoTone.ChildCare,
            "🧸" to Icons.TwoTone.Toys,
            "🚿" to Icons.TwoTone.Shower,
            "🛏" to Icons.TwoTone.Bed,
            "🚌" to Icons.TwoTone.DirectionsBus,
            "🚇" to Icons.TwoTone.Subway,
            "🚕" to Icons.TwoTone.LocalTaxi,
            "🚗" to Icons.TwoTone.DirectionsCar,
            "⛽" to Icons.TwoTone.LocalGasStation,
            "✈" to Icons.TwoTone.Flight,
            "🚲" to Icons.TwoTone.PedalBike,
            "🛵" to Icons.TwoTone.TwoWheeler,
            "🏨" to Icons.TwoTone.Hotel,
            "🗺" to Icons.TwoTone.Map,
            "🧳" to Icons.TwoTone.Luggage,
            "🚢" to Icons.TwoTone.DirectionsBoat,
            "🏖" to Icons.TwoTone.BeachAccess,
            "⛰" to Icons.TwoTone.Terrain,
            "🎫" to Icons.TwoTone.CardMembership,
            "🅿" to Icons.TwoTone.LocalParking,
            "💊" to Icons.TwoTone.Medication,
            "🏋" to Icons.TwoTone.FitnessCenter,
            "🎓" to Icons.TwoTone.School,
            "📚" to Icons.TwoTone.AutoStories,
            "✏" to Icons.TwoTone.Edit,
            "💈" to Icons.TwoTone.ContentCut,
            "👕" to Icons.TwoTone.Checkroom,
            "👟" to Icons.TwoTone.Hiking,
            "👜" to Icons.TwoTone.ShoppingBag,
            "💄" to Icons.TwoTone.FaceRetouchingNatural,
            "🎁" to Icons.TwoTone.CardGiftcard,
            "🎬" to Icons.TwoTone.Movie,
            "🎮" to Icons.TwoTone.SportsEsports,
            "🎵" to Icons.TwoTone.MusicNote,
            "📷" to Icons.TwoTone.PhotoCamera,
            "🎨" to Icons.TwoTone.Palette,
            "💰" to Icons.TwoTone.Paid,
            "👛" to Icons.TwoTone.AccountBalanceWallet,
            "💳" to Icons.TwoTone.CreditCard,
            "🏦" to Icons.TwoTone.AccountBalance,
            "🪙" to Icons.TwoTone.MonetizationOn,
            "📈" to Icons.TwoTone.Insights,
            "🐖" to Icons.TwoTone.Savings,
            "🐷" to Icons.TwoTone.Savings,
            "🏆" to Icons.TwoTone.EmojiEvents,
            "🧧" to Icons.TwoTone.Redeem,
            "🤝" to Icons.TwoTone.Handshake,
            "🍀" to Icons.TwoTone.Casino,
            "🔥" to Icons.TwoTone.LocalFireDepartment,
            "💎" to Icons.TwoTone.Diamond,
            "🧰" to Icons.TwoTone.HomeRepairService,
            "📦" to Icons.TwoTone.Inventory2,
            "⭐" to Icons.TwoTone.Star,
            "💵" to Icons.TwoTone.Payments,
            "📉" to Icons.TwoTone.MoneyOff,
            "🏪" to Icons.TwoTone.Storefront,
        )
    }

    private val rounded: Map<String, ImageVector> by lazy {
        mapOf(
            "🍗" to Icons.Rounded.Restaurant,
            "🍔" to Icons.Rounded.LunchDining,
            "🍕" to Icons.Rounded.LocalPizza,
            "🍜" to Icons.Rounded.RamenDining,
            "🍣" to Icons.Rounded.SetMeal,
            "🍱" to Icons.Rounded.Fastfood,
            "🥗" to Icons.Rounded.Eco,
            "🍎" to Icons.Rounded.CreditCard,
            "🥕" to Icons.Rounded.SouthWest,
            "🍬" to Icons.Rounded.Cookie,
            "🍰" to Icons.Rounded.Cake,
            "🍺" to Icons.Rounded.SportsBar,
            "☕" to Icons.Rounded.LocalCafe,
            "🧃" to Icons.Rounded.EmojiFoodBeverage,
            "🥤" to Icons.Rounded.LocalDrink,
            "🍷" to Icons.Rounded.WineBar,
            "🏠" to Icons.Rounded.Home,
            "🛋" to Icons.Rounded.Chair,
            "🧺" to Icons.Rounded.LocalLaundryService,
            "🧴" to Icons.Rounded.Sanitizer,
            "🧹" to Icons.Rounded.CleaningServices,
            "🔌" to Icons.Rounded.Power,
            "💡" to Icons.Rounded.Lightbulb,
            "🔧" to Icons.Rounded.Build,
            "🛒" to Icons.Rounded.ShoppingCart,
            "🧾" to Icons.Rounded.Receipt,
            "🪴" to Icons.Rounded.LocalFlorist,
            "🐾" to Icons.Rounded.Pets,
            "👶" to Icons.Rounded.ChildCare,
            "🧸" to Icons.Rounded.Toys,
            "🚿" to Icons.Rounded.Shower,
            "🛏" to Icons.Rounded.Bed,
            "🚌" to Icons.Rounded.DirectionsBus,
            "🚇" to Icons.Rounded.Subway,
            "🚕" to Icons.Rounded.LocalTaxi,
            "🚗" to Icons.Rounded.DirectionsCar,
            "⛽" to Icons.Rounded.LocalGasStation,
            "✈" to Icons.Rounded.Flight,
            "🚲" to Icons.Rounded.PedalBike,
            "🛵" to Icons.Rounded.TwoWheeler,
            "🏨" to Icons.Rounded.Hotel,
            "🗺" to Icons.Rounded.Map,
            "🧳" to Icons.Rounded.Luggage,
            "🚢" to Icons.Rounded.DirectionsBoat,
            "🏖" to Icons.Rounded.BeachAccess,
            "⛰" to Icons.Rounded.Terrain,
            "🎫" to Icons.Rounded.CardMembership,
            "🅿" to Icons.Rounded.LocalParking,
            "💊" to Icons.Rounded.Medication,
            "🏋" to Icons.Rounded.FitnessCenter,
            "🎓" to Icons.Rounded.School,
            "📚" to Icons.Rounded.AutoStories,
            "✏" to Icons.Rounded.Edit,
            "💈" to Icons.Rounded.ContentCut,
            "👕" to Icons.Rounded.Checkroom,
            "👟" to Icons.Rounded.Hiking,
            "👜" to Icons.Rounded.ShoppingBag,
            "💄" to Icons.Rounded.FaceRetouchingNatural,
            "🎁" to Icons.Rounded.CardGiftcard,
            "🎬" to Icons.Rounded.Movie,
            "🎮" to Icons.Rounded.SportsEsports,
            "🎵" to Icons.Rounded.MusicNote,
            "📷" to Icons.Rounded.PhotoCamera,
            "🎨" to Icons.Rounded.Palette,
            "💰" to Icons.Rounded.Paid,
            "👛" to Icons.Rounded.AccountBalanceWallet,
            "💳" to Icons.Rounded.CreditCard,
            "🏦" to Icons.Rounded.AccountBalance,
            "🪙" to Icons.Rounded.MonetizationOn,
            "📈" to Icons.Rounded.Insights,
            "🐖" to Icons.Rounded.Savings,
            "🐷" to Icons.Rounded.Savings,
            "🏆" to Icons.Rounded.EmojiEvents,
            "🧧" to Icons.Rounded.Redeem,
            "🤝" to Icons.Rounded.Handshake,
            "🍀" to Icons.Rounded.Casino,
            "🔥" to Icons.Rounded.LocalFireDepartment,
            "💎" to Icons.Rounded.Diamond,
            "🧰" to Icons.Rounded.HomeRepairService,
            "📦" to Icons.Rounded.Inventory2,
            "⭐" to Icons.Rounded.Star,
            "💵" to Icons.Rounded.Payments,
            "📉" to Icons.Rounded.MoneyOff,
            "🏪" to Icons.Rounded.Storefront,
        )
    }
}
