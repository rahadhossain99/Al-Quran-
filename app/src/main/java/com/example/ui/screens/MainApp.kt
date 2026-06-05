package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.viewmodel.QuranViewModel
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector

// Color themes mapping
data class QuranColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val primarySoft: Color,
    val accent: Color,
    val textMain: Color,
    val textMuted: Color,
    val borderColor: Color,
    val isDark: Boolean
)

val LightQuranColors = QuranColors(
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    primary = Color(0xFF0F9F59),
    primarySoft = Color(0xFFE6F5EC),
    accent = Color(0xFFFF9500),
    textMain = Color(0xFF1C1C1E),
    textMuted = Color(0xFF8E8E93),
    borderColor = Color(0xFFE5E5EA),
    isDark = false
)

val SepiaQuranColors = QuranColors(
    background = Color(0xFFF4ECD8),
    surface = Color(0xFFFAF6EB),
    primary = Color(0xFFD28F33),
    primarySoft = Color(0xFFF9EEDD),
    accent = Color(0xFFC25B3E),
    textMain = Color(0xFF5C4B37),
    textMuted = Color(0xFF8C7B66),
    borderColor = Color(0xFFE8DECA),
    isDark = false
)

val DarkQuranColors = QuranColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    primary = Color(0xFF30D158),
    primarySoft = Color(0xFF0C3817),
    accent = Color(0xFFFF9F0A),
    textMain = Color(0xFFFFFFFF),
    textMuted = Color(0xFF8E8E93),
    borderColor = Color(0xFF38383A),
    isDark = true
)

val LocalQuranColors = staticCompositionLocalOf { LightQuranColors }

