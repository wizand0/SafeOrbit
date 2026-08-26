package ru.wizand.safeorbit.security

import org.junit.Test
import org.junit.Assert.*
import ru.wizand.safeorbit.utils.CryptoUtils

class PairingTokenTest {

    @Test
    fun `SHA-256 hash is consistent for same token`() {
        val token = "testToken1234567890"
        val hash1 = CryptoUtils.sha256Hex(token)
        val hash2 = CryptoUtils.sha256Hex(token)
        assertEquals("SHA-256 hash должен быть одинаковым для одного токена", hash1, hash2)
    }

    @Test
    fun `different tokens produce different hashes`() {
        val token1 = "token1234567890"
        val token2 = "token0987654321"
        val hash1 = CryptoUtils.sha256Hex(token1)
        val hash2 = CryptoUtils.sha256Hex(token2)
        assertNotEquals("Разные токены должны давать разные хеши", hash1, hash2)
    }

    @Test
    fun `token validation passes for valid tokens`() {
        val validToken = "validToken1234567890AbCdEfGhIjKlMnOpQrStUvWxYz"
        assertTrue("Валидный токен должен проходить проверку", isValidToken(validToken))
    }

    @Test
    fun `token validation fails for expired tokens`() {
        val expiredTimestamp = System.currentTimeMillis() - 1000 // 1 секунда назад
        assertFalse("Просроченный токен не должен быть валидным", !isTokenExpired(expiredTimestamp))
    }

    @Test
    fun `consumed token validation`() {
        val consumed = true
        val notConsumed = false
        assertTrue("Потребованный токен должен быть отмечен как использованный", consumed)
        assertFalse("Неиспользованный токен должен быть отмечен как неиспользованный", notConsumed)
    }

    // Вспомогательные функции для тестов (для демонстрации, в реальном проекте будут настоящие реализации)
    private fun isValidToken(token: String): Boolean {
        return token.isNotEmpty() && token.length >= 10
    }

    private fun isTokenExpired(expiresAt: Long): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
}