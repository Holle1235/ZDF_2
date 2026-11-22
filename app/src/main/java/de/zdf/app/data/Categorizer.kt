package de.zdf.app.data

/**
 * Regelbasierte Kategorisierung – läuft komplett lokal.
 * Eigene Regeln (aus "Immer so kategorisieren") haben Vorrang vor den eingebauten.
 */
object Categorizer {

    private val builtIn: List<Pair<String, List<String>>> = listOf(
        Categories.TRANSFER to listOf("umbuchung", "uebertrag", "übertrag", "eigenes konto", "eigene konten"),
        "savings" to listOf("trade republic", "scalable", "comdirect depot", "sparplan", "depot", "etf", "bausparkasse", "wertpapier", "sparkonto", "tagesgeld", "flatex", "traders place", "bitpanda", "coinbase"),
        Categories.INCOME to listOf("gehalt", "lohn", "bezüge", "bezuege", "entgelt", "kindergeld", "familienkasse", "rente", "arbeitsagentur", "bafög", "bafoeg", "elterngeld", "steuererstattung", "zinsen", "dividende"),
        "groceries" to listOf("rewe", "edeka", "aldi", "lidl", "kaufland", "netto", "penny", "norma", "tegut", "globus", "real ", "marktkauf", "denns", "alnatura", "bio company", "hit markt", "famila", "combi", "nahkauf", "flink", "getir", "picnic", "bäckerei", "baeckerei", "metzgerei"),
        "restaurants" to listOf("lieferando", "wolt", "uber eats", "mcdonald", "burger king", "kfc", "subway", "starbucks", "restaurant", "pizzeria", "döner", "doener", "cafe", "café", "bistro", "imbiss", "vapiano", "dominos", "domino's", "nordsee", "backwerk"),
        "subscriptions" to listOf("netflix", "spotify", "disney", "amazon prime", "prime video", "dazn", "sky deutschland", "wow tv", "apple.com/bill", "apple services", "itunes", "google play", "youtube", "audible", "rtl+", "joyn", "paramount", "chatgpt", "openai", "microsoft 365", "adobe", "dropbox", "icloud", "patreon", "twitch", "playstation", "xbox", "nintendo", "steam"),
        "telecom" to listOf("telekom", "vodafone", "o2 ", "telefonica", "1&1", "congstar", "freenet", "mobilcom", "aldi talk", "fraenk", "drillisch", "winsim", "pyur", "unitymedia", "netcologne", "m-net", "ewe tel"),
        "energy" to listOf("stadtwerke", "vattenfall", "e.on", "eon ", "enbw", "rwe", "eprimo", "yello", "tibber", "ostrom", "octopus energy", "lichtblick", "strom", "gas ", "rundfunk", "ard zdf", "beitragsservice", "wasser", "nebenkosten", "heizkosten"),
        "housing" to listOf("miete", "kaltmiete", "warmmiete", "hausverwaltung", "wohnungsbau", "vonovia", "deutsche wohnen", "leg immobilien", "wohnungsgenossenschaft", "hausgeld", "ikea", "obi", "hornbach", "bauhaus", "toom", "hagebau", "möbel", "moebel"),
        "insurance" to listOf("versicherung", "allianz", "huk", "ergo", "axa", "generali", "debeka", "signal iduna", "devk", "r+v", "gothaer", "zurich", "provinzial", "hanse merkur", "hansemerkur", "barmenia", "wgv", "cosmos direkt", "techniker krankenkasse", " aok", "barmer", "dak-gesundheit"),
        "mobility" to listOf("shell", "aral", "esso", "total", "jet ", "agip", "tankstelle", "deutsche bahn", "db vertrieb", "db fernverkehr", "bahn.de", "flixbus", "bvg", "mvg", "hvv", "rmv", "vrs", "kvb", "deutschlandticket", "uber", "bolt", "free now", "share now", "miles", "tier", "lime", "parken", "parkhaus", "easypark", "adac", "kfz-steuer", "werkstatt", "atu", "sixt"),
        "travel" to listOf("lufthansa", "eurowings", "ryanair", "easyjet", "condor", "booking.com", "airbnb", "expedia", "hotel", "check24 reise", "tui", "hostel", "ab-in-den-urlaub"),
        "health" to listOf("apotheke", "dm-drogerie", "dm drogerie", "dm fil", "rossmann", "müller", "mueller", "budnikowsky", "arzt", "zahnarzt", "praxis", "klinik", "krankenhaus", "optiker", "fielmann", "physio", "docmorris", "shop apotheke"),
        "leisure" to listOf("fitness", "mcfit", "urban sports", "fitx", "clever fit", "kino", "cinemaxx", "eventim", "ticketmaster", "verein", "theater", "museum", "zoo", "freizeitpark", "thalia", "hugendubel"),
        "education" to listOf("universität", "universitaet", "hochschule", "semesterbeitrag", "studierendenwerk", "volkshochschule", "udemy", "coursera", "schule", "kita", "kindergarten"),
        "cash" to listOf("geldautomat", "bargeldauszahlung", "auszahlung", "atm", "bargeld", "cash"),
        "donations" to listOf("spende", "unicef", "caritas", "diakonie", "wikimedia", "greenpeace", "ärzte ohne grenzen", "aerzte ohne grenzen", "brot für die welt", "gofundme", "betterplace"),
        "fees" to listOf("finanzamt", "kontoführung", "kontofuehrung", "entgelt kontof", "gebühr", "gebuehr", "steuer", "bußgeld", "bussgeld", "gez", "stadtkasse", "gemeinde", "zollamt"),
        "loans" to listOf("kredit", "darlehen", "tilgung", "rate ", "ratenkauf", "klarna rate", "santander", "targobank", "auxmoney", "smava", "barclays", "advanzia"),
        "shopping" to listOf("amazon", "amzn", "zalando", "otto", "ebay", "about you", "h&m", "zara", "primark", "c&a", "tk maxx", "mediamarkt", "media markt", "saturn", "apple store", "galeria", "decathlon", "douglas", "shein", "temu", "aliexpress", "etsy", "vinted", "kleinanzeigen", "tchibo", "action ", "depot ", "deichmann", "klarna"),
    )

