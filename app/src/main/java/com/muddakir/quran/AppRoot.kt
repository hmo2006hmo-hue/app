package com.muddakir.quran

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

private val UiFont = FontFamily(Font(R.font.amiri_regular), Font(R.font.amiri_bold, FontWeight.Bold))

// BasicTextField (used internally by OutlinedTextField) does not always mirror
// itself to RTL the way a plain Text() does: with no explicit textDirection,
// an empty field's cursor - and the first character typed - can render on the
// left even though LocalLayoutDirection is Rtl, because the paragraph
// direction is otherwise inferred from content rather than from the ambient
// layout direction. Every text-entry field in this file should apply this
// explicit RTL text style so typed Arabic always starts from the right.
private val RtlFieldTextStyle = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Start)

private data class NavItem(val screen: String, val title: String, val icon: String)
private val NAV = listOf(
    NavItem(NativeNavigation.SCREEN_HOME, "الرئيسية", "⌂"),
    NavItem(NativeNavigation.SCREEN_MUSHAF, "المصحف", "📖"),
    NavItem(NativeNavigation.SCREEN_ADHKAR, "الأذكار والعدادات", "📿"),
    NavItem(NativeNavigation.SCREEN_KHATMA, "خطة الختم", "📅"),
    NavItem(NativeNavigation.SCREEN_LIBRARY, "المكتبة", "📚"),
    NavItem(NativeNavigation.SCREEN_SEARCH, "البحث الشامل", "🔎"),
    NavItem(NativeNavigation.SCREEN_OCCASIONS, "المناسبات", "🌙"),
    NavItem(NativeNavigation.SCREEN_NOTES, "المفضلة والعلامات", "⭐"),
    NavItem(NativeNavigation.SCREEN_REMINDERS, "التذكيرات", "🔔"),
    NavItem(NativeNavigation.SCREEN_SETTINGS, "الإعدادات", "⚙️")
)

@Composable
fun NativeAppRoot(startScreen: String = NativeNavigation.SCREEN_HOME) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(startScreen) }
    var backStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var drawer by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(AppThemeStore.get(context)) }
    val palette = paletteFor(theme)

    LaunchedEffect(startScreen) {
        screen = startScreen
        backStack = emptyList()
    }

    fun navigate(target: String) {
        if (target == screen) return
        if (target == NativeNavigation.SCREEN_MUSHAF) {
            NativeNavigation.open(context, target)
        } else {
            backStack = (backStack + screen).takeLast(20)
            screen = target
        }
    }

    fun goBack() {
        val previous = backStack.lastOrNull()
        when {
            previous != null -> { backStack = backStack.dropLast(1); screen = previous }
            screen != NativeNavigation.SCREEN_HOME -> screen = NativeNavigation.SCREEN_HOME
            else -> (context as? MainActivity)?.finish()
        }
    }

    BackHandler(enabled = drawer) { drawer = false }
    BackHandler(enabled = !drawer) { goBack() }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalAppPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = if (theme == AppThemeName.NIGHT || theme == AppThemeName.BLACK) darkColorScheme(
                primary = palette.gold, secondary = palette.teal2, background = palette.paper2,
                surface = palette.paper, onSurface = palette.ink, onBackground = palette.ink
            ) else lightColorScheme(
                primary = palette.gold, secondary = palette.teal, background = palette.paper2,
                surface = palette.paper, onSurface = palette.ink, onBackground = palette.ink
            )
        ) {
            Box(Modifier.fillMaxSize().background(palette.paper2)) {
                NativeScaffold(
                    title = NAV.firstOrNull { it.screen == screen }?.title ?: when (screen) {
                        NativeNavigation.SCREEN_AUDIO -> "الصوتيات"
                        NativeNavigation.SCREEN_DOWNLOADS -> "التنزيلات"
                        NativeNavigation.SCREEN_TODAY -> "برنامج اليوم"
                        else -> "مُذَكِّر"
                    },
                    onMenu = { drawer = true },
                    onBack = { goBack() },
                    palette = palette
                ) {
                    when (screen) {
                        NativeNavigation.SCREEN_HOME -> HomeScreen { navigate(it) }
                        NativeNavigation.SCREEN_ADHKAR -> AdhkarScreen()
                        NativeNavigation.SCREEN_KHATMA -> KhatmaScreen { page -> context.startActivity(MushafActivity.intent(context, page = page, khatmaMode = true)) }
                        NativeNavigation.SCREEN_LIBRARY -> LibraryScreen()
                        NativeNavigation.SCREEN_SEARCH -> SearchScreen()
                        NativeNavigation.SCREEN_OCCASIONS -> OccasionsScreen()
                        NativeNavigation.SCREEN_NOTES -> NotesScreen()
                        NativeNavigation.SCREEN_REMINDERS -> RemindersScreen()
                        NativeNavigation.SCREEN_SETTINGS -> SettingsScreen(
                            theme = theme,
                            onThemeChange = { selected -> theme = selected; AppThemeStore.save(context, selected) }
                        )
                        NativeNavigation.SCREEN_AUDIO, NativeNavigation.SCREEN_DOWNLOADS -> HomeScreen { navigate(it) }
                        NativeNavigation.SCREEN_TODAY -> TodayScreen()
                        else -> HomeScreen { navigate(it) }
                    }
                }
                if (drawer) DrawerPanel(screen, onClose = { drawer = false }) { target -> drawer = false; navigate(target) }
            }
        }
    }
}

