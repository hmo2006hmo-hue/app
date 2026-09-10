package com.muddakir.quran

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Generates a polished Quran ayah card for sharing. */
object AyahShareImage {
    private const val WIDTH = 1200
    private const val OUTER = 30f
    private const val INNER = 72f
    private const val TOP = 56f
    private const val BOTTOM = 62f
    private const val CARD_RADIUS = 52f
    private const val VERSE_RADIUS = 38f
    private const val CHIP_RADIUS = 30f

    private const val CARD = 0xFFF8F2E5.toInt()
    private const val VERSE_CARD = 0xFFF1E8D5.toInt()
    private const val INK = 0xFF173B35.toInt()
    private const val MUTED = 0xFF6F756E.toInt()
    private const val GOLD = 0xFFC89E32.toInt()
    private const val GOLD_SOFT = 0xFFD9B85A.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val WHITE_SOFT = 0xD9FFFFFF.toInt()

    suspend fun share(
        context: Context,
        ayah: UiAyah,
        juz: Int,
        hizb: Int
    ): Result<Unit> = runCatchingCancellable {
        val file = withContext(Dispatchers.IO) { createPng(context, ayah, juz, hizb) }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "آية من القرآن الكريم")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الآية"))
    }

    private fun createPng(context: Context, ayah: UiAyah, juz: Int, hizb: Int): File {
        val typeface = ResourcesCompat.getFont(context, R.font.amiri_regular) ?: Typeface.DEFAULT
        val boldTypeface = ResourcesCompat.getFont(context, R.font.amiri_bold) ?: typeface
        val contentWidth = WIDTH - ((OUTER + INNER) * 2).toInt()
        val verseWidth = contentWidth - 64

        // ترتيب المحتوى داخل الصورة كما طلب المستخدم: السورة، الآية + رقمها، الجزء، الحزب.
        val surahTitle = "سورة ${ayah.surahName}"
        val verseText = "${ayah.text.trim()}  ﴿${arabicNumber(ayah.numberInSurah)}﴾"

        val brandPaint = textPaint(27f, boldTypeface, WHITE)
        val titlePaint = textPaint(62f, boldTypeface, INK)
        val versePaint = textPaint(56f, typeface, INK)
        val metaLabelPaint = textPaint(23f, boldTypeface, MUTED)
        val metaValuePaint = textPaint(32f, boldTypeface, INK)
        val footerPaint = textPaint(22f, typeface, WHITE_SOFT)

        val titleLayout = staticLayout(surahTitle, titlePaint, contentWidth)
        val verseLayout = staticLayout(verseText, versePaint, verseWidth)
        val footerLayout = staticLayout("من المصحف الشريف • مِشكاة", footerPaint, contentWidth)

        val brandTop = TOP
        val titleTop = brandTop + 92f
        val titleBottom = titleTop + titleLayout.height
        val dividerY = titleBottom + 24f
        val verseTop = dividerY + 26f
        val verseCardHeight = verseLayout.height + 96f
        val metaTop = verseTop + verseCardHeight + 34f
        val metaHeight = 124f
        val footerTop = metaTop + metaHeight + 34f
        val contentHeight = (footerTop + footerLayout.height + BOTTOM).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, contentHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // بطاقة رئيسية.
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        canvas.drawRoundRect(
            OUTER,
            OUTER,
            WIDTH - OUTER,
            contentHeight - OUTER,
            CARD_RADIUS,
            CARD_RADIUS,
            cardPaint
        )

        // شارة صغيرة للهوية، بدون إدخالها في ترتيب البيانات المطلوب.
        val brandBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                WIDTH.toFloat(),
                0f,
                GOLD,
                GOLD_SOFT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            WIDTH / 2f - 180f,
            brandTop,
            WIDTH / 2f + 180f,
            brandTop + 52f,
            26f,
            26f,
            brandBoxPaint
        )
        drawCentered(canvas, "القرآن الكريم", brandPaint, brandTop + 2f, 48f)

        // 1) اسم السورة.
        titleLayout.drawAt(canvas, titleTop, horizontal = OUTER + INNER)

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        canvas.drawRoundRect(
            WIDTH / 2f - 64f,
            dividerY,
            WIDTH / 2f + 64f,
            dividerY + 5f,
            3f,
            3f,
            dividerPaint
        )

        // 2) نص الآية + رقمها في النهاية كما في المصحف.
        val verseCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VERSE_CARD }
        canvas.drawRoundRect(
            OUTER + 34f,
            verseTop,
            WIDTH - OUTER - 34f,
            verseTop + verseCardHeight,
            VERSE_RADIUS,
            VERSE_RADIUS,
            verseCardPaint
        )
        drawVerseOrnaments(canvas, verseTop, verseCardHeight)
        verseLayout.drawAt(
            canvas,
            verseTop + 48f,
            horizontal = OUTER + INNER + 32f
        )

        // 3) رقم الجزء.
        // 4) رقم الحزب.
        val chipGap = 26f
        val chipWidth = (contentWidth - chipGap) / 2f
        drawMetaChip(
            canvas = canvas,
            left = OUTER + INNER,
            top = metaTop,
            width = chipWidth,
            height = metaHeight,
            label = "الحزب",
            value = if (hizb > 0) arabicNumber(hizb) else "—",
            labelPaint = metaLabelPaint,
            valuePaint = metaValuePaint,
            icon = "◈"
        )
        drawMetaChip(
            canvas = canvas,
            left = OUTER + INNER + chipWidth + chipGap,
            top = metaTop,
            width = chipWidth,
            height = metaHeight,
            label = "الجزء",
            value = if (juz > 0) arabicNumber(juz) else "—",
            labelPaint = metaLabelPaint,
            valuePaint = metaValuePaint,
            icon = "◉"
        )

        footerLayout.drawAt(canvas, footerTop, horizontal = OUTER + INNER)

        val folder = File(context.cacheDir, "shared_ayahs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(folder, "ayah_${ayah.surahNumber}_${ayah.numberInSurah}_$stamp.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "Unable to encode share image"
            }
        }
        bitmap.recycle()
        pruneOldFiles(folder)
        return file
    }

    private fun drawVerseOrnaments(canvas: Canvas, top: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val size = 26f
        val left = OUTER + 56f
        val right = WIDTH - OUTER - 56f
        val upper = top + 24f
        val lower = top + height - 24f
        canvas.drawArc(left, upper, left + size, upper + size, 0f, 90f, false, paint)
        canvas.drawArc(right - size, upper, right, upper + size, 90f, 90f, false, paint)
        canvas.drawArc(left, lower - size, left + size, lower, 270f, 90f, false, paint)
        canvas.drawArc(right - size, lower - size, right, lower, 180f, 90f, false, paint)
    }

    private fun drawMetaChip(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        label: String,
        value: String,
        labelPaint: TextPaint,
        valuePaint: TextPaint,
        icon: String
    ) {
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VERSE_CARD }
        canvas.drawRoundRect(left, top, left + width, top + height, CHIP_RADIUS, CHIP_RADIUS, chipPaint)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        canvas.drawRoundRect(
            left + 18f,
            top + 28f,
            left + 24f,
            top + height - 28f,
            3f,
            3f,
            accent
        )

        val iconPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 27f
            typeface = valuePaint.typeface
            color = GOLD
        }
        val iconLayout = staticLayout(icon, iconPaint, 44)
        iconLayout.drawAt(canvas, top + 24f, left + 34f)

        val textLeft = left + 88f
        val textWidth = (width - 112f).toInt()
        val labelLayout = staticLayout(label, labelPaint, textWidth)
        val valueLayout = staticLayout(value, valuePaint, textWidth)
        labelLayout.drawAt(canvas, top + 20f, textLeft)
        valueLayout.drawAt(canvas, top + 62f, textLeft)
    }

    private fun drawCentered(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        top: Float,
        height: Float
    ) {
        val width = WIDTH - 120
        val layout = staticLayout(text, paint, width)
        layout.drawAt(canvas, top + (height - layout.height) / 2f, 60f)
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        val safeText = if (text.isBlank()) "—" else text
        return StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
    }

    private fun StaticLayout.drawAt(canvas: Canvas, top: Float, horizontal: Float) {
        canvas.save()
        canvas.translate(horizontal, top)
        draw(canvas)
        canvas.restore()
    }

    private fun textPaint(size: Float, typeface: Typeface, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.typeface = typeface
            this.color = color
            isSubpixelText = true
            isAntiAlias = true
        }

    private fun pruneOldFiles(folder: File) {
        val files = folder.listFiles { file -> file.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(8).forEach { runCatching { it.delete() } }
    }

    private fun arabicNumber(value: Int): String =
        value.toString().map {
            when (it) {
                '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
                '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
                else -> it
            }
        }.joinToString("")
}
