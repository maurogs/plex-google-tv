package dev.plexlite.tv.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.player.PlayerActivity
import dev.plexlite.tv.ui.detail.DetailScreen
import dev.plexlite.tv.ui.home.HomeScreen
import dev.plexlite.tv.ui.library.LibraryScreen
import dev.plexlite.tv.ui.login.LoginScreen
import dev.plexlite.tv.ui.settings.SettingsScreen
import dev.plexlite.tv.ui.theme.Background
import dev.plexlite.tv.ui.theme.PlexLiteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = if (AppGraph.prefs.authToken != null && AppGraph.server != null) "home" else "login"
        setContent {
            PlexLiteTheme {
                Box(Modifier.fillMaxSize().background(Background)) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = start) {
                        composable("login") {
                            LoginScreen(onDone = {
                                nav.navigate("home") { popUpTo(0) { inclusive = true } }
                            })
                        }
                        composable("home") {
                            HomeScreen(
                                onOpenSection = { section ->
                                    nav.navigate("library/${section.key}?title=${Uri.encode(section.title)}&type=${section.type}")
                                },
                                onOpenItem = { openItem(nav, it) },
                                onOpenSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("library/{id}?title={title}&type={type}") {
                            LibraryScreen(onOpenItem = { openItem(nav, it) })
                        }
                        composable("detail/{ratingKey}") {
                            DetailScreen(onPlay = { item, fromStart -> play(item, fromStart) })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onChangeServer = {
                                    AppGraph.forgetServer()
                                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                                },
                                onSignOut = {
                                    AppGraph.signOut()
                                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                                },
                            )
                        }
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
