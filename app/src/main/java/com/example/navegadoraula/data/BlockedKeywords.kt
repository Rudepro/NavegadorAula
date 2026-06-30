package com.example.navegadoraula.data

/**
 * BlockedKeywords
 *
 * Repositorio centralizado de palabras clave prohibidas clasificadas por categoría.
 * Se utiliza en dos contextos:
 *   1. Análisis de URL (antes de navegar).
 *   2. Análisis de contenido HTML y metadatos (después de cargar la página).
 *
 * Diseño:
 *  - Cada categoría tiene su propia lista para facilitar mantenimiento.
 *  - Los umbrales por categoría son configurables (ver [CategoryThreshold]).
 *  - Se normalizan las palabras a minúsculas para comparación insensible a mayúsculas.
 */
object BlockedKeywords {

    /**
     * Umbral de detección por categoría en el análisis de contenido HTML.
     * Si el contenido supera este número de coincidencias, la página se bloquea.
     */
    data class CategoryThreshold(
        val name: String,
        val keywords: Set<String>,
        val threshold: Int
    )

    // =========================================================================
    // PALABRAS CLAVE — CONTENIDO SEXUAL / PORNOGRAFÍA
    // =========================================================================
    private val sexualKeywords: Set<String> = setOf(
        "porn", "porno", "pornography", "pornographic", "xxx", "adult", "18+", "sex", "sexo",
        "nude", "desnudo", "desnuda", "cam", "cams", "livecam", "webcam", "camgirl", "camlive",
        "escort", "escorts", "dating", "hookup", "milf", "hentai", "onlyfans", "fansly",
        "blowjob", "anal", "hardcore", "fetish", "erotic", "erótico", "hot girls", "hot women",
        "live sex", "videochat", "private chat", "adult chat", "strip", "stripchat", "sexcam",
        "premium content", "adult content", "adult video", "adult film", "sex video", "sex clip",
        "naked", "nudity", "erotica", "explicit", "explicit content", "anime porn", "cartoon porn",
        "livejasmin", "chaturbate", "webcam sex", "bdsm", "bondage", "masturbation", "masturbate",
        "handjob", "incest", "taboo sex", "escort service", "prostitution", "strip club", "stripclub",
        "porn site", "free porn", "watch porn", "pornografia", "video sexual", "sexo explícito",
        "sexo gratis", "ver porno"
    )

    // =========================================================================
    // PALABRAS CLAVE — VIOLENCIA EXTREMA
    // =========================================================================
    private val violenceKeywords: Set<String> = setOf(
        "gore", "gory", "beheading", "torture",
        "murder video", "killing video", "death footage",
        "brutal killing", "execution video",
        "real death", "real murder",
        "snuff film", "snuff video",
        "gore content", "graphic violence",
        "decapitation", "mutilation",
        "violencia extrema", "ejecución en vivo",
        "decapitación", "tortura video"
    )

    // =========================================================================
    // PALABRAS CLAVE — APUESTAS
    // =========================================================================
    private val gamblingKeywords: Set<String> = setOf(
        "online casino", "online gambling", "bet online",
        "sports betting", "live betting", "place your bet",
        "poker online", "blackjack online", "roulette online",
        "slot machine", "free spins", "welcome bonus casino",
        "apuestas en línea", "casino online", "apuesta deportiva",
        "tragamonedas", "poker online", "ruleta online",
        "bono de casino", "jackpot"
    )

    // =========================================================================
    // PALABRAS CLAVE — DROGAS / SUSTANCIAS ILEGALES
    // =========================================================================
    private val drugKeywords: Set<String> = setOf(
        "buy drugs online", "order drugs", "purchase narcotics",
        "cocaine for sale", "heroin for sale", "meth for sale",
        "buy marijuana online", "weed delivery",
        "darknet drugs", "dark web drugs",
        "comprar drogas", "drogas en línea",
        "cocaína comprar", "heroína comprar",
        "marihuana envío"
    )

    // =========================================================================
    // PALABRAS CLAVE — MALWARE / PHISHING
    // =========================================================================
    private val malwareKeywords: Set<String> = setOf(
        "your computer is infected",
        "virus detected call now",
        "microsoft security alert",
        "apple security alert",
        "click to remove virus",
        "you have been hacked",
        "urgent security warning",
        "download to clean virus",
        "free antivirus download now",
        "your account has been compromised",
        "verify your paypal account",
        "verify your bank account",
        "update your payment information",
        "enter your credit card to continue",
        "you won a prize",
        "claim your reward now",
        "su computadora está infectada",
        "alerta de seguridad",
        "ingrese sus datos bancarios"
    )

    // =========================================================================
    // PALABRAS CLAVE EN URLs (análisis antes de navegar)
    // =========================================================================
    val urlKeywords: Set<String> = setOf(
        "porn", "porno", "xxx", "sex", "nude", "naked", "adult", "erotic", "hentai", "fetish",
        "nsfw", "gore", "bestgore", "liveleak", "casino", "gambling", "betting", "bet365",
        "poker", "cocaine", "heroin", "meth", "darkweb", "darknet", "sexo", "desnudo", "adulto",
        "apuestas", "camgirl", "onlyfans", "fansly", "escort", "milf", "blowjob", "stripchat"
    )

    // =========================================================================
    // CATEGORÍAS CON UMBRAL PARA ANÁLISIS DE HTML
    // =========================================================================
    val htmlCategories: List<CategoryThreshold> = listOf(
        CategoryThreshold(
            name = "sexual",
            keywords = sexualKeywords,
            threshold = 2  // Con 2 o más coincidencias se bloquea
        ),
        CategoryThreshold(
            name = "violence",
            keywords = violenceKeywords,
            threshold = 2
        ),
        CategoryThreshold(
            name = "gambling",
            keywords = gamblingKeywords,
            threshold = 3
        ),
        CategoryThreshold(
            name = "drugs",
            keywords = drugKeywords,
            threshold = 2
        ),
        CategoryThreshold(
            name = "malware_phishing",
            keywords = malwareKeywords,
            threshold = 1  // Una sola coincidencia de phishing es suficiente
        )
    )

    /**
     * Verifica si una URL contiene palabras clave prohibidas.
     * Se aplica antes de la navegación como primera línea de defensa.
     *
     * @param url La URL a analizar.
     * @return true si la URL contiene alguna palabra clave prohibida.
     */
    fun urlContainsBlockedKeyword(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return urlKeywords.any { keyword -> lowerUrl.contains(keyword) }
    }

    /**
     * Analiza un texto (HTML, metadatos, título) contra todas las categorías con umbral.
     *
     * @param text El texto a analizar.
     * @return El nombre de la categoría que superó el umbral, o null si no hay bloqueo.
     */
    fun analyzeText(text: String): String? {
        val lowerText = text.lowercase()
        for (category in htmlCategories) {
            val matchCount = category.keywords.count { keyword -> lowerText.contains(keyword) }
            if (matchCount >= category.threshold) {
                return category.name
            }
        }
        return null
    }
}