    private val boundary = HashMap<String, Regex>()

    /** Kurze Begriffe (≤ 4 Zeichen) nur als ganzes Wort – sonst steckt z. B. "etf" in "Netflix". */
    private fun matches(text: String, key: String): Boolean {
        val k = key.trim()
        if (k.length > 4) return text.contains(key)
        val r = synchronized(boundary) { boundary.getOrPut(k) { Regex("(?<![\\p{L}\\d])" + Regex.escape(k) + "(?![\\p{L}\\d])") } }
        return r.containsMatchIn(text)
    }

    fun normalize(s: String): String = s.lowercase().replace(Regex("\\s+"), " ")

    /**
     * @param isPaypalAccount true wenn die Buchung auf einem PayPal-Konto liegt
     * @param hasPaypal true wenn ein PayPal-Konto verbunden ist (Bank→PayPal-Abbuchungen sind dann Umbuchungen)
     */
    fun categorize(
        counterparty: String,
        purpose: String,
        amountCents: Long,
        userRules: List<Rule>,
        isPaypalAccount: Boolean = false,
        hasPaypal: Boolean = false,
        code: String? = null,
    ): String {
        val text = " " + normalize("$counterparty $purpose") + " "
        val cp = normalize(counterparty)

        userRules.firstOrNull { it.pattern.isNotBlank() && text.contains(normalize(it.pattern)) }?.let { return it.category }

        if (isPaypalAccount && code != null) {
            // T03xx = Bankeinzahlung aufs PayPal-Konto, T04xx = Auszahlung aufs Bankkonto,
            // T02xx = Währungsumrechnung (Gegenbuchung)
            if (code.startsWith("T03") || code.startsWith("T04")) return Categories.TRANSFER
        }
        if (!isPaypalAccount && hasPaypal && (cp.contains("paypal") || text.contains(" paypal "))) {
            return Categories.TRANSFER
        }

        for ((cat, keys) in builtIn) {
            if (cat == Categories.INCOME && amountCents < 0) continue
            if (keys.any { matches(text, it) }) return cat
        }
        return if (amountCents > 0) Categories.INCOME else Categories.OTHER
    }
}
