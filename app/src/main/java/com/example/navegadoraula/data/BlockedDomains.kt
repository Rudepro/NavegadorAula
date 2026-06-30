package com.example.navegadoraula.data

/**
 * BlockedDomains
 *
 * Repositorio centralizado de dominios prohibidos.
 * Arquitectura: Objeto singleton con conjuntos inmutables clasificados por categoría.
 * Mantenimiento: Para agregar nuevos dominios, basta con añadirlos a la lista correspondiente.
 *
 * La verificación soporta subdominios: si "example.com" está bloqueado,
 * también se bloquea "sub.example.com", "www.sub.example.com", etc.
 *
 * Fuentes de referencia para dominios: listas públicas de filtrado de contenido.
 * No se copió código de Internet; se construyó siguiendo principios de OWASP Mobile Security.
 */
object BlockedDomains {

    // =========================================================================
    // DOMINIOS ADULTOS / CONTENIDO SEXUAL EXPLÍCITO
    // =========================================================================
    private val adultDomains: Set<String> = setOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "redtube.com",
        "youporn.com",
        "tube8.com",
        "xhamster.com",
        "livejasmin.com",
        "stripchat.com",
        "chaturbate.com",
        "onlyfans.com",
        "fansly.com",
        "legioncaliente.com",
        "brazzers.com",
        "naughtyamerica.com",
        "bangbros.com",
        "mofos.com",
        "reality-kings.com",
        "realitykings.com",
        "teamskeet.com",
        "spankbang.com",
        "tnaflix.com",
        "porntrex.com",
        "eporner.com",
        "beeg.com",
        "drtuber.com",
        "keezmovies.com",
        "porn.com",
        "sex.com",
        "youpornhub.com",
        "hdporn.net",
        "fapster.xxx",
        "xxx.com",
        "adult.com",
        "adultfriendfinder.com",
        "ashleymadison.com",
        "camsoda.com",
        "bongacams.com",
        "myfreecams.com",
        "cam4.com",
        "webcamtoy.com",
        "xtube.com",
        "hclips.com",
        "hellporno.com",
        "hdzog.com",
        "wetplace.com",
        "txxx.com",
        "upornia.com",
        "fullpornnetwork.com",
        "eroprofile.com",
        "ixxx.com",
        "pornmd.com",
        "proporn.com",
        "anysex.com",
        "porntube.com",
        "slutload.com",
        "slutroulette.com",
        "rule34.xxx",
        "e621.net",
        "nhentai.net",
        "hentaihaven.org",
        "fakku.net"
    )

    // =========================================================================
    // DOMINIOS DE APUESTAS / JUEGOS DE AZAR
    // =========================================================================
    private val gamblingDomains: Set<String> = setOf(
        "bet365.com",
        "pokerstars.com",
        "partypoker.com",
        "888casino.com",
        "888poker.com",
        "betway.com",
        "williamhill.com",
        "bwin.com",
        "unibet.com",
        "ladbrokes.com",
        "coral.co.uk",
        "bovada.lv",
        "draftkings.com",
        "fanduel.com",
        "betmgm.com",
        "caesarscasino.com",
        "goldenugget.com",
        "casinomax.com",
        "spinpalace.com",
        "jackpotcity.com",
        "royalvegas.com",
        "mansion.com",
        "casinoroom.com",
        "netent.com",
        "casumo.com",
        "leovegas.com",
        "mrgreen.com",
        "rizk.com",
        "betfair.com",
        "pinnacle.com",
        "matchbook.com",
        "betsson.com",
        "nordicbet.com",
        "betchan.com",
        "betclic.com",
        "sportingbet.com",
        "winmasters.com",
        "melbet.com",
        "1xbet.com",
        "22bet.com",
        "betwinner.com",
        "marathonbet.com",
        "bethard.com",
        "vbet.com",
        "parimatch.com",
        "pari.com",
        "coolbet.com"
    )

    // =========================================================================
    // DOMINIOS DE MALWARE / PHISHING CONOCIDOS
    // =========================================================================
    private val malwareDomains: Set<String> = setOf(
        "malware.testing.google.test",
        "phishing.test.google.com",
        "0.0.0.0",
        "localhost.malware.test",
        "evil.example.com",
        "malicious-site.example",
        "phish-example.com",
        "click-fraud.example.com",
        "adware-distributor.example",
        "ransomware-c2.example",
        "cryptominer-pool.example"
    )

    // =========================================================================
    // DOMINIOS DE VIOLENCIA EXTREMA / CONTENIDO PERTURBADOR
    // =========================================================================
    private val violenceDomains: Set<String> = setOf(
        "bestgore.com",
        "liveleak.com",
        "goregrish.com",
        "ogrish.com",
        "rotten.com",
        "theync.com",
        "watchpeopledie.tv",
        "uncoverreality.com",
        "kaotic.com"
    )

    // =========================================================================
    // DOMINIOS DE DROGAS / MERCADOS ILEGALES
    // =========================================================================
    private val illegalDomains: Set<String> = setOf(
        "silkroad.onion",
        "darkmarket.onion",
        "alphabay.onion",
        "hansa.onion",
        "agora.onion"
    )

    // Conjunto unificado para búsqueda eficiente O(1)
    private val allBlockedDomains: Set<String> =
        adultDomains + gamblingDomains + malwareDomains + violenceDomains + illegalDomains

    /**
     * Verifica si el host dado (o alguno de sus dominios padre) está en la lista negra.
     *
     * Ejemplos:
     *  - "www.pornhub.com"     → true  (subdominio de pornhub.com)
     *  - "pornhub.com"         → true  (dominio exacto)
     *  - "api.sub.bet365.com"  → true  (sub-subdominio)
     *  - "google.com"          → false (no está bloqueado)
     *
     * @param host El nombre de host extraído de la URL (sin esquema ni puerto).
     * @return true si el host o alguno de sus dominios padre está bloqueado.
     */
    fun isBlocked(host: String): Boolean {
        if (host.isBlank()) return false
        val cleanHost = host.lowercase().trimStart('.')

        // Verificación directa
        if (allBlockedDomains.contains(cleanHost)) return true

        // Verificación por dominio padre (subdominio → dominio → TLD)
        val parts = cleanHost.split(".")
        for (startIndex in 1 until parts.size - 1) {
            val parentDomain = parts.subList(startIndex, parts.size).joinToString(".")
            if (allBlockedDomains.contains(parentDomain)) return true
        }

        return false
    }

    /**
     * Retorna el total de dominios bloqueados registrados.
     * Útil para logging y diagnóstico.
     */
    fun totalCount(): Int = allBlockedDomains.size
}
