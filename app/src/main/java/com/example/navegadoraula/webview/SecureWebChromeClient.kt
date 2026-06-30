package com.example.navegadoraula.webview

import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * SecureWebChromeClient
 *
 * Extiende [WebChromeClient] para proporcionar retroalimentación de progreso
 * precisa al usuario mientras se carga una página.
 *
 * Responsabilidad: Actualizar el ProgressBar con el porcentaje real de carga
 * reportado por el motor de renderizado del WebView.
 *
 * Diferencia con WebViewClient:
 *  - WebViewClient: eventos de navegación (inicio, fin, errores, URLs).
 *  - WebChromeClient: eventos de la interfaz (progreso, título, diálogos JS).
 *
 * El [ProgressCallback] desacopla este cliente de la Activity,
 * siguiendo el principio de inversión de dependencias.
 */
class SecureWebChromeClient(
    private val progressCallback: ProgressCallback
) : WebChromeClient() {

    /**
     * Interfaz de callback para reportar el progreso de carga.
     */
    interface ProgressCallback {
        /**
         * Invocado cada vez que el progreso de carga cambia.
         *
         * @param progress Porcentaje de carga: 0..100.
         *                 100 indica que la página terminó de cargar.
         */
        fun onProgressChanged(progress: Int)
    }

    /**
     * Llamado por el motor WebView cada vez que el progreso de carga cambia.
     * El ProgressBar se actualiza directamente a través del callback.
     *
     * @param view El WebView que reporta el progreso.
     * @param newProgress El nuevo porcentaje de progreso (0-100).
     */
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        progressCallback.onProgressChanged(newProgress)
    }
}
