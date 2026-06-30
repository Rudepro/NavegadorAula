package com.example.navegadoraula.security

import android.net.Uri
import com.example.navegadoraula.data.BlockedDomains

/**
 * DomainFilter
 *
 * Responsabilidad única: Filtrar URLs basándose en la lista negra de dominios.
 *
 * Funciones principales:
 *  1. Extraer el host de una URL de forma segura.
 *  2. Verificar si el host está en la lista negra (incluyendo subdominios).
 *  3. Validar URLs de redirección antes de permitirlas.
 *  4. Verificar que el protocolo sea exclusivamente HTTPS.
 *
 * Principio SOLID aplicado: Single Responsibility — esta clase solo
 * se ocupa del filtrado por dominio, nada más.
 */
class DomainFilter {

    /**
     * Resultado del análisis de dominio.
     */
    sealed class DomainCheckResult {
        data object Allowed : DomainCheckResult()
        data object Blocked : DomainCheckResult()
        data object InvalidProtocol : DomainCheckResult()
        data object MalformedUrl : DomainCheckResult()
    }

    /**
     * Conjunto de protocolos peligrosos que nunca deben ser permitidos.
     * No existe ningún escenario en que estos esquemas sean aceptables
     * dentro de un navegador educativo seguro.
     */
    private val dangerousSchemes: Set<String> = setOf(
        "http",       // Sin cifrado
        "ftp",        // Protocolo de transferencia sin cifrado
        "file",       // Acceso al sistema de archivos local
        "content",    // Proveedor de contenido de Android
        "intent",     // Esquema de intents de Android (puede explotar apps)
        "javascript", // Ejecución de código JavaScript arbitrario
        "data",       // Datos embebidos (puede usarse para phishing)
        "blob",       // URLs de blob (puede contener datos arbitrarios)
        "about",      // Páginas internas del navegador
        "chrome",     // APIs internas de Chrome
        "ws",         // WebSocket sin cifrado
        "wss",        // WebSocket cifrado (solo se usa como subrecurso, no navegación directa)
        "market",     // Play Store
        "tel",        // Teléfono
        "sms",        // SMS
        "mailto"      // Email
    )

    /**
     * Verifica si una URL es segura para navegar.
     *
     * Proceso de verificación (en orden):
     *  1. Parsear la URL de forma segura con Uri.
     *  2. Extraer y verificar el esquema.
     *  3. Verificar que el esquema sea HTTPS.
     *  4. Extraer el host.
     *  5. Verificar el host contra la lista negra.
     *
     * @param url La URL completa a verificar.
     * @return [DomainCheckResult] indicando el resultado.
     */
    fun checkUrl(url: String): DomainCheckResult {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return DomainCheckResult.MalformedUrl
        }

        val scheme = uri.scheme?.lowercase() ?: return DomainCheckResult.MalformedUrl
        val host = uri.host?.lowercase() ?: return DomainCheckResult.MalformedUrl

        // Verificar esquemas peligrosos explícitamente
        if (dangerousSchemes.contains(scheme)) {
            return DomainCheckResult.InvalidProtocol
        }

        // Solo HTTPS está permitido
        if (scheme != "https") {
            return DomainCheckResult.InvalidProtocol
        }

        // Verificar contra la lista negra de dominios
        if (BlockedDomains.isBlocked(host)) {
            return DomainCheckResult.Blocked
        }

        return DomainCheckResult.Allowed
    }

    /**
     * Validación específica para URLs de redirección.
     * Las redirecciones son un vector común de ataque; se validan igual de estrictamente.
     *
     * @param redirectUrl La URL de destino de la redirección.
     * @return true si la redirección debe bloquearse.
     */
    fun shouldBlockRedirect(redirectUrl: String): Boolean {
        return when (checkUrl(redirectUrl)) {
            is DomainCheckResult.Allowed -> false
            else -> true
        }
    }

    /**
     * Extrae el host de una URL de forma segura.
     *
     * @param url La URL completa.
     * @return El host en minúsculas, o null si no se puede extraer.
     */
    fun extractHost(url: String): String? {
        return try {
            Uri.parse(url).host?.lowercase()
        } catch (e: Exception) {
            null
        }
    }
}
