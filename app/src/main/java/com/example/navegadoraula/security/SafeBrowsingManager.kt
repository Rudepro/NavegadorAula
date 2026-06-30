package com.example.navegadoraula.security

import android.webkit.SafeBrowsingResponse
import android.webkit.WebView

/**
 * SafeBrowsingManager
 *
 * Responsabilidad única: Gestionar los eventos generados por Google Safe Browsing
 * cuando detecta un sitio potencialmente peligroso.
 *
 * Google Safe Browsing verifica las URLs contra listas actualizadas de:
 *  - Sitios de phishing
 *  - Sitios con malware/software no deseado
 *  - Sitios con amenazas sociales
 *
 * Disponible desde API 27 (Android 8.1). Para API 26 se verifica en tiempo de ejecución.
 * La habilitación global se configura en AndroidManifest.xml con la meta-data
 * "android.webkit.WebView.EnableSafeBrowsing" = true.
 *
 * Comportamiento ante amenaza detectada:
 *  1. NO mostrar la advertencia del sistema (podría ser ignorada).
 *  2. Bloquear inmediatamente la navegación.
 *  3. Notificar a MainActivity para limpiar y mostrar mensaje de error.
 */
class SafeBrowsingManager {

    /**
     * Callback para notificar a la capa superior (WebViewClient → MainActivity)
     * cuando Safe Browsing detecta una amenaza.
     */
    interface SafeBrowsingCallback {
        /**
         * Invocado cuando Safe Browsing detecta un sitio peligroso.
         *
         * @param webView El WebView que intentó acceder al sitio.
         * @param url La URL del sitio bloqueado.
         * @param threatType El tipo de amenaza detectada (constante de SafeBrowsingThreat).
         */
        fun onThreatDetected(webView: WebView, url: String, threatType: Int)
    }

    /**
     * Maneja el evento onSafeBrowsingHit del WebViewClient.
     *
     * Esta función debe invocarse desde:
     * WebViewClient.onSafeBrowsingHit(view, request, threatType, callback)
     *
     * @param webView El WebView donde se detectó la amenaza.
     * @param url La URL del sitio detectado como peligroso.
     * @param threatType El tipo de amenaza (ver WebViewClient.SAFE_BROWSING_THREAT_*).
     * @param safeBrowsingResponse El callback de Safe Browsing para tomar acción.
     * @param appCallback Callback de la aplicación para notificar la detección.
     */
    fun handleSafeBrowsingHit(
        webView: WebView,
        url: String,
        threatType: Int,
        safeBrowsingResponse: SafeBrowsingResponse,
        appCallback: SafeBrowsingCallback
    ) {
        // Opción elegida: "backToSafety" — retrocede sin mostrar pantalla de advertencia.
        // La pantalla de advertencia del sistema puede ser ignorada por el usuario;
        // retroceder es la acción más segura para un entorno educativo.
        safeBrowsingResponse.backToSafety(true)

        // Notificar a la capa superior para mostrar mensaje y limpiar datos
        appCallback.onThreatDetected(webView, url, threatType)
    }

    /**
     * Convierte el código numérico de amenaza a un texto descriptivo.
     * Útil para logging y mensajes de error más informativos.
     *
     * @param threatType El código de amenaza de Safe Browsing.
     * @return Descripción textual del tipo de amenaza.
     */
    fun describeThreat(threatType: Int): String {
        return when (threatType) {
            // WebViewClient.SAFE_BROWSING_THREAT_PHISHING = 2
            2 -> "Phishing (suplantación de identidad)"
            // WebViewClient.SAFE_BROWSING_THREAT_MALWARE = 1
            1 -> "Malware (software malicioso)"
            // WebViewClient.SAFE_BROWSING_THREAT_HARMFUL_APP = 3
            3 -> "Aplicación potencialmente dañina"
            // WebViewClient.SAFE_BROWSING_THREAT_BILLING = 4
            4 -> "Cargo no solicitado"
            else -> "Amenaza de seguridad desconocida (código: $threatType)"
        }
    }
}
