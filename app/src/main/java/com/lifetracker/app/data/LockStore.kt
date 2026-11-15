package com.lifetracker.app.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The lock choices, kept on this phone only (the file is left out of backups). */
object LockSettings {
    const val FILE = "lock_settings"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(context: Context, screen: String): LockMode = LockMode.of(prefs(context).getString("mode_$screen", null))

    fun setMode(context: Context, screen: String, mode: LockMode) = prefs(context).edit { putString("mode_$screen", mode.key) }

    fun modes(context: Context, screens: List<String>): Map<String, LockMode> = screens.associateWith { mode(context, it) }

    /** Minutes a screen stays unlocked after you leave it; 0 locks it as soon as you leave. */
    fun stayMinutes(context: Context): Int = LockRules.cleanStay(prefs(context).getInt("stay", LockRules.DEFAULT_STAY))

    fun setStayMinutes(context: Context, minutes: Int) = prefs(context).edit { putInt("stay", LockRules.cleanStay(minutes)) }

    fun strictness(context: Context): Strictness = Strictness.of(prefs(context).getString("strictness", null))

    fun setStrictness(context: Context, s: Strictness) = prefs(context).edit { putString("strictness", s.key) }

    /** Whether a photo of you is turned away (the live-face check). On. */
    fun blockPhoto(context: Context): Boolean = prefs(context).getBoolean("block_photo", true)

    fun setBlockPhoto(context: Context, on: Boolean) = prefs(context).edit { putBoolean("block_photo", on) }
}

/**
 * Your face as a list of number lists ("fingerprints"), never a picture. Encrypted with a key that lives in the phone's key store and
 * cannot be taken out of it, and kept in the folder that backups skip. A copy moved to another phone cannot be read.
 */
object FaceStore {
    private const val ALIAS = "kairos_face_key"
    private const val STORE = "AndroidKeyStore"

    private fun file(context: Context) = File(context.noBackupFilesDir, "face.bin")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Whether a face has been saved (it is not opened here). */
    fun exists(context: Context): Boolean = file(context).isFile

    fun save(context: Context, faces: List<FloatArray>) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(FaceMath.encode(faces))
        file(context).apply { parentFile?.mkdirs() }.writeBytes(cipher.iv + sealed)
    }

    /** The saved faces, or an empty list when there are none or they cannot be read (then the file is removed). */
    fun load(context: Context): List<FloatArray> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return try {
            val bytes = f.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            FaceMath.decode(cipher.doFinal(bytes, 12, bytes.size - 12))
        } catch (e: Exception) {
            f.delete()
            emptyList()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        runCatching { KeyStore.getInstance(STORE).apply { load(null) }.deleteEntry(ALIAS) }
    }
}

/** Which screens are open right now. Nothing is kept on disk, so closing the app locks everything. */
object LockState {
    private val until = mutableStateMapOf<String, Long>()
    private val misses = mutableStateMapOf<String, Int>()
    private var tick by mutableIntStateOf(0)

    private fun now() = SystemClock.elapsedRealtime()

    fun isOpen(screen: String): Boolean {
        tick // read so a time check can redraw the screen
        return LockRules.isOpen(until[screen] ?: 0L, now())
    }

    fun unlock(screen: String) {
        until[screen] = LockRules.OPEN
        misses[screen] = 0
    }

    /** You left [screen]: it locks again [stayMinutes] from now. */
    fun leave(screen: String, stayMinutes: Int) {
        if (until[screen] == LockRules.OPEN) until[screen] = LockRules.lockedAgainAt(now(), stayMinutes)
    }

    /** The app went to the background: every open screen counts as left. */
    fun leaveAll(stayMinutes: Int) {
        for (screen in until.keys.toList()) leave(screen, stayMinutes)
    }

    /** You came back to [screen]: still open if its time has not run out. */
    fun reenter(screen: String) {
        val before = until[screen] ?: return
        val after = LockRules.onReturn(before, now())
        if (after != before) until[screen] = after
        tick++
    }

    fun lockAll() {
        for (screen in until.keys.toList()) until[screen] = 0L
        tick++
    }

    fun misses(screen: String): Int = misses[screen] ?: 0

    fun addMiss(screen: String) {
        misses[screen] = misses(screen) + 1
    }
}

/** Android's own lock screen prompt: fingerprint, face, pattern or PIN, as the phone is set up. Needs Android 11 or newer. */
object PhoneLock {
    private const val ANY = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private const val PIN_ONLY = BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun status(context: Context, authenticators: Int): Int =
        if (Build.VERSION.SDK_INT < 30) BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        else BiometricManager.from(context).canAuthenticate(authenticators)

    fun available(context: Context): Boolean = status(context, ANY) == BiometricManager.BIOMETRIC_SUCCESS

    fun pinAvailable(context: Context): Boolean = status(context, PIN_ONLY) == BiometricManager.BIOMETRIC_SUCCESS

    /** One line for Settings about whether the phone lock can be used. */
    fun statusText(context: Context): String = when {
        Build.VERSION.SDK_INT < 30 -> "The phone lock needs Android 11 or newer."
        available(context) -> "Ready. Uses your phone's fingerprint, face, pattern or PIN."
        else -> "Set a screen lock (PIN, pattern or password) in your phone's settings first."
    }

    /** Shows the prompt. [pinOnly] asks for the PIN, pattern or password only. [onResult] gets true, or false with Android's reason. */
    fun prompt(activity: FragmentActivity, title: String, subtitle: String, pinOnly: Boolean, onResult: (Boolean, String) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true, "")
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false, errString.toString())
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(if (pinOnly) PIN_ONLY else ANY)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
