package com.muddakir.quran

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Internal, opaque storage location for protected SQLite books. */
object BookStorage {
    private const val ROOT = "library/books/.vault"

    fun encryptedFile(context: Context, logicalFileName: String): File {
        require(logicalFileName.matches(Regex("book_[A-Za-z0-9_-]+\\.db"))) {
            "Invalid book database filename: $logicalFileName"
        }
        val opaqueName = sha256(logicalFileName) + ".qsb"
        return File(context.filesDir, "$ROOT/$opaqueName")
    }

    fun root(context: Context): File = File(context.filesDir, ROOT)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
