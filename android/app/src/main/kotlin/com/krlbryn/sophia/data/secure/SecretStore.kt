package com.krlbryn.sophia.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Stores small secrets such as AI provider API keys. Values never leave the device. */
interface SecretStore {
    /** Names of the secrets that are currently set (never their values). */
    val storedNames: StateFlow<Set<String>>
    suspend fun put(name: String, value: String)
    suspend fun get(name: String): String?
    suspend fun remove(name: String)
}

/**
 * AES-256-GCM encryption with a non-exportable key held in the Android Keystore. Ciphertext (IV + data)
 * lives in a private SharedPreferences file that is excluded from backups.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val prefs = context.getSharedPreferences("sophia_secrets", Context.MODE_PRIVATE)
    private val _names = MutableStateFlow(prefs.all.keys.toSet())
    override val storedNames: StateFlow<Set<String>> = _names.asStateFlow()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override suspend fun put(name: String, value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        // Binds the ciphertext to its slot, so a value cannot be moved to another name.
        cipher.updateAAD(name.toByteArray())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + encrypted
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        _names.value = prefs.all.keys.toSet() + name
    }

    override suspend fun get(name: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(name, null) ?: return@withContext null
        try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray())
            String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // The key was invalidated (for example after a device restore) or the data was tampered with.
            prefs.edit().remove(name).apply()
            _names.value = _names.value - name
            null
        }
    }

    override suspend fun remove(name: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(name).apply()
        _names.value = _names.value - name
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "sophia_secret_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
