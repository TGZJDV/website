package com.famousmusic.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.famousmusic.app.ui.components.MiniPlayer
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
import com.famousmusic.app.ui.screens.SongDetailScreen
import com.famousmusic.app.ui.screens.UploadScreen
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppSurface3

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

    Scaffold(
        containerColor = AppSurface,
        bottomBar = {
            if (!hideBars) {
                Column {
                    MiniPlayer(onOpen = { nav.navigate(Routes.PLAY) })
                    BottomNavBar(nav, route)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = Routes.HOME) {
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
                    )
                }
                composable(Routes.EQUALIZER) {
                    EqualizerScreen(onBack = { nav.popBackStack() })
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
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun BottomNavBar(nav: NavHostController, currentRoute: String?) {
    val items = listOf(
        NavItem(Routes.HOME, "发现", Icons.Rounded.Home),
        NavItem(Routes.SEARCH, "搜索", Icons.Rounded.Search),
        NavItem(Routes.GENRES, "分类", Icons.Rounded.Category),
        NavItem(Routes.PLAYLISTS, "歌单", Icons.Rounded.QueueMusic),
        NavItem(Routes.ME, "我的", Icons.Rounded.Person),
    )

    NavigationBar(containerColor = AppSurface2) {
        items.forEach { item ->
            val selected = currentRoute == item.route ||
                (item.route == Routes.GENRES && currentRoute == Routes.GENRES_WITH_ARG)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        nav.navigate(item.route) {
                            // 点底栏统一回到该 tab 的根页面：先把压在根页面之上的详情页
                            // 全部弹出，再由 launchSingleTop 复用根页面本身（不新建实例）。
                            //
                            // ⚠️ 这里刻意不用 saveState / restoreState：
                            // 非 inclusive 的 popUpTo 会被 NavController 把保存的状态映射到
                            // popUpTo 目标（startDestination = home）的 id 上；而我们紧接着
                            // 又导航回 home，NavController 发现 backStackMap 里已有 home 的键，
                            // 就会立刻把刚刚弹掉的详情页原样恢复出来 —— 表现就是
                            // 「点底栏没反应」以及「切到别的 tab 再切回来仍停在详情页」。
                            popUpTo(nav.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
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