@Composable
private fun NativeScaffold(title: String, onMenu: () -> Unit, onBack: () -> Unit, palette: AppPalette, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(palette.paper2)) {
        Row(
            Modifier.fillMaxWidth().background(palette.teal).padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "القائمة", tint = Color.White) }
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White) }
        }
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun DrawerPanel(current: String, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val palette = LocalAppPalette.current
    Box(Modifier.fillMaxSize()) {
        Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable { onClose() })
        Surface(
            modifier = Modifier.fillMaxHeight().widthIn(max = 370.dp).fillMaxWidth(.86f),
            color = palette.teal,
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
        ) {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Text("مُذَكِّر", color = Color.White, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("رفيقك اليومي للقرآن والذكر", color = palette.gold, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                items(NAV) { item ->
                    Button(
                        onClick = { onSelect(item.screen) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (current == item.screen) palette.gold else palette.teal2),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("${item.icon}  ${item.title}", fontFamily = UiFont, fontWeight = FontWeight.Bold, color = if (current == item.screen) palette.ink else Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
                item { OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("إغلاق", fontFamily = UiFont) } }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val prefs = context.getSharedPreferences("mushaf_prefs", Context.MODE_PRIVATE)
    val lastPage = prefs.getInt("mushaf_page", 1)
    val khatmaPrefs = context.getSharedPreferences("khatma_native", Context.MODE_PRIVATE)
    val khatmaPage = khatmaPrefs.getInt("khatma_current_page", khatmaPrefs.getInt("start_page", 1))
    val dhikrPrefs = context.getSharedPreferences(ADHKAR_PREFS, Context.MODE_PRIVATE)
    val today = NativeContentRepository.todayKey()
    val dhikrTotal = loadDhikrTotal(dhikrPrefs, today)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.Start) {
                    Text("🌙 رفيقك اليومي", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("وردك اليوم: قرآن وذكر ودعاء بخطوات بسيطة", color = palette.ink, fontFamily = UiFont, fontSize = 18.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        StatPill("قراءة", "صفحة ${arabicDigits(lastPage)}", Modifier.weight(1f), palette)
                        StatPill("الذكر", arabicDigits(dhikrTotal), Modifier.weight(1f), palette)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { NativeNavigation.open(context, NativeNavigation.SCREEN_MUSHAF) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) {
                        Text("📖 متابعة القراءة — صفحة ${arabicDigits(lastPage)}", fontFamily = UiFont, color = Color.White)
                    }
                    Button(onClick = { onOpen(NativeNavigation.SCREEN_TODAY) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = palette.gold)) {
                        Text("✨ برنامج اليوم", fontFamily = UiFont, color = palette.ink)
                    }
                    if (khatmaPrefs.getBoolean("started", false)) {
                        Spacer(Modifier.height(4.dp))
                        Text("ورد الختم الحالي: الصفحة ${arabicDigits(khatmaPage)}", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        }
        items(NAV.filter { it.screen !in setOf(NativeNavigation.SCREEN_HOME, NativeNavigation.SCREEN_TODAY) }) { item ->
            Card(onClick = { onOpen(item.screen) }, colors = CardDefaults.cardColors(containerColor = palette.teal2), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.icon, fontSize = 27.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(item.title, color = Color.White, fontFamily = UiFont, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(Icons.Default.ChevronLeft, null, tint = palette.gold)
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier, palette: AppPalette) {
    Column(modifier.background(palette.paper2, RoundedCornerShape(14.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = UiFont, fontWeight = FontWeight.Bold, color = palette.ink, fontSize = 18.sp)
        Text(label, fontFamily = UiFont, color = palette.soft, fontSize = 13.sp)
    }
}

private fun localDayToken(): Long {
    val local = Calendar.getInstance(Locale.US)
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), Locale.US)
    utc.clear()
    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    return utc.timeInMillis / 86_400_000L
}

private fun arabicDigits(value: Int): String = value.toString().map { c -> if (c in '0'..'9') ('٠'.code + (c.code - '0'.code)).toChar() else c }.joinToString("")

private data class Dhikr(val id: String, val name: String, val text: String, val goal: Int, val type: String? = null)
private val BASE_DHIKR = listOf(
    Dhikr("zahra", "تسبيحة الزهراء عليها السلام", "الله أكبر", 100, "zahra"),
    Dhikr("salawat", "الصلاة على محمد وآل محمد", "اللهم صل على محمد وآل محمد", 100),
    Dhikr("istighfar", "الاستغفار", "أستغفر الله ربي وأتوب إليه", 100),
    Dhikr("faraj", "دعاء الفرج", "اللهم كن لوليك الحجة بن الحسن في هذه الساعة وفي كل ساعة، ولياً وحافظاً وقائداً وناصراً ودليلاً وعيناً حتى تسكنه أرضك طوعاً وتمتعه فيها طويلاً.", 1)
)
private const val ADHKAR_PREFS = "quranstudy_adhkar_v1"

@Composable
private fun AdhkarScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val prefs = remember { context.getSharedPreferences(ADHKAR_PREFS, Context.MODE_PRIVATE) }
    var customs by remember { mutableStateOf(loadCustomDhikr(prefs)) }
    val items = remember(customs) { BASE_DHIKR + customs }
    var currentId by remember { mutableStateOf("zahra") }
    LaunchedEffect(items) {
        if (items.none { it.id == currentId }) currentId = items.firstOrNull()?.id ?: "zahra"
    }
    val current = items.firstOrNull { it.id == currentId } ?: BASE_DHIKR.first()
    var count by remember(current.id, current.goal) {
        mutableIntStateOf(prefs.getInt("count_${current.id}", 0).coerceIn(0, current.goal))
    }
    var showAdd by remember { mutableStateOf(false) }
    var deleteCustom by remember { mutableStateOf<Dhikr?>(null) }
    val today = NativeContentRepository.todayKey()
    val vibration = prefs.getBoolean("vibration", true)

    LaunchedEffect(current.id, current.goal) {
        val stored = prefs.getInt("count_${current.id}", 0)
        val safe = stored.coerceIn(0, current.goal)
        count = safe
        if (stored != safe) prefs.edit().putInt("count_${current.id}", safe).apply()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { Text("📿 الأذكار والعدادات", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        item {
            LazyRowLike(items, currentId, palette) { d -> currentId = d.id }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.name, fontFamily = UiFont, fontWeight = FontWeight.Bold, color = palette.ink, fontSize = 23.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(if (current.type == "zahra") zahraText(count) else current.text, fontFamily = UiFont, color = palette.ink, fontSize = 27.sp, lineHeight = 44.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(5.dp))
                    Text("${arabicDigits(count)} / ${arabicDigits(current.goal)}", color = palette.soft, fontFamily = UiFont)
                    Slider(value = count.coerceAtMost(current.goal).toFloat(), onValueChange = {}, valueRange = 0f..current.goal.toFloat(), enabled = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            runCatching {
                                val before = count
                                val after = (count + 1).coerceAtMost(current.goal)
                                count = after
                                saveDhikrIncrement(prefs, current, 1, today)
                                if (vibration) vibrate(context, if (before < current.goal && after == current.goal) longArrayOf(80, 50, 120) else longArrayOf(25))
                            }.onFailure {
                                count = prefs.getInt("count_${current.id}", count).coerceIn(0, current.goal)
                                Toast.makeText(context, "تعذر حفظ العداد، حاول مرة أخرى.", Toast.LENGTH_SHORT).show()
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) { Text("+١", color = Color.White, fontFamily = UiFont) }
                        OutlinedButton(onClick = { runCatching { count = 0; prefs.edit().putInt("count_${current.id}", 0).apply() }.onFailure { Toast.makeText(context, "تعذر إعادة العداد.", Toast.LENGTH_SHORT).show() } }) { Text("إعادة", fontFamily = UiFont, color = palette.ink) }
                    }
                    if (current.id.startsWith("custom_")) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { deleteCustom = current }) { Text("حذف هذا العداد", color = Color(0xFF9B3A2F), fontFamily = UiFont) }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.teal2), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) {
                    Text("📊 إحصائية اليوم", color = palette.gold, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    val total = loadDhikrTotal(prefs, today)
                    Text("اليوم: ${arabicDigits(total)} ذكر • العداد الحالي: ${arabicDigits(count)}", color = Color.White, fontFamily = UiFont)
                    Text("الاهتزاز: ${if (vibration) "مفعل" else "متوقف"}", color = Color.White, fontFamily = UiFont)
                    Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = palette.gold)) { Text("➕ عداد مخصص", color = palette.ink, fontFamily = UiFont) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.teal2), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) {
                    Text("🏆 إنجازاتي", color = palette.gold, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("أتممت اليوم ${arabicDigits(completedTodayCount(prefs, today))} أذكار • إجمالي الإنجازات ${arabicDigits(completedTotalCount(prefs))}", color = Color.White, fontFamily = UiFont)
                    Spacer(Modifier.height(6.dp))
                    Text(recentCompleted(prefs).ifBlank { "لم يكتمل أي ذكر بعد." }, color = Color.White, fontFamily = UiFont, textAlign = TextAlign.Start)
                }
            }
        }
    }
    if (showAdd) AddDhikrDialog(onDismiss = { showAdd = false }) { name, text, goal ->
        val id = "custom_${System.currentTimeMillis()}"
        customs = customs + Dhikr(id, name, text.ifBlank { name }, goal, null)
        saveCustomDhikr(prefs, customs)
        currentId = id
        showAdd = false
        Toast.makeText(context, "تمت إضافة العداد", Toast.LENGTH_SHORT).show()
    }
    val pending = deleteCustom
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { deleteCustom = null },
            title = { Text("حذف العداد") },
            text = { Text("هل تريد حذف «${pending.name}» نهائيًا؟") },
            confirmButton = { TextButton(onClick = {
                customs = customs.filterNot { it.id == pending.id }
                saveCustomDhikr(prefs, customs)
                prefs.edit().remove("count_${pending.id}").apply()
                currentId = "zahra"
                deleteCustom = null
            }) { Text("حذف", color = Color(0xFF9B3A2F)) } },
            dismissButton = { TextButton(onClick = { deleteCustom = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun LazyRowLike(dhikrItems: List<Dhikr>, currentId: String, palette: AppPalette, onSelect: (Dhikr) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(dhikrItems, key = { it.id }) { d ->
            FilterChip(
                selected = d.id == currentId,
                onClick = { onSelect(d) },
                label = { Text(d.name, fontFamily = UiFont) }
            )
        }
    }
}

@Composable
private fun AddDhikrDialog(onDismiss: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عداد جديد", fontFamily = UiFont) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("اسم الذكر") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFont, textDirection = TextDirection.Rtl, textAlign = TextAlign.Start))
            OutlinedTextField(text, { text = it }, label = { Text("نص الذكر") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFont, textDirection = TextDirection.Rtl, textAlign = TextAlign.Start))
            OutlinedTextField(goal, { goal = it.filter(Char::isDigit) }, label = { Text("الهدف") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFont, textDirection = TextDirection.Rtl, textAlign = TextAlign.Start))
        } },
        confirmButton = { TextButton(onClick = { val g = goal.toIntOrNull()?.coerceAtLeast(1) ?: 100; if (name.isNotBlank()) onAdd(name.trim(), text.trim(), g) }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun loadCustomDhikr(prefs: SharedPreferences): List<Dhikr> = runCatching {
    val arr = org.json.JSONArray(prefs.getString("custom", "[]") ?: "[]")
    buildList {
        val used = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val name = o.optString("name").trim()
            if (!id.startsWith("custom_") || id.length > 120 || !used.add(id) || name.isBlank()) continue
            val text = o.optString("text").trim().ifBlank { name }
            val goal = o.optInt("goal", 100).coerceIn(1, 1_000_000)
            add(Dhikr(id, name, text, goal, null))
        }
    }
}.getOrElse { emptyList() }
private fun saveCustomDhikr(prefs: SharedPreferences, list: List<Dhikr>) {
    val arr = org.json.JSONArray()
    list.forEach { d -> arr.put(org.json.JSONObject().apply { put("id", d.id); put("name", d.name); put("text", d.text); put("goal", d.goal) }) }
    prefs.edit().putString("custom", arr.toString()).apply()
}
private fun saveDhikrIncrement(prefs: SharedPreferences, item: Dhikr, amount: Int, today: String) {
    val current = prefs.getInt("count_${item.id}", 0)
    prefs.edit().putInt("count_${item.id}", (current + amount).coerceAtMost(item.goal)).apply()
    val date = prefs.getString("today_date", "")
    val total = if (date == today) prefs.getInt("today_total", 0) else 0
    prefs.edit().putString("today_date", today).putInt("today_total", total + amount).apply()
    if (current < item.goal && current + amount >= item.goal) {
        val completed = prefs.getString("completed", "") ?: ""
        if (!completed.split("|").any { it == "${item.id}:$today" }) prefs.edit().putString("completed", (completed.split("|").filter { it.isNotBlank() } + "${item.id}:$today").joinToString("|")).apply()
    }
}
private fun loadDhikrTotal(prefs: SharedPreferences, today: String): Int = if (prefs.getString("today_date", "") == today) prefs.getInt("today_total", 0) else 0
private fun completedTodayCount(prefs: SharedPreferences, today: String): Int = (prefs.getString("completed", "") ?: "").split("|").count { it.endsWith(":$today") }
private fun completedTotalCount(prefs: SharedPreferences): Int = (prefs.getString("completed", "") ?: "").split("|").count { it.isNotBlank() }
private fun recentCompleted(prefs: SharedPreferences): String = (prefs.getString("completed", "") ?: "").split("|").filter { it.isNotBlank() }.takeLast(8).reversed().joinToString("\n") { "✓ ${it.substringBefore(":")} — ${it.substringAfter(":")}" }
private fun zahraText(n: Int): String = when { n < 34 -> "الله أكبر"; n < 67 -> "الحمد لله"; n < 100 -> "سبحان الله"; else -> "تمت تسبيحة الزهراء عليها السلام" }
@Suppress("DEPRECATION")
private fun vibrate(context: Context, pattern: LongArray) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    } catch (_: RuntimeException) {
        // Haptics are optional; a vendor-specific vibrator failure must never crash the counter screen.
    }
}

@Composable
private fun KhatmaScreen(onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val prefs = remember { context.getSharedPreferences("khatma_native", Context.MODE_PRIVATE) }
    var days by remember { mutableIntStateOf(prefs.getInt("days", 30).coerceIn(1, 365)) }
    var started by remember { mutableStateOf(prefs.getBoolean("started", false)) }
    val pagesPerDay = ceil(604f / days.coerceAtLeast(1)).toInt()
    val khatmaCurrent = prefs.getInt("khatma_current_page", 1).coerceIn(1, 604)
    val todayToken = localDayToken()
    val startToken = prefs.getLong("start_day_token", todayToken)
    val dayIndex = if (started) ((todayToken - startToken).coerceAtLeast(0)).toInt() + 1 else 1
    val from = ((dayIndex - 1) * pagesPerDay + 1).coerceIn(1, 604)
    val to = (from + pagesPerDay - 1).coerceAtMost(604)
    val wardPage = khatmaCurrent.takeIf { it in from..to } ?: from
    val khatmaPrefs = prefs
    var readCount by remember { mutableIntStateOf(KhatmaProgress.readCount(context)) }
    DisposableEffect(khatmaPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "read_ayahs_v1") readCount = KhatmaProgress.readCount(context)
        }
        khatmaPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { khatmaPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val progress = (readCount / 6236f).coerceIn(0f, 1f)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("📖 خطة ختم القرآن", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.Start) {
                    Text("اختر مدة الختمة", fontFamily = UiFont, color = palette.ink, fontSize = 19.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf(7, 30, 40, 60, 90).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d يوم") }) } }
                    Spacer(Modifier.height(10.dp))
                    Text("$pagesPerDay صفحة تقريبًا يوميًا", fontFamily = UiFont, color = palette.ink)
                    Text("الآيات المقروءة ضمن الختمة: ${arabicDigits(readCount)} / ${arabicDigits(6236)}", fontFamily = UiFont, color = palette.ink)
                    androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    if (started) {
                        Spacer(Modifier.height(6.dp))
                        Text("ورد اليوم: الصفحة ${arabicDigits(from)} — ${arabicDigits(to)}", fontFamily = UiFont, color = palette.ink)
                        Text("صفحة المتابعة الخاصة بالختم: ${arabicDigits(wardPage)}", fontFamily = UiFont, color = palette.soft)
                    }
                    Button(onClick = {
                        val firstStart = !started
                        if (firstStart) KhatmaProgress.reset(context)
                        started = true
                        prefs.edit().putInt("days", days).putBoolean("started", true).putInt("start_page", 1)
                            .putLong("start_day_token", if (firstStart) todayToken else startToken)
                            .putInt("khatma_current_page", if (firstStart) 1 else khatmaCurrent).apply()
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) {
                        Text(if (started) "تحديث الخطة" else "ابدأ الخطة من أول المصحف", color = Color.White, fontFamily = UiFont)
                    }
                    if (started) Button(onClick = { onOpenMushaf(wardPage) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = palette.gold)) { Text("فتح ورد اليوم", color = palette.ink, fontFamily = UiFont) }
                }
            }
        }
        item { Text("الختمة تبدأ دائمًا من الصفحة ١، وحالة المصحف العادية محفوظة بشكل مستقل عن ورد الختمة.", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
    }
}

@Composable
private fun SearchScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchRepository.Category.ALL) }
    var results by remember { mutableStateOf<List<SearchRepository.Result>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query, category) {
        delay(250)
        if (query.trim().length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        loading = true; error = null
        try { results = SearchRepository.search(context, query, category) }
        catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            results = emptyList(); error = t.message ?: "تعذر تنفيذ البحث"
        }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ابحث في القرآن والتفسير والكتب") }, singleLine = true, trailingIcon = { Icon(Icons.Default.Search, "بحث") }, textStyle = RtlFieldTextStyle)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(SearchRepository.Category.entries.toList()) { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.title, fontFamily = UiFont) }) } }
        Spacer(Modifier.height(8.dp))
        if (loading) Text("جاري البحث…", color = palette.soft, fontFamily = UiFont)
        error?.let { Text("خطأ في البحث: $it", color = Color(0xFFB00020), fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        if (!loading && error == null && query.trim().length >= 2 && results.isEmpty()) Text("لا توجد نتائج مطابقة.", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(results) { r ->
                Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), onClick = {
                    when (r.category) {
                        SearchRepository.Category.QURAN -> if (r.surah != null && r.ayah != null) scope.launch { runCatchingCancellable { QcfQuranRepository.pageForVerse(context, r.surah, r.ayah) }.onSuccess { page -> context.startActivity(MushafActivity.intent(context, page = page)) }.onFailure { Toast.makeText(context, "تعذر فتح الآية: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show() } }
                        SearchRepository.Category.TAFSIR -> if (r.surah != null && r.ayah != null) context.startActivity(MushafActivity.intent(context, tafsirSurah = r.surah, tafsirAyah = r.ayah))
                        SearchRepository.Category.BOOKS -> r.bookId?.let { id -> scope.launch { runCatchingCancellable { LibraryCatalogRepository.getBookByExternalId(context, id) }.onSuccess { book -> when { book == null -> Toast.makeText(context, "الكتاب غير موجود في الكتالوج.", Toast.LENGTH_LONG).show(); !book.isDownloaded -> context.startActivity(BookDetailActivity.intent(context, book.id)); book.format.equals("json", true) -> context.startActivity(Intent(context, LibraryReaderActivity::class.java).putExtra(LibraryReaderActivity.EXTRA_BOOK_ID, id)); else -> context.startActivity(LibrarySqliteReaderActivity.intent(context, book.dbFile, book.title)) } }.onFailure { Toast.makeText(context, "تعذر فتح الكتاب: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show() } } }
                        else -> Unit
                    }
                }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                        Text(r.title, color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold)
                        Text(r.subtitle, color = palette.ink, fontFamily = UiFont, textAlign = TextAlign.Start)
                        Text(r.category.title, color = palette.gold, fontFamily = UiFont, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OccasionsScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val date by produceState<CalendarRepository.TodayDate?>(initialValue = null) {
        value = runCatching { CalendarRepository.today(context) }.getOrNull()
    }
    val allOccasions by produceState<List<NativeContentRepository.Occasion>>(initialValue = emptyList()) {
        value = runCatching { NativeContentRepository.occasions(context) }.getOrDefault(emptyList())
    }
    var showCalendar by remember { mutableStateOf(false) }
    val month = date?.hijriMonth ?: 0
    val day = date?.hijriDay ?: 0
    val todayOccasions = allOccasions.filter { it.month == month && it.day == day }
    val monthOccasions = allOccasions.filter { it.month == month }.sortedBy { it.day }
    val upcoming = allOccasions.filter { it.month > month || (it.month == month && it.day > day) }
        .sortedWith(compareBy<NativeContentRepository.Occasion> { it.month }.thenBy { it.day }).take(6)

    if (showCalendar) {
        CalendarPage(
            date = Date(),
            occasions = allOccasions,
            palette = palette,
            onBack = { showCalendar = false }
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showCalendar = true }) { Text("📅 التقويم", fontFamily = UiFont) }
                Text("🌙 المناسبات والأيام", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
        }
        date?.let { current ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.Start) {
                        Text(current.weekday, color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(current.hijri, color = palette.ink, fontFamily = UiFont, fontSize = 22.sp)
                        Text(current.gregorian, color = palette.soft, fontFamily = UiFont, fontSize = 17.sp)
                    }
                }
            }
        }
        item { Text("مناسبة اليوم", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        if (todayOccasions.isEmpty()) item { OccasionMessage("لا توجد مناسبة مسجلة لهذا اليوم ضمن البيانات المحلية.", palette) }
        else items(todayOccasions, key = { "today-${it.month}-${it.day}-${it.title}" }) { OccasionCard(it, palette) }
        item { Text("مناسبات هذا الشهر الهجري", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        if (monthOccasions.isEmpty()) item { OccasionMessage("لا توجد مناسبات مدخلة لهذا الشهر في ملف المناسبات المحلي.", palette) }
        else items(monthOccasions, key = { "month-${it.month}-${it.day}-${it.title}" }) { OccasionCard(it, palette) }
        item { Text("المناسبات القادمة", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        if (upcoming.isEmpty()) item { OccasionMessage("لا توجد مناسبات قادمة أخرى ضمن البيانات المحلية الحالية.", palette) }
        else items(upcoming, key = { "up-${it.month}-${it.day}-${it.title}" }) { OccasionCard(it, palette) }
    }
}

@Composable
private fun CalendarPage(
    date: Date,
    occasions: List<NativeContentRepository.Occasion>,
    palette: AppPalette,
    onBack: () -> Unit
) {
    var showHijri by remember { mutableStateOf(false) }
    val weekdayNames = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    val currentYear = remember(date) { Calendar.getInstance(Locale.US).apply { time = date }.get(Calendar.YEAR) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = palette.ink) }
            Text("التقويم", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showHijri, onClick = { showHijri = false }, label = { Text("ميلادي", fontFamily = UiFont) }, modifier = Modifier.weight(1f))
            FilterChip(selected = showHijri, onClick = { showHijri = true }, label = { Text("هجري", fontFamily = UiFont) }, modifier = Modifier.weight(1f))
        }
        if (showHijri) {
            HijriCalendarSection(occasions, palette, weekdayNames)
        } else {
            GregorianYearSection(currentYear, occasions, palette, weekdayNames)
        }
    }
}

/** Shows all 12 Gregorian months of the given year, one grid per month, in a
 * single scrollable column - not just the current month. */
@Composable
private fun GregorianYearSection(
    year: Int,
    occasions: List<NativeContentRepository.Occasion>,
    palette: AppPalette,
    weekdayNames: List<String>
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(12, key = { it }) { monthIndex ->
            val calendar = remember(year, monthIndex) {
                Calendar.getInstance(Locale.US).apply {
                    set(year, monthIndex, 1, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            val monthName = SimpleDateFormat("MMMM yyyy", Locale("ar", "IQ")).format(calendar.time)
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstColumn = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val cells = List(firstColumn) { null } + (1..daysInMonth).map { it }
            val rows = (cells.size + 6) / 7
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(monthName, color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().padding(6.dp), textAlign = TextAlign.Start)
                    Row(Modifier.fillMaxWidth()) {
                        weekdayNames.forEach { name ->
                            Text(name, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 7.dp))
                        }
                    }
                    repeat(rows) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            repeat(7) { col ->
                                val day = cells.getOrNull(row * 7 + col)
                                CalendarCell(year, monthIndex, day, occasions, palette, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("اضغط على أي يوم لعرض المناسبات المسجلة فيه.", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
    }
}

/** Separate Hijri-month view: unlike Gregorian months, Hijri months are not
 * a fixed length (29 or 30 days), so each month's grid is generated from
 * the real tabulated length for that specific month/year rather than
 * assumed. Supports navigating to adjacent Hijri months. */
@Composable
private fun HijriCalendarSection(
    occasions: List<NativeContentRepository.Occasion>,
    palette: AppPalette,
    weekdayNames: List<String>
) {
    var monthOffset by remember { mutableStateOf(0) }
    val today = remember { CalendarRepository.forDate(Date()) }
    val base = remember(monthOffset) {
        // Walk forward/backward from today by whole Hijri months using the
        // platform's own Islamic calendar so each step lands on day 1 of
        // the target month with that month's real length.
        val ic = android.icu.util.IslamicCalendar(
            android.icu.util.TimeZone.getTimeZone("Asia/Baghdad"),
            Locale("ar", "IQ")
        ).apply {
            setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
            time = Date()
            set(android.icu.util.IslamicCalendar.DAY_OF_MONTH, 1)
            add(android.icu.util.IslamicCalendar.MONTH, monthOffset)
        }
        val daysInMonth = ic.getActualMaximum(android.icu.util.IslamicCalendar.DAY_OF_MONTH)
        val hijriMonth = ic.get(android.icu.util.IslamicCalendar.MONTH) + 1
        val hijriYear = ic.get(android.icu.util.IslamicCalendar.YEAR)
        val gregorianFirstDay = ic.time
        Triple(daysInMonth, hijriMonth, hijriYear) to gregorianFirstDay
    }
    val (info, gregorianFirstDay) = base
    val (daysInMonth, hijriMonth, hijriYear) = info
    val gregForFirst = Calendar.getInstance(Locale.US).apply { time = gregorianFirstDay }
    val firstColumn = gregForFirst.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val cells = List(firstColumn) { null } + (1..daysInMonth).map { it }
    val rows = (cells.size + 6) / 7
    val monthTitle = "${arabicHijriMonth(hijriMonth)} $hijriYear هـ"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "الشهر السابق", tint = palette.ink) }
            Text(monthTitle, color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { monthOffset++ }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "الشهر القادم", tint = palette.ink) }
        }
        if (monthOffset != 0) {
            TextButton(onClick = { monthOffset = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("اليوم", fontFamily = UiFont)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    weekdayNames.forEach { name ->
                        Text(name, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 7.dp))
                    }
                }
                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        repeat(7) { col ->
                            val hDay = cells.getOrNull(row * 7 + col)
                            val isToday = monthOffset == 0 && hDay == today.hijriDay && hijriMonth == today.hijriMonth
                            val events = if (hDay != null) occasions.filter { it.month == hijriMonth && it.day == hDay } else emptyList()
                            val bg = when {
                                hDay == null -> Color.Transparent
                                isToday -> palette.teal.copy(alpha = .25f)
                                events.isNotEmpty() -> palette.gold.copy(alpha = .20f)
                                else -> palette.paper2
                            }
                            var open by remember(row, col, monthOffset) { mutableStateOf(false) }
                            Box(Modifier.weight(1f).height(72.dp).background(bg).clickable(enabled = hDay != null) { open = true }.padding(4.dp)) {
                                if (hDay != null) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
                                        Text(arabicDigits(hDay), color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        events.take(2).forEach { event ->
                                            Text(event.title, color = palette.teal, fontFamily = UiFont, fontSize = 9.sp, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                        }
                                    }
                                }
                            }
                            if (open && hDay != null) {
                                AlertDialog(
                                    onDismissRequest = { open = false },
                                    title = { Text("${arabicDigits(hDay)} ${arabicHijriMonth(hijriMonth)}", fontFamily = UiFont, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (events.isEmpty()) Text("لا توجد مناسبة مسجلة لهذا اليوم.", fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                            else events.forEach { event ->
                                                Text("${event.icon} ${event.title}", fontFamily = UiFont, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                                if (event.subtitle.isNotBlank()) Text(event.subtitle, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق", fontFamily = UiFont) } }
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "أطوال الأشهر الهجرية هنا محسوبة تقويمياً (٢٩ أو ٣٠ يوماً)؛ يوم اليوم تحديداً يُصحَّح تلقائياً حسب إعلان مكتب المرجعية.",
            color = palette.soft, fontFamily = UiFont, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun CalendarCell(
    year: Int,
    month: Int,
    day: Int?,
    occasions: List<NativeContentRepository.Occasion>,
    palette: AppPalette,
    modifier: Modifier
) {
    var open by remember { mutableStateOf(false) }
    val cellDate = remember(year, month, day) {
        day?.let { Calendar.getInstance(Locale.US).apply { set(year, month, it, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.time }
    }
    val hijri = cellDate?.let { CalendarRepository.forDate(it) }
    val events = if (hijri != null) occasions.filter { it.month == hijri.hijriMonth && it.day == hijri.hijriDay } else emptyList()
    val bg = when {
        day == null -> Color.Transparent
        events.isNotEmpty() -> palette.gold.copy(alpha = .20f)
        else -> palette.paper2
    }
    Box(modifier.height(72.dp).background(bg).clickable(enabled = day != null) { open = true }.padding(4.dp)) {
        if (day != null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
                Text(arabicDigits(day), color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                events.take(2).forEach { event ->
                    Text(event.title, color = palette.teal, fontFamily = UiFont, fontSize = 9.sp, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
            }
        }
    }
    if (open && day != null) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("${arabicDigits(day)} ${monthNameForDialog(month)}", fontFamily = UiFont, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hijri?.let { Text(it.hijri, color = palette.teal, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                    if (events.isEmpty()) Text("لا توجد مناسبة مسجلة لهذا اليوم.", fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    else events.forEach { event ->
                        Text("${event.icon} ${event.title}", fontFamily = UiFont, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        if (event.subtitle.isNotBlank()) Text(event.subtitle, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق", fontFamily = UiFont) } }
        )
    }
}

private fun monthNameForDialog(month: Int): String = listOf(
    "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
    "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"
).getOrElse(month) { "" }

@Composable
private fun OccasionCard(occasion: NativeContentRepository.Occasion, palette: AppPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) { Text("${occasion.icon}  ${occasion.title}", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("${arabicDigits(occasion.day)} ${arabicHijriMonth(occasion.month)}", color = palette.gold, fontFamily = UiFont); if (occasion.subtitle.isNotBlank()) Text(occasion.subtitle, color = palette.soft, fontFamily = UiFont, textAlign = TextAlign.Start) } }
}

@Composable
private fun OccasionMessage(text: String, palette: AppPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) { Text(text, color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Start) }
}

private fun arabicHijriMonth(month: Int): String = when (month) {
    1 -> "محرم"; 2 -> "صفر"; 3 -> "ربيع الأول"; 4 -> "ربيع الثاني"; 5 -> "جمادى الأولى"; 6 -> "جمادى الآخرة"; 7 -> "رجب"; 8 -> "شعبان"; 9 -> "رمضان"; 10 -> "شوال"; 11 -> "ذو القعدة"; 12 -> "ذو الحجة"; else -> ""
}

@Composable
private fun NotesScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    var notes by remember { mutableStateOf(NativeNotesStore.load(context)) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عام") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) {
                    Text("⭐ المفضلة والعلامات", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("📝 إضافة ملاحظة جديدة", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold)
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الملاحظة") }, textStyle = RtlFieldTextStyle)
                    OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(150.dp), label = { Text("الملاحظة أو التأمل") }, textStyle = RtlFieldTextStyle)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("عام", "القرآن", "دعاء", "ذكر", "تأمل").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
                    Button(onClick = {
                        if (body.isNotBlank()) {
                            val n = NativeNotesStore.Note(System.currentTimeMillis(), title.ifBlank { "ملاحظة بدون عنوان" }, body.trim(), category, SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar", "IQ")).format(Date()))
                            NativeNotesStore.save(context, n); notes = NativeNotesStore.load(context); title = ""; body = ""
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) { Text("💾 حفظ", color = Color.White, fontFamily = UiFont) }
                }
            }
        }
        item { Text("📌 ملاحظاتك المحفوظة", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        items(notes, key = { it.id }) { note ->
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.Start) {
                    Text(note.title, color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(note.createdAt, color = palette.soft, fontFamily = UiFont, fontSize = 12.sp)
                    Text(note.category, color = palette.gold, fontFamily = UiFont)
                    Text(note.body, color = palette.ink, fontFamily = UiFont, lineHeight = 28.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    TextButton(onClick = { NativeNotesStore.delete(context, note.id); notes = NativeNotesStore.load(context) }) { Text("حذف") }
                }
            }
        }
    }
}

@Composable
private fun TodayScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val date = Date()
    val key = NativeContentRepository.todayKey(date)
    val prefs = context.getSharedPreferences("quranstudy_today_smart_$key", Context.MODE_PRIVATE)
    val base = remember { listOf("📖 ورد القرآن" to "اقرأ ما تستطيع ولو بضع آيات، فالمداومة أهم.", "📿 تسبيحة الزهراء" to "34 الله أكبر • 33 الحمد لله • 33 سبحان الله", "🤍 الصلاة على محمد وآل محمد" to "افتح الأذكار واختر وردك.", "🌿 محاسبة النفس" to "دقيقة هادئة للتفكر في النية والعمل.") }
    var done by remember { mutableStateOf((0 until base.size).map { prefs.getBoolean("done_$it", false) }) }
    val complete = done.count { it }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("🌙 برنامج اليوم", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        item { Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(17.dp), horizontalAlignment = Alignment.Start) { Text("برنامجك اليومي", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 21.sp); Text(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar", "IQ")).format(date), color = palette.ink, fontFamily = UiFont); Text("الإنجاز: ${arabicDigits(complete)} / ${arabicDigits(base.size)}", color = palette.teal, fontFamily = UiFont) } } }
        itemsIndexed(base) { index, pair ->
            val (title, sub) = pair
            Card(colors = CardDefaults.cardColors(containerColor = if (done[index]) palette.paper else palette.teal2), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = done[index], onCheckedChange = { v -> done = done.toMutableList().also { it[index] = v }; prefs.edit().putBoolean("done_$index", v).apply() })
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, color = if (done[index]) palette.ink else Color.White, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(sub, color = if (done[index]) palette.ink else Color.White, fontFamily = UiFont, textAlign = TextAlign.Start) }
                }
            }
        }
    }
}

@Composable
private fun RemindersScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val prefs = remember { context.getSharedPreferences(GeneralReminderWorker.PREFS, Context.MODE_PRIVATE) }
    val types = listOf(GeneralReminderWorker.QURAN to "ورد القرآن", GeneralReminderWorker.DUA to "وقت الدعاء", GeneralReminderWorker.PROGRAM to "برنامج اليوم", GeneralReminderWorker.OCCASION to "مناسبة اليوم")
    var hasCustomSound by remember { mutableStateOf(ReminderSoundStore.hasCustomSound(context)) }
    var soundMessage by remember { mutableStateOf<String?>(null) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val saved = ReminderSoundStore.save(context, uri)
            hasCustomSound = ReminderSoundStore.hasCustomSound(context)
            soundMessage = if (saved) "تم حفظ صوت التنبيه المخصص." else "تعذر حفظ الصوت المختار."
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("🔔 التذكيرات", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start); Text("تذكيرات Android فعلية، والضغط على الإشعار يفتح الصفحة المرتبطة به.", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                    Text("🔊 صوت التنبيه", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold)
                    Text(
                        if (hasCustomSound) "يستخدم التطبيق حالياً صوتاً مخصصاً اخترته من جهازك." else "يستخدم التطبيق حالياً صوت الإشعارات الافتراضي للجهاز.",
                        color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start
                    )
                    soundMessage?.let { Text(it, color = palette.teal, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }, colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) {
                            Text("اختيار صوت من الجهاز", color = Color.White, fontFamily = UiFont)
                        }
                        if (hasCustomSound) OutlinedButton(onClick = {
                            ReminderSoundStore.clear(context)
                            hasCustomSound = false
                            soundMessage = "تمت العودة للصوت الافتراضي."
                        }) { Text("استخدام الافتراضي") }
                    }
                }
            }
        }
        items(types) { (type, label) -> ReminderRow(type, label, prefs, palette) }
        item {
            if (Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { (context as? MainActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 700) }, colors = ButtonDefaults.buttonColors(containerColor = palette.gold), modifier = Modifier.fillMaxWidth()) { Text("السماح بإشعارات التطبيق", color = palette.ink, fontFamily = UiFont) }
            }
        }
    }
}

@Composable
private fun ReminderRow(type: String, label: String, prefs: SharedPreferences, palette: AppPalette) {
    val context = LocalContext.current
    var enabled by remember(type) { mutableStateOf(GeneralReminderWorker.isEnabled(context, type)) }
    var time by remember(type) { mutableStateOf(GeneralReminderWorker.time(context, type)) }
    Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = {
                val parsed = parseReminderTime(time)
                enabled = it
                GeneralReminderWorker.set(context, type, it, parsed.first, parsed.second)
            })
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val parsed = parseReminderTime(time)
                TimePickerDialog(
                    context,
                    { _, h, m ->
                        time = String.format(Locale.US, "%02d:%02d", h, m)
                        GeneralReminderWorker.set(context, type, enabled, h, m)
                    },
                    parsed.first,
                    parsed.second,
                    DateFormat.is24HourFormat(context)
                ).show()
            }) { Text(time) }
            Text(label, color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
}

private fun parseReminderTime(raw: String): Pair<Int, Int> {
    val parts = raw.split(":", limit = 2)
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}

@Composable
private fun SettingsScreen(theme: AppThemeName, onThemeChange: (AppThemeName) -> Unit) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val prefs = remember { context.getSharedPreferences("native_settings", Context.MODE_PRIVATE) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var exactAlarm by remember { mutableStateOf(prefs.getBoolean("exact_alarm_hint", true)) }
    var aboutOpen by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) { Text("🎨 المظهر والقراءة", color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("اختر المظهر الذي يناسبك", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start); androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { items(AppThemeName.entries.toList()) { t -> FilterChip(selected = theme == t, onClick = { onThemeChange(t) }, label = { Text("${t.icon} ${t.title}", fontFamily = UiFont) }) } } } } }
        item { Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Switch(checked = notifications, onCheckedChange = { enabled -> notifications = enabled; prefs.edit().putBoolean("notifications", enabled).apply(); ReminderBootstrap.ensureScheduled(context); if (enabled && Build.VERSION.SDK_INT >= 33) (context as? MainActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701) }); Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text("السماح بإشعارات التطبيق", color = palette.ink, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start); Text("يشمل تذكيرات القرآن والدعاء والمناسبات والأذكار.", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } } } }
        item { Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Switch(checked = exactAlarm, onCheckedChange = { enabled -> exactAlarm = enabled; prefs.edit().putBoolean("exact_alarm_hint", enabled).apply(); ReminderBootstrap.ensureScheduled(context); if (enabled && Build.VERSION.SDK_INT >= 31) runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) } }); Text("تفعيل دعم المنبهات الدقيقة", color = palette.ink, fontFamily = UiFont, modifier = Modifier.weight(1f), textAlign = TextAlign.Start) }; if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) } }, modifier = Modifier.fillMaxWidth()) { Text("فتح إعداد المنبهات الدقيقة", fontFamily = UiFont) } } } }
        item { SettingActionCard("ℹ️ عن التطبيق", "معلومات النسخة ومكونات مِشكاة", { aboutOpen = true }, palette) }
        item { SettingActionCard("⚙️ إعدادات النظام للتطبيق", "الأذونات والإشعارات والبيانات", { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:${context.packageName}") }) } }, palette) }
    }
    if (aboutOpen) AlertDialog(
        onDismissRequest = { aboutOpen = false },
        title = { Text("عن التطبيق", fontFamily = UiFont) },
        text = {
            Text(
                "مِشكاة تطبيق إسلامي شامل يجمع لك في مكان واحد: قراءة القرآن الكريم بخط عثماني واضح مع الاستماع للتلاوة، تفسير الآيات من عدة مصادر، مكتبة تضم مئات الكتب الشيعية للتصفح والقراءة، أذكار وعدادات يومية، خطة لختم القرآن، مناسبات وتقويم هجري دقيق، وتذكيرات يومية قابلة للتخصيص.\n\nهدف مِشكاة أن يكون رفيقك اليومي للقرآن والمعرفة الدينية، ببساطة وبدون تشتيت.",
                fontFamily = UiFont,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        },
        confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("إغلاق", fontFamily = UiFont) } }
    )
}

@Composable
private fun SettingActionCard(title: String, subtitle: String, onClick: () -> Unit, palette: AppPalette) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) { Text(title, color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text(subtitle, color = palette.soft, fontFamily = UiFont, textAlign = TextAlign.Start); Text("اضغط للفتح", color = palette.gold, fontFamily = UiFont, fontSize = 12.sp) } }
}

@Composable
private fun AudioScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    var reciter by remember { mutableStateOf(AudioRepository.reciter(context)) }
    var quality by remember { mutableStateOf(AudioRepository.quality(context)) }
    var auto by remember { mutableStateOf(AudioRepository.autoDownload(context)) }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val latestPlayer = rememberUpdatedState(player)
    var selectedGlobal by remember { mutableIntStateOf(1) }
    DisposableEffect(Unit) { onDispose { latestPlayer.value?.release() } }
    Column(Modifier.fillMaxSize()) {
        Text("🎧 الصوتيات", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.Start) {
                        Text("القارئ", color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            items(AudioRepository.reciters) { r -> FilterChip(selected = reciter == r.id, onClick = { reciter = r.id; AudioRepository.saveSettings(context, reciter, quality, auto) }, label = { Text(r.name, fontFamily = UiFont) }) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("الجودة", color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AudioRepository.qualities.forEach { (q, name) -> FilterChip(selected = quality == q, onClick = { quality = q; AudioRepository.saveSettings(context, reciter, quality, auto) }, label = { Text("$name $q", fontFamily = UiFont) }) } }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = auto, onCheckedChange = { auto = it; AudioRepository.saveSettings(context, reciter, quality, auto) })
                            Text("التحميل التلقائي لصوت الآية والسورة", color = palette.ink, fontFamily = UiFont, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = palette.teal2), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.Start) {
                        Text("تجربة التشغيل", color = palette.gold, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("جرّب سورة الفاتحة / الآية 1 للتأكد من الصوت.", color = Color.White, fontFamily = UiFont)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (playing) { player?.stop(); player?.release(); player = null; playing = false }
                                else {
                                    playing = true
                                    val urls = AudioRepository.urls(1, reciter, quality)
                                    val local = AudioRepository.findLocal(context, reciter, quality, 1)
                                    fun startAt(index: Int) {
                                        if (!playing) return
                                        runCatching {
                                            player?.release()
                                            val mp = android.media.MediaPlayer()
                                            mp.setOnPreparedListener { it.start(); playing = true }
                                            mp.setOnCompletionListener { playing = false; it.release(); player = null }
                                            mp.setOnErrorListener { _, _, _ -> if (index + 1 < urls.size) { startAt(index + 1); true } else { playing = false; false } }
                                            if (local != null) mp.setDataSource(local.absolutePath) else mp.setDataSource(urls[index])
                                            mp.prepareAsync(); player = mp
                                        }.onFailure { playing = false }
                                    }
                                    startAt(0)
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = palette.gold)) { Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = palette.ink); Text(if (playing) "إيقاف" else "تشغيل", color = palette.ink, fontFamily = UiFont) }
                            Button(onClick = { scope.launch { AudioRepository.downloadAyah(context, reciter, quality, selectedGlobal) } }, colors = ButtonDefaults.buttonColors(containerColor = palette.gold)) { Icon(Icons.Default.Download, null, tint = palette.ink); Text("تنزيل الآية الأولى", color = palette.ink, fontFamily = UiFont) }
                        }
                    }
                }
            }
        }
    }
}

private val SURAH_NAMES_ALL = listOf(
    "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس", "التكوير", "الإنفطار", "المطففين", "الإنشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
)


@Composable
private fun DownloadsScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    var reciter by remember { mutableStateOf(AudioRepository.reciter(context)) }
    var quality by remember { mutableStateOf(AudioRepository.quality(context)) }
    var selectedSurah by remember { mutableIntStateOf(1) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(-1L) }
    var status by remember { mutableStateOf("لم تبدأ عملية تنزيل.") }
    var busy by remember { mutableStateOf(false) }
    val names = SURAH_NAMES_ALL
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) {
                    Text("📥 التنزيلات الصوتية", color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("هذه الصفحة للصوتيات فقط. الكتب لا تظهر هنا.", color = palette.soft, fontFamily = UiFont)
                    Spacer(Modifier.height(7.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(AudioRepository.reciters) { r -> FilterChip(selected = reciter == r.id, onClick = { reciter = r.id; AudioRepository.saveSettings(context, reciter, quality, AudioRepository.autoDownload(context)) }, label = { Text(r.name, fontFamily = UiFont) }) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AudioRepository.qualities.forEach { (q, name) -> FilterChip(selected = quality == q, onClick = { quality = q; AudioRepository.saveSettings(context, reciter, quality, AudioRepository.autoDownload(context)) }, label = { Text("$name $q") }) } }
                    Spacer(Modifier.height(7.dp))
                    var openSurahPicker by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { openSurahPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("السورة: ${selectedSurah}. ${names[selectedSurah - 1]}", fontFamily = UiFont) }
                    Button(enabled = !busy, onClick = {
                        busy = true; downloadedBytes = 0L; totalBytes = -1L; status = "جاري تنزيل السورة…"
                        scope.launch {
                            val result = AudioRepository.downloadSurah(
                                context, reciter, quality, selectedSurah,
                                onProgress = { _, _ -> },
                                onBytesProgress = { doneBytes, sizeBytes -> downloadedBytes = doneBytes; totalBytes = sizeBytes }
                            )
                            busy = false
                            status = result.fold({ "اكتمل تنزيل السورة ✅" }, { "فشل التنزيل: ${it.message ?: "خطأ غير معروف"}" })
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = palette.teal)) { Text("تحميل السورة", color = Color.White, fontFamily = UiFont) }
                    Button(enabled = !busy, onClick = {
                        busy = true; downloadedBytes = 0L; totalBytes = -1L; status = "جاري تنزيل المصحف كاملًا…"
                        scope.launch {
                            val result = AudioRepository.downloadQuran(
                                context, reciter, quality,
                                onProgress = { _, _ -> },
                                onBytesProgress = { doneBytes, sizeBytes -> downloadedBytes = doneBytes; totalBytes = sizeBytes }
                            )
                            busy = false
                            status = result.fold({ "اكتمل تنزيل القارئ المختار ✅" }, { "فشل التنزيل: ${it.message ?: "خطأ غير معروف"}" })
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = palette.gold)) { Text("تحميل المصحف للقارئ المختار", color = palette.ink, fontFamily = UiFont) }
                    OutlinedButton(enabled = !busy, onClick = { AudioRepository.deleteOtherReciters(context, reciter); status = "تم الاحتفاظ بأصوات القارئ المختار فقط." }, modifier = Modifier.fillMaxWidth()) { Text("حذف أصوات القراء الآخرين", fontFamily = UiFont) }
                    OutlinedButton(enabled = !busy, onClick = { AudioRepository.clear(context); downloadedBytes = 0L; totalBytes = -1L; status = "تم مسح الملفات الصوتية." }, modifier = Modifier.fillMaxWidth()) { Text("مسح كل الصوتيات", fontFamily = UiFont) }
                    Spacer(Modifier.height(7.dp))
                    LinearProgressWithText(busy, downloadedBytes, totalBytes, status, palette)
                    Text("المساحة الصوتية: ${formatBytes(AudioRepository.sizeBytes(context))}", color = palette.soft, fontFamily = UiFont)
                    if (openSurahPicker) SurahPickerDialog(selectedSurah, names) { selectedSurah = it; openSurahPicker = false }
                }
            }
        }
    }
}

@Composable
private fun LinearProgressWithText(busy: Boolean, downloadedBytes: Long, totalBytes: Long, text: String, palette: AppPalette) {
    if (busy) {
        if (totalBytes > 0L) {
            val value = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            androidx.compose.material3.LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
            Text("الملف الحالي: ${(value * 100f).toInt()}% • ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}", color = palette.ink, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        } else {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("جاري التنزيل… حجم الملف غير متوفر حاليًا", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
    }
    Text(text, color = palette.ink, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
}

@Composable
private fun SurahPickerDialog(selected: Int, names: List<String>, onSelect: (Int) -> Unit) {
    AlertDialog(onDismissRequest = { onSelect(selected) }, title = { Text("اختر السورة") }, text = {
        LazyColumn(Modifier.height(420.dp)) { itemsIndexed(names) { index, name -> TextButton(onClick = { onSelect(index + 1) }, modifier = Modifier.fillMaxWidth()) { Text("${index + 1}. $name", fontFamily = UiFont, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) } } }
    }, confirmButton = {})
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private val BOOK_NUMBER_PATTERN = Regex("book_(\\d+)\\.db", RegexOption.IGNORE_CASE)
internal fun bookNumber(dbFile: String): Int? =
    BOOK_NUMBER_PATTERN.matchEntire(dbFile)?.groupValues?.get(1)?.toIntOrNull()

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<List<LibraryCatalogRepository.Book>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val tafsirs = remember { TafsirRepository.catalog() }
    // Per-book download state, keyed by catalog id. Each download runs in
    // its own coroutine (LibraryBookManager.install already serializes only
    // same-book installs via a per-book lock), so several books can
    // download at once - this map is what lets the UI show each one's own
    // progress bar/status instead of a single shared message clobbering
    // itself across concurrent downloads.
    val downloadProgress = remember { mutableStateMapOf<Long, Float>() }
    val downloadStatus = remember { mutableStateMapOf<Long, String>() }

    suspend fun refreshCatalog() {
        loading = true
        try {
            catalog = LibraryCatalogRepository.getBooks(context)
            message = ""
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            message = "تعذر تحديث المكتبة: ${t.message ?: "خطأ غير معروف"}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshCatalog() }
    val filtered = remember(catalog, query) {
        val n = SearchRepository.normalize(query)
        catalog.filter { book ->
            n.isBlank() ||
                SearchRepository.normalize(book.title).contains(n) ||
                SearchRepository.normalize(book.author.orEmpty()).contains(n) ||
                SearchRepository.normalize(book.description.orEmpty()).contains(n)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = !loading && tab == 0, onClick = { scope.launch { refreshCatalog() } }) { Text("تحديث") }
            Text("📚 المكتبة", color = palette.ink, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text("الكتب", fontFamily = UiFont) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1; message = "" },
                label = { Text("التفاسير", fontFamily = UiFont) },
                modifier = Modifier.weight(1f)
            )
        }

        if (tab == 0) {
            Text("${catalog.size} كتاب في الكتالوج • ${catalog.count { book: LibraryCatalogRepository.Book -> book.isDownloaded }} مثبت على الجهاز", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("بحث في الكتب") }, textStyle = RtlFieldTextStyle)
            if (message.isNotBlank()) Text(message, color = Color(0xFFB00020), fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            if (loading && catalog.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(filtered, key = { book: LibraryCatalogRepository.Book -> book.id }) { book ->
                    val local = book.isDownloaded
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.paper),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { m ->
                                if (!local) m.clickable { context.startActivity(BookDetailActivity.intent(context, book.id)) } else m
                            }
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.Start) {
                            Text("📖  ${book.title}", color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            book.author?.takeIf { it.isNotBlank() }?.let { Text(it, color = palette.ink, fontFamily = UiFont) }
                            book.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = palette.soft, fontFamily = UiFont, textAlign = TextAlign.Start) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                bookNumber(book.dbFile)?.let { n -> Text("كتاب رقم $n", color = palette.soft, fontFamily = UiFont, fontSize = 12.sp) }
                                book.sizeBytes?.let { Text(formatBytes(it), color = palette.soft, fontFamily = UiFont, fontSize = 12.sp) }
                            }
                            Text(if (book.format.equals("sqlite", true)) "قاعدة SQLite" else "ملف JSON قابل للتنزيل", color = palette.soft, fontFamily = UiFont)
                            val progress = downloadProgress[book.id]
                            val status = downloadStatus[book.id]
                            val isDownloading = progress != null
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    color = palette.teal
                                )
                                Text("${(progress * 100).toInt()}٪", color = palette.soft, fontFamily = UiFont, fontSize = 12.sp)
                            }
                            status?.let { Text(it, color = if (it.startsWith("تم")) palette.teal else Color(0xFFB00020), fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !isDownloading,
                                    onClick = {
                                    if (local) {
                                        when (book.format.lowercase()) {
                                            "sqlite" -> context.startActivity(LibrarySqliteReaderActivity.intent(context, book.dbFile, book.title))
                                            "json" -> context.startActivity(Intent(context, LibraryReaderActivity::class.java).putExtra(LibraryReaderActivity.EXTRA_BOOK_ID, book.externalId))
                                        }
                                    } else scope.launch {
                                        downloadProgress[book.id] = 0f
                                        downloadStatus.remove(book.id)
                                        try {
                                            LibraryBookManager.install(context, book) { fraction ->
                                                downloadProgress[book.id] = fraction
                                            }
                                            catalog = catalog.map { if (it.id == book.id) it.copy(isDownloaded = true) else it }
                                            downloadStatus[book.id] = "تم تنزيل ${book.title} بنجاح."
                                            if (book.format.equals("sqlite", true)) context.startActivity(LibrarySqliteReaderActivity.intent(context, book.dbFile, book.title))
                                            else context.startActivity(Intent(context, LibraryReaderActivity::class.java).putExtra(LibraryReaderActivity.EXTRA_BOOK_ID, book.externalId))
                                        } catch (t: Throwable) {
                                            if (t is kotlinx.coroutines.CancellationException) throw t
                                            downloadStatus[book.id] = "فشل تنزيل ${book.title}: ${t.message ?: "خطأ غير معروف"}"
                                        } finally {
                                            downloadProgress.remove(book.id)
                                        }
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = if (local) palette.gold else palette.teal)) {
                                    Text(
                                        if (local) "فتح الكتاب" else if (isDownloading) "جارٍ التنزيل…" else "تنزيل الكتاب",
                                        color = if (local) palette.ink else Color.White, fontFamily = UiFont
                                    )
                                }
                                if (local) OutlinedButton(onClick = {
                                    scope.launch {
                                        runCatchingCancellable { LibraryBookManager.delete(context, book) }
                                            .onSuccess {
                                                catalog = catalog.map { if (it.id == book.id) it.copy(isDownloaded = false) else it }
                                                downloadStatus[book.id] = "تم حذف ${book.title} من الجهاز."
                                            }
                                            .onFailure { downloadStatus[book.id] = "تعذر حذف الكتاب: ${it.message ?: "خطأ غير معروف"}" }
                                    }
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        } else {
            Text("5 تفاسير • تنزيل عند الطلب • تبقى ملفاتها خارج APK", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Text("اختر تفسيرًا لتنزيله. بعد التنزيل يمكن استخدامه داخل المصحف عند اختيار «عرض التفسير». ", color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            if (message.isNotBlank()) Text(message, color = if (message.startsWith("تم")) palette.teal else Color(0xFFB00020), fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(tafsirs, key = { tafsir: TafsirRepository.CatalogItem -> tafsir.id }) { tafsir ->
                    val installed = TafsirRepository.isInstalled(context, tafsir)
                    Card(colors = CardDefaults.cardColors(containerColor = palette.paper), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start) {
                            Text("📚 ${tafsir.title}", color = palette.teal, fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Start)
                            Text(tafsir.author, color = palette.ink, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            Text(tafsir.fileName, color = palette.soft, fontFamily = UiFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            if (!TafsirRepository.isInstalled(context, tafsir)) {
                                                message = "جاري تنزيل ${tafsir.title}…"
                                                TafsirRepository.install(context, tafsir)
                                            }
                                            TafsirRepository.select(context, tafsir.id)
                                            message = "تم تجهيز ${tafsir.title}."
                                            context.startActivity(TafsirReaderActivity.intent(context, tafsir.id))
                                        } catch (t: Throwable) {
                                            if (t is kotlinx.coroutines.CancellationException) throw t
                                            message = "فشل تنزيل ${tafsir.title}: ${t.message ?: "خطأ غير معروف"}"
                                        }
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = if (installed) palette.gold else palette.teal)) {
                                    Text(if (installed) "فتح التفسير" else "تنزيل التفسير", color = if (installed) palette.ink else Color.White, fontFamily = UiFont)
                                }
                                if (installed) OutlinedButton(onClick = {
                                    scope.launch {
                                        runCatchingCancellable { TafsirRepository.delete(context, tafsir) }
                                            .onSuccess { message = "تم حذف ${tafsir.title} من الجهاز." }
                                            .onFailure { message = "تعذر حذف التفسير: ${it.message ?: "خطأ غير معروف"}" }
                                    }
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
}

