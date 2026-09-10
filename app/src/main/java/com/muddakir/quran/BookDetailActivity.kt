package com.muddakir.quran

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Book detail screen (spec section 4): shown when the user taps an
 * not-yet-installed book from the library list. Every field here comes
 * straight from [LibraryCatalogRepository.Book] - nothing is invented.
 */
class BookDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, BookDetailActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    DetailContent(bookId)
                }
            }
        }
    }

    @Composable
    private fun DetailContent(bookId: Long) {
        val scope = rememberCoroutineScope()
        var book by remember { mutableStateOf<LibraryCatalogRepository.Book?>(null) }
        var loading by remember { mutableStateOf(true) }
        var working by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }

        suspend fun reload() {
            runCatchingCancellable {
                LibraryCatalogRepository.getBook(this@BookDetailActivity, bookId)
            }.onSuccess { book = it }
                .onFailure { message = it.message ?: "تعذر تحميل بيانات الكتاب" }
        }

        LaunchedEffect(bookId) {
            loading = true
            reload()
            loading = false
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "تفاصيل الكتاب",
                    style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { finish() }) {
                    Text("رجوع", style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val current = book
            if (current == null) {
                Text(
                    message ?: "الكتاب غير موجود في الكتالوج.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                return@Column
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("📖", fontSize = 48.sp)
            }

            Text(
                current.title,
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            bookNumber(current.dbFile)?.let {
                DetailRow("رقم الكتاب", "$it")
            }
            current.author?.takeIf { it.isNotBlank() }?.let { DetailRow("المؤلف", it) }
            current.description?.takeIf { it.isNotBlank() }?.let { DetailRow("الوصف/النوع", it) }
            DetailRow("النوع", if (current.format.equals("sqlite", true)) "قاعدة SQLite" else "ملف JSON")
            current.sizeBytes?.let { DetailRow("حجم قاعدة البيانات", formatBytes(it)) }
            DetailRow("الحالة", if (current.isDownloaded) "مثبت" else "غير مثبت")

            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("تم")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(Modifier.height(8.dp))

            fun openReader() {
                when (current.format.lowercase()) {
                    "sqlite" -> startActivity(LibrarySqliteReaderActivity.intent(this@BookDetailActivity, current.dbFile, current.title))
                    "json" -> startActivity(Intent(this@BookDetailActivity, LibraryReaderActivity::class.java).putExtra(LibraryReaderActivity.EXTRA_BOOK_ID, current.externalId))
                }
            }

            if (current.isDownloaded) {
                Button(
                    onClick = { openReader() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working
                ) { Text("قراءة الكتاب") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            working = true
                            runCatchingCancellable { LibraryBookManager.delete(this@BookDetailActivity, current) }
                                .onSuccess {
                                    message = "تم حذف الكتاب من الجهاز."
                                    reload()
                                }
                                .onFailure { message = "تعذر حذف الكتاب: ${it.message ?: "خطأ غير معروف"}" }
                            working = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working
                ) { Text("حذف الكتاب") }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            working = true
                            message = "جاري تنزيل ${current.title}…"
                            runCatchingCancellable { LibraryBookManager.install(this@BookDetailActivity, current) }
                                .onSuccess {
                                    message = "تم تنزيل الكتاب بنجاح."
                                    reload()
                                }
                                .onFailure { message = "فشل تنزيل الكتاب: ${it.message ?: "خطأ غير معروف"}" }
                            working = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working
                ) {
                    if (working) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text("تحميل الكتاب")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(value, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}
