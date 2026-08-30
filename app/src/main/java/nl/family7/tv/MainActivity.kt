package nl.family7.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import nl.family7.tv.data.EpisodeItem
import nl.family7.tv.data.Family7AuthRepository
import nl.family7.tv.data.Family7CatalogRepository
import nl.family7.tv.data.Family7LiveRepository
import nl.family7.tv.data.Family7VideoRepository
import nl.family7.tv.data.ProgramDetail
import nl.family7.tv.data.ProgramItem
import nl.family7.tv.data.UserSession
import nl.family7.tv.ui.screens.HomeScreen
import nl.family7.tv.ui.screens.LiveTVScreen
import nl.family7.tv.ui.screens.LoginScreen
import nl.family7.tv.ui.screens.PlayerScreen
import nl.family7.tv.ui.screens.ProgramDetailScreen
import nl.family7.tv.ui.screens.SearchScreen
import nl.family7.tv.ui.theme.Family7TVTheme

sealed class ScreenState {
    object Splash : ScreenState()
    object Login : ScreenState()
    object Home : ScreenState()
    object Live : ScreenState()
    data class ProgramDetailView(val program: ProgramItem) : ScreenState()
    data class VideoPlayer(val episode: EpisodeItem, val program: ProgramDetail) : ScreenState()
    object Search : ScreenState()
}

class MainActivity : ComponentActivity() {
    private lateinit var authRepo: Family7AuthRepository
    private lateinit var liveRepo: Family7LiveRepository
    private lateinit var catalogRepo: Family7CatalogRepository
    private lateinit var videoRepo: Family7VideoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authRepo = Family7AuthRepository(this)
        liveRepo = Family7LiveRepository(this)
        catalogRepo = Family7CatalogRepository(this)
        videoRepo = Family7VideoRepository(this)

        setContent {
            Family7TVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Family7TVApp(
                        authRepo = authRepo,
                        liveRepo = liveRepo,
                        catalogRepo = catalogRepo,
                        videoRepo = videoRepo
                    )
                }
            }
        }
    }
}

@Composable
fun Family7TVApp(
    authRepo: Family7AuthRepository,
    liveRepo: Family7LiveRepository,
    catalogRepo: Family7CatalogRepository,
    videoRepo: Family7VideoRepository
) {
    var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Splash) }
    var currentSession by remember { mutableStateOf<UserSession?>(null) }

    // Check existing session
    LaunchedEffect(Unit) {
        val session = authRepo.checkSession()
        currentSession = session
        screenState = if (session.isLoggedIn) {
            ScreenState.Home
        } else {
            ScreenState.Login
        }
    }

    when (val state = screenState) {
        ScreenState.Splash -> {
            // Loading splash
        }
        ScreenState.Login -> {
            LoginScreen(
                authRepo = authRepo,
                onLoginSuccess = { session ->
                    currentSession = session
                    screenState = ScreenState.Home
                }
            )
        }
        ScreenState.Home -> {
            HomeScreen(
                catalogRepo = catalogRepo,
                onNavigateToLive = { screenState = ScreenState.Live },
                onSelectProgram = { program ->
                    screenState = ScreenState.ProgramDetailView(program)
                },
                onNavigateToSearch = { screenState = ScreenState.Search },
                onNavigateToAZ = { screenState = ScreenState.Search },
                onLogout = {
                    authRepo.logout()
                    screenState = ScreenState.Login
                }
            )
        }
        ScreenState.Live -> {
            BackHandler {
                screenState = ScreenState.Home
            }
            LiveTVScreen(
                liveRepo = liveRepo,
                onBack = { screenState = ScreenState.Home }
            )
        }
        is ScreenState.ProgramDetailView -> {
            BackHandler {
                screenState = ScreenState.Home
            }
            ProgramDetailScreen(
                programItem = state.program,
                videoRepo = videoRepo,
                onPlayEpisode = { episode, detail ->
                    screenState = ScreenState.VideoPlayer(episode, detail)
                },
                onBack = { screenState = ScreenState.Home }
            )
        }
        is ScreenState.VideoPlayer -> {
            BackHandler {
                screenState = ScreenState.ProgramDetailView(
                    ProgramItem(
                        id = state.program.slug,
                        slug = state.program.slug,
                        title = state.program.title,
                        thumbnailUrl = state.program.posterUrl
                    )
                )
            }
            PlayerScreen(
                episode = state.episode,
                program = state.program,
                videoRepo = videoRepo,
                onBack = {
                    screenState = ScreenState.ProgramDetailView(
                        ProgramItem(
                            id = state.program.slug,
                            slug = state.program.slug,
                            title = state.program.title,
                            thumbnailUrl = state.program.posterUrl
                        )
                    )
                }
            )
        }
        ScreenState.Search -> {
            BackHandler {
                screenState = ScreenState.Home
            }
            SearchScreen(
                catalogRepo = catalogRepo,
                onSelectProgram = { program ->
                    screenState = ScreenState.ProgramDetailView(program)
                },
                onBack = { screenState = ScreenState.Home }
            )
        }
    }
}
