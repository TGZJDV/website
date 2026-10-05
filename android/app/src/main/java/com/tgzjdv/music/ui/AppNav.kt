package com.tgzjdv.music.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tgzjdv.music.ui.components.LiquidGlassNavBar
import com.tgzjdv.music.ui.components.MiniPlayer
import com.tgzjdv.music.ui.components.appNavDests
import com.tgzjdv.music.ui.components.navDestIndex
import com.tgzjdv.music.ui.components.navigateToTab
import com.tgzjdv.music.ui.screens.AdminScreen
import com.tgzjdv.music.ui.screens.EqualizerScreen
import com.tgzjdv.music.ui.screens.ForgotScreen
import com.tgzjdv.music.ui.screens.GenreScreen
import com.tgzjdv.music.ui.screens.HomeScreen
import com.tgzjdv.music.ui.screens.LoginScreen
import com.tgzjdv.music.ui.screens.MeScreen
import com.tgzjdv.music.ui.screens.NowPlayingScreen
import com.tgzjdv.music.ui.screens.PlaylistDetailScreen
import com.tgzjdv.music.ui.screens.PlaylistsScreen
import com.tgzjdv.music.ui.screens.RegisterScreen
import com.tgzjdv.music.ui.screens.SearchScreen
import com.tgzjdv.music.ui.screens.SettingsScreen
import com.tgzjdv.music.ui.screens.SongDetailScreen
import com.tgzjdv.music.ui.screens.UploadScreen
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppSurface3
import com.tgzjdv.music.ui.theme.AppText
import com.tgzjdv.music.ui.theme.LocalBottomBarInset
import com.tgzjdv.music.ui.theme.LocalGlassBackdrop
import com.tgzjdv.music.ui.theme.NavStyle
import com.tgzjdv.music.ui.theme.NavStyleStore
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tgzjdv.music.data.BackgroundStore
import com.tgzjdv.music.data.BgMode
import coil.compose.AsyncImage

