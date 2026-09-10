package com.muddakir.quran

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Stores a user-picked notification sound the same way library books are
 * stored: encrypted at rest under app-internal storage, with an opaque
 * filename. The Android notification system cannot read an encrypted file
 * directly, so when a channel needs the sound, this decrypts it once into
 * the app's existing FileProvider cache directory and hands back a
 * content:// URI the system is granted read access to.
 *
 * Because a notification channel's sound cannot be changed after the
 * channel is created (Android 8+), the channel id is versioned here: every
 * time the sound changes, the version bumps and the caller must create a
 * new channel under the new id (see [channelId]) and delete the old one
 * (see [previousChannelId]).
 */
object ReminderSoundStore {
    private const val PREFS = "reminder_sound_prefs"
    private const val KEY_VERSION = "sound_version"
    private const val KEY_HAS_CUSTOM = "has_custom_sound"
    private const val ENCRYPTED_NAME = "reminder_sound.qsb"
    private const val CACHE_SUBDIR = "reminder_sound"
    private const val DECRYPTED_NAME = "current.tmp"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encryptedFile(context: Context): File =
        File(context.filesDir, "reminder_sound/$ENCRYPTED_NAME").apply { parentFile?.mkdirs() }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }

    /** Copies, validates, and encrypts a user-picked audio file. Deletes any
     * previous custom sound and bumps the channel version so callers know
     * to recreate the notification channel. */
    fun save(context: Context, source: Uri): Boolean {
        val plainTemp = File(cacheDir(context), "incoming.tmp")
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                plainTemp.outputStream().use { out -> input.copyTo(out) }
            } ?: return false
            if (!plainTemp.isFile || plainTemp.length() <= 0L) return false

            val target = encryptedFile(context)
            val partTarget = File(target.parentFile, "${target.name}.part")
            BookCrypto.encryptFile(plainTemp, partTarget)
            if (target.exists() && !target.delete()) return false
            if (!partTarget.renameTo(target)) return false

            clearDecryptedCache(context)
            val nextVersion = prefs(context).getInt(KEY_VERSION, 0) + 1
            prefs(context).edit()
                .putInt(KEY_VERSION, nextVersion)
                .putBoolean(KEY_HAS_CUSTOM, true)
                .apply()
            true
        } catch (_: Throwable) {
            false
        } finally {
            plainTemp.delete()
        }
    }

    /** Reverts to the default system notification sound. */
    fun clear(context: Context) {
        encryptedFile(context).delete()
        clearDecryptedCache(context)
        val nextVersion = prefs(context).getInt(KEY_VERSION, 0) + 1
        prefs(context).edit()
            .putInt(KEY_VERSION, nextVersion)
            .putBoolean(KEY_HAS_CUSTOM, false)
            .apply()
    }

    fun hasCustomSound(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_CUSTOM, false) && encryptedFile(context).isFile

    /** The versioned channel id a caller should use right now. Distinct
     * from the previous version whenever the sound has changed. */
    fun channelId(context: Context, baseId: String): String =
        "${baseId}_snd${prefs(context).getInt(KEY_VERSION, 0)}"

    /** The channel id that was current just before the latest sound change,
     * so the caller can delete it (a stale channel with the old sound
     * would otherwise linger forever). Null if there is nothing to clean up. */
    fun previousChannelId(context: Context, baseId: String): String? {
        val version = prefs(context).getInt(KEY_VERSION, 0)
        if (version <= 0) return null
        return "${baseId}_snd${version - 1}"
    }

    /** Decrypts the stored custom sound (once, cached) and returns a
     * content:// URI the notification system can read. Null if no custom
     * sound is set or decryption fails for any reason - callers must treat
     * null as "use the default sound", never crash. */
    fun currentSoundUri(context: Context): Uri? {
        if (!hasCustomSound(context)) return null
        val decrypted = File(cacheDir(context), DECRYPTED_NAME)
        if (!decrypted.isFile) {
            try {
                BookCrypto.decryptToTemp(encryptedFile(context), decrypted)
            } catch (_: Throwable) {
                return null
            }
        }
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", decrypted)
        } catch (_: Throwable) {
            null
        }
    }

    fun notificationAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun clearDecryptedCache(context: Context) {
        File(cacheDir(context), DECRYPTED_NAME).delete()
    }
}
