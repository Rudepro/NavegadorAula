package com.example.navegadoraula.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BrowsingHistoryManager
 *
 * Gestiona el historial de navegación del navegador seguro.
 *
 * Características:
 *  - Máximo 10 entradas (las más recientes primero).
 *  - Solo guarda páginas permitidas (se llama desde onLoadFinished, DESPUÉS de
 *    que todas las capas de seguridad confirmaron que el contenido es seguro).
 *  - Persiste el historial en SharedPreferences para sobrevivir reinicios.
 *  - Permite borrar todo o una entrada individual.
 */
class BrowsingHistoryManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "navegador_aula_history"
        private const val KEY_HISTORY = "history_entries"
        private const val MAX_ENTRIES = 10
    }

    /**
     * Modelo de datos de una entrada del historial.
     *
     * @param url   URL completa de la página visitada.
     * @param title Título de la página (de WebView.title) o la URL si no hay título.
     * @param timestamp Marca de tiempo en milisegundos (System.currentTimeMillis).
     */
    data class HistoryEntry(
        val url: String,
        val title: String,
        val timestamp: Long
    ) {
        /** Formatea el timestamp para mostrar en la UI (ej: "29/06/2026 19:45"). */
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        /** Retorna el dominio corto de la URL para mostrar en la UI. */
        fun shortDomain(): String {
            return try {
                val host = java.net.URL(url).host
                host.removePrefix("www.")
            } catch (e: Exception) {
                url
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Lista en memoria (más reciente primero)
    private val entries: MutableList<HistoryEntry> = mutableListOf()

    init {
        loadFromPrefs()
    }

    // =========================================================================
    // API PÚBLICA
    // =========================================================================

    /**
     * Agrega una nueva entrada al historial.
     *
     * Reglas:
     *  1. Solo se agrega si la URL no es nula, vacía, ni "about:blank".
     *  2. Si la misma URL ya está en la primera posición, no se duplica.
     *  3. Si ya existe en otra posición, se mueve al principio.
     *  4. Se mantiene un máximo de MAX_ENTRIES entradas.
     *
     * @param url   URL de la página visitada.
     * @param title Título de la página (puede ser null si el sitio no lo tiene).
     */
    fun addEntry(url: String, title: String?) {
        if (url.isBlank() || url == "about:blank") return

        val resolvedTitle = if (!title.isNullOrBlank()) title else url
        val normalizedUrl = url.trimEnd('/')

        // Si la URL ya está al principio, solo actualizamos el título
        if (entries.isNotEmpty() && entries[0].url.trimEnd('/') == normalizedUrl) {
            val updated = entries[0].copy(title = resolvedTitle, timestamp = System.currentTimeMillis())
            entries[0] = updated
            saveToPrefs()
            return
        }

        // Si existe en otra posición, la eliminamos primero (para moverla al principio)
        entries.removeAll { it.url.trimEnd('/') == normalizedUrl }

        // Agregar al principio
        entries.add(0, HistoryEntry(url, resolvedTitle, System.currentTimeMillis()))

        // Mantener máximo
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }

        saveToPrefs()
    }

    /**
     * Retorna una copia inmutable de la lista de entradas (más reciente primero).
     */
    fun getEntries(): List<HistoryEntry> = entries.toList()

    /**
     * Elimina la entrada en la posición dada.
     *
     * @param index Índice de la entrada a eliminar (0 = más reciente).
     * @return true si se eliminó correctamente, false si el índice era inválido.
     */
    fun removeEntry(index: Int): Boolean {
        if (index < 0 || index >= entries.size) return false
        entries.removeAt(index)
        saveToPrefs()
        return true
    }

    /**
     * Borra todas las entradas del historial (memoria y persistencia).
     */
    fun clearAll() {
        entries.clear()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /** Retorna true si el historial está vacío. */
    fun isEmpty(): Boolean = entries.isEmpty()

    // =========================================================================
    // PERSISTENCIA
    // =========================================================================

    private fun saveToPrefs() {
        val jsonArray = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("url", entry.url)
            obj.put("title", entry.title)
            obj.put("timestamp", entry.timestamp)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                entries.add(
                    HistoryEntry(
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            // Si el JSON está corrupto, iniciamos con historial vacío
            entries.clear()
        }
    }
}
