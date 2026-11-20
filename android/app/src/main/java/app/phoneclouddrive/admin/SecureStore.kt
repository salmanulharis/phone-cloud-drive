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
 * Small secrets (admin password, Cloudflare tunnel token), each encrypted with an AES-256-GCM key
 * that lives in the Android Keystore (hardware-backed where available, never leaves the device,
 * survives reboots). Only ciphertext and IVs are written to SharedPreferences.
 */
class SecureStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("drive_admin_secure", Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(ivKey(name), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(ctKey(name), Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    /** The stored value, or null if there is none or it can't be decrypted (e.g. key reset). */
    fun get(name: String): String? {
        val iv = prefs.getString(ivKey(name), null) ?: return null
        val ct = prefs.getString(ctKey(name), null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            remove(name)
            null
        }
    }

    fun has(name: String): Boolean = prefs.contains(ctKey(name))

    fun remove(name: String) {
        prefs.edit().remove(ivKey(name)).remove(ctKey(name)).apply()
    }

    // "Stay signed in" for an external (Termux) server. Kept on the original keys so existing
    // saved logins keep working after the update.
    fun save(password: String) = put(LOGIN, password)
    fun load(): String? = get(LOGIN)
    fun hasSaved(): Boolean = has(LOGIN)
    fun clear() = remove(LOGIN)

    private fun ivKey(name: String) = if (name == LOGIN) "iv" else "$name.iv"
    private fun ctKey(name: String) = if (name == LOGIN) "ct" else "$name.ct"

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

    companion object {
        const val ADMIN_PASSWORD = "builtin_admin"
        const val TUNNEL_TOKEN = "tunnel_token"
        private const val LOGIN = "login"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "drive_admin_login"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
