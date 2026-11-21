package com.xern.xrnwal.data

object Seed {
    val expenseCategories = listOf(
        "Food" to "🍗", "Membership" to "🎫", "Trans" to "🚌", "Daily nes." to "🧴",
        "Snacks" to "🍬", "Gaming" to "🎮", "Digital" to "📷", "Drink" to "🍺",
        "Fun" to "🎬", "Service" to "💎", "Education" to "🎓", "Rent" to "🏠",
        "Health" to "💊", "Appliances" to "🧺", "Fitness" to "🏋️", "Clothing" to "👕",
        "Stationery" to "✏️", "Gift" to "🎁", "Secret" to "🧰", "Lending" to "🤝",
    )

    val incomeCategories = listOf(
        "Received" to "🥕", "Fun" to "🔥", "Salary" to "👛", "Side job" to "🏪",
        "Investment" to "🐖", "Bonus" to "🏆", "Dividend" to "🪙", "Rent" to "🏠",
        "Gift" to "🧧", "Borrowing" to "🤝", "Receipt" to "🧾", "Windfall" to "🍀",
    )

    data class AccountPreset(val name: String, val kind: Int, val icon: String, val color: Long, val custom: Boolean = false)

    val accountPresets = listOf(
        AccountPreset("PayPal", AccountKind.SAVINGS, "🅿️", 0xFF1F3A93),
        AccountPreset("Google Wallet", AccountKind.SAVINGS, "👛", 0xFF34A853),
        AccountPreset("Amazon Pay", AccountKind.SAVINGS, "📦", 0xFF232F3E),
        AccountPreset("Cash", AccountKind.SAVINGS, "💵", 0xFF4CAF50),
        AccountPreset("Apple Card", AccountKind.SAVINGS, "🍎", 0xFF8E8E93),
        AccountPreset("Bank Card", AccountKind.SAVINGS, "💳", 0xFF7C83FD, custom = true),
        AccountPreset("Other", AccountKind.SAVINGS, "🪙", 0xFFFFC107, custom = true),
        AccountPreset("Apple Card", AccountKind.CREDIT, "🍎", 0xFF8E8E93),
        AccountPreset("Debt", AccountKind.CREDIT, "📉", 0xFFFF9800),
        AccountPreset("Credit Card", AccountKind.CREDIT, "💳", 0xFF7C83FD, custom = true),
        AccountPreset("Other", AccountKind.CREDIT, "🪙", 0xFF8BC34A, custom = true),
    )

    val tagColors: List<Long> = listOf(
        0xFFFFB300, 0xFFFF7043, 0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
        0xFF5C6BC0, 0xFF42A5F5, 0xFF26A69A, 0xFF66BB6A, 0xFF9CCC65, 0xFF8D6E63,
    )

    val ledgerCovers: List<Pair<String, Long>> = listOf(
        "📒" to 0xFFE0BC9C, "⛄" to 0xFF9FCBFF, "🌿" to 0xFFA8D8A0, "🌸" to 0xFFF6B8C8,
        "🌊" to 0xFF7FD3D8, "🌙" to 0xFF8D86C9,
    )

    val avatars = "🦊 🐻 🐼 🐨 🐯 🦁 🐸 🐵 🐧 🐢 🦉 🐙 🍎 🍑 🍋 🌵".split(" ")

    /** Best icon for a category name met during import, falling back to a neutral one. */
    fun iconFor(name: String, type: Int): String {
        val list = if (type == BillType.INCOME) incomeCategories + expenseCategories else expenseCategories + incomeCategories
        return list.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second ?: "⭐"
    }
}
