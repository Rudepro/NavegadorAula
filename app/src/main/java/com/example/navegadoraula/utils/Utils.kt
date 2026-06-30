package com.example.navegadoraula.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.google.android.material.snackbar.Snackbar

/**
 * Utils
 *
 * Colección de funciones de utilidad reutilizables en toda la aplicación.
 *
 * Principio: Funciones puras sin estado, sin dependencias externas innecesarias.
 * Cada función tiene una única responsabilidad bien definida.
 */
object Utils {

    /**
     * Muestra un MaterialAlertDialogBuilder con el mensaje indicado.
     * Utilizado para bloquear explícitamente la navegación y requerir
     * acuse de recibo del usuario mediante el botón Aceptar.
     *
     * @param context El contexto (Activity).
     * @param title El título del diálogo.
     * @param message El mensaje a mostrar.
     */
    fun showMessage(
        context: Context,
        title: String = "Aviso de Seguridad",
        message: String
    ) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .setCancelable(false)
            .show()
    }

    /**
     * Limpia completamente todos los datos de navegación del WebView.
     *
     * Según OWASP Mobile Security, cuando un sitio es bloqueado debe eliminarse:
     *  - Caché de páginas y recursos.
     *  - Historial de navegación.
     *  - Cookies (todas, incluyendo las de sesión).
     *  - Almacenamiento local (localStorage, sessionStorage).
     *  - Base de datos web (Web SQL).
     *
     * @param webView El WebView a limpiar.
     */
    fun clearWebViewData(webView: WebView) {
        // Limpiar caché de páginas y recursos descargados
        webView.clearCache(true)

        // Limpiar historial de navegación
        webView.clearHistory()

        // Limpiar formularios guardados
        webView.clearFormData()

        // Limpiar todas las cookies
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }

        // Limpiar almacenamiento web (localStorage, sessionStorage, Web SQL)
        WebStorage.getInstance().deleteAllData()
    }

    /**
     * Verifica si el dispositivo tiene conectividad a Internet activa.
     *
     * Usa [NetworkCapabilities] (API 23+) en lugar del deprecado [NetworkInfo].
     *
     * @param context El contexto de Android.
     * @return true si hay conectividad activa.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Normaliza una URL eliminando espacios y estandarizando el formato.
     * Solo debe usarse DESPUÉS de que la validación ya falló por espacios
     * (no como alternativa a mostrar el mensaje de error).
     *
     * @param url La URL a normalizar.
     * @return La URL sin espacios al inicio/final.
     */
    fun normalizeUrl(url: String): String = url.trim()

    /**
     * Determina si el WebView tiene una página cargada.
     * Verifica que la URL no sea null, no esté vacía y no sea "about:blank".
     *
     * @param webView El WebView a verificar.
     * @return true si hay una página real cargada.
     */
    fun hasPageLoaded(webView: WebView): Boolean {
        val url = webView.url
        return !url.isNullOrBlank() && url != "about:blank"
    }
}
