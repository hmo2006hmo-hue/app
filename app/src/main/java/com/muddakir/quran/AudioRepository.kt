package com.muddakir.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AudioRepository {
    data class Reciter(val id: String, val name: String)
    val reciters = listOf(
        Reciter("ar.alafasy", "مشاري راشد العفاسي"), Reciter("ar.husary", "محمود خليل الحصري"),
        Reciter("ar.minshawi", "محمد صديق المنشاوي"), Reciter("ar.minshawimujawwad", "المنشاوي — مجود"),
        Reciter("ar.sudais", "عبد الرحمن السديس"), Reciter("ar.shuraim", "سعود الشريم"),
        Reciter("ar.abdulbasit", "عبد الباسط عبد الصمد"), Reciter("ar.abdulbasitmujawwad", "عبد الباسط — مجود"),
        Reciter("ar.hudhaify", "علي الحذيفي"), Reciter("ar.ajamy", "أحمد العجمي"),
        Reciter("ar.muhammadayoub", "محمد أيوب"), Reciter("ar.muhammadjibreel", "محمد جبريل")
    )
    val qualities = listOf("64" to "اقتصادية", "128" to "متوازنة", "192" to "عالية")

    private const val PREFS = "audio_settings"
    private const val RECITER = "reciter"
    private const val QUALITY = "quality"
    private const val AUTO = "auto_download"

    fun reciter(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECITER, "ar.abdulbasit")
        return reciters.firstOrNull { it.id == saved }?.id ?: "ar.abdulbasit"
    }
    fun quality(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(QUALITY, "128")
        return qualities.firstOrNull { it.first == saved }?.first ?: "128"
    }
    fun autoDownload(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO, false)
    fun saveSettings(context: Context, reciter: String, quality: String, auto: Boolean) {
        val safeReciter = reciters.firstOrNull { it.id == reciter }?.id ?: "ar.abdulbasit"
        val safeQuality = qualities.firstOrNull { it.first == quality }?.first ?: "128"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(RECITER, safeReciter)
            .putString(QUALITY, safeQuality)
            .putBoolean(AUTO, auto)
            .apply()
    }

    /**
     * Primary audio source. Islamic Network's CDN has intermittently returned
     * 403/routing errors, so playback/download no longer depends on it.
     * EveryAyah publishes stable verse-by-verse files using SSSAAA names.
     */
    fun globalUrl(global: Int, reciter: String, quality: String): String {
        val (surah, ayah) = surahAyahFromGlobal(global)
        return "https://everyayah.com/data/${everyAyahFolder(reciter, quality)}/${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3"
    }

    /**
     * First try EveryAyah with the requested/closest available bitrate. The
     * old Islamic Network URL remains only as a last compatibility fallback.
     */
    fun urls(global: Int, reciter: String, quality: String): List<String> = buildList {
        add(globalUrl(global, reciter, quality))
        val fallbackQualities = when (reciter) {
            "ar.sudais", "ar.abdulbasit", "ar.abdulbasitmujawwad", "ar.minshawimujawwad" -> listOf("128", "64", "192")
            else -> listOf("128", "64", "192")
        }
        fallbackQualities.forEach { q -> add(globalUrl(global, reciter, q)) }
        // Final compatibility URL; it is deliberately last because the CDN
        // has been observed to return HTTP 403/routing errors.
        add("https://cdn.islamic.network/quran/audio/$quality/$reciter/$global.mp3")
    }.distinct()

    private fun everyAyahFolder(reciter: String, quality: String): String {
        return when (reciter) {
            "ar.alafasy" -> if (quality == "64") "Alafasy_64kbps" else "Alafasy_128kbps"
            "ar.husary" -> if (quality == "64") "Husary_64kbps" else "Husary_128kbps"
            "ar.minshawi" -> "Minshawy_Murattal_128kbps"
            "ar.minshawimujawwad" -> if (quality == "64") "Minshawy_Mujawwad_64kbps" else "Minshawy_Mujawwad_192kbps"
            "ar.sudais" -> if (quality == "64") "Abdurrahmaan_As-Sudais_64kbps" else "Abdurrahmaan_As-Sudais_192kbps"
            "ar.shuraim" -> if (quality == "64") "Saood_ash-Shuraym_64kbps" else "Saood_ash-Shuraym_128kbps"
            "ar.abdulbasit" -> if (quality == "64") "Abdul_Basit_Murattal_64kbps" else "Abdul_Basit_Murattal_192kbps"
            "ar.abdulbasitmujawwad" -> "Abdul_Basit_Mujawwad_128kbps"
            "ar.hudhaify" -> when (quality) { "64" -> "Hudhaify_64kbps"; "192" -> "Hudhaify_128kbps"; else -> "Hudhaify_128kbps" }
            "ar.ajamy" -> "ahmed_ibn_ali_al_ajamy_128kbps"
            "ar.muhammadayoub" -> when (quality) { "64" -> "Muhammad_Ayyoub_64kbps"; "192" -> "Muhammad_Ayyoub_128kbps"; else -> "Muhammad_Ayyoub_128kbps" }
            "ar.muhammadjibreel" -> when (quality) { "64" -> "Muhammad_Jibreel_64kbps"; else -> "Muhammad_Jibreel_128kbps" }
            else -> "Alafasy_128kbps"
        }
    }

    private fun surahAyahFromGlobal(global: Int): Pair<Int, Int> {
        require(global in 1..6236) { "Invalid global ayah number: $global" }
        var remaining = global
        for (surah in 1..114) {
            val count = surahAyahCount(surah)
            if (remaining <= count) return surah to remaining
            remaining -= count
        }
        error("Unable to resolve global ayah number: $global")
    }

    fun audioFile(context: Context, reciter: String, quality: String, global: Int): File = File(File(context.filesDir, "audio/$reciter/$quality"), "$global.mp3")
    fun findLocal(context: Context, reciter: String, quality: String, global: Int): File? = listOf(quality, "128").distinct().map { audioFile(context, reciter, it, global) }.firstOrNull { it.isFile && it.length() > 1024 }

    suspend fun downloadAyah(
        context: Context,
        reciter: String,
        quality: String,
        global: Int,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        notificationTask: AudioDownloadNotifier.Task? = null
    ): Result<File> {
        val ownTask = notificationTask ?: AudioDownloadNotifier.start(
            context,
            "تحميل آية $global — ${reciters.firstOrNull { it.id == reciter }?.name ?: reciter}"
        )
        val result = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                val local = findLocal(context, reciter, quality, global)
                if (local != null) {
                    local
                } else {
                    val target = audioFile(context, reciter, quality, global)
                    target.parentFile?.mkdirs()
                    var lastError: Throwable? = null
                    var saved: File? = null

                    for (urlText in urls(global, reciter, quality)) {
                        val temp = File(target.parentFile, "$global.mp3.part")
                        try {
                            if (temp.exists() && !temp.delete()) {
                                throw IllegalStateException("تعذر تنظيف ملف الصوت المؤقت")
                            }

                            val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 15_000
                                readTimeout = 30_000
                                requestMethod = "GET"
                                useCaches = false
                                instanceFollowRedirects = true
                                setRequestProperty("User-Agent", "QuranStudy/Native")
                            }

                            try {
                                if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                                    throw IllegalStateException("تنزيل مرفوض: إعادة التوجيه إلى رابط غير HTTPS")
                                }
                                if (connection.responseCode !in 200..299) {
                                    throw IllegalStateException("HTTP ${connection.responseCode}")
                                }

                                val totalBytes = connection.contentLengthLong
                                var downloadedBytes = 0L
                                connection.inputStream.use { input ->
                                    temp.outputStream().buffered().use { output ->
                                        val buffer = ByteArray(16 * 1024)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            if (count == 0) continue
                                            output.write(buffer, 0, count)
                                            downloadedBytes += count
                                            if (totalBytes > 0L) {
                                                onProgress(downloadedBytes, totalBytes)
                                                ownTask?.updateBytes(downloadedBytes, totalBytes)
                                            }
                                        }
                                    }
                                }
                                if (totalBytes <= 0L) {
                                    onProgress(downloadedBytes, -1L)
                                    ownTask?.update(100, "تم تنزيل الملف الحالي")
                                }
                            } finally {
                                connection.disconnect()
                            }

                            if (temp.length() <= 1024L) {
                                throw IllegalStateException("ملف صوتي فارغ أو غير صالح")
                            }

                            val backup = File(target.parentFile, "$global.mp3.bak")
                            if (backup.exists() && !backup.delete()) {
                                throw IllegalStateException("تعذر تنظيف النسخة الاحتياطية للصوت")
                            }
                            var backedUp = false
                            try {
                                if (target.exists()) {
                                    if (!target.renameTo(backup)) {
                                        throw IllegalStateException("تعذر حفظ الملف الصوتي الحالي")
                                    }
                                    backedUp = true
                                }
                                if (!temp.renameTo(target)) {
                                    if (backedUp && backup.exists()) backup.renameTo(target)
                                    throw IllegalStateException("تعذر حفظ الملف الصوتي")
                                }
                                if (backup.exists()) backup.delete()
                                saved = target
                            } catch (t: Throwable) {
                                if (target.exists() && backup.exists()) target.delete()
                                if (backedUp && backup.exists()) backup.renameTo(target)
                                throw t
                            } finally {
                                temp.delete()
                            }
                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            lastError = e
                            temp.delete()
                        }

                        if (saved != null) break
                    }

                    saved ?: throw (lastError ?: IllegalStateException("تعذر تنزيل الصوت"))
                }
            }
        }
        if (result.isSuccess) ownTask?.complete("تم تنزيل الآية ✅")
        else ownTask?.fail("فشل تنزيل الآية: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}")
        return result
    }

    suspend fun downloadSurah(
        context: Context, reciter: String, quality: String, surah: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onBytesProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Unit> {
        val total = surahAyahCount(surah)
        val task = AudioDownloadNotifier.start(
            context,
            "تحميل سورة $surah — ${reciters.firstOrNull { it.id == reciter }?.name ?: reciter}"
        )
        val result = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                var done = 0
                for (a in 1..total) {
                    val item = downloadAyah(
                        context,
                        reciter,
                        quality,
                        globalAyahNumber(surah, a),
                        onProgress = { downloaded, bytes ->
                            onBytesProgress(downloaded, bytes)
                            if (bytes > 0L) {
                                val within = (downloaded.toDouble() / bytes.toDouble()).coerceIn(0.0, 1.0)
                                val percent = (((done + within) / total.toDouble()) * 100.0).toInt()
                                task?.update(percent, "السورة: $percent%")
                            }
                        },
                        notificationTask = task
                    )
                    item.getOrThrow()
                    done++
                    onProgress(done, total)
                    task?.update(((done.toDouble() / total.toDouble()) * 100.0).toInt(), "الآيات: $done / $total")
                }
                Unit
            }
        }
        if (result.isSuccess) task?.complete("اكتمل تحميل السورة ✅")
        else task?.fail("فشل تحميل السورة: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}")
        return result
    }

    suspend fun downloadSurahs(
        context: Context, reciter: String, quality: String, surahs: Set<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onBytesProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Unit> {
        val ordered = surahs.filter { it in 1..114 }.sorted()
        require(ordered.isNotEmpty()) { "لم يتم تحديد أي سورة" }
        val totalAyahs = ordered.sumOf { surahAyahCount(it) }
        val task = AudioDownloadNotifier.start(
            context,
            "تحميل ${ordered.size} سور — ${reciters.firstOrNull { it.id == reciter }?.name ?: reciter}"
        )
        val result = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                var done = 0
                for (surah in ordered) {
                    val count = surahAyahCount(surah)
                    for (a in 1..count) {
                        val currentDone = done
                        val item = downloadAyah(
                            context,
                            reciter,
                            quality,
                            globalAyahNumber(surah, a),
                            onProgress = { downloaded, bytes ->
                                onBytesProgress(downloaded, bytes)
                                if (bytes > 0L) {
                                    val within = (downloaded.toDouble() / bytes.toDouble()).coerceIn(0.0, 1.0)
                                    val percent = (((currentDone + within) / totalAyahs.toDouble()) * 100.0).toInt()
                                    task?.update(percent, "السور: $percent%")
                                }
                            },
                            notificationTask = task
                        )
                        item.getOrThrow()
                        done++
                        onProgress(done, totalAyahs)
                        task?.update(((done.toDouble() / totalAyahs.toDouble()) * 100.0).toInt(), "الآيات: $done / $totalAyahs")
                    }
                }
                Unit
            }
        }
        if (result.isSuccess) task?.complete("اكتمل تحميل السور المحددة ✅")
        else task?.fail("فشل تحميل السور: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}")
        return result
    }

    suspend fun downloadQuran(
        context: Context, reciter: String, quality: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onBytesProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Unit> {
        val total = 6236
        val task = AudioDownloadNotifier.start(
            context,
            "تحميل القرآن كاملًا — ${reciters.firstOrNull { it.id == reciter }?.name ?: reciter}"
        )
        val result = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                var done = 0
                for (s in 1..114) for (a in 1..surahAyahCount(s)) {
                    val currentDone = done
                    val item = downloadAyah(
                        context,
                        reciter,
                        quality,
                        globalAyahNumber(s, a),
                        onProgress = { downloaded, bytes ->
                            onBytesProgress(downloaded, bytes)
                            if (bytes > 0L) {
                                val within = (downloaded.toDouble() / bytes.toDouble()).coerceIn(0.0, 1.0)
                                val percent = (((currentDone + within) / total.toDouble()) * 100.0).toInt()
                                task?.update(percent, "القرآن: $percent%")
                            }
                        },
                        notificationTask = task
                    )
                    item.getOrThrow()
                    done++
                    onProgress(done, total)
                    task?.update(((done.toDouble() / total.toDouble()) * 100.0).toInt(), "الآيات: $done / $total")
                }
                Unit
            }
        }
        if (result.isSuccess) task?.complete("اكتمل تحميل القرآن كاملًا ✅")
        else task?.fail("فشل تحميل القرآن: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}")
        return result
    }

    fun downloadedAyahCount(context: Context, reciter: String, quality: String): Int {
        val root = File(context.filesDir, "audio/$reciter/$quality")
        return root.listFiles()?.count { it.isFile && it.length() > 1024L } ?: 0
    }

    fun deleteOtherReciters(context: Context, keep: String) { val root = File(context.filesDir, "audio"); root.listFiles()?.filter { it.isDirectory && it.name != keep }?.forEach { it.deleteRecursively() } }
    fun clear(context: Context) { File(context.filesDir, "audio").deleteRecursively() }
    fun sizeBytes(context: Context): Long {
        fun size(file: File): Long {
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { child -> size(child) } ?: 0L
        }
        return size(File(context.filesDir, "audio"))
    }

    private fun surahAyahCount(surah: Int): Int {
        require(surah in 1..114) { "Invalid surah number: $surah" }
        return intArrayOf(7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6)[surah-1]
    }
}
