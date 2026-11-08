package net.kuafuai.andee.brain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Vault: Keystore-backed AES/GCM encryption for secrets.
 * Keys never leave Keystore; only encrypted blobs stored in EncryptedSharedPreferences.
 */
class VaultManager(private val context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        "vault",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "vault_master_key"

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keystore.containsAlias(keyAlias)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }
    }

    /** Store a secret (encrypts with Keystore key, saves to EncryptedSharedPreferences) */
    suspend fun putSecret(key: String, value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = keystore.getKey(keyAlias, null) as SecretKey
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(value.toByteArray())
        val iv = cipher.iv
        // Store IV + ciphertext as base64
        val combined = iv + encrypted
        prefs.edit().putString(key, android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)).apply()
    }

    /** Retrieve a secret */
    suspend fun getSecret(key: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(key, null) ?: return@withContext null
        val combined = android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = keystore.getKey(keyAlias, null) as SecretKey
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(ciphertext)
        String(decrypted)
    }

    /** Delete a secret */
    suspend fun deleteSecret(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    /** List all secret keys */
    fun listSecrets(): List<String> = prefs.all.keys.filter { !it.startsWith("_") }.toList()

    /** Fill a secret into a template (for tool calls that need secrets) */
    suspend fun fillSecret(template: String): String = withContext(Dispatchers.IO) {
        var result = template
        val secretRefs = Regex("\\{\\{vault:([^}]+)\\}\\}").findAll(template)
        for (match in secretRefs) {
            val key = match.groupValues[1]
            val secret = getSecret(key) ?: return@withContext template // Return original if secret not found
            result = result.replace(match.value, secret)
        }
        result
    }
}