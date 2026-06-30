package com.example.navegadoraula.security

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * HtmlAnalyzer
 *
 * Responsabilidad única: Extraer metadatos y contenido de una página web
 * mediante JavaScript, analizarlos y notificar si debe bloquearse.
 *
 * Mecanismo:
 *  1. Inyecta JavaScript en el WebView para extraer:
 *     - document.title
 *     - meta name="keywords"
 *     - meta name="description"
 *     - meta property="og:title"
 *     - meta property="og:description"
 *     - Texto visible del body (primeras 3000 palabras)
 *  2. Pasa los datos extraídos al [KeywordFilter].
 *  3. Si se detecta contenido prohibido, notifica mediante el callback.
 *
 * Consideraciones de seguridad:
 *  - El JavaScript inyectado es de solo lectura (no modifica el DOM).
 *  - Los datos se reciben como cadenas primitivas (no objetos complejos).
 *  - Se utiliza evaluateJavascript() que es seguro en API 19+.
 *
 * IMPORTANTE: Se requiere que javaScriptEnabled = true para que funcione.
 * Esto es necesario porque la mayoría de sitios educativos usan JavaScript.
 */
class HtmlAnalyzer(
    private val keywordFilter: KeywordFilter
) {

    /**
     * Interfaz de callback para notificar al WebViewClient cuando
     * se termina de analizar el contenido.
     */
    interface ContentAnalysisCallback {
        /**
         * Invocado cuando el contenido analizado contiene material prohibido.
         *
         * @param webView El WebView donde se detectó el contenido.
         * @param category La categoría del contenido bloqueado.
         */
        fun onProhibitedContentDetected(webView: WebView, category: String)

        /**
         * Invocado cuando el contenido es analizado y no contiene material prohibido.
         *
         * @param webView El WebView analizado.
         */
        fun onContentClean(webView: WebView)
    }

    /**
     * Analiza los metadatos y contenido de texto de la página actual en el WebView.
     *
     * Se invoca desde onPageFinished() del WebViewClient.
     * La extracción es asíncrona pero el callback de resultado es en el hilo principal.
     *
     * @param webView El WebView con la página cargada.
     * @param callback Callback invocado si se detecta contenido prohibido.
     */
    fun analyzePageContent(webView: WebView, callback: ContentAnalysisCallback) {
        // JavaScript que extrae todos los metadatos relevantes en una sola llamada.
        // Se construye como un objeto JSON serializado a string para pasar múltiples valores.
        val extractionScript = """
            (function() {
                try {
                    var title = document.title || '';
                    
                    var metaKw = '';
                    var kwTag = document.querySelector('meta[name="keywords"]');
                    if (kwTag) metaKw = kwTag.getAttribute('content') || '';
                    
                    var metaDesc = '';
                    var descTag = document.querySelector('meta[name="description"]');
                    if (descTag) metaDesc = descTag.getAttribute('content') || '';
                    
                    var ogTitle = '';
                    var ogTitleTag = document.querySelector('meta[property="og:title"]');
                    if (ogTitleTag) ogTitle = ogTitleTag.getAttribute('content') || '';
                    
                    var ogDesc = '';
                    var ogDescTag = document.querySelector('meta[property="og:description"]');
                    if (ogDescTag) ogDesc = ogDescTag.getAttribute('content') || '';
                    
                    var bodyText = '';
                    if (document.body) {
                        bodyText = (document.body.innerText || '').substring(0, 3000);
                    }
                    
                    return JSON.stringify({
                        t: title,
                        mk: metaKw,
                        md: metaDesc,
                        ot: ogTitle,
                        od: ogDesc,
                        bt: bodyText
                    });
                } catch(e) {
                    return '{}';
                }
            })()
        """.trimIndent()

        webView.evaluateJavascript(extractionScript) { jsonResult ->
            processExtractedData(webView, jsonResult, callback)
        }
    }

    /**
     * Procesa el JSON retornado por el script de extracción.
     * Analiza cada campo y delega a [KeywordFilter].
     *
     * @param webView El WebView fuente.
     * @param jsonResult El JSON como string retornado por evaluateJavascript.
     * @param callback Callback para notificar bloqueo.
     */
    private fun processExtractedData(
        webView: WebView,
        jsonResult: String?,
        callback: ContentAnalysisCallback
    ) {
        if (jsonResult.isNullOrBlank() || jsonResult == "null" || jsonResult == "{}") {
            callback.onContentClean(webView)
            return
        }

        // Lanza la corrutina en el hilo principal para poder hacer callbacks a la UI de forma segura,
        // pero la pesada carga de análisis se delega a Dispatchers.Default dentro de KeywordFilter.
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Parseo manual simple para evitar dependencia de librería JSON
                val cleaned = jsonResult.trim().removeSurrounding("\"").replace("\\\"", "\"")

                val title = extractJsonField(cleaned, "t")
                val metaKeywords = extractJsonField(cleaned, "mk")
                val metaDescription = extractJsonField(cleaned, "md")
                val ogTitle = extractJsonField(cleaned, "ot")
                val ogDescription = extractJsonField(cleaned, "od")
                val bodyText = extractJsonField(cleaned, "bt")

                // Análisis 1: Metadatos (title, meta, OG)
                val metadataResult = keywordFilter.analyzeMetadata(
                    title = title,
                    metaKeywords = metaKeywords,
                    metaDescription = metaDescription,
                    ogTitle = ogTitle,
                    ogDescription = ogDescription
                )

                if (metadataResult is KeywordFilter.KeywordCheckResult.Blocked) {
                    callback.onProhibitedContentDetected(webView, metadataResult.category)
                    return@launch
                }

                // Análisis 2: Contenido del body
                val htmlResult = keywordFilter.analyzeHtmlContent(bodyText)
                if (htmlResult is KeywordFilter.KeywordCheckResult.Blocked) {
                    callback.onProhibitedContentDetected(webView, htmlResult.category)
                    return@launch
                }

                // Si pasa todos los análisis, el contenido es seguro
                callback.onContentClean(webView)

            } catch (e: Exception) {
                // Error de parseo: no bloquear, fallar de forma segura
                callback.onContentClean(webView)
            }
        }
    }

    /**
     * Extrae el valor de un campo de un JSON simple (no anidado).
     * Implementación manual para evitar dependencias adicionales.
     *
     * @param json El JSON como string.
     * @param key La clave a buscar.
     * @return El valor del campo, o string vacío si no existe.
     */
    private fun extractJsonField(json: String, key: String): String {
        return try {
            val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            pattern.find(json)?.groupValues?.get(1)
                ?.replace("\\n", " ")
                ?.replace("\\t", " ")
                ?.replace("\\\"", "\"")
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