/** 路由表 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val GENRES = "genres"
    const val PLAYLISTS = "playlists"
    const val ME = "me"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val PLAY = "play"
    const val UPLOAD = "upload"
    const val ADMIN = "admin"
    const val PLAYLIST_DETAIL = "playlist/{id}"
    const val SONG_DETAIL = "song/{id}"
    const val EQUALIZER = "equalizer"
    const val SETTINGS = "settings"

    const val GENRES_WITH_ARG = "genres?genre={genre}"

    fun playlist(id: Int) = "playlist/$id"
    fun song(id: Int) = "song/$id"
    fun genres(genre: String?) =
        if (genre.isNullOrBlank()) GENRES else "$GENRES?genre=${Uri.encode(genre)}"
}

/** 应用外壳：底部导航 + 迷你播放条 + 路由 */
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // 全屏播放与登录流程隐藏底部导航
    val hideBars = route == Routes.PLAY || route == Routes.LOGIN ||
        route == Routes.REGISTER || route == Routes.FORGOT

    // 底栏样式（设置里可切）
    val navStyle by NavStyleStore.style.collectAsState()
    // 自定义背景（相册图片 / UAPI 随机图片）
    val bgState by BackgroundStore.state.collectAsState()

    // ⚠️ 这里必须分成两个开关：
    //   glassBars 控制「底栏/迷你播放条要不要显示成玻璃」→ 与 hideBars 有关
    //   glassMode 控制「页面内的面板要不要用玻璃材质」→ **与 hideBars 无关**
    // 之前两者共用一个变量，进播放页时 hideBars=true 会让 LocalGlassBackdrop 变 null，
    // 正在淡出的上一页所有面板会当场退回普通样式（淡入时"元素变普通"就是这个）
    val glassBars = !hideBars && navStyle == NavStyle.LIQUID_GLASS
    val glassMode = navStyle == NavStyle.LIQUID_GLASS
    val backdrop = rememberLayerBackdrop()
    // ⚠️ 页面**内部**的面板（卡片/按钮）必须采样另一份「背景层」：
    // 它们位于内容层内部，若采样内容层＝在自己的采样层里画自己 → HWUI 栈溢出闪退。
    // 这一份只画背景，是内容层的**兄弟节点**，所以安全。
    val bgBackdrop = rememberLayerBackdrop()

    Scaffold(
        containerColor = AppSurface,
        bottomBar = {
            // 经典样式仍走 Scaffold 的 bottomBar（预留空间，行为与改动前一致）
            if (!hideBars && navStyle == NavStyle.CLASSIC) {
                Column {
                    MiniPlayer(onOpen = { nav.navigate(Routes.PLAY) })
                    BottomNavBar(nav, route)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ⓪ 背景采样层：只画背景，**不含任何内容/玻璃元素**。
            //    页面内部的卡片与按钮采它 → 真玻璃且绝不成环。
            //    （加一点微弱的径向光，否则纯色背景下玻璃折射看不出来）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(bgBackdrop),
            ) {
                AppBackground(bgState)
            }

            // 内容层：整屏，并作为底栏/迷你播放条的玻璃采样源
            CompositionLocalProvider(
                LocalBottomBarInset provides if (glassBars) 152.dp else 0.dp,
                // 页面内部的卡片/面板采「背景层」（安全）；底栏等浮层用另一份（见下）
                LocalGlassBackdrop provides if (glassMode) bgBackdrop else null,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop),
                ) {
                    // ⚠️ 背景必须是采样层**内部的子节点**：
                    // 写成 .background() 挂在这个 Box 上时，它排 layerBackdrop 之前，
                    // 不会被录进采样层 —— 层在底栏那个位置就是透明的，
                    // 于是玻璃画出来的模糊副本是透明的，底下「清晰的原内容」直接透出来，
                    // 看上去就像「背板没加模糊」。
                    //
                    // 同时这里也要画**用户自定义背景**：否则内容层的不透明底色会把
                    // 背景采样层里的背景图整个盖住，用户就看不到自己设的背景。
                    AppBackground(bgState)

                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onSongDetail = { nav.navigate(Routes.song(it)) },
                        onGenreClick = { nav.navigate(Routes.genres(it)) },
                        onUpload = { nav.navigate(Routes.UPLOAD) },
                        onSeeAll = { nav.navigate(Routes.SEARCH) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(onSongDetail = { nav.navigate(Routes.song(it)) })
                }
                composable(
                    route = Routes.GENRES_WITH_ARG,
                    arguments = listOf(navArgument("genre") {
                        type = NavType.StringType
                        defaultValue = ""
                    }),
                ) { entry ->
                    GenreScreen(
                        initialGenre = entry.arguments?.getString("genre")?.ifBlank { null },
                        onSongDetail = { nav.navigate(Routes.song(it)) },
                    )
                }
                composable(Routes.PLAYLISTS) {
                    PlaylistsScreen(
                        onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(
                    route = Routes.PLAYLIST_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) { entry ->
                    PlaylistDetailScreen(
                        playlistId = entry.arguments?.getInt("id") ?: 0,
                        onBack = { nav.popBackStack() },
                        onSongDetail = { nav.navigate(Routes.song(it)) },
                    )
                }
                composable(Routes.ME) {
                    MeScreen(
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        onUpload = { nav.navigate(Routes.UPLOAD) },
                        onAdmin = { nav.navigate(Routes.ADMIN) },
                        onSongDetail = { nav.navigate(Routes.song(it)) },
                        onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onEqualizer = { nav.navigate(Routes.EQUALIZER) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.EQUALIZER) {
                    EqualizerScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
                composable(
                    route = Routes.SONG_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) { entry ->
                    SongDetailScreen(
                        songId = entry.arguments?.getInt("id") ?: 0,
                        onBack = { nav.popBackStack() },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.UPLOAD) {
                    UploadScreen(
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        onDone = { id -> nav.navigate(Routes.song(id)) },
                    )
                }
                composable(Routes.ADMIN) {
                    AdminScreen(
                        onBack = { nav.popBackStack() },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.PLAY) {
                    NowPlayingScreen(
                        onBack = { nav.popBackStack() },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        // 播放页顶栏的音效按钮 → 均衡器页
                        onEqualizer = { nav.navigate(Routes.EQUALIZER) },
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onDone = { nav.popBackStack() },
                        onGoRegister = { nav.navigate(Routes.REGISTER) },
                        onGoForgot = { nav.navigate(Routes.FORGOT) },
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                }
                composable(Routes.FORGOT) {
                    ForgotScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                }
                    }
                }
            }

            // 玻璃样式：迷你播放条 + 底栏浮在内容之上（内容从它们背后滚过）
            if (glassBars) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    MiniPlayer(onOpen = { nav.navigate(Routes.PLAY) }, glass = true, backdrop = backdrop)
                    LiquidGlassNavBar(nav, route, backdrop)
                }
            }
        }
    }
}

/** 经典底栏：Material3 NavigationBar（原有样式，保持不变） */
@Composable
private fun BottomNavBar(nav: NavHostController, currentRoute: String?) {
    val selectedIndex = navDestIndex(currentRoute)

    NavigationBar(containerColor = AppSurface2) {
        appNavDests.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) navigateToTab(nav, item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppPrimary,
                    selectedTextColor = AppPrimary,
                    unselectedIconColor = AppMuted,
                    unselectedTextColor = AppMuted,
                    indicatorColor = AppSurface3,
                ),
            )
        }
    }
}

/**
 * 应用背景：纯色底 → 用户自定义背景（相册 / UAPI 随机图）→ 压暗 → 微弱径向光。
 *
 * ⚠️ 这个组件被画在**两处**：
 *   ① 背景采样层（供页面内部的液态玻璃面板采样）
 *   ② 内容层（否则内容层的不透明底会把背景图盖住，用户看不到自己设的背景）
 * 两处必须画同样的东西，否则玻璃折射出来的和肉眼看到的会对不上。
 */
@Composable
private fun AppBackground(bgState: BackgroundStore.State) {
    Box(modifier = Modifier.fillMaxSize().background(AppSurface))

    when (bgState.mode) {
        BgMode.LOCAL -> {
            val uri = bgState.localUri
            if (!uri.isNullOrBlank()) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        BgMode.UAPI -> {
            val f = BackgroundStore.uapiFile()
            if (f != null && f.exists()) {
                AsyncImage(
                    model = f,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        BgMode.NONE -> Unit
    }

    // 压暗，保证文字与图标在任何背景上都读得清
    if (bgState.mode != BgMode.NONE) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)))
    }

    // 微弱径向光：纯色背景下也给玻璃一点可折射的层次
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.055f),
                        Color.Transparent,
                    ),
                    center = Offset(0.30f * 1080f, 0.18f * 2400f),
                    radius = 1500f,
                ),
            ),
    )
}
