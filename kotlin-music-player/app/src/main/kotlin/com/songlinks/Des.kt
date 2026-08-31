package com.songlinks

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Port of src/des.js — DES ECB PKCS5 (CryptoJS DES ECB PKCS7 compatible)
object Des {
    private const val DEFAULT_KEY = "38346591"
    private const val TRANSFORMATION = "DES/ECB/PKCS5Padding"
    private const val ALGO = "DES"

    private fun parseKey(keyStr: String = DEFAULT_KEY): SecretKeySpec {
        val keyBytes = keyStr.toByteArray(Charsets.UTF_8).let {
            when {
                it.size == 8 -> it
                it.size > 8 -> it.copyOf(8)
                else -> it.copyOf(8) // zero-pad
            }
        }
        return SecretKeySpec(keyBytes, ALGO)
    }

    fun decryptBase64(cipherB64: String, keyStr: String = DEFAULT_KEY): String {
        val cleaned = cipherB64.trim().replace("\\s".toRegex(), "")
            .replace('-', '+').replace('_', '/')
            .let { val p = it.length % 4; if (p != 0) it + "=".repeat(4 - p) else it }
        val enc = Base64.decode(cleaned, Base64.DEFAULT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, parseKey(keyStr))
        val dec = cipher.doFinal(enc)
        val txt = String(dec, Charsets.UTF_8).trim()
        require(txt.isNotEmpty()) { "DES decrypt empty — wrong key or corrupted base64" }
        return txt
    }

    fun encryptBase64(plain: String, keyStr: String = DEFAULT_KEY): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, parseKey(keyStr))
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(enc, Base64.NO_WRAP)
    }
}
