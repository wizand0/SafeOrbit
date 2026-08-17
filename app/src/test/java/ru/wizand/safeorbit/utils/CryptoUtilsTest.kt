package ru.wizand.safeorbit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun `encrypt then decrypt returns original text`() {
        val key = CryptoUtils.deriveKey("SERVER123", "654321")
        val plaintext = "Тест уведомления: Привет, мир! — 1234567890"
        val encrypted = CryptoUtils.encrypt(key, plaintext)
        assertEquals(plaintext, CryptoUtils.decryptOrNull(key, encrypted))
    }

    @Test
    fun `wrong key cannot decrypt`() {
        val key = CryptoUtils.deriveKey("SERVER123", "654321")
        val wrongKey = CryptoUtils.deriveKey("SERVER123", "000000")
        val encrypted = CryptoUtils.encrypt(key, "secret")
        assertNull(CryptoUtils.decryptOrNull(wrongKey, encrypted))
    }

    @Test
    fun `same input produces different ciphertext`() {
        val key = CryptoUtils.deriveKey("SERVER123", "654321")
        val a = CryptoUtils.encrypt(key, "same")
        val b = CryptoUtils.encrypt(key, "same")
        assertNotEquals(a, b)
        assertEquals("same", CryptoUtils.decryptOrNull(key, a))
        assertEquals("same", CryptoUtils.decryptOrNull(key, b))
    }

    @Test
    fun `corrupted payload returns null`() {
        val key = CryptoUtils.deriveKey("SERVER123", "654321")
        assertNull(CryptoUtils.decryptOrNull(key, "not-base64!!!"))
        assertNull(CryptoUtils.decryptOrNull(key, "c2hvcnQ="))
    }

    @Test
    fun `empty and long texts roundtrip`() {
        val key = CryptoUtils.deriveKey("SERVER123", "654321")
        assertEquals("", CryptoUtils.decryptOrNull(key, CryptoUtils.encrypt(key, "")))
        val long = "x".repeat(4000)
        assertEquals(long, CryptoUtils.decryptOrNull(key, CryptoUtils.encrypt(key, long)))
    }
}
