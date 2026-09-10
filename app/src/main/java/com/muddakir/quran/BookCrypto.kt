package com.muddakir.quran

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

/**
 * Encrypts library books at rest using an AES-256 key held by Android Keystore.
 * The plaintext SQLite database is only materialized temporarily while it is opened.
 */
object BookCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "QuranStudy.LibraryBooks.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val IO_BUFFER_SIZE = 1024 * 1024
    private val MAGIC = byteArrayOf('Q'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encryptFile(input: File, output: File) {
        require(input.isFile) { "Source file does not exist: ${input.name}" }
        output.parentFile?.mkdirs()
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }

        FileOutputStream(output).use { raw ->
            raw.write(MAGIC)
            raw.write(iv.size)
            raw.write(iv)
            CipherOutputStream(BufferedOutputStream(raw, 64 * 1024), cipher).use { encrypted ->
                FileInputStream(input).use { source ->
                    BufferedInputStream(source, 64 * 1024).use { buffered ->
                        buffered.copyTo(encrypted, 64 * 1024)
                    }
                }
            }
        }
    }

    /** Opens a temporary plaintext copy. Caller MUST close the returned database and delete tempFile. */
    fun decryptToTemp(encrypted: File, tempFile: File): File {
        require(isEncrypted(encrypted)) { "Book is not encrypted: ${encrypted.name}" }
        tempFile.parentFile?.mkdirs()
        val tempPart = File(tempFile.parentFile, "${tempFile.name}.part")
        tempPart.delete()
        try {
            FileInputStream(encrypted).use { raw ->
                BufferedInputStream(raw, 64 * 1024).use { input ->
                    val magic = ByteArray(MAGIC.size)
                    readFully(input, magic)
                    if (!magic.contentEquals(MAGIC)) throw IOException("Invalid protected book header")
                    val ivSize = input.read()
                    if (ivSize != IV_SIZE) throw IOException("Invalid protected book IV")
                    val iv = ByteArray(ivSize)
                    readFully(input, iv)
                    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                    }
                    CipherInputStream(input, cipher).use { decrypted ->
                        FileOutputStream(tempPart).use { out ->
                            BufferedOutputStream(out, IO_BUFFER_SIZE).use { bufferedOut ->
                                decrypted.copyTo(bufferedOut, IO_BUFFER_SIZE)
                                bufferedOut.flush()
                            }
                        }
                    }
                }
            }
            if (!tempPart.renameTo(tempFile)) throw IOException("Unable to prepare protected book")
            return tempFile
        } catch (t: Throwable) {
            tempPart.delete()
            tempFile.delete()
            throw t
        }
    }

    fun isEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() <= MAGIC.size + 1 + IV_SIZE) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val magic = ByteArray(MAGIC.size)
                readFully(input, magic)
                magic.contentEquals(MAGIC) && input.read() == IV_SIZE
            }
        }.getOrDefault(false)
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) throw IOException("Unexpected end of protected book")
            offset += n
        }
    }
}
