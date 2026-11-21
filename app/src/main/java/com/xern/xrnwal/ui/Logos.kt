package com.xern.xrnwal.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xern.xrnwal.R

private val LOGOS = listOf(
    "commonwealth" to R.drawable.logo_commbank,
    "commbank" to R.drawable.logo_commbank,
    "westpac" to R.drawable.logo_westpac,
    "revolut" to R.drawable.logo_revolut,
    "vietcom" to R.drawable.logo_vietcombank,
    "paypal" to R.drawable.logo_paypal,
    "google wallet" to R.drawable.logo_googlewallet,
    "amazon pay" to R.drawable.logo_amazonpay,
    "apple" to R.drawable.logo_applecard,
    "invest" to R.drawable.logo_investment,
)

private const val CASH_ICON = "💵"
private const val CASH_COLOR = 0xFF4CAF50

/**
 * The logo for an account, found from its name. A name is enough, so imported and restored accounts get
 * theirs with nothing to set up, and a new account gets one as soon as it is named.
 */
@DrawableRes
fun accountLogo(name: String): Int? {
    val lower = name.lowercase()
    return LOGOS.firstOrNull { it.first in lower }?.second
}

@Composable
fun AccountBubble(name: String, icon: String, color: Long, size: Dp) {
    val logo = accountLogo(name)
    when {
        logo != null -> Image(
            painterResource(logo),
            null,
            Modifier
                .size(size)
                .clip(CircleShape)
                // A white logo would otherwise melt into a white card.
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        name.trim().equals("cash", ignoreCase = true) -> Bubble(CASH_ICON, size, Color(CASH_COLOR))
        else -> Bubble(icon, size, Color(color))
    }
}
