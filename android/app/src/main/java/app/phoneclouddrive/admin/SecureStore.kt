package app.phoneclouddrive.admin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the admin password for "Stay signed in", encrypted with an AES-256-GCM key that lives in the
 * Android Keystore (hardware-backed where available, never leaves the device, survives reboots).
 */
class SecureStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("drive_admin_secure", Context.MODE_PRIVATE)

    fun save(password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(PREF_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    /** Returns the saved password, or null if none is saved or it can't be decrypted (e.g. key reset). */
    fun load(): String? {
        val iv = prefs.getString(PREF_IV, null) ?: return null
        val ct = prefs.getString(PREF_CT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun hasSaved(): Boolean = prefs.contains(PREF_CT)

    fun clear() {
        prefs.edit().remove(PREF_IV).remove(PREF_CT).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "drive_admin_login"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREF_IV = "iv"
        const val PREF_CT = "ct"
    }
}
