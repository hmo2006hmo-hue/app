@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.muddakir.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.io.File
import org.json.JSONArray
import org.json.JSONException
import java.text.NumberFormat
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/* ------------------------------------------------------------------------
 * أسماء السور الـ114 - مطابقة لبيانات السور المستخدمة في قارئ المصحف
 * ------------------------------------------------------------------------ */
private val SURAH_NAMES = arrayOf(
    "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
    "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
    "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
    "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
    "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان",
    "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم",
    "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف",
    "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
    "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات",
    "النبأ", "النازعات", "عبس", "التكوير", "الإنفطار", "المطففين", "الإنشقاق", "البروج",
    "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح",
    "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر",
    "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
    "المسد", "الإخلاص", "الفلق", "الناس"
)

/* ------------------------------------------------------------------------
 * الخط: Amiri (مضمّن بالمشروع) - يُستخدم فقط لنصوص الواجهة (العناوين، BottomSheet)
 * أما نص المصحف نفسه فيُرسم بخطوط QCF4 (انظر QcfFonts.kt) حرفًا بحرف مطابقًا للمطبوع.
 * ------------------------------------------------------------------------ */
private val AmiriFontFamily: FontFamily = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold)
)

/* ------------------------------------------------------------------------
 * ألوان واجهة قارئ المصحف الأصلية
 * ------------------------------------------------------------------------ */
private const val MIN_PAGE = 1
private const val MAX_PAGE = 604
private const val CHROME_HIDE_DELAY_MS = 2700L

private const val PREFS_NAME = "mushaf_prefs"
private const val KEY_CURRENT_PAGE = "mushaf_page"
private const val KEY_PAGE_BOOKMARKS = "mushaf_bookmarks_v1"
private const val KEY_AYAH_BOOKMARKS = "mushaf_ayah_bookmarks_all_v1"
private const val KEY_KHATMA_PAGE = "khatma_current_page"

private fun arabicNumber(n: Int): String =
    NumberFormat.getInstance(Locale("ar", "IQ")).format(n)

private fun clampPage(n: Int): Int = n.coerceIn(MIN_PAGE, MAX_PAGE)

/** آية جاهزة للعرض بالـ BottomSheet / نص المشاركة والنسخ */
data class UiAyah(
    val global: Int,
    val surahNumber: Int,
    val surahName: String,
    val numberInSurah: Int,
    val text: String
)

/* ------------------------------------------------------------------------
 * تخزين محلي (بديل localStorage): آخر صفحة، علامات الصفحات، علامات الآيات
 * ------------------------------------------------------------------------ */
private object MushafPrefs {

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadLastPage(context: Context): Int =
        clampPage(prefs(context).getInt(KEY_CURRENT_PAGE, 1))

    fun saveCurrentPage(context: Context, page: Int) {
        prefs(context).edit { putInt(KEY_CURRENT_PAGE, page) }
    }

    fun loadKhatmaPage(context: Context, fallback: Int): Int = clampPage(context.getSharedPreferences("khatma_native", Context.MODE_PRIVATE).getInt(KEY_KHATMA_PAGE, fallback))

    fun saveKhatmaPage(context: Context, page: Int) {
        context.getSharedPreferences("khatma_native", Context.MODE_PRIVATE).edit { putInt(KEY_KHATMA_PAGE, clampPage(page)) }
    }

    private fun loadList(context: Context, key: String): MutableList<String> {
        val raw = prefs(context).getString(key, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> arr.getString(i) }
        } catch (_: JSONException) {
            mutableListOf()
        }
    }

    private fun saveList(context: Context, key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs(context).edit { putString(key, arr.toString()) }
    }

    fun isPageBookmarked(context: Context, page: Int): Boolean =
        loadList(context, KEY_PAGE_BOOKMARKS).contains(page.toString())

    /** يرجع الحالة الجديدة بعد التبديل (true = تمت الإضافة) */
    fun togglePageBookmark(context: Context, page: Int): Boolean {
        var list = loadList(context, KEY_PAGE_BOOKMARKS)
        val key = page.toString()
        val existed = list.contains(key)
        list = if (existed) list.filterNot { it == key }.toMutableList()
        else (mutableListOf(key) + list).toMutableList()
        if (list.size > 100) list = list.take(100).toMutableList()
        saveList(context, KEY_PAGE_BOOKMARKS, list)
        return !existed
    }

    fun isAyahBookmarked(context: Context, surah: Int, ayah: Int): Boolean =
        loadList(context, KEY_AYAH_BOOKMARKS).contains("$surah:$ayah")

    fun toggleAyahBookmark(context: Context, surah: Int, ayah: Int): Boolean {
        var list = loadList(context, KEY_AYAH_BOOKMARKS)
        val key = "$surah:$ayah"
        val existed = list.contains(key)
        list = if (existed) list.filterNot { it == key }.toMutableList()
        else (mutableListOf(key) + list).toMutableList()
        if (list.size > 300) list = list.take(300).toMutableList()
        saveList(context, KEY_AYAH_BOOKMARKS, list)
        return !existed
    }

    /**
     * رقم الآية (global) لآخر آية محفوظة كـ "آخر قراءة" (يستخدمها BookmarkWidget/AudioWidget).
     * يقرأ من نفس SharedPreferences الموجودة أصلًا بـ MainActivity (BOOKMARK_PREFS) حتى تبقى
     * متوافقة مع الويدجتات القديمة بدون أي تعديل عليها.
     */
    fun lastBookmarkedGlobalAyah(context: Context): Int? {
        val prefs = context.getSharedPreferences(MainActivity.BOOKMARK_PREFS, Context.MODE_PRIVATE)
        val ayah = prefs.getInt(MainActivity.KEY_BOOKMARK_AYAH, -1)
        return ayah.takeIf { it > 0 }
    }
}

