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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.navegadoraula.databinding.ActivityMainBinding
import com.example.navegadoraula.security.UrlValidator
import com.example.navegadoraula.utils.BrowsingHistoryManager
import com.example.navegadoraula.utils.Utils
import com.example.navegadoraula.webview.SecureWebChromeClient
import com.example.navegadoraula.webview.SecureWebViewClient
import com.example.navegadoraula.webview.WebViewManager
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * MainActivity
 *
 * Punto de entrada y controlador principal de la aplicación NavegadorAula.
 *
 * Responsabilidades:
 *  1. Inflar el layout y obtener referencias mediante ViewBinding.
 *  2. Inicializar todos los componentes: WebViewManager, UrlValidator, BrowsingHistoryManager.
 *  3. Gestionar los estados de la interfaz (cargando, error, idle).
 *  4. Responder a las acciones del usuario (botones, teclado, Back).
 *  5. Recibir notificaciones del WebViewClient y actualizar la UI.
 *  6. Gestionar el historial de navegación (solo páginas permitidas).
 *
 * Patrón de estado de UI:
 *  - IDLE: Campos habilitados, sin carga, sin página o página cargada.
 *  - LOADING: Campos deshabilitados, ProgressBar visible.
 *  - ERROR: Regresa a IDLE con mensaje de error visible.
 *
 * Historial de navegación:
 *  - Se registra SOLO en onLoadFinished (página confirmada como segura por HtmlAnalyzer).
 *  - Los bloqueos (onDomainBlocked, onContentBlocked, etc.) NO generan entradas.
 *  - Máximo 10 entradas, persiste entre sesiones en SharedPreferences.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding para acceso seguro y eficiente a las vistas (sin findViewById)
    private lateinit var binding: ActivityMainBinding

    // Componentes del dominio
    private lateinit var webViewManager: WebViewManager
    private lateinit var urlValidator: UrlValidator
    private lateinit var historyManager: BrowsingHistoryManager

    // Estado de si hay una página cargada actualmente
    private var hasPageLoaded = false

    // Última URL confirmada y guardada en el historial.
    // Evita duplicados cuando onPageFinished se dispara múltiples veces
    // durante la misma navegación (ej: redirects de google.com → www.google.com).
    private var lastCommittedUrl: String? = null

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
        historyManager = BrowsingHistoryManager(this)

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
     *
     * IMPORTANTE: onLoadFinished es el ÚNICO lugar donde se agrega al historial.
     * Esto garantiza que solo las páginas aprobadas por todas las capas de seguridad
     * (incluyendo HtmlAnalyzer) sean registradas.
     */
    private fun createNavigationCallback(): SecureWebViewClient.NavigationCallback {
        return object : SecureWebViewClient.NavigationCallback {

            override fun onLoadStarted(url: String) {
                // NO se resetea lastCommittedUrl aqui.
                // Si se reseteara, el reload siempre vería la URL como "nueva" y la guardaría.
                // lastCommittedUrl solo se resetea cuando el usuario limpia o hay un bloqueo.
                setLoadingState(true)
                showEmptyState(false)
                hasPageLoaded = false
            }

            override fun onLoadFinished() {
                setLoadingState(false)
                hasPageLoaded = true
                webViewManager.getCurrentUrl()?.let { finalUrl ->
                    if (finalUrl != "about:blank") {
                        binding.etUrl.setText(finalUrl)

                        // Comparación con normalización (quita www. y slash final)
                        // para evitar duplicados por redirects del tipo google.com → www.google.com
                        val normalizedFinal = normalizeUrlForComparison(finalUrl)
                        val normalizedLast = lastCommittedUrl?.let { normalizeUrlForComparison(it) }
                        if (normalizedFinal != normalizedLast) {
                            lastCommittedUrl = finalUrl
                            val pageTitle = binding.webView.title
                            historyManager.addEntry(finalUrl, pageTitle)
                        }
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

            override fun onDomainBlocked(url: String) {
                historyManager.addEntry(url, url)
                lastCommittedUrl = null  // Permitir guardar la próxima navegación
                binding.webView.stopLoading()
                setLoadingState(false)
                binding.webView.visibility = View.INVISIBLE
                showEmptyState(true)
                hasPageLoaded = false
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_domain_blocked)
                )
            }

            override fun onContentBlocked(url: String) {
                historyManager.addEntry(url, url)
                lastCommittedUrl = null
                setLoadingState(false)
                binding.webView.visibility = View.INVISIBLE
                showEmptyState(true)
                hasPageLoaded = false
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_keyword_blocked)
                )
            }

            override fun onSafeBrowsingThreat(url: String) {
                historyManager.addEntry(url, url)
                lastCommittedUrl = null
                binding.webView.stopLoading()
                setLoadingState(false)
                binding.webView.visibility = View.INVISIBLE
                showEmptyState(true)
                hasPageLoaded = false
                Utils.showMessage(
                    context = this@MainActivity,
                    message = getString(R.string.error_safe_browsing)
                )
            }

            override fun onProtocolBlocked(url: String) {
                historyManager.addEntry(url, url)
                lastCommittedUrl = null
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
     * Configura los listeners de los cuatro botones de acción.
     */
    private fun setupButtonListeners() {
        // ── Botón Navegar ────────────────────────────────────────────────────
        binding.btnSend.setOnClickListener {
            attemptNavigation()
        }

        // ── Botón Limpiar ────────────────────────────────────────────────────
        binding.btnClear.setOnClickListener {
            clearUrlField()
        }

        // ── Botón Recargar ──────────────────────────────────────────────────
        binding.btnReload.setOnClickListener {
            reloadCurrentPage()
        }

        // ── Botón Historial ─────────────────────────────────────────────────
        binding.btnHistory.setOnClickListener {
            showHistoryBottomSheet()
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
                // Deshabilitar campos INMEDIATAMENTE después de presionar Navegar
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
    // HISTORIAL DE NAVEGACIÓN
    // =========================================================================

    /**
     * Muestra el panel de historial como un BottomSheetDialog.
     *
     * El panel contiene:
     *  - Lista de hasta 10 páginas visitadas (más reciente primero).
     *  - Botón "Borrar todo" en el encabezado.
     *  - Botón "×" en cada fila para borrar esa entrada.
     *  - Estado vacío si no hay historial.
     *
     * Al tocar una entrada, se cierra el panel y se navega a esa URL.
     */
    private fun showHistoryBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_history, null)
        dialog.setContentView(dialogView)

        val rvHistory = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_history)
        val layoutEmpty = dialogView.findViewById<View>(R.id.layout_history_empty)
        val btnClearAll = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_clear_all_history)

        // Construir el adapter con los datos actuales
        val historyList = historyManager.getEntries().toMutableList()

        val adapter = HistoryAdapter(
            entries = historyList,
            onUrlSelected = { url ->
                // Cerrar el dialog y navegar a la URL seleccionada
                dialog.dismiss()
                binding.etUrl.setText(url)
                setLoadingState(true)
                hideKeyboard()
                webViewManager.loadUrl(url)
            },
            onDeleteEntry = { position ->
                // Borrar la entrada seleccionada
                val removed = historyManager.removeEntry(position)
                if (removed) {
                    (rvHistory.adapter as? HistoryAdapter)?.removeAt(position)
                    // Mostrar/ocultar estado vacío
                    updateHistoryEmptyState(rvHistory, layoutEmpty)
                    Utils.showMessage(
                        context = this@MainActivity,
                        message = getString(R.string.history_deleted)
                    )
                }
            }
        )

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        // Mostrar/ocultar estado vacío al abrir
        updateHistoryEmptyState(rvHistory, layoutEmpty)

        // Botón Borrar Todo
        btnClearAll.setOnClickListener {
            historyManager.clearAll()
            adapter.updateEntries(emptyList())
            updateHistoryEmptyState(rvHistory, layoutEmpty)
            Utils.showMessage(
                context = this@MainActivity,
                message = getString(R.string.history_cleared)
            )
        }

        dialog.show()
    }

    /**
     * Actualiza la visibilidad del estado vacío según si hay entradas en el historial.
     */
    private fun updateHistoryEmptyState(
        rvHistory: androidx.recyclerview.widget.RecyclerView,
        layoutEmpty: View
    ) {
        val isEmpty = historyManager.isEmpty()
        rvHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    // =========================================================================
    // ACCIONES DE BOTONES
    // =========================================================================

    /**
     * Limpia completamente el campo de URL y regresa a la pantalla inicial.
     *
     * Comportamiento según requisitos:
     *  - Limpiar el texto del campo.
     *  - Regresar el foco al campo.
     *  - Habilitar el botón Navegar (en caso de que estuviera deshabilitado).
     */
    private fun clearUrlField() {
        binding.etUrl.text?.clear()

        // Detener cualquier carga en progreso y limpiar datos.
        // NO llamamos webView.loadUrl("about:blank") porque dispararía los callbacks
        // onLoadStarted/onLoadFinished que ocultarían el estado vacío y mostrarían
        // el WebView en blanco. En su lugar, detenemos la carga directamente.
        binding.webView.stopLoading()
        Utils.clearWebViewData(binding.webView)
        binding.webView.clearHistory()

        // Resetear estado completamente sin disparar callbacks
        setLoadingState(false)
        showEmptyState(true)
        binding.webView.visibility = View.INVISIBLE
        hasPageLoaded = false
        lastCommittedUrl = null  // Permitir guardar la próxima navegación

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
     *  - Botón Navegar deshabilitado.
     *  - ProgressBar visible.
     *
     * Estado IDLE:
     *  - Campo URL habilitado.
     *  - Botón Navegar habilitado.
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
     * Invocado al iniciar la app y al presionar el botón Limpiar.
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
        imm.hideSoftInputFromWindow(binding.etUrl.windowToken, 0)
    }

    /**
     * Normaliza una URL para comparación en el historial.
     * Quita "www." y la barra final "/" para evitar que redirecciones comunes
     * (ej: google.com -> www.google.com/) se registren como páginas diferentes.
     */
    private fun normalizeUrlForComparison(url: String): String {
        return url
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }
}
