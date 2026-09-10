package com.muddakir.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
class LibrarySqliteReaderActivity : ComponentActivity() {

    private var activeReaderSession: BookRepository.ReaderSession? = null

    override fun onDestroy() {
        activeReaderSession?.close()
        activeReaderSession = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DB_FILE = "db_file"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, dbFile: String, title: String): Intent =
            Intent(context, LibrarySqliteReaderActivity::class.java)
                .putExtra(EXTRA_DB_FILE, dbFile)
                .putExtra(EXTRA_TITLE, title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val dbFile = intent.getStringExtra(EXTRA_DB_FILE).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (dbFile.isBlank() || title.isBlank()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ReaderContent(dbFile, title)
                }
            }
        }
    }

    @Composable
    private fun ReaderContent(dbFile: String, title: String) {
        val scope = rememberCoroutineScope()
        var session by remember { mutableStateOf<BookRepository.ReaderSession?>(null) }
        var chapters by remember { mutableStateOf<List<BookRepository.Chapter>>(emptyList()) }
        var selectedChapter by remember { mutableStateOf<BookRepository.Chapter?>(null) }
        var bookItems by remember { mutableStateOf<List<BookRepository.Item>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var loadingItems by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<BookRepository.SearchResult>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var selectedTab by remember { mutableStateOf(0) }
        var itemOffset by remember { mutableStateOf(0) }
        var hasMoreItems by remember { mutableStateOf(true) }
        var searchJob by remember { mutableStateOf<Job?>(null) }


        fun loadChapter(chapter: BookRepository.Chapter, reset: Boolean = true) {
            selectedChapter = chapter
            selectedTab = 1
            loadingItems = true
            error = null
            if (reset) {
                bookItems = emptyList()
                itemOffset = 0
                hasMoreItems = true
            }

            scope.launch {
                val active = session
                if (active == null) {
                    loadingItems = false
                    error = "تعذر فتح الكتاب."
                    return@launch
                }
                runCatchingCancellable {
                    withContext(Dispatchers.IO) {
                        active.getItems(
                            chapterId = chapter.id,
                            limit = 40,
                            offset = if (reset) 0 else itemOffset
                        )
                    }
                }.onSuccess { loaded ->
                    bookItems = if (reset) loaded else bookItems + loaded
                    itemOffset = bookItems.size
                    hasMoreItems = loaded.size == 40
                }.onFailure {
                    error = it.message ?: "تعذر قراءة نصوص الفصل"
                }
                loadingItems = false
            }
        }

        LaunchedEffect(dbFile) {
            loading = true
            error = null
            runCatchingCancellable {
                val opened = BookRepository.openSession(this@LibrarySqliteReaderActivity, dbFile)
                val loadedChapters = withContext(Dispatchers.IO) { opened.getChapters() }
                opened to loadedChapters
            }.onSuccess { (opened, loadedChapters) ->
                session?.close()
                session = opened
                activeReaderSession = opened
                chapters = loadedChapters
                if (loadedChapters.isNotEmpty()) {
                    selectedChapter = loadedChapters.first()
                    // Show the index immediately after the database is opened.
                    // The first chapter is loaded in the background so the user
                    // is never blocked by its content query.
                    selectedTab = 0
                    loadingItems = true
                    scope.launch {
                        runCatchingCancellable {
                            withContext(Dispatchers.IO) {
                                opened.getItems(loadedChapters.first().id, 40, 0)
                            }
                        }.onSuccess { loaded ->
                            bookItems = loaded
                            itemOffset = loaded.size
                            hasMoreItems = loaded.size == 40
                        }.onFailure {
                            error = it.message ?: "تعذر قراءة أول فصل"
                        }
                        loadingItems = false
                    }
                } else {
                    loadingItems = true
                    scope.launch {
                        runCatchingCancellable {
                            withContext(Dispatchers.IO) { opened.getItems(null, 40, 0) }
                        }.onSuccess { loaded ->
                            bookItems = loaded
                            itemOffset = loaded.size
                            hasMoreItems = loaded.size == 40
                            selectedTab = 1
                        }.onFailure {
                            error = it.message ?: "تعذر قراءة نصوص الكتاب"
                        }
                        loadingItems = false
                    }
                }
            }.onFailure {
                error = it.message ?: "تعذر فتح قاعدة الكتاب"
            }
            loading = false
        }

        LaunchedEffect(searchQuery, session) {
            searchJob?.cancel()
            if (searchQuery.isBlank() || session == null) {
                searchResults = emptyList()
                searching = false
                searchError = null
                return@LaunchedEffect
            }
            searchJob = launch {
                delay(450)
                searching = true
                searchError = null
                runCatchingCancellable {
                    withContext(Dispatchers.IO) {
                        session!!.search(searchQuery, limit = 50, offset = 0)
                    }
                }.onSuccess { searchResults = it }
                    .onFailure {
                        searchResults = emptyList()
                        searchError = it.message ?: "تعذر البحث داخل الكتاب"
                    }
                searching = false
            }
        }

        val primary = MaterialTheme.colorScheme.primary
        val surface = MaterialTheme.colorScheme.surface
        val muted = MaterialTheme.colorScheme.onSurfaceVariant

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                title,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textDirection = TextDirection.Rtl
                                )
                            )
                            if (chapters.isNotEmpty()) {
                                Text(
                                    "${chapters.size} فصل",
                                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Rtl),
                                    color = muted
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("بحث داخل الكتاب") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Start
                    )
                )

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("جاري تجهيز الكتاب…", color = muted)
                        }
                    }
                    return@Column
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                if (searchQuery.isNotBlank()) {
                    SearchResults(
                        results = searchResults,
                        searching = searching,
                        error = searchError,
                        title = title
                    )
                    return@Column
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الفهرس") },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("المحتوى") },
                        icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) }
                    )
                }

                if (selectedTab == 0) {
                    ChapterList(
                        chapters = chapters,
                        selectedChapter = selectedChapter,
                        onChapterClick = { loadChapter(it) }
                    )
                } else {
                    ContentList(
                        chapter = selectedChapter,
                        items = bookItems,
                        loading = loadingItems,
                        hasMore = hasMoreItems,
                        onLoadMore = {
                            selectedChapter?.let { loadChapter(it, reset = false) }
                        },
                        title = title
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<BookRepository.Chapter>,
    selectedChapter: BookRepository.Chapter?,
    onChapterClick: (BookRepository.Chapter) -> Unit
) {
    if (chapters.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد فصول في هذا الكتاب.", textAlign = TextAlign.Center)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chapters, key = { it.id }) { chapter ->
            val selected = selectedChapter?.id == chapter.id
            Card(
                onClick = { onChapterClick(chapter) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        chapter.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textDirection = TextDirection.Rtl
                        ),
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "›",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentList(
    chapter: BookRepository.Chapter?,
    items: List<BookRepository.Item>,
    loading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    title: String
) {
    Column(Modifier.fillMaxSize()) {
        chapter?.let {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    it.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textDirection = TextDirection.Rtl
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                it.sourceName?.takeIf(String::isNotBlank)?.let { source ->
                    Text(
                        source,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
            Divider()
        }

        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد نصوص في هذا الفصل.", textAlign = TextAlign.Center)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                ItemCard(item, index + 1, title)
            }

            if (hasMore) {
                item {
                    Button(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        else Text("عرض المزيد")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: BookRepository.Item, number: Int, bookTitle: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "النص $number",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Row {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("book_text", item.content))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ النص")
                    }
                    IconButton(onClick = {
                        val shareText = buildString {
                            append(item.content)
                            item.author?.takeIf(String::isNotBlank)?.let { append("\n\n").append(it) }
                            item.source?.takeIf(String::isNotBlank)?.let { append("\n").append(it) }
                            append("\n\n").append(bookTitle)
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, bookTitle))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "مشاركة النص")
                    }
                }
            }

            Text(
                item.content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = TextDirection.Rtl,
                    lineHeight = 30.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            item.author?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
            item.source?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<BookRepository.SearchResult>,
    searching: Boolean,
    error: String?,
    title: String
) {
    if (searching) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center
        )
        return
    }
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد نتائج مطابقة.", textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(results, key = { it.item.id }) { result ->
            ItemCard(result.item, result.item.id.toInt(), title)
        }
    }
}