/** يجمع نص آية كاملة (قد تمتد على أكثر من سطر بنفس الصفحة) من كلمات نوعها "word" فقط. */
private fun ayahTextFor(page: QcfPage, verseKey: String): String =
    page.lines.asSequence()
        .flatMap { it.words.asSequence() }
        .filter { it.verseKey == verseKey && it.type == "word" }
        .joinToString(" ") { it.text }

/** يبني UiAyah كاملة من مفتاح الآية "سورة:رقم" باستخدام بيانات الصفحة الحالية. */
private fun uiAyahFor(page: QcfPage, verseKey: String): UiAyah? {
    val parts = verseKey.split(":", limit = 2)
    if (parts.size != 2) return null
    val surahNumber = parts[0].toIntOrNull() ?: return null
    val numberInSurah = parts[1].toIntOrNull() ?: return null
    if (surahNumber !in 1..114 || numberInSurah <= 0) return null
    val global = runCatching { globalAyahNumber(surahNumber, numberInSurah) }.getOrNull() ?: return null
    return UiAyah(
        global = global,
        surahNumber = surahNumber,
        surahName = SURAH_NAMES.getOrNull(surahNumber - 1) ?: return null,
        numberInSurah = numberInSurah,
        text = ayahTextFor(page, verseKey)
    )
}

/* ------------------------------------------------------------------------
 * الشاشة الرئيسية
 * ------------------------------------------------------------------------ */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
private val LIBRARY_TAFSIR_BOOK_IDS = setOf<Long>(6L, 9L, 10L, 14L, 24L, 31L, 51L, 89L, 98L, 121L, 133L, 195L, 213L, 228L, 259L, 263L)

