
package com.yazid.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePrefs(context: Context) {

    private val appContext = context.applicationContext

    private val securePrefs = appContext.getSharedPreferences(
        "yazid_vpn_secure",
        Context.MODE_PRIVATE
    )

    private val legacyPrefs = appContext.getSharedPreferences(
        "yazid_vpn",
        Context.MODE_PRIVATE
    )

    private val alias = "YAZID_VPN_AES_KEY"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(alias, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )

        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val iv = cipher.iv

        return Base64.encodeToString(
            iv + encrypted,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String? {
        return try {
            val data = Base64.decode(value, Base64.NO_WRAP)

            if (data.size <= 12) return null

            val iv = data.copyOfRange(0, 12)
            val encrypted = data.copyOfRange(12, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(128, iv)
            )

            String(
                cipher.doFinal(encrypted),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getString(key: String, defaultValue: String?): String? {
        val stored = securePrefs.getString(key, null)

        if (stored != null) {
            return decrypt(stored) ?: defaultValue
        }

        // انتقال داده‌های نسخه قدیمی به ذخیره‌سازی رمزنگاری‌شده
        val oldValue = legacyPrefs.getString(key, null)

        if (oldValue != null) {
            try {
                securePrefs.edit()
                    .putString(key, encrypt(oldValue))
                    .commit()
            } catch (_: Exception) {
                return defaultValue
            }

            return oldValue
        }

        return defaultValue
    }

    fun edit(): Editor {
        return Editor()
    }

    inner class Editor {

        private val changes = linkedMapOf<String, String?>()

        fun putString(key: String, value: String?): Editor {
            changes[key] = value
            return this
        }

        @Synchronized
        fun apply() {
            val editor = securePrefs.edit()

            for ((key, value) in changes) {
                if (value == null) {
                    editor.remove(key)
                } else {
                    editor.putString(key, encrypt(value))
                }
            }

            editor.apply()
        }
    }
}
