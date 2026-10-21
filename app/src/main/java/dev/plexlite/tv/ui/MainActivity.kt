package dev.plexlite.tv.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.AppLocale
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.player.PlayerActivity
import dev.plexlite.tv.ui.detail.DetailScreen
import dev.plexlite.tv.ui.home.HeaderActions
import dev.plexlite.tv.ui.home.HomeScreen
import dev.plexlite.tv.ui.library.LibraryScreen
import dev.plexlite.tv.ui.login.LoginScreen
import dev.plexlite.tv.ui.profiles.ProfilesScreen
import dev.plexlite.tv.ui.search.SearchScreen
import dev.plexlite.tv.ui.settings.LibrariesScreen
import dev.plexlite.tv.ui.settings.SettingsScreen
import dev.plexlite.tv.ui.theme.Background
import dev.plexlite.tv.ui.theme.PlexLiteTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.prefs.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppGraph.prefs
        val skipProfiles = prefs.autoSignIn && prefs.currentUser?.isProtected == false
        val start = when {
            prefs.authToken == null || AppGraph.server == null -> "login"
            skipProfiles -> "home"
            else -> "profilesStart"
        }
        setContent {
            PlexLiteTheme {
                Box(Modifier.fillMaxSize().background(Background)) {
                    val nav = rememberNavController()
                    val actions = remember(nav) {
                        HeaderActions(
                            onHome = {
                                if (!nav.popBackStack("home", inclusive = false)) {
                                    nav.navigate("home") { popUpTo(0) { inclusive = true } }
                                }
                            },
                            onSearch = { nav.navigate("search") { launchSingleTop = true } },
                            onOpenSection = { section ->
                                nav.navigate("library/${section.key}?title=${Uri.encode(section.title)}&type=${section.type}") {
                                    popUpTo("home")
                                    launchSingleTop = true
                                }
                            },
                            onChangeServer = { nav.navigate("pickServer") },
                            onChangeUser = { nav.navigate("profiles") },
                            onOrganizeLibraries = { nav.navigate("libraries") },
                            onOpenSettings = { nav.navigate("settings") },
                            onLanguageChanged = { tag ->
                                AppGraph.setLanguage(tag)
                                recreate()
                            },
                        )
                    }
                    NavHost(navController = nav, startDestination = start) {
                        composable("login") {
                            LoginScreen(onDone = {
                                nav.navigate("profiles") { popUpTo(0) { inclusive = true } }
                            })
                        }
                        composable(
                            "profilesStart",
                            arguments = listOf(navArgument("auto") {
                                type = NavType.BoolType
                                defaultValue = true
                            }),
                        ) {
                            ProfilesScreen(onDone = { nav.navigate("home") { popUpTo(0) { inclusive = true } } })
                        }
                        composable("profiles") {
                            ProfilesScreen(onDone = { nav.navigate("home") { popUpTo(0) { inclusive = true } } })
                        }
                        composable(
                            "pickServer",
                            arguments = listOf(navArgument("pick") {
                                type = NavType.BoolType
                                defaultValue = true
                            }),
                        ) {
                            LoginScreen(onDone = {
                                nav.navigate("home") { popUpTo(0) { inclusive = true } }
                            })
                        }
                        composable("home") {
                            HomeScreen(onOpenItem = { openItem(nav, it) }, actions = actions)
                        }
                        composable("library/{id}?title={title}&type={type}") {
                            LibraryScreen(onOpenItem = { openItem(nav, it) }, actions = actions)
                        }
                        composable("detail/{ratingKey}") {
                            DetailScreen(onPlay = { item, fromStart -> play(item, fromStart) })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onChangeServer = actions.onChangeServer,
                                onSignOut = {
                                    AppGraph.signOut()
                                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                                },
                                onOrganizeLibraries = actions.onOrganizeLibraries,
                                onLanguageChanged = actions.onLanguageChanged,
                            )
                        }
                        composable("libraries") { LibrariesScreen() }
                        composable("search") { SearchScreen(onOpenItem = { openItem(nav, it) }) }
                    }
                }
            }
        }
    }

    private fun openItem(nav: NavHostController, item: Metadata) {
        when (item.type) {
            "episode" -> play(item, fromStart = false)
            "season" -> (item.parentRatingKey ?: item.ratingKey).let { nav.navigate("detail/$it") }
            else -> nav.navigate("detail/${item.ratingKey}")
        }
    }

    @OptIn(UnstableApi::class)
    private fun play(item: Metadata, fromStart: Boolean) {
        startActivity(PlayerActivity.intent(this, item.ratingKey, fromStart))
    }
}