@Composable
fun MushafScreen(
    onBack: () -> Unit = {},
    /** رقم صفحة يُفتح عليها المصحف مباشرة (بديل EXTRA_OPEN_PAGE القديم). null = آخر صفحة محفوظة. */
    initialPage: Int? = null,
    /** لفتح تفسير آية محددة تلقائيًا عند الدخول (بديل EXTRA_OPEN_TAFSIR + EXTRA_SURAH/EXTRA_AYAH). */
    initialTafsirSurah: Int? = null,
    initialTafsirAyah: Int? = null,
    /** لتشغيل صوت آخر آية محفوظة تلقائيًا عند الدخول (بديل extra "auto_play"). */
    autoPlayOnOpen: Boolean = false,
    khatmaMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appTheme = remember { AppThemeStore.get(context) }
    val appPalette = remember(appTheme) { paletteFor(appTheme) }

    val startPage = remember { clampPage(initialPage ?: if (khatmaMode) MushafPrefs.loadKhatmaPage(context, MushafPrefs.loadLastPage(context)) else MushafPrefs.loadLastPage(context)) }
    val pagerState = rememberPagerState(initialPage = startPage - 1) { MAX_PAGE }

    // كاش الصفحات بالذاكرة: page-number -> QcfPage? (بيانات QCF4 المطابقة حرفيًا للمطبوع)
    val pageCache = remember { mutableStateMapOf<Int, QcfPage>() }
    // الجزء (juz) الدقيق لكل صفحة - نقرأه من بيانات المصحف القديمة (assets/quran/page-N.json)
    // التي ما زالت موجودة وتحتوي رقم الجزء الصحيح لكل صفحة.
    val juzCache = remember { mutableStateMapOf<Int, Int>() }
    val hizbCache = remember { mutableStateMapOf<Int, Int>() }

    // تحميل الصفحة الحالية + المجاورة (beyondBoundsPageCount = 1 بمصطلح Foundation الحديث: beyondViewportPageCount)
    LaunchedEffect(pagerState.currentPage) {
        val current = pagerState.currentPage + 1
        if (khatmaMode) MushafPrefs.saveKhatmaPage(context, current) else MushafPrefs.saveCurrentPage(context, current)
        listOf(current - 1, current, current + 1)
            .filter { it in MIN_PAGE..MAX_PAGE }
            .forEach { p ->
                if (!pageCache.containsKey(p)) {
                    QcfQuranRepository.loadPage(context, p)?.let { pageCache[p] = it }
                }
                if (!juzCache.containsKey(p)) {
                    QuranRepository.loadPage(context, p)?.let { juzCache[p] = it.juz }
                }
                if (khatmaMode && p == current) {
                    pageCache[p]?.let { KhatmaProgress.markPageRead(context, it) }
                }
                if (!hizbCache.containsKey(p)) {
                    val firstVerseKey = pageCache[p]?.lines
                        ?.asSequence()
                        ?.flatMap { it.words.asSequence() }
                        ?.firstNotNullOfOrNull { it.verseKey }
                    if (firstVerseKey != null) {
                        val parts = firstVerseKey.split(":")
                        if (parts.size == 2) {
                            val surah = parts[0].toIntOrNull()
                            val ayah = parts[1].toIntOrNull()
                            if (surah != null && ayah != null) {
                                AyahMetaRepository.get(context, surah, ayah)?.hizb?.let { hizbCache[p] = it }
                            }
                        }
                    }
                }
            }
    }

    // إخفاء الشريط العلوي تلقائيًا بعد 2.7 ثانية من عدم التفاعل (مطابق لسلوك chrome بالملف الأصلي)
    var chromeVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionTick) {
        chromeVisible = true
        delay(CHROME_HIDE_DELAY_MS.milliseconds)
        chromeVisible = false
    }

    var selectedAyah by remember { mutableStateOf<UiAyah?>(null) }
    var ayahBookmarked by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var sheetOpen by remember { mutableStateOf(false) }

    var tafsirText by remember { mutableStateOf<String?>(null) }
    var tafsirOpen by remember { mutableStateOf(false) }
    var tafsirPickerOpen by remember { mutableStateOf(false) }
    var tafsirSearchQuery by remember { mutableStateOf("") }
    var libraryTafsirBooks by remember { mutableStateOf<List<LibraryCatalogRepository.Book>>(emptyList()) }
    var selectedLibraryTafsirId by remember { mutableStateOf<Long?>(null) }
    var libraryTafsirBusyId by remember { mutableStateOf<Long?>(null) }
    var libraryTafsirProgress by remember { mutableStateOf(0f) }
    var libraryTafsirSession by remember { mutableStateOf<BookRepository.ReaderSession?>(null) }
    val latestLibraryTafsirSession = rememberUpdatedState(libraryTafsirSession)

    var pageJumpOpen by remember { mutableStateOf(false) }
    var pageJumpValue by remember { mutableStateOf("") }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val latestMediaPlayer = rememberUpdatedState(mediaPlayer)
    var audioStatus by remember { mutableStateOf<String?>(null) }
    var audioGlobal by remember { mutableIntStateOf(0) }
    var audioPlaying by remember { mutableStateOf(false) }
    var audioPositionMs by remember { mutableLongStateOf(0L) }
    var audioDurationMs by remember { mutableLongStateOf(0L) }
    var audioBusy by remember { mutableStateOf(false) }
    var tafsirAyah by remember { mutableStateOf<UiAyah?>(null) }
    val tafsirSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    DisposableEffect(Unit) {
        onDispose { latestLibraryTafsirSession.value?.close() }
    }
    DisposableEffect(Unit) {
        onDispose { latestMediaPlayer.value?.release() }
    }

    fun stopAudio(clearStatus: Boolean = true) {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        audioPlaying = false
        audioBusy = false
        audioPositionMs = 0L
        audioDurationMs = 0L
        if (clearStatus) audioStatus = null
    }

    fun playAudioGlobal(global: Int) {
        if (global !in 1..6236) return
        val reciter = AudioRepository.reciter(context)
        val quality = AudioRepository.quality(context)
        audioGlobal = global
        audioPositionMs = 0L
        audioDurationMs = 0L
        audioPlaying = false
        audioBusy = true
        audioStatus = "جاري تجهيز الصوت…"
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        scope.launch {
            val local = withContext(Dispatchers.IO) { AudioRepository.findLocal(context, reciter, quality, global) }
            val sources = if (local != null) listOf(local.absolutePath) else AudioRepository.urls(global, reciter, quality)
            suspend fun downloadFallback(): File? = withContext(Dispatchers.IO) {
                AudioRepository.downloadAyah(context, reciter, quality, global).getOrNull()
            }
            var sourceIndex = 0
            fun prepareSource() {
                if (sourceIndex >= sources.size) {
                    scope.launch {
                        val downloaded = downloadFallback()
                        if (downloaded == null) {
                            audioBusy = false
                            audioStatus = "تعذر تشغيل الصوت. المصدر الصوتي رفض الطلب أو تعذر الوصول إليه."
                        } else {
                            runCatching {
                                mediaPlayer?.release()
                                val mp = MediaPlayer()
                                mp.setOnPreparedListener {
                                    audioDurationMs = it.duration.coerceAtLeast(0).toLong()
                                    it.start()
                                    audioPlaying = true
                                    audioBusy = false
                                    audioStatus = "تشغيل سورة ${SURAH_NAMES[surahAyahFromGlobal(global).first - 1]} — الآية ${arabicNumber(surahAyahFromGlobal(global).second)}"
                                }
                                mp.setOnCompletionListener {
                                    audioPlaying = false
                                    audioPositionMs = audioDurationMs
                                    it.release()
                                    if (mediaPlayer === it) mediaPlayer = null
                                    audioStatus = "اكتمل تشغيل الآية."
                                }
                                mp.setOnErrorListener { player, _, _ ->
                                    runCatching { player.release() }
                                    mediaPlayer = null
                                    audioPlaying = false
                                    audioBusy = false
                                    audioStatus = "تعذر تشغيل الملف الصوتي المحمّل."
                                    true
                                }
                                mp.setDataSource(downloaded.absolutePath)
                                mp.prepareAsync()
                                mediaPlayer = mp
                            }.onFailure { audioBusy = false; audioStatus = "تعذر تشغيل الصوت: ${it.message ?: "خطأ غير معروف"}" }
                        }
                    }
                    return
                }
                val source = sources[sourceIndex++]
                runCatching {
                    mediaPlayer?.release()
                    val mp = MediaPlayer()
                    mp.setOnPreparedListener {
                        audioDurationMs = it.duration.coerceAtLeast(0).toLong()
                        it.start()
                        audioPlaying = true
                        audioBusy = false
                        audioStatus = "تشغيل سورة ${SURAH_NAMES[surahAyahFromGlobal(global).first - 1]} — الآية ${arabicNumber(surahAyahFromGlobal(global).second)}"
                    }
                    mp.setOnCompletionListener {
                        audioPlaying = false
                        audioPositionMs = audioDurationMs
                        it.release()
                        if (mediaPlayer === it) mediaPlayer = null
                        audioStatus = "اكتمل تشغيل الآية."
                    }
                    mp.setOnErrorListener { player, _, _ ->
                        runCatching { player.release() }
                        mediaPlayer = null
                        audioPlaying = false
                        prepareSource()
                        true
                    }
                    mp.setDataSource(source)
                    mp.prepareAsync()
                    mediaPlayer = mp
                }.onFailure { prepareSource() }
            }
            prepareSource()
        }
    }

    LaunchedEffect(mediaPlayer, audioPlaying) {
        while (audioPlaying) {
            val mp = mediaPlayer
            if (mp != null) {
                audioPositionMs = runCatching { mp.currentPosition.toLong() }.getOrDefault(audioPositionMs)
                audioDurationMs = runCatching { mp.duration.toLong() }.getOrDefault(audioDurationMs)
            }
            delay(200.milliseconds)
        }
    }

    fun loadLibraryTafsir(book: LibraryCatalogRepository.Book, ayah: UiAyah) {
        scope.launch {
            selectedLibraryTafsirId = book.id
            tafsirText = null
            libraryTafsirSession?.close()
            libraryTafsirSession = null
            runCatchingCancellable {
                val opened = BookRepository.openSession(context, book.dbFile)
                opened to withContext(Dispatchers.IO) {
                    opened.findTafsirText(ayah.surahName, ayah.text, ayah.numberInSurah)
                }
            }.onSuccess { (opened, text) ->
                libraryTafsirSession = opened
                tafsirText = text ?: "لم يتم العثور على تفسير مباشر لهذه الآية في هذا الكتاب."
            }.onFailure {
                tafsirText = "تعذر قراءة التفسير: ${it.message ?: "خطأ غير معروف"}"
            }
        }
    }

    fun downloadAndSelectLibraryTafsir(book: LibraryCatalogRepository.Book, ayah: UiAyah) {
        if (libraryTafsirBusyId != null) return
        scope.launch {
            libraryTafsirBusyId = book.id
            libraryTafsirProgress = 0f
            runCatchingCancellable {
                LibraryBookManager.install(context, book) { fraction ->
                    libraryTafsirProgress = fraction.coerceIn(0f, 1f)
                }
            }.onSuccess {
                libraryTafsirBooks = libraryTafsirBooks.map {
                    if (it.id == book.id) it.copy(isDownloaded = true) else it
                }
                libraryTafsirBusyId = null
                libraryTafsirProgress = 1f
                tafsirPickerOpen = false
                loadLibraryTafsir(book.copy(isDownloaded = true), ayah)
            }.onFailure {
                libraryTafsirBusyId = null
                libraryTafsirProgress = 0f
                tafsirText = "تعذر تنزيل التفسير: ${it.message ?: "خطأ غير معروف"}"
            }
        }
    }

    // نوايا التفسير تُعالج مرة واحدة عند الدخول. تشغيل الصوت أصبح عبر مشغل المصحف نفسه.
    LaunchedEffect(Unit) {
        if (initialTafsirSurah != null && initialTafsirAyah != null) {
            runCatchingCancellable { QcfQuranRepository.pageForVerse(context, initialTafsirSurah, initialTafsirAyah) }
                .onSuccess { targetPage ->
                    if (targetPage != null && targetPage in MIN_PAGE..MAX_PAGE) pagerState.scrollToPage(targetPage - 1)
                }
            runCatchingCancellable {
                if (TafsirRepository.installedItems(context).isEmpty()) TafsirRepository.ensureInstalled(context)
                TafsirRepository.get(context, initialTafsirSurah, initialTafsirAyah)?.text
            }
                .onSuccess { text -> tafsirText = text ?: "لا يوجد نص لهذه الآية في التفسير المحدد." }
                .onFailure { tafsirText = "تعذر تحميل التفسير: ${it.message ?: "خطأ غير معروف"}" }
            tafsirOpen = true
        }
    }

    LaunchedEffect(autoPlayOnOpen) {
        if (autoPlayOnOpen) {
            MushafPrefs.lastBookmarkedGlobalAyah(context)?.let { playAudioGlobal(it) }
        }
    }

    val currentPageNumber = pagerState.currentPage + 1
    var currentPageBookmarked by remember(currentPageNumber) {
        mutableStateOf(MushafPrefs.isPageBookmarked(context, currentPageNumber))
    }

    fun bump() { interactionTick++ }

    val selectedVerseKey = selectedAyah?.let { "${it.surahNumber}:${it.numberInSurah}" }
    val highlightColor = appPalette.teal.copy(alpha = if (appTheme == AppThemeName.BLACK || appTheme == AppThemeName.NIGHT) 0.30f else 0.22f)
    val readerBg = appPalette.readerBackground

    CompositionLocalProvider(LocalAppPalette provides appPalette, LocalLayoutDirection provides LayoutDirection.Rtl) {
    Box(modifier = Modifier.fillMaxSize().background(readerBg)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                val pageNumber = index + 1
                val data = pageCache[pageNumber]
                val juz = juzCache[pageNumber] ?: 1
                QcfPageBody(
                    page = data,
                    pageNumber = pageNumber,
                    juz = juz,
                    hizb = hizbCache[pageNumber] ?: 0,
                    selectedVerseKey = selectedVerseKey,
                    highlightColor = highlightColor,
                    onAyahLongPress = { ayah ->
                        selectedAyah = ayah
                        ayahBookmarked = MushafPrefs.isAyahBookmarked(context, ayah.surahNumber, ayah.numberInSurah)
                        sheetOpen = true
                        bump()
                    },
                    onInteraction = { bump() }
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = { Text("المصحف") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appPalette.teal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الرجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { pageJumpValue = currentPageNumber.toString(); pageJumpOpen = true; bump() }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "تصفح الصفحات")
                    }
                    IconButton(onClick = {
                        currentPageBookmarked = MushafPrefs.togglePageBookmark(context, currentPageNumber)
                        bump()
                    }) {
                        Icon(
                            if (currentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "العلامة المرجعية"
                        )
                    }
                }
            )
        }
        if (audioGlobal > 0) {
            val (audioSurah, audioAyah) = surahAyahFromGlobal(audioGlobal)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(appPalette.paper.copy(alpha = 0.98f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    "🎧 سورة ${SURAH_NAMES[audioSurah - 1]} — الآية ${arabicNumber(audioAyah)}",
                    color = appPalette.ink, fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start
                )
                audioStatus?.let {
                    Text(it, color = appPalette.soft, fontFamily = AmiriFontFamily, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                if (audioDurationMs > 0L) {
                    Slider(
                        value = audioPositionMs.coerceIn(0L, audioDurationMs).toFloat(),
                        onValueChange = { value ->
                            audioPositionMs = value.toLong()
                            runCatching { mediaPlayer?.seekTo(value.toInt()) }
                        },
                        valueRange = 0f..audioDurationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatAudioTime(audioPositionMs), color = appPalette.soft, fontSize = 11.sp)
                        Text(formatAudioTime(audioDurationMs), color = appPalette.soft, fontSize = 11.sp)
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = audioGlobal > 1 && !audioBusy, onClick = { playAudioGlobal(audioGlobal - 1) }) { Text("السابق", fontFamily = AmiriFontFamily) }
                    Button(onClick = { if (audioPlaying) { runCatching { mediaPlayer?.pause() }; audioPlaying = false } else { runCatching { mediaPlayer?.start() }; audioPlaying = true } }, enabled = !audioBusy && mediaPlayer != null, colors = ButtonDefaults.buttonColors(containerColor = appPalette.teal)) {
                        Text(if (audioPlaying) "إيقاف مؤقت" else "تشغيل", color = Color.White, fontFamily = AmiriFontFamily)
                    }
                    TextButton(enabled = audioGlobal < 6236 && !audioBusy, onClick = { playAudioGlobal(audioGlobal + 1) }) { Text("التالي", fontFamily = AmiriFontFamily) }
                    TextButton(enabled = !audioBusy, onClick = {
                        audioBusy = true
                        audioStatus = "جاري تنزيل الآية…"
                        scope.launch {
                            val result = AudioRepository.downloadAyah(context, AudioRepository.reciter(context), AudioRepository.quality(context), audioGlobal)
                            audioBusy = false
                            audioStatus = if (result.isSuccess) "تم حفظ الآية على الجهاز ✅" else "فشل تنزيل الآية: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
                        }
                    }) { Text("تنزيل", fontFamily = AmiriFontFamily) }
                    TextButton(onClick = { stopAudio() }) { Text("إغلاق", fontFamily = AmiriFontFamily) }
                }
            }
        }
    }

    // BottomSheet خيارات الآية
    selectedAyah?.takeIf { sheetOpen }?.let { ayah ->
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false; selectedAyah = null },
            sheetState = sheetState,
            containerColor = LocalAppPalette.current.paper
        ) {
            AyahSheetContent(
                ayah = ayah,
                bookmarked = ayahBookmarked,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ayah", ayah.text))
                },
                onShare = {
                    scope.launch {
                        val meta = AyahMetaRepository.get(context, ayah.surahNumber, ayah.numberInSurah)
                        val juz = meta?.juz ?: 0
                        val hizb = meta?.hizb ?: 0
                        AyahShareImage.share(context, ayah, juz, hizb).onFailure {
                            Toast.makeText(context, "تعذر تجهيز صورة الآية للمشاركة", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onToggleBookmark = {
                    ayahBookmarked = MushafPrefs.toggleAyahBookmark(context, ayah.surahNumber, ayah.numberInSurah)
                },
                onTafsir = {
                    scope.launch {
                        tafsirText = null
                        tafsirAyah = ayah
                        tafsirOpen = true
                        tafsirPickerOpen = false
                        tafsirSearchQuery = ""
                        sheetOpen = false
                        selectedAyah = null
                        runCatchingCancellable {
                            libraryTafsirBooks = LibraryCatalogRepository.getBooks(context)
                                .filter { it.id in LIBRARY_TAFSIR_BOOK_IDS }
                            if (TafsirRepository.installedItems(context).isEmpty()) TafsirRepository.ensureInstalled(context)
                            TafsirRepository.get(context, ayah.surahNumber, ayah.numberInSurah)?.text
                        }
                            .onSuccess { tafsirText = it ?: "لا يوجد نص لهذه الآية في التفسير المحدد." }
                            .onFailure { tafsirText = "تعذر تحميل التفسير: ${it.message ?: "خطأ غير معروف"}" }
                    }
                },
                onListen = {
                    playAudioGlobal(ayah.global)
                    sheetOpen = false
                    selectedAyah = null
                }

            )
        }
    }

    // التفسير: اختيار الكتب أصبح عموديًا وقابلًا للبحث بدل القائمة الأفقية الثقيلة.
    if (tafsirOpen) {
        ModalBottomSheet(
            onDismissRequest = { tafsirOpen = false },
            sheetState = tafsirSheetState,
            containerColor = appPalette.paper
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("التفسير", fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = appPalette.ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                tafsirAyah?.let { a ->
                    Text("سورة ${a.surahName} • الآية ${arabicNumber(a.numberInSurah)}", fontFamily = AmiriFontFamily, color = appPalette.gold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = appPalette.paper2), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(a.text, fontFamily = AmiriFontFamily, fontSize = 19.sp, lineHeight = 34.sp, color = appPalette.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(14.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(onClick = { tafsirPickerOpen = !tafsirPickerOpen }, colors = CardDefaults.cardColors(containerColor = appPalette.paper2), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("كتب التفاسير", fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = appPalette.ink)
                                Text(if (selectedLibraryTafsirId != null) libraryTafsirBooks.firstOrNull { it.id == selectedLibraryTafsirId }?.title ?: "تفسير من المكتبة" else "اضغط لاختيار تفسير من المكتبة", fontFamily = AmiriFontFamily, fontSize = 14.sp, color = appPalette.soft)
                            }
                            Text(if (tafsirPickerOpen) "⌃" else "⌄", fontSize = 24.sp, color = appPalette.ink)
                        }
                    }
                    if (tafsirPickerOpen) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = tafsirSearchQuery, onValueChange = { tafsirSearchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, label = { Text("ابحث عن اسم التفسير", fontFamily = AmiriFontFamily) }, textStyle = TextStyle(textDirection = TextDirection.Rtl, fontFamily = AmiriFontFamily))
                        val filteredTafsirs = libraryTafsirBooks.filter {
                            tafsirSearchQuery.isBlank() || it.title.contains(tafsirSearchQuery.trim(), ignoreCase = true) || it.author.orEmpty().contains(tafsirSearchQuery.trim(), ignoreCase = true)
                        }
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(filteredTafsirs, key = { it.id }) { book ->
                                val busy = libraryTafsirBusyId == book.id
                                Card(colors = CardDefaults.cardColors(containerColor = if (selectedLibraryTafsirId == book.id) appPalette.paper2 else appPalette.paper), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(book.title, fontFamily = AmiriFontFamily, fontWeight = if (selectedLibraryTafsirId == book.id) FontWeight.Bold else FontWeight.Normal, fontSize = 17.sp, color = appPalette.ink, textAlign = TextAlign.Start)
                                            Text(if (book.isDownloaded) "مثبت على الجهاز" else "غير محمل", fontFamily = AmiriFontFamily, fontSize = 13.sp, color = if (book.isDownloaded) appPalette.gold else appPalette.soft)
                                        }
                                        if (busy) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(progress = { libraryTafsirProgress.coerceIn(0f, 1f) }, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                                                Text("${(libraryTafsirProgress * 100).toInt()}%", fontSize = 10.sp, color = appPalette.soft)
                                            }
                                        } else if (book.isDownloaded) {
                                            TextButton(onClick = { tafsirPickerOpen = false; loadLibraryTafsir(book, a) }) { Text(if (selectedLibraryTafsirId == book.id) "محدد" else "اختيار", fontFamily = AmiriFontFamily) }
                                        } else {
                                            Button(onClick = { downloadAndSelectLibraryTafsir(book, a) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text("تحميل", fontFamily = AmiriFontFamily, fontSize = 13.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(tafsirText ?: "جاري تحميل التفسير…", fontFamily = AmiriFontFamily, fontSize = 18.sp, lineHeight = 34.sp, color = appPalette.ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                TextButton(onClick = { tafsirOpen = false }, modifier = Modifier.align(Alignment.Start)) { Text("إغلاق", fontFamily = AmiriFontFamily) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // الانتقال المباشر لصفحة
    if (pageJumpOpen) {
        AlertDialog(
            onDismissRequest = { pageJumpOpen = false },
            title = { Text("الانتقال إلى صفحة") },
            text = {
                Column {
                    Text("أدخل رقم صفحة المصحف من ١ إلى ٦٠٤", color = LocalAppPalette.current.readerMuted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageJumpValue,
                        onValueChange = { v -> pageJumpValue = v.filter { it.isDigit() } },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = clampPage(pageJumpValue.toIntOrNull() ?: currentPageNumber)
                    pageJumpOpen = false
                    scope.launch { pagerState.scrollToPage(target - 1) }
                }) { Text("انتقال") }
            },
            dismissButton = {
                TextButton(onClick = { pageJumpOpen = false }) { Text("إلغاء") }
            }
        )
    }
    }
}

/* ------------------------------------------------------------------------
 * محتوى صفحة واحدة من المصحف - عرض QCF4 سطرًا بسطر، مطابق حرفيًا للمطبوع.
 * كل سطر JSON = سطر مطبوع فعلي (نهايات الأسطر ثابتة، بلا أي التفاف حسابي).
 * ------------------------------------------------------------------------ */
private const val MEASURE_REF_SIZE_SP = 100f
private const val MIN_LINE_FONT_SP = 14f
private const val MAX_LINE_FONT_SP = 34f

@Composable
private fun QcfPageBody(
    page: QcfPage?,
    pageNumber: Int,
    juz: Int,
    hizb: Int,
    selectedVerseKey: String?,
    highlightColor: Color,
    onAyahLongPress: (UiAyah) -> Unit,
    onInteraction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppPalette.current.readerBackground)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(64.dp)) // مساحة أسفل الشريط العلوي

        if (page == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("جارٍ فتح المصحف…", color = LocalAppPalette.current.readerText, fontFamily = AmiriFontFamily, fontSize = 20.sp)
            }
            return@Column
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val availableWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
            val touchSlop = LocalViewConfiguration.current.touchSlop

            // نحسب حجم خط موحّد لهذه الصفحة بحيث يملأ أطول سطر فيها كامل عرض الشاشة
            // (تمامًا كما هي مصممة الخطوط لتُعرض بعرض صفحة المصحف الكاملة) دون التفاف.
            val fontSizeSp = remember(page, availableWidthPx) {
                var maxWidthAtRef = 1f
                for (line in page.lines) {
                    val annotated = buildLineAnnotatedString(line, includeAnnotations = false)
                    if (annotated.text.isEmpty()) continue
                    val result = textMeasurer.measure(
                        text = annotated,
                        style = TextStyle(fontSize = MEASURE_REF_SIZE_SP.sp)
                    )
                    val w = result.size.width.toFloat()
                    if (w > maxWidthAtRef) maxWidthAtRef = w
                }
                val scale = availableWidthPx / maxWidthAtRef
                (MEASURE_REF_SIZE_SP * scale).coerceIn(MIN_LINE_FONT_SP, MAX_LINE_FONT_SP)
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Center
            ) {
                for (line in page.lines) {
                    val hasHeader = line.words.any { it.type == "surah_header" }
                    val hasBismillah = line.words.any { it.type == "bismillah" }

                    when {
                        hasHeader -> {
                            val w = line.words.first { it.type == "surah_header" }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = w.char,
                                    fontFamily = QcfFonts.familyFor(w.font),
                                    fontSize = (fontSizeSp * 0.95f).sp,
                                    color = LocalAppPalette.current.gold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        hasBismillah -> {
                            val w = line.words.first { it.type == "bismillah" }
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = w.char,
                                    fontFamily = QcfFonts.familyFor(w.font),
                                    fontSize = fontSizeSp.sp,
                                    color = LocalAppPalette.current.readerText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        else -> {
                            var layoutResult by remember(page.page, line.line) {
                                mutableStateOf<TextLayoutResult?>(null)
                            }
                            val annotated = remember(page.page, line.line, selectedVerseKey, highlightColor) {
                                buildLineAnnotatedString(line, includeAnnotations = true, selectedVerseKey = selectedVerseKey)
                            }
                            Text(
                                text = annotated,
                                fontSize = fontSizeSp.sp,
                                color = LocalAppPalette.current.readerText,
                                textAlign = TextAlign.Center,
                                style = TextStyle(textDirection = TextDirection.Ltr),
                                maxLines = 1,
                                onTextLayout = { layoutResult = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind {
                                        // نرسم التحديد مرة واحدة على مستوى الآية كاملة، بدلاً من
                                        // وضع خلفية منفصلة على كل كلمة، حتى لا يظهر التحديد كمقاطع.
                                        if (selectedVerseKey != null) {
                                            val tl = layoutResult
                                            if (tl != null && annotated.isNotEmpty()) {
                                                val ranges = annotated
                                                    .getStringAnnotations("ayah", 0, annotated.length)
                                                    .filter { it.item == selectedVerseKey }
                                                if (ranges.isNotEmpty()) {
                                                    var left = Float.POSITIVE_INFINITY
                                                    var top = Float.POSITIVE_INFINITY
                                                    var right = Float.NEGATIVE_INFINITY
                                                    var bottom = Float.NEGATIVE_INFINITY
                                                    for (range in ranges) {
                                                        val start = tl.getBoundingBox(range.start)
                                                        val end = tl.getBoundingBox((range.end - 1).coerceAtLeast(range.start))
                                                        left = minOf(left, start.left, end.left)
                                                        top = minOf(top, start.top, end.top)
                                                        right = maxOf(right, start.right, end.right)
                                                        bottom = maxOf(bottom, start.bottom, end.bottom)
                                                    }
                                                    if (left.isFinite() && right.isFinite()) {
                                                        val pad = 4.dp.toPx()
                                                        drawRoundRect(
                                                            color = highlightColor.copy(alpha = 0.42f),
                                                            topLeft = androidx.compose.ui.geometry.Offset(left - pad, top - pad),
                                                            size = androidx.compose.ui.geometry.Size(
                                                                (right - left + pad * 2).coerceAtLeast(0f),
                                                                (bottom - top + pad * 2).coerceAtLeast(0f)
                                                            ),
                                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                                6.dp.toPx(), 6.dp.toPx()
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .pointerInput(page.page, line.line, selectedVerseKey) {
                                        // نتعرف على "نقرة" فورًا عند رفع الإصبع بدون أي انتظار زمني:
                                        // إن تحرك الإصبع أكثر من touchSlop قبل الرفع فهذه عملية تمرير للصفحة
                                        // (نتركها لمكوّن HorizontalPager)، وإلا فهي نقرة صحيحة على السطر
                                        // فنحدد الآية تحت موضع اللمس فورًا ونعرضها ونفتح قائمة خياراتها مباشرة.
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var moved = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (change.positionChange().getDistance() > touchSlop) {
                                                    moved = true
                                                    break
                                                }
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }

                                            if (!moved) {
                                                val tl = layoutResult
                                                val tappedAyah = if (tl != null && annotated.text.isNotEmpty()) {
                                                    val offset = tl.getOffsetForPosition(down.position).coerceIn(0, annotated.text.lastIndex)
                                                    val startOffset = (offset - 4).coerceAtLeast(0)
                                                    val endOffset = (offset + 5).coerceAtMost(annotated.text.length)
                                                    annotated.getStringAnnotations("ayah", startOffset, endOffset).firstOrNull()
                                                        ?.let { ann -> uiAyahFor(page, ann.item) }
                                                } else null
                                                if (tappedAyah != null) onAyahLongPress(tappedAyah) else onInteraction()
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        PageFooter(page = page, pageNumber = pageNumber, juz = juz, hizb = hizb)
        Spacer(Modifier.height(16.dp))
    }
}

/** يبني نص سطر واحد (Row من كلمات) بخط كل كلمة الصحيح، مع علامة "ayah" لكل كلمة قابلة للتحديد. */
private fun buildLineAnnotatedString(
    line: QcfLine,
    includeAnnotations: Boolean,
    selectedVerseKey: String? = null
): AnnotatedString = buildAnnotatedString {
    // بيانات QCF تخزن الكلمات بترتيب القراءة العربي (من أول الكلمة إلى آخرها)،
    // لكن أحرف QCF هي Private-Use glyphs ويعاملها محرك النص كرموز LTR.
    // لذلك نعكس ترتيب الوحدات هنا ونعرض السطر كـ LTR حتى يظهر بصريًا من اليمين إلى اليسار.
    for ((idx, w) in line.words.asReversed().withIndex()) {
        if (w.char.isEmpty()) continue
        val highlight = selectedVerseKey != null && w.verseKey == selectedVerseKey
        withStyle(
            SpanStyle(
                fontFamily = QcfFonts.familyFor(w.font),
                // خلفية التحديد تُرسم في drawBehind على مستوى الآية كاملة.
                color = if (highlight) Color.White else Color.Unspecified
            )
        ) {
            if (includeAnnotations && w.verseKey != null) {
                pushStringAnnotation(tag = "ayah", annotation = w.verseKey)
                append(w.char)
                pop()
            } else {
                append(w.char)
            }
        }
        if (idx != line.words.lastIndex) append(" ")
    }
}

private fun surahAyahFromGlobal(global: Int): Pair<Int, Int> {
    require(global in 1..6236) { "Invalid global ayah number: $global" }
    var remaining = global
    for (surah in 1..114) {
        val count = SURAH_AYAH_COUNTS_FOR_AUDIO[surah - 1]
        if (remaining <= count) return surah to remaining
        remaining -= count
    }
    return 114 to SURAH_AYAH_COUNTS_FOR_AUDIO.last()
}

private val SURAH_AYAH_COUNTS_FOR_AUDIO = intArrayOf(
    7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6
)

private fun formatAudioTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun PageFooter(page: QcfPage, pageNumber: Int, juz: Int, hizb: Int) {
    val surahName = page.surahs.firstOrNull()?.nameArabic ?: "المصحف"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arabicNumber(pageNumber), color = LocalAppPalette.current.readerMuted, fontSize = 13.sp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "الجزء ${arabicNumber(juz)}",
                color = LocalAppPalette.current.readerMuted,
                fontSize = 11.sp
            )
            if (hizb > 0) {
                Text(
                    "الحزب ${arabicNumber(hizb)}",
                    color = LocalAppPalette.current.readerMuted,
                    fontSize = 10.sp
                )
            }
        }
        Text(
            "سورة $surahName",
            color = LocalAppPalette.current.readerText,
            fontFamily = AmiriFontFamily,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun AyahSheetContent(
    ayah: UiAyah,
    bookmarked: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTafsir: () -> Unit,
    onListen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "خيارات الآية",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = LocalAppPalette.current.readerText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            ayah.text,
            fontFamily = AmiriFontFamily,
            fontSize = 20.sp,
            color = LocalAppPalette.current.readerText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "سورة ${ayah.surahName} • الآية ${arabicNumber(ayah.numberInSurah)}",
            fontSize = 12.sp,
            color = LocalAppPalette.current.readerMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)
        )

        SheetActionRow(title = "نسخ الآية", subtitle = "نسخ نص الآية إلى الحافظة", onClick = onCopy)
        SheetActionRow(title = "مشاركة الآية", subtitle = "مشاركة الآية مع اسم السورة ورقمها", onClick = onShare)
        SheetActionRow(
            title = if (bookmarked) "إزالة العلامة المرجعية" else "إضافة علامة مرجعية",
            subtitle = "حفظ الآية للرجوع إليها لاحقًا",
            onClick = onToggleBookmark
        )
        SheetActionRow(title = "عرض التفسير", subtitle = "قراءة تفسير الآية بشكل مريح", onClick = onTafsir)
        SheetActionRow(title = "الاستماع إلى الآية", subtitle = "تشغيل صوت القارئ المحدد", onClick = onListen)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, color = LocalAppPalette.current.readerText, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
            Text(subtitle, color = LocalAppPalette.current.readerMuted, fontSize = 11.sp, textAlign = TextAlign.Start)
        }
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(LocalAppPalette.current.teal.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                title.contains("نسخ") -> "⧉"
                title.contains("مشاركة") -> "↗"
                title.contains("علامة") -> "🔖"
                title.contains("التفسير") -> "📖"
                else -> "▶"
            }
            Text(icon, color = LocalAppPalette.current.teal, fontSize = 19.sp)
        }
    }
}

