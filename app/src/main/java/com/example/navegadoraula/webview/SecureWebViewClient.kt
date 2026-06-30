package com.example.navegadoraula.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.navegadoraula.security.DomainFilter
import com.example.navegadoraula.security.HtmlAnalyzer
import com.example.navegadoraula.security.KeywordFilter
import com.example.navegadoraula.security.SafeBrowsingManager
import com.example.navegadoraula.utils.Utils

/**
 * SecureWebViewClient
 *
 * El componente central de seguridad de navegación.
 * Implementa la arquitectura de defensa multicapa interceptando cada evento
 * del ciclo de vida de navegación del WebView.
 *
 * Capas de seguridad implementadas en esta clase:
 *  - Capa 3: Interceptación de navegación (shouldOverrideUrlLoading)
 *  - Capa 4: Interceptación de recursos (shouldInterceptRequest)
 *  - Capa 5: Análisis de HTML (onPageFinished → HtmlAnalyzer)
 *  - Capa 6: Análisis de metadatos (via HtmlAnalyzer)
 *  - Capa 7: Validación de redirecciones (shouldOverrideUrlLoading detecta cambios de URL)
 *  - Capa 8: Google Safe Browsing (onSafeBrowsingHit)
 *  - Capa 9: Limpieza al bloquear (Utils.clearWebViewData)
 *
 * Principio de diseño: Este cliente no toma decisiones de UI directamente;
 * notifica a MainActivity mediante el [NavigationCallback].
 */
