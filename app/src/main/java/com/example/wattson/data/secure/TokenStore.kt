package com.example.wattson.data.secure

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.wattson.data.rivian.RivianSession
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted Rivian session store (Android counterpart of upstream crypto.py). Tokens are
 * sealed with AES-256-GCM under a key that lives in the Android Keystore and never leaves
 * it. Only session tokens are stored; the password is never written anywhere.
 */
class TokenStore(private val prefs: SharedPreferences) {

    fun load(): RivianSession? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            fromJson(String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8))
        } catch (e: Exception) {
            null // key lost (e.g. restored to a new device): the user links again
        }
    }

    fun save(session: RivianSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(toJson(session).toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun toJson(s: RivianSession) = JSONObject()
        .put("email", s.email)
        .put("user_session_token", s.userSessionToken)
        .put("access_token", s.accessToken ?: JSONObject.NULL)
        .put("refresh_token", s.refreshToken ?: JSONObject.NULL)
        .put("vehicle_id", s.vehicleId ?: JSONObject.NULL)
        .put("vehicle_name", s.vehicleName ?: JSONObject.NULL)
        .put("vin", s.vehicleVin ?: JSONObject.NULL)
        .toString()

    private fun fromJson(json: String): RivianSession {
        val o = JSONObject(json)
        fun opt(name: String) = if (o.isNull(name)) null else o.optString(name)
        return RivianSession(
            email = o.getString("email"),
            userSessionToken = o.getString("user_session_token"),
            accessToken = opt("access_token"),
            refreshToken = opt("refresh_token"),
            vehicleId = opt("vehicle_id"),
            vehicleName = opt("vehicle_name"),
            vehicleVin = opt("vin")
        )
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "wattson_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_BLOB = "rivian_session_sealed"
    }
}
