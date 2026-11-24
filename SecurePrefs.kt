
package com.yazid.vpn

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecurePrefs(context: Context) {

    private val secure = context.getSharedPreferences(
        "yazid_vpn_secure", Context.MODE_PRIVATE
    )

    private val legacy = context.getSharedPreferences(
        "yazid_vpn", Context.MODE_PRIVATE
    )

    fun getString(key: String, defaultValue: String?): String? {
        val encrypted = secure.getString(key, null)

        if (encrypted != null) {
            return decrypt(encrypted)
        }

        val oldValue = legacy.getString(key, null)
            ?: return defaultValue

        val saved = secure.edit()
            .putString(key, encrypt(oldValue))
            .commit()

        check(saved) { "Secure migration failed" }

        legacy.edit().remove(key).commit()

        return oldValue
    }

    fun edit() = Editor()

    inner class Editor {
        private val values = linkedMapOf<String, String>()

        fun putString(key: String, value: String?): Editor {
            if (value != null) values[key] = value
            return this
        }

        fun apply() {
            val editor = secure.edit()

            values.forEach { (key, value) ->
                editor.putString(key, encrypt(value))
            }

            check(editor.commit()) {
                "Secure storage failed"
            }

            values.keys.forEach { key ->
                legacy.edit().remove(key).commit()
            }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )

        return "v1:" + Base64.encodeToString(
            cipher.iv + encrypted,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        require(value.startsWith("v1:"))

        val packed = Base64.decode(
            value.removePrefix("v1:"),
            Base64.NO_WRAP
        )

        require(packed.size > 12)

        val iv = packed.copyOfRange(0, 12)
        val data = packed.copyOfRange(12, packed.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv)
        )

        return String(
            cipher.doFinal(data),
            Charsets.UTF_8
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)

        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }

        val generator = KeyGenerator.getInstance(
            "AES",
            "AndroidKeyStore"
        )

        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(
                android.security.keystore.KeyProperties.BLOCK_MODE_GCM
            )
            .setEncryptionPaddings(
                android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)

        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "com.yazid.vpn.local-storage.aes"
    }
}
