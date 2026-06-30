package com.example.navegadoraula.security

import com.example.navegadoraula.data.BlockedKeywords
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KeywordFilter
 *
 * Responsabilidad única: Analizar URLs y textos en busca de palabras clave prohibidas.
 *
 * Contextos de uso:
 *  1. Análisis previo a la navegación: verifica la URL completa.
 *  2. Análisis de metadatos: analiza título, meta keywords, meta description, Open Graph.
 *
 * Incluye sanitización avanzada para evitar evasión mediante ofuscación.
 */
class KeywordFilter {

    sealed class KeywordCheckResult {
        data object Clean : KeywordCheckResult()
        data class Blocked(val category: String) : KeywordCheckResult()
    }

    /**
     * Sanitiza el texto para neutralizar ofuscación.
     * Elimina acentos y reemplaza caracteres leetspeak (ej: @->a, 0->o).
     */
    private fun sanitizeText(text: String): String {
        // 1. Convertir a minúsculas
        var sanitized = text.lowercase()
        
        // 2. Reemplazar leetspeak básico
        sanitized = sanitized.replace('@', 'a')
            .replace('0', 'o')
            .replace('$', 's')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('!', 'i')
            .replace('5', 's')

        // 3. Eliminar acentos
        val normalized = Normalizer.normalize(sanitized, Normalizer.Form.NFD)
        return normalized.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    suspend fun analyzeUrl(url: String): KeywordCheckResult = withContext(Dispatchers.Default) {
        val sanitized = sanitizeText(url)
        if (BlockedKeywords.urlContainsBlockedKeyword(sanitized)) {
            KeywordCheckResult.Blocked("url_keyword")
        } else {
            KeywordCheckResult.Clean
        }
    }

    suspend fun analyzeMetadata(
        title: String,
        metaKeywords: String,
        metaDescription: String,
        ogTitle: String,
        ogDescription: String
    ): KeywordCheckResult = withContext(Dispatchers.Default) {
        val combinedText = buildString {
            append(title).append(" ")
            append(metaKeywords).append(" ")
            append(metaDescription).append(" ")
            append(ogTitle).append(" ")
            append(ogDescription)
        }

        val sanitized = sanitizeText(combinedText)
        val blockedCategory = BlockedKeywords.analyzeText(sanitized)
        
        if (blockedCategory != null) {
            KeywordCheckResult.Blocked(blockedCategory)
        } else {
            KeywordCheckResult.Clean
        }
    }

    suspend fun analyzeHtmlContent(htmlContent: String): KeywordCheckResult = withContext(Dispatchers.Default) {
        val sampleText = if (htmlContent.length > 5000) {
            htmlContent.substring(0, 5000)
        } else {
            htmlContent
        }

        val sanitized = sanitizeText(sampleText)
        val blockedCategory = BlockedKeywords.analyzeText(sanitized)
        
        if (blockedCategory != null) {
            KeywordCheckResult.Blocked(blockedCategory)
        } else {
            KeywordCheckResult.Clean
        }
    }
}