class SecureWebViewClient(
    private val domainFilter: DomainFilter,
    private val keywordFilter: KeywordFilter,
    private val htmlAnalyzer: HtmlAnalyzer,
    private val safeBrowsingManager: SafeBrowsingManager,
    private val navigationCallback: NavigationCallback
) : WebViewClient() {

    /**
     * Interfaz de callback para comunicar eventos de navegación a la Activity.
     * Sigue el patrón Observer para desacoplar la lógica de seguridad de la UI.
     */
    interface NavigationCallback {
        /** La página comenzó a cargar. */
        fun onLoadStarted()

        /** La página terminó de cargar exitosamente. */
        fun onLoadFinished()

        /** Ocurrió un error de conexión de red. */
        fun onConnectionError(description: String)

        /** Ocurrió un error de certificado SSL. */
        fun onSslError()

        /** Un dominio fue bloqueado por la lista negra. */
        fun onDomainBlocked()

        /** Se detectó contenido prohibido en la URL o la página. */
        fun onContentBlocked()

        /** Safe Browsing detectó una amenaza. */
        fun onSafeBrowsingThreat()

        /** Se intentó usar un protocolo no permitido. */
        fun onProtocolBlocked()
    }

    // =========================================================================
    // CAPA 3 + 7: INTERCEPTACIÓN DE NAVEGACIÓN Y REDIRECCIONES
    // =========================================================================

    /**
     * Intercepta cada intento de navegación (clic en enlace, redirección, carga inicial).
     *
     * Se invoca para:
     *  - Clics en enlaces dentro del WebView.
     *  - Redirecciones HTTP (302, 301).
     *  - Navegación programática.
     *
     * Retorna true para BLOQUEAR la navegación, false para PERMITIRLA.
     * Nunca retornamos false sin haber validado primero la URL.
     *
     * @param view El WebView que solicita la navegación.
     * @param request Los detalles de la solicitud de navegación.
     * @return true si se bloquea la navegación, false si se permite.
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        return when (val result = domainFilter.checkUrl(url)) {
            is DomainFilter.DomainCheckResult.Allowed -> {
                // Verificar también palabras clave en la URL (Capa 2 a nivel de navegación)
                val kwResult = kotlinx.coroutines.runBlocking { keywordFilter.analyzeUrl(url) }
                if (kwResult is KeywordFilter.KeywordCheckResult.Blocked) {
                    Utils.clearWebViewData(view)
                    navigationCallback.onContentBlocked()
                    true // Bloquear
                } else {
                    false // Permitir: el WebView cargará la URL
                }
            }
            is DomainFilter.DomainCheckResult.Blocked -> {
                Utils.clearWebViewData(view)
                navigationCallback.onDomainBlocked()
                true // Bloquear
            }
            is DomainFilter.DomainCheckResult.InvalidProtocol -> {
                navigationCallback.onProtocolBlocked()
                true // Bloquear
            }
            is DomainFilter.DomainCheckResult.MalformedUrl -> {
                navigationCallback.onProtocolBlocked()
                true // Bloquear
            }
        }
    }

    // =========================================================================
    // CAPA 4: INTERCEPTACIÓN DE RECURSOS
    // =========================================================================

    /**
     * Intercepta cada solicitud de recurso realizada por la página cargada.
     *
     * Esto incluye: imágenes, scripts JS, hojas de estilo CSS, iframes,
     * videos, fuentes, XMLHttpRequests (AJAX), etc.
     *
     * Si el recurso proviene de un dominio bloqueado, se retorna una
     * respuesta vacía en lugar de null (que permitiría la carga).
     *
     * NOTA: Este método se invoca en un hilo secundario, no en el hilo
     * principal de UI. No modificar la UI directamente aquí.
     *
     * @param view El WebView que solicita el recurso.
     * @param request Los detalles de la solicitud del recurso.
     * @return WebResourceResponse vacía si se bloquea, null si se permite.
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: ""

        // Bloquear cualquier recurso que no sea HTTPS
        if (scheme != "https") {
            return createBlockedResponse()
        }

        // Verificar el dominio del recurso contra la lista negra
        val host = request.url.host?.lowercase() ?: ""
        if (host.isNotBlank() && com.example.navegadoraula.data.BlockedDomains.isBlocked(host)) {
            return createBlockedResponse()
        }

        // Verificar palabras clave en la URL del recurso
        val kwResult = kotlinx.coroutines.runBlocking { keywordFilter.analyzeUrl(url) }
        if (kwResult is KeywordFilter.KeywordCheckResult.Blocked) {
            return createBlockedResponse()
        }

        return null // Permitir el recurso
    }

    /**
     * Crea una respuesta vacía y vacía para bloquear la carga de un recurso.
     * Una respuesta nula (null stream) es la forma correcta de bloquear sin causar errores.
     */
    private fun createBlockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", null)
    }

    // =========================================================================
    // EVENTOS DE CICLO DE VIDA DE PÁGINA
    // =========================================================================

    /**
     * Invocado cuando comienza la carga de una nueva página.
     * Notifica a la Activity para actualizar el estado de la UI.
     */
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        navigationCallback.onLoadStarted()
    }

    /**
     * Invocado cuando la página termina de cargar completamente.
     *
     * Tras la carga exitosa se ejecutan:
     *  - Capa 5: Análisis del contenido HTML visible.
     *  - Capa 6: Análisis de metadatos (via HtmlAnalyzer que extrae title, meta, OG).
     */
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)

        // Ejecutar análisis de HTML y metadatos (Capas 5 y 6)
        if (!url.isNullOrBlank() && url != "about:blank") {
            htmlAnalyzer.analyzePageContent(view, object : HtmlAnalyzer.ContentAnalysisCallback {
                override fun onProhibitedContentDetected(webView: WebView, category: String) {
                    // Limpiar todo antes de bloquear (Capa 9)
                    Utils.clearWebViewData(webView)
                    webView.loadUrl("about:blank")
                    navigationCallback.onContentBlocked()
                }

                override fun onContentClean(webView: WebView) {
                    // Solo cuando se confirme que está limpia, se notifica que terminó la carga
                    navigationCallback.onLoadFinished()
                }
            })
        } else {
            // Si es about:blank o nula, finalizamos la carga inmediatamente
            navigationCallback.onLoadFinished()
        }
    }

    // =========================================================================
    // MANEJO DE ERRORES
    // =========================================================================

    /**
     * Invocado cuando ocurre un error de red al cargar la página principal.
     * Los errores de recursos secundarios no se reportan aquí para no saturar al usuario.
     */
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        // Solo reportar errores de la página principal (no de sub-recursos)
        if (request.isForMainFrame) {
            super.onReceivedError(view, request, error)
            navigationCallback.onConnectionError(error.description.toString())
        }
    }

    /**
     * Invocado cuando se detecta un error de certificado SSL.
     *
     * IMPORTANTE: No se llama a handler.proceed() bajo ninguna circunstancia.
     * Hacerlo aceptaría el certificado inválido, creando una vulnerabilidad de
     * Man-in-the-Middle (MitM). Siempre se cancela.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // NUNCA llamar a handler.proceed() — siempre cancelar
        handler.cancel()
        Utils.clearWebViewData(view)
        navigationCallback.onSslError()
    }

    // =========================================================================
    // CAPA 8: GOOGLE SAFE BROWSING
    // =========================================================================

    /**
     * Invocado cuando Google Safe Browsing detecta que una URL es peligrosa.
     *
     * Se delega al [SafeBrowsingManager] que toma la acción apropiada:
     * retroceder silenciosamente en lugar de mostrar la pantalla de advertencia
     * del sistema (que el usuario podría ignorar).
     *
     * @param view El WebView que accedió al sitio peligroso.
     * @param request La solicitud que activó Safe Browsing.
     * @param threatType El tipo de amenaza detectada.
     * @param callback El callback para indicar qué acción tomar.
     */
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse
    ) {
        safeBrowsingManager.handleSafeBrowsingHit(
            webView = view,
            url = request.url.toString(),
            threatType = threatType,
            safeBrowsingResponse = callback,
            appCallback = object : SafeBrowsingManager.SafeBrowsingCallback {
                override fun onThreatDetected(webView: WebView, url: String, threatType: Int) {
                    Utils.clearWebViewData(webView)
                    navigationCallback.onSafeBrowsingThreat()
                }
            }
        )
    }
}
