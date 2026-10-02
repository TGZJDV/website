package com.famousmusic.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.famousmusic.app.ui.components.LiquidGlassNavBar
import com.famousmusic.app.ui.components.MiniPlayer
import com.famousmusic.app.ui.components.appNavDests
import com.famousmusic.app.ui.components.navDestIndex
import com.famousmusic.app.ui.components.navigateToTab
import com.famousmusic.app.ui.screens.AdminScreen
import com.famousmusic.app.ui.screens.EqualizerScreen
import com.famousmusic.app.ui.screens.ForgotScreen
import com.famousmusic.app.ui.screens.GenreScreen
import com.famousmusic.app.ui.screens.HomeScreen
import com.famousmusic.app.ui.screens.LoginScreen
import com.famousmusic.app.ui.screens.MeScreen
import com.famousmusic.app.ui.screens.NowPlayingScreen
import com.famousmusic.app.ui.screens.PlaylistDetailScreen
import com.famousmusic.app.ui.screens.PlaylistsScreen
import com.famousmusic.app.ui.screens.RegisterScreen
import com.famousmusic.app.ui.screens.SearchScreen
import com.famousmusic.app.ui.screens.SettingsScreen
import com.famousmusic.app.ui.screens.SongDetailScreen
import com.famousmusic.app.ui.screens.UploadScreen
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppSurface3
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.ui.theme.LocalBottomBarInset
import com.famousmusic.app.ui.theme.NavStyle
import com.famousmusic.app.ui.theme.NavStyleStore
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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

    // 液态玻璃要采样「底栏背后的真实内容」：内容层铺满全屏，与底栏共用同一份 backdrop
    val glass = !hideBars && navStyle == NavStyle.LIQUID_GLASS
    val backdrop = rememberLayerBackdrop()

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
            // 内容层：整屏，并作为玻璃的采样源
            CompositionLocalProvider(
                LocalBottomBarInset provides if (glass) 152.dp else 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop),
                ) {
                    // ⚠️ 不透明底必须是采样层**内部的子节点**：
                    // 写成 .background() 挂在这个 Box 上时，它排 layerBackdrop 之前，
                    // 不会被录进采样层 —— 层在底栏那个位置就是透明的，
                    // 于是玻璃画出来的模糊副本是透明的，底下「清晰的原内容」直接透出来，
                    // 看上去就像「背板没加模糊」。
                    Box(modifier = Modifier.fillMaxSize().background(AppSurface))

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
            if (glass) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    MiniPlayer(onOpen = { nav.navigate(Routes.PLAY) })
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
