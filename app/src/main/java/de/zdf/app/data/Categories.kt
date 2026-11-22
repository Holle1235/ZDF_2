package de.zdf.app.data

data class Category(val key: String, val label: String, val color: Long, val icon: String)

object Categories {
    const val INCOME = "income"
    const val TRANSFER = "transfer"
    const val OTHER = "other"

    val all = listOf(
        Category(INCOME, "Einkommen", 0xFF16A34A, "💶"),
        Category("groceries", "Lebensmittel", 0xFF65A30D, "🛒"),
        Category("restaurants", "Restaurants & Lieferdienste", 0xFFEA580C, "🍽️"),
        Category("shopping", "Shopping", 0xFFDB2777, "🛍️"),
        Category("housing", "Wohnen & Miete", 0xFF7C3AED, "🏠"),
        Category("energy", "Strom, Gas & Nebenkosten", 0xFFCA8A04, "⚡"),
        Category("telecom", "Handy & Internet", 0xFF0891B2, "📱"),
        Category("subscriptions", "Abos & Streaming", 0xFFE11D48, "📺"),
        Category("insurance", "Versicherungen", 0xFF2563EB, "🛡️"),
        Category("mobility", "Mobilität", 0xFF0D9488, "🚗"),
        Category("travel", "Reisen", 0xFF0284C7, "✈️"),
        Category("health", "Gesundheit & Drogerie", 0xFFDC2626, "💊"),
        Category("leisure", "Freizeit & Sport", 0xFF9333EA, "🎟️"),
        Category("education", "Bildung", 0xFF4F46E5, "📚"),
        Category("cash", "Bargeld", 0xFF57534E, "🏧"),
        Category("donations", "Spenden & Geschenke", 0xFFF59E0B, "🎁"),
        Category("fees", "Steuern & Gebühren", 0xFF475569, "🧾"),
        Category("loans", "Kredite & Finanzierung", 0xFF9F1239, "🏦"),
        Category("savings", "Sparen & Investieren", 0xFF059669, "📈"),
        Category(TRANSFER, "Umbuchung (eigene Konten)", 0xFF94A3B8, "🔁"),
        Category(OTHER, "Sonstiges", 0xFF64748B, "❔"),
    )

    private val byKey = all.associateBy { it.key }
    fun get(key: String): Category = byKey[key] ?: byKey.getValue(OTHER)
}
