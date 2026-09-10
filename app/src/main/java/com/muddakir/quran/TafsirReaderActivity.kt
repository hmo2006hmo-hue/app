package com.muddakir.quran

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class TafsirReaderActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_TAFSIR_ID = "tafsir_id"
        fun intent(context: Context, tafsirId: Int): Intent =
            Intent(context, TafsirReaderActivity::class.java).putExtra(EXTRA_TAFSIR_ID, tafsirId)
    
        private val SURAH_NAMES_LOCAL = listOf(
            "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس", "التكوير", "الإنفطار", "المطففين", "الإنشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
        )

        private val SURAH_AYAH_COUNTS = intArrayOf(
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
            123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
            112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
            34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
            54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
            60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
            14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
            28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
            29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
            15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
            11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
            5, 4, 5, 6
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val id = intent.getIntExtra(EXTRA_TAFSIR_ID, -1)
        val item = TafsirRepository.catalogItem(id)
        if (item == null) {
            finish()
            return
        }
        TafsirRepository.select(this, id)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { Reader(item) }
            }
        }
    }

    private fun moveNext(surah: Int, ayah: Int): Pair<Int, Int>? {
        val maxAyah = SURAH_AYAH_COUNTS.getOrNull(surah - 1) ?: return null
        return when {
            ayah < maxAyah -> surah to (ayah + 1)
            surah < SURAH_NAMES_LOCAL.size -> (surah + 1) to 1
            else -> null
        }
    }

    private fun movePrevious(surah: Int, ayah: Int): Pair<Int, Int>? {
        return when {
            ayah > 1 -> surah to (ayah - 1)
            surah > 1 -> (surah - 1) to SURAH_AYAH_COUNTS[surah - 2]
            else -> null
        }
    }

    @Composable
    private fun Reader(item: TafsirRepository.CatalogItem) {
        val scope = rememberCoroutineScope()
        var surah by remember { mutableIntStateOf(1) }
        var ayah by remember { mutableIntStateOf(1) }
        var loading by remember { mutableStateOf(true) }
        var result by remember { mutableStateOf<TafsirRepository.AyahTafsir?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        fun load() {
            loading = true
            error = null
            scope.launch {
                runCatchingCancellable { TafsirRepository.get(this@TafsirReaderActivity, surah, ayah) }
                    .onSuccess { value -> result = value; if (value == null) error = "لا يوجد تفسير لهذه الآية في هذا المصدر." }
                    .onFailure { error = it.message ?: "تعذر قراءة التفسير" }
                loading = false
            }
        }

        LaunchedEffect(item.id) { load() }

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { finish() }) { Text("رجوع") }
                Text(item.title, modifier = Modifier.weight(1f), fontSize = 21.sp, textAlign = TextAlign.Start)
            }
            Text(item.author, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = moveNext(surah, ayah) != null, onClick = {
                    moveNext(surah, ayah)?.let { (nextSurah, nextAyah) ->
                        surah = nextSurah
                        ayah = nextAyah
                        load()
                    }
                }) { Text("الآية التالية") }
                Button(enabled = movePrevious(surah, ayah) != null, onClick = {
                    movePrevious(surah, ayah)?.let { (prevSurah, prevAyah) ->
                        surah = prevSurah
                        ayah = prevAyah
                        load()
                    }
                }) { Text("الآية السابقة") }
                Button(onClick = { load() }) { Text("عرض") }
            }
            Text("${SURAH_NAMES_LOCAL.getOrNull(surah - 1) ?: "السورة $surah"} — الآية $ayah", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                result != null -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(result!!.text, fontSize = 19.sp, lineHeight = 32.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                Spacer(Modifier.height(10.dp))
                                Text("${result!!.surahName} • الآية ${result!!.ayahNumber}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            }
        }
    }

}
