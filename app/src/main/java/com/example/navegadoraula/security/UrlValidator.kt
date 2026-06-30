package com.example.navegadoraula.security

/**
 * UrlValidator
 *
 * Responsabilidad única: Validar el formato de una URL ingresada por el usuario
 * antes de intentar navegar, aplicando exactamente las reglas especificadas.
 *
 * Reglas implementadas:
 *   Regla 1 — No se permiten espacios en blanco (inicio, fin, medio, tabs, newlines).
 *   Regla 2 — La URL debe comenzar exactamente con "https://".
 *   Regla 3 — La URL debe ser sintácticamente válida (validación por regex).
 *
 * Principio de diseño: Retorna un resultado sellado (sealed class) para
 * forzar al llamador a manejar todos los casos posibles.
 */
class UrlValidator {

    /**
     * Resultado sellado de la validación.
     * El llamador debe manejar cada caso con un when exhaustivo.
     */
    sealed class ValidationResult {
        /** La URL es válida y puede usarse para navegar. */
        data object Valid : ValidationResult()

        /** La URL contiene espacios en blanco. */
        data object ContainsSpaces : ValidationResult()

        /** La URL no comienza con https://. */
        data object NotHttps : ValidationResult()

        /** La URL está mal formada (regex falla). */
        data object MalformedUrl : ValidationResult()

        /** El campo está vacío. */
        data object Empty : ValidationResult()
    }

    companion object {
        /**
         * Regex para validar URLs HTTPS bien formadas.
         *
         * Reglas del regex:
         *  - Debe comenzar con https://
         *  - Seguido de dominio válido (letras, números, guiones, puntos)
         *  - Dominio puede tener subdominios: www.sub.example.com
         *  - TLD de 2 a 24 caracteres
         *  - Puerto opcional: :8080
         *  - Ruta, query string y fragmento opcionales
         */
        private val URL_REGEX = Regex(
            "^https://" +
            "([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)" +
            "+[a-zA-Z]{2,24}" +
            "(:[0-9]{1,5})?" +
            "(/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?" +
            "$"
        )

        /**
         * Regex para detectar cualquier tipo de espacio en blanco.
         * Incluye: espacio regular, tab (\t), salto de línea (\n), retorno (\r),
         * espacio no separable (\u00A0), y otros espacios Unicode.
         */
        private val WHITESPACE_REGEX = Regex("[\\s\\u00A0\\u2000-\\u200B\\u2028\\u2029]")
    }

    /**
     * Valida una URL siguiendo las tres reglas obligatorias en orden de prioridad.
     *
     * @param rawUrl La cadena ingresada por el usuario (sin procesar).
     * @return [ValidationResult] indicando si es válida o el tipo de error.
     */
    fun validate(rawUrl: String): ValidationResult {
        // Verificar que no esté vacía
        if (rawUrl.isBlank()) {
            return ValidationResult.Empty
        }

        // Regla 1: No se permiten espacios en blanco de ningún tipo
        if (WHITESPACE_REGEX.containsMatchIn(rawUrl)) {
            return ValidationResult.ContainsSpaces
        }

        // Regla 2: Debe comenzar exactamente con "https://"
        // Se verifica de forma estricta antes que el regex para dar mensaje específico
        if (!rawUrl.startsWith("https://")) {
            return ValidationResult.NotHttps
        }

        // Regla 3: Debe ser una URL bien formada según el regex
        if (!URL_REGEX.matches(rawUrl)) {
            return ValidationResult.MalformedUrl
        }

        return ValidationResult.Valid
    }
}