// Reciters list
val QARIS_LIST = listOf(
    Qari("ar.alafasy", "মিশারি আল-আফাসি", "Mishary Rashid Alafasy"),
    Qari("ar.abdurrahmaansudais", "আব্দুর রহমান আস-সুদাইস", "Abdur-Rahman as-Sudais"),
    Qari("ar.abdulbasitmurattal", "আব্দুল বাসিত মুরাত্তাল", "AbdulBaset AbdulSamad"),
    Qari("ar.husary", "খলিল আল-হুসারি", "Mahmoud Khalil Al-Husary"),
    Qari("ar.saadalghamidi", "সাদ আল-গামদি", "Saad al-Ghamidi"),
    Qari("ar.mahermuaiqly", "মাহের আল মুআইক্লি", "Maher Al Muaiqly"),
    Qari("ar.minshawi", "মুহাম্মাদ আল-মিনশাভি", "Al-Minshawi"),
    Qari("ar.shuraym", "সাউদ আশ-শুরাইম", "Saud Al-Shuraim"),
    Qari("ar.yasserdossari", "ইয়াসির আদ-দুসারি", "Yasser Al-Dosari"),
    Qari("ar.hudhaify", "আলী আল-হুদাইফি", "Ali Al-Hudhaify")
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainApp(
    viewModel: QuranViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val quranColors = when (settings.theme) {
        "dark" -> DarkQuranColors
        "sepia" -> SepiaQuranColors
        else -> LightQuranColors
    }

    val context = LocalContext.current
    LaunchedEffect(settings.theme) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val view = window.decorView
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            val isDark = settings.theme == "dark"
            val isSepia = settings.theme == "sepia"
            
            // Set appropriate status bar icon tint (light icons on dark background, dark on light)
            controller.isAppearanceLightStatusBars = !isDark && !isSepia
            controller.isAppearanceLightNavigationBars = !isDark && !isSepia
            
            // Adjust system bar background colors dynamically to seamlessly match theme surfaces
            val barColor = when (settings.theme) {
                "dark" -> 0xFF121212.toInt()
                "sepia" -> 0xFFFBF4E9.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
        }
    }

    CompositionLocalProvider(LocalQuranColors provides quranColors) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = LocalQuranColors.current.background
        ) {
            val scope = rememberCoroutineScope()
            var currentTab by remember { mutableStateOf("home") } // "home", "favorites", "settings"
            val readingSurahState by viewModel.readingSurahState.collectAsStateWithLifecycle()

            // Connect player service
            val playerService by viewModel.playerService.collectAsStateWithLifecycle()
            val isPlaying = playerService?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
            val serviceSurah = playerService?.currentSurah?.collectAsStateWithLifecycle()?.value
            val serviceAyahIndex = playerService?.currentAyahIndex?.collectAsStateWithLifecycle()?.value ?: -1

            // Expanded Player toggle
            var isPlayerExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Custom Premium Header
                    HeaderBlock(
                        title = if (readingSurahState is UiState.Success) {
                            (readingSurahState as UiState.Success<FormattedSurah>).data.englishName
                        } else {
                            "আল-কুরআন"
                        },
                        showBackButton = readingSurahState is UiState.Success,
                        onBackClicked = {
                            viewModel.closeSurahReadingView()
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = readingSurahState,
                            transitionSpec = {
                                if (targetState is UiState.Success) {
                                    (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                                } else {
                                    (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { width -> width / 3 } + fadeOut())
                                }
                            },
                            label = "screen_navigation"
                        ) { readingState ->
                            when (readingState) {
                                is UiState.Idle -> {
                                    // Main Navigation Tabs
                                    when (currentTab) {
                                        "home" -> HomeScreen(viewModel)
                                        "favorites" -> FavoritesScreen(viewModel)
                                        "settings" -> SettingsScreen(viewModel)
                                    }
                                }
                                is UiState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = LocalQuranColors.current.primary)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "আয়াত প্রস্তুত করা হচ্ছে...",
                                                color = LocalQuranColors.current.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                                is UiState.Success -> {
                                    SurahReadingScreen(
                                        viewModel = viewModel,
                                        formattedSurah = readingState.data,
                                        isPlaying = isPlaying && serviceSurah?.number == readingState.data.number,
                                        activeAyahIndex = if (serviceSurah?.number == readingState.data.number) serviceAyahIndex else -1
                                    )
                                }
                                is UiState.Error -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Error",
                                                tint = Color.Red,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                readingState.message,
                                                color = LocalQuranColors.current.textMain,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = { viewModel.closeSurahReadingView() },
                                                colors = ButtonDefaults.buttonColors(containerColor = LocalQuranColors.current.primary)
                                            ) {
                                                Text("ফিরে যান", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Navigation Spacer if mini player is showing
                    val bottomPadding = if (serviceSurah != null) 160.dp else 80.dp
                    Spacer(modifier = Modifier.height(bottomPadding))
                }

                // Global Mini Floating Player
                AnimatedVisibility(
                    visible = serviceSurah != null,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                    ) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = if (readingSurahState is UiState.Success) 24.dp else 90.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                ) {
                    if (serviceSurah != null) {
                        MiniPlayerBlock(
                            surah = serviceSurah,
                            isPlaying = isPlaying,
                            currentAyahIndex = serviceAyahIndex,
                            progress = playerService?.progress?.collectAsStateWithLifecycle()?.value ?: 0,
                            duration = playerService?.duration?.collectAsStateWithLifecycle()?.value ?: 1,
                            onPlayPauseClicked = { playerService?.togglePlayPause() },
                            onExpandClicked = { isPlayerExpanded = true }
                        )
                    }
                }

                // Bottom Navigation (Hidden inside Reading View to maximize focus)
                AnimatedVisibility(
                    visible = readingSurahState is UiState.Idle,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomNavBar(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it }
                    )
                }

                // Expanded Player Sheet Overlay (iOS Modal transition)
                AnimatedVisibility(
                    visible = isPlayerExpanded && serviceSurah != null,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(400, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(350, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))
                    ) + fadeOut()
                ) {
                    if (serviceSurah != null) {
                        ExpandedPlayerView(
                            surah = serviceSurah,
                            isPlaying = isPlaying,
                            currentAyahIndex = serviceAyahIndex,
                            progress = playerService?.progress?.collectAsStateWithLifecycle()?.value ?: 0,
                            duration = playerService?.duration?.collectAsStateWithLifecycle()?.value ?: 1,
                            loopMode = playerService?.loopMode?.collectAsStateWithLifecycle()?.value ?: 0,
                            settings = settings,
                            onCloseRequested = { isPlayerExpanded = false },
                            onPlayPauseClicked = { playerService?.togglePlayPause() },
                            onNextClicked = { playerService?.playNext() },
                            onPrevClicked = { playerService?.playPrev() },
                            onLoopClicked = {
                                val currentMode = playerService?.loopMode?.value ?: 0
                                playerService?.setLoopMode((currentMode + 1) % 3)
                            },
                            onSeekIndexChanged = { index ->
                                playerService?.playAyah(index)
                            },
                            onProgressSeek = { ms ->
                                playerService?.seekTo(ms)
                            },
                            onGoToSurahClicked = {
                                isPlayerExpanded = false
                                viewModel.loadSurahReadingView(serviceSurah.number)
                            }
                        )
                    }
                }
            }
        }
    }
}

