package com.example.navegadoraula.webview

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.navegadoraula.security.DomainFilter
import com.example.navegadoraula.security.HtmlAnalyzer
import com.example.navegadoraula.security.KeywordFilter
import com.example.navegadoraula.security.SafeBrowsingManager

/**
 * WebViewManager
 *
 * Responsabilidad única: Configurar el WebView con ajustes de seguridad óptimos
 * y asignar los clientes correctos (WebViewClient y WebChromeClient).
 *
 * Centraliza toda la configuración del WebView en un solo lugar, haciendo
 * que sea fácil auditar y modificar los ajustes de seguridad.
 *
 * Configuración de seguridad aplicada:
 *  ✓ Safe Browsing habilitado
 *  ✓ Acceso a archivos locales deshabilitado
 *  ✓ Acceso a content providers deshabilitado
 *  ✓ Contenido mixto (HTTP en HTTPS) nunca permitido
 *  ✓ Sin acceso a base de datos local
 *  ✓ Geolocalización deshabilitada
 *  ✓ JavaScript habilitado (requerido para sitios educativos modernos)
 *  ✓ DOM Storage habilitado (para que los sitios funcionen correctamente)
 *  ✓ User agent estándar (no revelar información adicional)
 *  ✓ Zoom deshabilitado para UI consistente
 */
class WebViewManager(
    private val webView: WebView,
    private val navigationCallback: SecureWebViewClient.NavigationCallback,
    private val progressCallback: SecureWebChromeClient.ProgressCallback
) {

    /**
     * Configura el WebView con todos los ajustes de seguridad y asigna los clientes.
     *
     * Debe invocarse una única vez desde [MainActivity.onCreate()].
     * El [SuppressLint("SetJavaScriptEnabled")] está justificado porque:
     *  1. JavaScript es necesario para que los sitios educativos modernos funcionen.
     *  2. La seguridad está garantizada por las capas de filtrado adicionales.
     *  3. setAllowFileAccess(false) y setAllowContentAccess(false) limitan el riesgo.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        configureSecureSettings()
        assignClients()
    }

    /**
     * Configura todos los WebSettings de forma segura.
     */
    private fun configureSecureSettings() {
        webView.settings.apply {

            // ─── JavaScript ────────────────────────────────────────────────
            // Habilitado: necesario para que la mayoría de sitios funcionen.
            // Riesgo mitigado por shouldInterceptRequest y listas de filtrado.
            javaScriptEnabled = true

            // ─── Acceso al sistema de archivos ─────────────────────────────
            // DESHABILITADO: Previene que páginas maliciosas lean archivos locales.
            // Esto es crítico según OWASP Mobile Security.
            @Suppress("DEPRECATION")
            allowFileAccess = false

            // ─── Acceso a Content Providers ────────────────────────────────
            // DESHABILITADO: Previene acceso a datos de otras aplicaciones.
            @Suppress("DEPRECATION")
            allowContentAccess = false

            // ─── Contenido mixto ───────────────────────────────────────────
            // NEVER_ALLOW: Bloquea cualquier recurso HTTP dentro de una página HTTPS.
            // Sin esta configuración, un atacante podría inyectar recursos HTTP.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // ─── Google Safe Browsing ──────────────────────────────────────
            // Habilitado: protección adicional contra phishing y malware conocidos.
            // Disponible desde API 26 (nuestro minSdk).
            safeBrowsingEnabled = true

            // ─── Almacenamiento ────────────────────────────────────────────
            // DOM Storage habilitado: localStorage y sessionStorage.
            // Requerido por la mayoría de sitios web modernos.
            domStorageEnabled = true

            // Base de datos Web SQL DESHABILITADA: funcionalidad obsoleta y riesgosa.
            @Suppress("DEPRECATION")
            databaseEnabled = false

            // ─── Geolocalización ───────────────────────────────────────────
            // DESHABILITADA: No es necesaria para un navegador educativo.
            // Habilitar esto requeriría permisos de ubicación adicionales.
            @Suppress("DEPRECATION")
            setGeolocationEnabled(false)

            // ─── Acceso JavaScript universal ───────────────────────────────
            // DESHABILITADO: Previene que iframes accedan a frames padre y viceversa.
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false

            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false

            // ─── Caché ─────────────────────────────────────────────────────
            // Modo por defecto: carga desde la red cuando sea necesario.
            cacheMode = WebSettings.LOAD_DEFAULT

            // ─── Zoom y viewport ───────────────────────────────────────────
            // Zoom táctil habilitado para mejor experiencia de usuario.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false // Ocultar botones de zoom (solo zoom táctil)

            // Viewport responsivo para adaptarse a pantallas de diferentes tamaños.
            useWideViewPort = true
            loadWithOverviewMode = true

            // ─── Codificación de texto ─────────────────────────────────────
            defaultTextEncodingName = "UTF-8"

            // ─── Aceleración de hardware ───────────────────────────────────
            // Ya habilitada por defecto en API 14+. No se necesita configuración adicional.
        }

        // Configurar aceleración de hardware a nivel de vista
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Crea e instancia los clientes seguros y los asigna al WebView.
     */
    private fun assignClients() {
        val domainFilter = DomainFilter()
        val keywordFilter = KeywordFilter()
        val htmlAnalyzer = HtmlAnalyzer(keywordFilter)
        val safeBrowsingManager = SafeBrowsingManager()

        // Asignar el cliente seguro de navegación
        webView.webViewClient = SecureWebViewClient(
            domainFilter = domainFilter,
            keywordFilter = keywordFilter,
            htmlAnalyzer = htmlAnalyzer,
            safeBrowsingManager = safeBrowsingManager,
            navigationCallback = navigationCallback
        )

        // Asignar el cliente seguro de Chrome (UI/progreso)
        webView.webChromeClient = SecureWebChromeClient(
            progressCallback = progressCallback
        )
    }

    /**
     * Navega a una URL de forma segura.
     * Se asume que la URL ya fue validada por UrlValidator y DomainFilter
     * antes de llegar aquí.
     *
     * @param url La URL validada a cargar.
     */
    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    /**
     * Recarga la página actual.
     *
     * @return true si había una página que recargar, false si el WebView estaba vacío.
     */
    fun reload(): Boolean {
        val currentUrl = webView.url
        return if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank") {
            webView.reload()
            true
        } else {
            false
        }
    }

    /**
     * Verifica si el WebView puede retroceder en el historial.
     *
     * @return true si hay páginas anteriores en el historial.
     */
    fun canGoBack(): Boolean = webView.canGoBack()

    /**
     * Retrocede en el historial del WebView.
     */
    fun goBack() = webView.goBack()

    /**
     * Retorna la URL actual del WebView.
     *
     * @return La URL actual, o null si no hay página cargada.
     */
    fun getCurrentUrl(): String? = webView.url
}
