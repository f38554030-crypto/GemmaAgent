package com.gemmaagent.app.util

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Enkripsi backup: AES-256-GCM dengan kunci dari kata sandi (PBKDF2-HMAC-SHA256, 120.000 iterasi). */
object BackupCrypto {
    private const val ITER = 120_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = f.generateSecret(PBEKeySpec(password, salt, ITER, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        return JSONObject()
            .put("app", "GemmaChatHybrid")
            .put("enc", "AES-256-GCM/PBKDF2-SHA256")
            .put("iter", ITER)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(data))
            .toString()
    }

    fun isEncrypted(text: String) = runCatching { JSONObject(text).has("enc") }.getOrDefault(false)

    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text)
        fun d(k: String) = Base64.decode(o.getString(k), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password.toCharArray(), d("salt")), GCMParameterSpec(128, d("iv")))
        return try {
            String(c.doFinal(d("data")), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("Kata sandi salah atau file rusak.")
        }
    }
}
