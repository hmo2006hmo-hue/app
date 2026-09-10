@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.muddakir.quran

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val LibraryFont = FontFamily(Font(R.font.amiri_regular), Font(R.font.amiri_bold, FontWeight.Bold))

class LibraryReaderActivity : ComponentActivity() {
    companion object { const val EXTRA_BOOK_ID = "book_id" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val id = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { LibraryReaderScreen(id) } }
    }
}

@Composable
private fun LibraryReaderScreen(bookId: String) {
    val context = LocalContext.current
    val palette = paletteFor(AppThemeStore.get(context))
    var pages by remember { mutableStateOf<List<LibraryPage>>(emptyList()) }
    var validationFailed by remember { mutableStateOf(false) }
    LaunchedEffect(bookId) {
        val book = runCatching {
            LibraryCatalogRepository.getBookByExternalId(context, bookId)
        }.getOrNull()
        if (book == null || !book.format.equals("json", ignoreCase = true) || !book.isDownloaded) {
            validationFailed = true
            pages = emptyList()
            return@LaunchedEffect
        }
        validationFailed = false
        pages = LibraryRepository.loadPages(context, bookId)
    }
    if (validationFailed || pages.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("هذا الكتاب غير مثبت أو لا يحتوي على صفحات قابلة للعرض.", fontFamily = LibraryFont, textAlign = TextAlign.Center)
        }
        return
    }
    val initialPage = remember(pages, bookId) { LibraryPreferences.getLastPosition(context, bookId).toInt().coerceIn(0, (pages.size - 1).coerceAtLeast(0)) }
    val state = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.currentPage, bookId) { LibraryPreferences.setLastPosition(context, bookId, state.currentPage.toLong()) }
    Scaffold(
        containerColor = palette.paper2,
        topBar = { TopAppBar(title = { Text("قارئ المكتبة", fontFamily = LibraryFont, color = Color.White) }, navigationIcon = { IconButton(onClick = { (context as? LibraryReaderActivity)?.finish() }) { Text("‹", color = Color.White, fontSize = 32.sp) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.teal)) },
        bottomBar = { Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = state.currentPage > 0, onClick = { scope.launch { state.animateScrollToPage(state.currentPage - 1) } }, colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) { Text("السابق", fontFamily = LibraryFont, color = Color.White) }
            Text("${state.currentPage + 1} / ${pages.size}", color = palette.ink, fontFamily = LibraryFont)
            Button(enabled = state.currentPage + 1 < pages.size, onClick = { scope.launch { state.animateScrollToPage(state.currentPage + 1) } }, colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) { Text("التالي", fontFamily = LibraryFont, color = Color.White) }
        } }
    ) { padding ->
        HorizontalPager(state = state, modifier = Modifier.fillMaxSize().padding(padding)) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
                Text(page.title, color = palette.teal, fontFamily = LibraryFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl))
                Spacer(Modifier.height(18.dp))
                Text(page.text, color = palette.ink, fontFamily = LibraryFont, fontSize = 23.sp, lineHeight = 44.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl))
                if (page.footnote.isNotBlank()) { Spacer(Modifier.height(16.dp)); Text(page.footnote, color = palette.gold, fontFamily = LibraryFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
            }
        }
    }
}