// App Header Block
@Composable
fun HeaderBlock(
    title: String,
    showBackButton: Boolean,
    onBackClicked: () -> Unit
) {
    val quranColors = LocalQuranColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(quranColors.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton) {
            IconButton(
                onClick = onBackClicked,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(quranColors.background)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = quranColors.textMain
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(quranColors.primary, quranColors.accent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = quranColors.textMain,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// iOS Style bottom navigation
@Composable
fun BottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val quranColors = LocalQuranColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp)
            .background(quranColors.surface)
            .navigationBarsPadding(),
        color = quranColors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val tabs = listOf(
                Triple("home", Icons.Default.Home, "হোম"),
                Triple("favorites", Icons.Default.Bookmark, "বুকমার্ক"),
                Triple("settings", Icons.Default.Settings, "সেটিংস")
            )

            tabs.forEach { (tabId, icon, label) ->
                val isActive = currentTab == tabId
                val tint by animateColorAsState(
                    targetValue = if (isActive) quranColors.primary else quranColors.textMuted,
                    label = "tab_color"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.1f else 1.0f,
                    label = "tab_scale"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            onClick = { onTabSelected(tabId) },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
                        .weight(1f)
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActive) quranColors.primarySoft else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Tab 1: Home List
@Composable
fun HomeScreen(viewModel: QuranViewModel) {
    val quranColors = LocalQuranColors.current
    val surahListState by viewModel.surahListState.collectAsStateWithLifecycle()
    val filteredList by viewModel.filteredSurahs.collectAsStateWithLifecycle()
    val lastPlayed by viewModel.lastPlayed.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val bookmarks by viewModel.favoriteSurahs.collectAsStateWithLifecycle()
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val bookmarkedIds = remember(bookmarks) { bookmarks.map { it.surahNumber }.toSet() }
    val downloadedJson = settings.downloadedSurahsJson

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            // Welcome Card Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(quranColors.primary, Color(0xFF139755))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        "আসসালামু আলাইকুম",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "পবিত্র কুরআনুল কারিম পড়ুন এবং শুনুন",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Custom search engine inside
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("সূরা অনুসন্ধান করুন...", color = Color.White.copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.15f),
                            disabledContainerColor = Color.Black.copy(alpha = 0.15f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Resume widget
        if (lastPlayed != null && searchQuery.isEmpty()) {
            item {
                Text(
                    "সর্বশেষ পঠিত",
                    color = quranColors.textMain,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(quranColors.surface, RoundedCornerShape(24.dp))
                        .border(1.dp, quranColors.borderColor, RoundedCornerShape(24.dp))
                        .clickable { viewModel.resumeLastPlayed(lastPlayed!!) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(quranColors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            lastPlayed!!.surahNumber.toString(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "পুনরায় শুরু করুন",
                            color = quranColors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            lastPlayed!!.surahEnglishName,
                            color = quranColors.textMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "আয়াত ${lastPlayed!!.ayahNumberInSurah}",
                            color = quranColors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        lastPlayed!!.surahNameArabic,
                        color = quranColors.primary,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Surah Grid title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "সকল সূরা",
                    color = quranColors.textMain,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    "১১৪ টি",
                    color = quranColors.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(quranColors.primarySoft, RoundedCornerShape(99.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        when (surahListState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = quranColors.primary)
                    }
                }
            }
            is UiState.Success -> {
                if (filteredList.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = quranColors.textMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "কোনো সূরা পাওয়া যায়নি",
                                color = quranColors.textMain,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredList, key = { _, surah -> surah.number }) { index, surah ->
                        val isBookmark = bookmarkedIds.contains(surah.number)
                        val isDownloaded = downloadedJson.contains(",${surah.number},")
                        val onSurahClick = remember(surah) { { viewModel.loadSurahReadingView(surah.number) } }
                        val onBookmarkClick = remember(surah) { { viewModel.toggleFavoriteSurah(surah) } }
                        SurahCardRow(
                            surah = surah,
                            isBookmark = isBookmark,
                            isDownloaded = isDownloaded,
                            onSurahClicked = onSurahClick,
                            onBookmarkClicked = onBookmarkClick
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Text(
                            (surahListState as UiState.Error).message,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadSurahList() },
                            colors = ButtonDefaults.buttonColors(containerColor = quranColors.primary)
                        ) {
                            Text("পুনরায় চেষ্টা করুন", color = Color.White)
                        }
                    }
                }
            }
            else -> {}
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Single Surah card block
@Composable
fun SurahCardRow(
    surah: SurahModel,
    isBookmark: Boolean,
    isDownloaded: Boolean = false,
    onSurahClicked: () -> Unit,
    onBookmarkClicked: () -> Unit
) {
    val quranColors = LocalQuranColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(quranColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, quranColors.borderColor, RoundedCornerShape(20.dp))
            .clickable { onSurahClicked() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(quranColors.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                surah.number.toString(),
                color = quranColors.textMain,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    surah.englishName,
                    color = quranColors.textMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isDownloaded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = "Offline Available",
                        tint = quranColors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                "${if (surah.revelationType == "Meccan") "মাক্কী" else "মাদানী"} • ${surah.numberOfAyahs} আয়াত",
                color = quranColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Text(
            surah.name,
            color = quranColors.primary,
            fontSize = 20.sp,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(onClick = onBookmarkClicked) {
            Icon(
                imageVector = if (isBookmark) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Bookmark",
                tint = if (isBookmark) quranColors.accent else quranColors.textMuted
            )
        }
    }
}

// Tab 2: Favorites Bookmarks
@Composable
fun FavoritesScreen(viewModel: QuranViewModel) {
    val quranColors = LocalQuranColors.current
    val bookmarks by viewModel.favoriteSurahs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "বুকমার্ক সমূহ",
            color = quranColors.textMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "আপনার পছন্দের সংরক্ষিত সূরাগুলো",
            color = quranColors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = quranColors.textMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "কোনো বুকমার্ক নেই",
                        color = quranColors.textMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "সূরা পড়ার সময় স্টারে ক্লিক করে সেভ করুন",
                        color = quranColors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.surahNumber }) { _, bookmark ->
                    // Convert favorite Surah entity to standard surrogate for presentation with remember cache
                    val surahModelRepresentation = remember(bookmark) {
                        SurahModel(
                            number = bookmark.surahNumber,
                            name = bookmark.nameArabic,
                            englishName = bookmark.englishName,
                            englishNameTranslation = "",
                            numberOfAyahs = bookmark.numberOfAyahs,
                            revelationType = bookmark.revelationType
                        )
                    }
                    val onSurahClick = remember(bookmark) { { viewModel.loadSurahReadingView(bookmark.surahNumber) } }
                    val onBookmarkClick = remember(bookmark) { { viewModel.toggleFavoriteSurahFromFavorite(bookmark) } }
                    SurahCardRow(
                        surah = surahModelRepresentation,
                        isBookmark = true,
                        onSurahClicked = onSurahClick,
                        onBookmarkClicked = onBookmarkClick
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// Tab 3: settings screen
@Composable
fun SettingsScreen(viewModel: QuranViewModel) {
    val quranColors = LocalQuranColors.current
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "অ্যাপ সেটিংস",
                color = quranColors.textMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // FontSize Controller Card
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = quranColors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(quranColors.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = quranColors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "অক্ষরের আকার",
                            color = quranColors.textMain,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Arabic Slider
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("আরবি ফন্ট", color = quranColors.textMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${settings.arabicFontSize}px", color = quranColors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = settings.arabicFontSize.toFloat(),
                            onValueChange = { viewModel.updateArabicFontSize(it.toInt()) },
                            valueRange = 24f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = quranColors.primary,
                                activeTrackColor = quranColors.primary,
                                inactiveTrackColor = quranColors.borderColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Translation Slider
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("বাংলা ও উচ্চারণ", color = quranColors.textMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${settings.translationFontSize}px", color = quranColors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = settings.translationFontSize.toFloat(),
                            onValueChange = { viewModel.updateTranslationFontSize(it.toInt()) },
                            valueRange = 14f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = quranColors.primary,
                                activeTrackColor = quranColors.primary,
                                inactiveTrackColor = quranColors.borderColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "লাইভ প্রিভিউ (Preview)",
                        color = quranColors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Card with preview text
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(quranColors.background, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                            fontSize = settings.arabicFontSize.sp,
                            fontFamily = FontFamily.Serif,
                            color = quranColors.textMain,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (settings.arabicFontSize * 1.5).sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "পরম করুণাময় অসীম দয়ালু আল্লাহর নামে।",
                            fontSize = settings.translationFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = quranColors.textMain,
                            lineHeight = (settings.translationFontSize * 1.3).sp
                        )
                    }
                }
            }
        }

        // Daily Notifications Reminder Card
        item {
            val context = LocalContext.current
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = quranColors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(quranColors.primarySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = quranColors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "দৈনিক স্মরণিকা",
                                    color = quranColors.textMain,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "কুরআন পড়ার দৈনিক রিমাইন্ডার",
                                    color = quranColors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Switch(
                            checked = settings.dailyNotificationEnabled,
                            onCheckedChange = { viewModel.updateDailyNotificationEnabled(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = quranColors.primary,
                                uncheckedThumbColor = quranColors.textMuted,
                                uncheckedTrackColor = quranColors.background
                            )
                        )
                    }

                    if (settings.dailyNotificationEnabled) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(quranColors.borderColor))
                        Spacer(modifier = Modifier.height(16.dp))

                        val displayTime = remember(settings.notificationHour, settings.notificationMinute) {
                            val h = settings.notificationHour
                            val m = settings.notificationMinute
                            val amPm = if (h >= 12) "PM" else "AM"
                            val hr = when {
                                h == 0 -> 12
                                h > 12 -> h - 12
                                else -> h
                            }
                            String.format("%02d:%02d %s", hr, m, amPm)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "রিমাইন্ডার শিডিউল সময়:",
                                color = quranColors.textMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                displayTime,
                                color = quranColors.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .background(quranColors.primarySoft, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Hour Adjuster
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ঘণ্টা (Hour)", color = quranColors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${settings.notificationHour} টা", color = quranColors.textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.notificationHour.toFloat(),
                                onValueChange = { viewModel.updateNotificationTime(context, it.toInt(), settings.notificationMinute) },
                                valueRange = 0f..23f,
                                colors = SliderDefaults.colors(
                                    thumbColor = quranColors.primary,
                                    activeTrackColor = quranColors.primary,
                                    inactiveTrackColor = quranColors.borderColor
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Minute Adjuster
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("মিনিট (Minute)", color = quranColors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${settings.notificationMinute} মি.", color = quranColors.textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.notificationMinute.toFloat(),
                                onValueChange = { viewModel.updateNotificationTime(context, settings.notificationHour, it.toInt()) },
                                valueRange = 0f..59f,
                                colors = SliderDefaults.colors(
                                    thumbColor = quranColors.primary,
                                    activeTrackColor = quranColors.primary,
                                    inactiveTrackColor = quranColors.borderColor
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                try {
                                    com.example.data.receiver.DailyReminderReceiver().onReceive(context, android.content.Intent())
                                } catch (e: Exception) {
                                    // Ignored
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = quranColors.primarySoft),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = quranColors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "একটি টেস্ট রিমাইন্ডার এখনই পাঠান",
                                color = quranColors.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Themes Card
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = quranColors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(quranColors.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = quranColors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "অ্যাপ থিম",
                            color = quranColors.textMain,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val themes = listOf(
                            "light" to "লাইট",
                            "dark" to "ডার্ক",
                            "sepia" to "সেপিয়া"
                        )
                        themes.forEach { (themeId, label) ->
                            val isSelected = settings.theme == themeId
                            Button(
                                onClick = { viewModel.updateTheme(themeId) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) quranColors.primarySoft else quranColors.background,
                                    contentColor = if (isSelected) quranColors.primary else quranColors.textMuted
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                border = if (isSelected) BorderStroke(1.dp, quranColors.primary) else null
                            ) {
                                Text(
                                    label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Qaris custom list
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = quranColors.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(quranColors.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = quranColors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "তেলাওয়াতকারী নির্বাচন",
                            color = quranColors.textMain,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    QARIS_LIST.forEach { qari ->
                        val isSelected = settings.selectedQari == qari.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) quranColors.primarySoft else Color.Transparent)
                                .clickable { viewModel.updateQari(qari.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isSelected) quranColors.primary else quranColors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    qari.name,
                                    color = quranColors.textMain,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    qari.englishName,
                                    color = quranColors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(quranColors.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// Overlay View: Reading view
@Composable
fun SurahReadingScreen(
    viewModel: QuranViewModel,
    formattedSurah: FormattedSurah,
    isPlaying: Boolean,
    activeAyahIndex: Int
) {
    val quranColors = LocalQuranColors.current
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val bookmarks by viewModel.favoriteSurahs.collectAsStateWithLifecycle()
    val isFav = bookmarks.any { it.surahNumber == formattedSurah.number }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    val isDownloaded = settings.downloadedSurahsJson.contains(",${formattedSurah.number},")
    val scope = rememberCoroutineScope()

    // Automatic Scroll into view when an ayah is changed inside playback
    LaunchedEffect(activeAyahIndex) {
        if (activeAyahIndex in 0 until formattedSurah.ayahs.size) {
            // Scroll to center active index on playback change
            scrollState.animateScrollToItem(activeAyahIndex + 1) // index + 1 due to header card
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Main Surah Info Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(quranColors.primary, Color(0xFF139755))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${if (formattedSurah.revelationType == "Meccan") "মাক্কী" else "মাদানী"} • ${formattedSurah.numberOfAyahs} আয়াত",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (downloadProgress != null) {
                                CircularProgressIndicator(
                                    progress = downloadProgress ?: 0f,
                                    color = quranColors.accent,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${((downloadProgress ?: 0f) * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (isDownloaded) {
                                            viewModel.toggleOfflineSurah(formattedSurah.number)
                                        } else {
                                            // Animate download simulation
                                            scope.launch {
                                                downloadProgress = 0.0f
                                                while ((downloadProgress ?: 0f) < 1.0f) {
                                                    kotlinx.coroutines.delay(120)
                                                    downloadProgress = (downloadProgress ?: 0f) + 0.1f
                                                }
                                                downloadProgress = null
                                                viewModel.toggleOfflineSurah(formattedSurah.number)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDownloaded) Icons.Default.FileDownloadDone else Icons.Default.FileDownload,
                                        contentDescription = "Download Surah Offline",
                                        tint = if (isDownloaded) quranColors.accent else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    // Dummy model representing active view items
                                    val reference = SurahModel(
                                        number = formattedSurah.number,
                                        name = formattedSurah.name,
                                        englishName = formattedSurah.englishName,
                                        englishNameTranslation = formattedSurah.englishNameTranslation,
                                        numberOfAyahs = formattedSurah.numberOfAyahs,
                                        revelationType = formattedSurah.revelationType
                                    )
                                    viewModel.toggleFavoriteSurah(reference)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (isFav) quranColors.accent else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        formattedSurah.name,
                        fontFamily = FontFamily.Serif,
                        color = quranColors.accent,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        formattedSurah.englishName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        formattedSurah.englishNameTranslation,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Big full audio start button
                    Button(
                        onClick = {
                            viewModel.playerService.value?.setSurahAndPlay(formattedSurah, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = quranColors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isDownloaded) "অফলাইন তেলাওয়াত শুরু করুন" else "সম্পূর্ণ তেলাওয়াত শুরু করুন",
                            color = quranColors.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Bismillah Banner (Omit for At-Tawbah 9 & Al-Fatihah 1)
        if (formattedSurah.number != 9 && formattedSurah.number != 1) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, quranColors.borderColor, RoundedCornerShape(16.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Serif,
                        color = quranColors.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // List of Ayahs
        itemsIndexed(formattedSurah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { index, ayah ->
            val isActive = activeAyahIndex == index

            // High performance borders for zero lists lag
            val activeBorderWidth = if (isActive) 2.dp else 1.dp
            val activeBorderColor = if (isActive) quranColors.accent else quranColors.borderColor
            val backgroundGradient = remember(isActive, quranColors) {
                if (isActive) {
                    Brush.linearGradient(
                        colors = listOf(quranColors.surface, quranColors.primarySoft.copy(alpha = 0.15f))
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(quranColors.surface, quranColors.surface)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(backgroundGradient, RoundedCornerShape(24.dp))
                    .border(activeBorderWidth, activeBorderColor, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(quranColors.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ayah.numberInSurah.toString(),
                                color = quranColors.textMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // Play/Pause individual trigger button
                        IconButton(
                            onClick = {
                                val service = viewModel.playerService.value
                                if (service != null && service.currentSurah.value?.number == formattedSurah.number && service.currentAyahIndex.value == index) {
                                    service.togglePlayPause()
                                } else {
                                    viewModel.playerService.value?.setSurahAndPlay(formattedSurah, index)
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isActive && isPlaying) quranColors.primary else quranColors.background)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = if (isActive && isPlaying) Color.White else quranColors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Arabic Text block
                    Text(
                        text = ayah.arabicText,
                        fontSize = settings.arabicFontSize.sp,
                        fontFamily = FontFamily.Serif,
                        color = quranColors.textMain,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = (settings.arabicFontSize * 1.6).sp,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bengali Translation text block
                    Text(
                        text = ayah.bengaliText,
                        fontSize = settings.translationFontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = quranColors.textMain,
                        lineHeight = (settings.translationFontSize * 1.35).sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pronunciation / Transliteration phonetic block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(quranColors.background.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "উচ্চারণ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = quranColors.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = ayah.transliterationText,
                            fontSize = (settings.translationFontSize * 0.85).sp,
                            fontWeight = FontWeight.Medium,
                            color = quranColors.textMuted,
                            lineHeight = (settings.translationFontSize * 1.25).sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// Mini Floating Audio Player
@Composable
fun MiniPlayerBlock(
    surah: FormattedSurah,
    isPlaying: Boolean,
    currentAyahIndex: Int,
    progress: Int,
    duration: Int,
    onPlayPauseClicked: () -> Unit,
    onExpandClicked: () -> Unit
) {
    val quranColors = LocalQuranColors.current
    val currentAyahNumber = if (currentAyahIndex in 0 until surah.ayahs.size) {
        surah.ayahs[currentAyahIndex].numberInSurah
    } else {
        1
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .background(quranColors.surface, RoundedCornerShape(24.dp))
            .clickable { onExpandClicked() },
        color = quranColors.surface,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Slider line progress on top bar
            val progressRatio = (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                drawLine(
                    color = quranColors.borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 4.dp.toPx()
                )
                drawLine(
                    color = quranColors.primary,
                    start = Offset(0f, 0f),
                    end = Offset(size.width * progressRatio, 0f),
                    strokeWidth = 4.dp.toPx()
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ayah Index badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(quranColors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    // Quick EQ animations
                    if (isPlaying) {
                        PlayEqAnimation(color = Color.White)
                    } else {
                        Text(
                            currentAyahNumber.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        surah.englishName,
                        color = quranColors.textMain,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isPlaying) "Playing Ayah $currentAyahNumber" else "Paused Ayah $currentAyahNumber",
                        color = quranColors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play control
                IconButton(
                    onClick = onPlayPauseClicked,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(quranColors.background)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = quranColors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = quranColors.textMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Fullscreen Slide up player
@Composable
fun ExpandedPlayerView(
    surah: FormattedSurah,
    isPlaying: Boolean,
    currentAyahIndex: Int,
    progress: Int,
    duration: Int,
    loopMode: Int,
    settings: UserSettings,
    onCloseRequested: () -> Unit,
    onPlayPauseClicked: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onLoopClicked: () -> Unit,
    onSeekIndexChanged: (Int) -> Unit,
    onProgressSeek: (Int) -> Unit,
    onGoToSurahClicked: () -> Unit
) {
    val quranColors = LocalQuranColors.current
    val currentAyahNumber = if (currentAyahIndex in 0 until surah.ayahs.size) {
        surah.ayahs[currentAyahIndex].numberInSurah
    } else {
        1
    }
    val currentAyah = if (currentAyahIndex in 0 until surah.ayahs.size) {
        surah.ayahs[currentAyahIndex]
    } else {
        surah.ayahs.getOrNull(0)
    }

    val context = LocalContext.current

    Surface(
        color = quranColors.surface,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle / Header closing bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(quranColors.borderColor)
                )
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCloseRequested,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(quranColors.background)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = quranColors.textMain
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NOW PLAYING",
                        color = quranColors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        surah.englishName,
                        color = quranColors.textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = {
                        // Share via intents
                        if (currentAyah != null) {
                            val textStr = "${currentAyah.arabicText}\n\n${currentAyah.bengaliText}\n\n- সূরা ${surah.name} (${surah.englishName}), আয়াত ${currentAyah.numberInSurah}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, textStr)
                            }
                            context.startActivity(Intent.createChooser(intent, "আয়াত শেয়ার করুন"))
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(quranColors.background)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = quranColors.textMain
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Large scripture display
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, quranColors.borderColor, RoundedCornerShape(32.dp))
                    .background(quranColors.background.copy(alpha = 0.3f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentAyah != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        val qariName = QARIS_LIST.find { it.id == settings.selectedQari }?.englishName ?: "Quran Reciter"
                        Text(
                            text = qariName.uppercase(),
                            color = quranColors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Text(
                            text = currentAyah.arabicText,
                            fontSize = (settings.arabicFontSize * 1.1).sp,
                            fontFamily = FontFamily.Serif,
                            color = quranColors.textMain,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (settings.arabicFontSize * 1.8).sp,
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = currentAyah.bengaliText,
                            fontSize = (settings.translationFontSize * 1.1).sp,
                            fontWeight = FontWeight.Bold,
                            color = quranColors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = (settings.translationFontSize * 1.5).sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Player Seeker Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                // Verse Navigation Slider (Ayah jump slider)
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    val totalVerses = surah.ayahs.size
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "আয়াত $currentAyahNumber",
                            color = quranColors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "মোট $totalVerses আয়াত",
                            color = quranColors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Slider(
                        value = currentAyahIndex.coerceAtLeast(0).toFloat(),
                        onValueChange = { index ->
                            onSeekIndexChanged(index.toInt())
                        },
                        valueRange = 0f..(totalVerses - 1).toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = quranColors.primary,
                            activeTrackColor = quranColors.primary,
                            inactiveTrackColor = quranColors.borderColor
                        )
                    )
                }

                // Audio track position timeline (Time Seeker)
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    val formattedCurrent = formatProgressTime(progress)
                    val formattedDuration = formatProgressTime(duration)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(formattedCurrent, color = quranColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(formattedDuration, color = quranColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = progress.toFloat(),
                        onValueChange = { ms ->
                            onProgressSeek(ms.toInt())
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = quranColors.primary,
                            activeTrackColor = quranColors.primary,
                            inactiveTrackColor = quranColors.borderColor
                        )
                    )
                }

                // Control panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Loop Mode toggle
                    IconButton(
                        onClick = onLoopClicked,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Loop Mode",
                                tint = when (loopMode) {
                                    1 -> quranColors.primary
                                    2 -> quranColors.accent
                                    else -> quranColors.textMuted
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            if (loopMode == 2) {
                                Text(
                                    "1",
                                    color = quranColors.accent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-2).dp)
                                        .background(quranColors.surface, CircleShape)
                                        .border(0.5.dp, quranColors.accent, CircleShape)
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    // Skip previous
                    IconButton(
                        onClick = onPrevClicked,
                        enabled = currentAyahIndex > 0,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (currentAyahIndex > 0) quranColors.textMain else quranColors.borderColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Large Play Pause Glow Button
                    Box(
                        modifier = Modifier
                            .shadow(12.dp, CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(quranColors.primary, Color(0xFF139755))
                                ),
                                CircleShape
                            )
                            .size(76.dp)
                            .clickable { onPlayPauseClicked() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Skip next
                    IconButton(
                        onClick = onNextClicked,
                        enabled = currentAyahIndex < surah.ayahs.size - 1,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = if (currentAyahIndex < surah.ayahs.size - 1) quranColors.textMain else quranColors.borderColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Snaps to Surah List
                    IconButton(
                        onClick = onGoToSurahClicked,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Go back to Quran",
                            tint = quranColors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// Convert ms to string
private fun formatProgressTime(ms: Int): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return String.format("%d:%02d", minutes, seconds)
}

// Custom EQ line animations
@Composable
fun PlayEqAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
            label = "h1"
        ),
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
            label = "h2"
        ),
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
            label = "h3"
        )
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { anim ->
            Box(
                modifier = Modifier
                    .fillMaxHeight(anim.value)
                    .width(4.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}


