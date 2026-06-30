package com.example.navegadoraula

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.navegadoraula.databinding.ActivityMainBinding
import com.example.navegadoraula.security.UrlValidator
import com.example.navegadoraula.utils.Utils
import com.example.navegadoraula.webview.SecureWebChromeClient
import com.example.navegadoraula.webview.SecureWebViewClient
import com.example.navegadoraula.webview.WebViewManager

/**
 * MainActivity
 *
 * Punto de entrada y controlador principal de la aplicación NavegadorAula.
 *
 * Responsabilidades:
 *  1. Inflar el layout y obtener referencias mediante ViewBinding.
 *  2. Inicializar todos los componentes: WebViewManager, UrlValidator.
 *  3. Gestionar los estados de la interfaz (cargando, error, idle).
 *  4. Responder a las acciones del usuario (botones, teclado, Back).
 *  5. Recibir notificaciones del WebViewClient y actualizar la UI.
 *
 * Patrón de estado de UI:
 *  - IDLE: Campos habilitados, sin carga, sin página o página cargada.
 *  - LOADING: Campos deshabilitados, ProgressBar visible.
 *  - ERROR: Regresa a IDLE con mensaje de error visible.
 *
 * Principio Clean Architecture: MainActivity es solo la capa de presentación.
 * Toda la lógica de seguridad y navegación vive en las capas inferiores.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding para acceso seguro y eficiente a las vistas (sin findViewById)
    private lateinit var binding: ActivityMainBinding

    // Componentes del dominio
    private lateinit var webViewManager: WebViewManager
    private lateinit var urlValidator: UrlValidator

    // Estado de si hay una página cargada actualmente
    private var hasPageLoaded = false

    // =========================================================================
    // CICLO DE VIDA
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar edge-to-edge para usar toda la pantalla
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Inflar layout mediante ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manejar insets del sistema (status bar, navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        // Inicializar componentes
        initComponents()
        setupButtonListeners()
        setupKeyboardAction()

        // Enfocar automáticamente el campo URL al iniciar
        focusUrlField()
    }

    /**
     * Maneja el botón físico "Atrás" del dispositivo.
     * Si el WebView tiene historial, retrocede. Si no, cierra la app.
     */
    @Deprecated("onBackPressed is deprecated but required for API < 33 compatibility")
    override fun onBackPressed() {
        if (webViewManager.canGoBack()) {
            webViewManager.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // =========================================================================
    // INICIALIZACIÓN
    // =========================================================================

    /**
     * Inicializa todos los componentes de la aplicación.
     */
    private fun initComponents() {
        urlValidator = UrlValidator()

        webViewManager = WebViewManager(
            webView = binding.webView,
            navigationCallback = createNavigationCallback(),
            progressCallback = createProgressCallback()
        )

        webViewManager.initialize()
    }

    /**
     * Crea el callback de navegación que conecta el WebViewClient con la UI.
     * Todos los métodos se ejecutan en el hilo principal (garantizado por WebView).
     */
    private fun createNavigationCallback(): SecureWebViewClient.NavigationCallback {
        return object : SecureWebViewClient.NavigationCallback {

            override fun onLoadStarted() {
                setLoadingState(true)
                showEmptyState(false)
                hasPageLoaded = false
            }

            override fun onLoadFinished() {
                setLoadingState(false)
                hasPageLoaded = true
                // Actualizar el campo URL con la URL final (por redirecciones)
                webViewManager.getCurrentUrl()?.let { finalUrl ->
                    if (finalUrl != "about:blank") {
                        binding.etUrl.setText(finalUrl)
                    }
                }
            }

            override fun onConnectionError(description: String) {
                setLoadingState(false)
                showEmptyState(!hasPageLoaded)
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_connection)
                )
            }

            override fun onSslError() {
                setLoadingState(false)
                showEmptyState(!hasPageLoaded)
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_ssl)
                )
            }

            override fun onDomainBlocked() {
                setLoadingState(false)
                binding.webView.visibility = View.INVISIBLE
                showEmptyState(true)
                hasPageLoaded = false
                binding.webView.loadUrl("about:blank")
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_domain_blocked)
                )
            }

            override fun onContentBlocked() {
                setLoadingState(false)
                binding.webView.visibility = View.INVISIBLE
                showEmptyState(true)
                hasPageLoaded = false
                binding.webView.loadUrl("about:blank")
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_keyword_blocked)
                )
            }

            override fun onSafeBrowsingThreat() {
                setLoadingState(false)
                binding.webView.visibility = View.INVISIBLE
                showEmptyState(true)
                hasPageLoaded = false
                binding.webView.loadUrl("about:blank")
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_safe_browsing)
                )
            }

            override fun onProtocolBlocked() {
                setLoadingState(false)
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_protocol_not_allowed)
                )
            }
        }
    }

    /**
     * Crea el callback de progreso para actualizar el LinearProgressIndicator.
     */
    private fun createProgressCallback(): SecureWebChromeClient.ProgressCallback {
        return object : SecureWebChromeClient.ProgressCallback {
            override fun onProgressChanged(progress: Int) {
                if (progress < 100) {
                    binding.progressBar.progress = progress
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.INVISIBLE
                }
            }
        }
    }

    // =========================================================================
    // LISTENERS DE BOTONES Y TECLADO
    // =========================================================================

    /**
     * Configura los listeners de los tres botones de acción.
     */
    private fun setupButtonListeners() {
        // ── Botón Enviar ────────────────────────────────────────────────────
        binding.btnSend.setOnClickListener {
            attemptNavigation()
        }

        // ── Botón Borrar ────────────────────────────────────────────────────
        binding.btnClear.setOnClickListener {
            clearUrlField()
        }

        // ── Botón Recargar ──────────────────────────────────────────────────
        binding.btnReload.setOnClickListener {
            reloadCurrentPage()
        }
    }

    /**
     * Configura la acción del teclado virtual.
     * Al presionar "Go" / "Ir" / Enter en el campo URL, intenta navegar.
     */
    private fun setupKeyboardAction() {
        binding.etUrl.setOnEditorActionListener { _, actionId, event ->
            val isGoAction = actionId == EditorInfo.IME_ACTION_GO
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                             event.action == KeyEvent.ACTION_DOWN

            if (isGoAction || isEnterKey) {
                attemptNavigation()
                true
            } else {
                false
            }
        }
    }

    // =========================================================================
    // LÓGICA DE NAVEGACIÓN
    // =========================================================================

    /**
     * Intenta navegar a la URL ingresada por el usuario.
     *
     * Proceso completo de validación antes de navegar:
     *  1. Extraer la URL del campo de texto.
     *  2. Validar formato con UrlValidator (espacios, HTTPS, regex).
     *  3. Si es válida, cargar en el WebView (las capas de seguridad adicionales
     *     se ejecutarán dentro de SecureWebViewClient).
     *
     * La validación de dominio y keywords a nivel de UI se delega a
     * SecureWebViewClient.shouldOverrideUrlLoading() para evitar duplicación.
     * La validación de formato (Reglas 1, 2, 3) sí se hace aquí para dar
     * retroalimentación inmediata sin necesidad de iniciar la carga.
     */
    private fun attemptNavigation() {
        val rawUrl = binding.etUrl.text?.toString() ?: ""

        when (val result = urlValidator.validate(rawUrl)) {
            is UrlValidator.ValidationResult.Valid -> {
                // Deshabilitar campos INMEDIATAMENTE después de presionar Enviar
                setLoadingState(true)
                hideKeyboard()
                webViewManager.loadUrl(rawUrl)
            }

            is UrlValidator.ValidationResult.Empty -> {
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_url_invalid)
                )
            }

            is UrlValidator.ValidationResult.ContainsSpaces -> {
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_url_spaces)
                )
                // No borrar el campo: el usuario debe ver y corregir el error
            }

            is UrlValidator.ValidationResult.NotHttps -> {
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_url_https_required)
                )
            }

            is UrlValidator.ValidationResult.MalformedUrl -> {
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_url_invalid)
                )
            }
        }
    }

    // =========================================================================
    // ACCIONES DE BOTONES
    // =========================================================================

    /**
     * Limpia completamente el campo de URL.
     *
     * Comportamiento según requisitos:
     *  - Limpiar el texto del campo.
     *  - Regresar el foco al campo.
     *  - Habilitar el botón Enviar (en caso de que estuviera deshabilitado).
     */
    private fun clearUrlField() {
        binding.etUrl.text?.clear()
        binding.etUrl.isEnabled = true
        binding.btnSend.isEnabled = true
        
        // Regresar a la pantalla inicial y limpiar datos
        binding.webView.loadUrl("about:blank")
        Utils.clearWebViewData(binding.webView)
        showEmptyState(true)
        binding.webView.visibility = View.INVISIBLE
        hasPageLoaded = false
        
        focusUrlField()
    }

    /**
     * Recarga la página actual del WebView.
     *
     * Si no hay ninguna página cargada, muestra el mensaje indicado en los requisitos.
     */
    private fun reloadCurrentPage() {
        val reloaded = webViewManager.reload()
        if (!reloaded) {
            Utils.showMessage(
                context = this@MainActivity,
                message = getString(R.string.info_no_page_to_reload)
            )
        }
    }

    // =========================================================================
    // GESTIÓN DE ESTADOS DE UI
    // =========================================================================

    /**
     * Cambia el estado de la UI entre "cargando" e "idle".
     *
     * Estado CARGANDO:
     *  - Campo URL deshabilitado.
     *  - Botón Enviar deshabilitado.
     *  - ProgressBar visible.
     *
     * Estado IDLE:
     *  - Campo URL habilitado.
     *  - Botón Enviar habilitado.
     *  - ProgressBar oculto.
     *
     * @param isLoading true para activar el estado de carga.
     */
    private fun setLoadingState(isLoading: Boolean) {
        binding.etUrl.isEnabled = !isLoading
        binding.btnSend.isEnabled = !isLoading

        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            // Ocultar WebView mientras carga y se analiza para evitar visualización previa
            binding.webView.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.INVISIBLE
            // Volver a mostrar el WebView una vez finalizada la carga y el análisis (si es seguro)
            binding.webView.visibility = View.VISIBLE
        }
    }

    /**
     * Muestra u oculta el panel de estado vacío (bienvenida).
     *
     * @param show true para mostrar el estado vacío, false para ocultarlo.
     */
    private fun showEmptyState(show: Boolean) {
        binding.layoutEmptyState.visibility = if (show) View.VISIBLE else View.GONE
    }

    // =========================================================================
    // UTILIDADES DE UI
    // =========================================================================

    /**
     * Coloca el foco en el campo de URL y muestra el teclado virtual.
     * Invocado al iniciar la app y al presionar el botón Borrar.
     */
    private fun focusUrlField() {
        binding.etUrl.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        binding.etUrl.postDelayed({
            imm.showSoftInput(binding.etUrl, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    /**
     * Oculta el teclado virtual. Se llama antes de iniciar la carga de una página.
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.rootLayout.windowToken, 0)
    }
}
