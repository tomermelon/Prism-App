package com.prism.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.app.data.model.UserProfile
import com.prism.app.ui.article.ArticleScreen
import com.prism.app.ui.dm.ChatScreen
import com.prism.app.ui.dm.DmListScreen
import com.prism.app.ui.home.HomeScreen
import com.prism.app.ui.interests.InterestsScreen
import com.prism.app.ui.onboarding.AuthScreen
import com.prism.app.ui.onboarding.InterestPickerScreen
import com.prism.app.ui.session.SessionViewModel
import com.prism.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Interests("Interests", Icons.Default.Bookmark),
    Dms("DMs", Icons.Default.Chat),
    Settings("Settings", Icons.Default.Settings)
}

private sealed interface DetailDestination {
    data class Article(val articleId: String) : DetailDestination
    data class Chat(
        val conversationId: String,
        val title: String,
        val initialDraft: String = ""
    ) : DetailDestination
}

@Composable
fun PrismNavHost(
    sessionViewModel: SessionViewModel = viewModel()
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()

    if (session.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val user = session.user
    val profile = session.profile

    when {
        user == null -> AuthScreen()
        profile == null || !profile.onboardingComplete -> InterestPickerScreen(
            uid = user.uid,
            onFinished = { sessionViewModel.refreshProfile() }
        )
        else -> LoggedInShell(profile = profile)
    }
}

@Composable
private fun LoggedInShell(profile: UserProfile) {
    var detailStack by remember { mutableStateOf<List<DetailDestination>>(emptyList()) }
    val currentDetail = detailStack.lastOrNull()

    fun pushDetail(destination: DetailDestination) {
        detailStack = detailStack + destination
    }

    fun popDetail() {
        if (detailStack.isNotEmpty()) {
            detailStack = detailStack.dropLast(1)
        }
    }

    BackHandler(enabled = detailStack.isNotEmpty()) {
        popDetail()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Stays composed under details so back does not rebuild tabs.
        MainScaffold(
            profile = profile,
            onOpenArticle = { pushDetail(DetailDestination.Article(it)) },
            onOpenChat = { id, title ->
                pushDetail(DetailDestination.Chat(id, title))
            }
        )

        AnimatedContent(
            targetState = currentDetail,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith
                    fadeOut(animationSpec = tween(180))
            },
            label = "detailOverlay"
        ) { destination ->
            if (destination != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (destination) {
                        is DetailDestination.Article -> ArticleScreen(
                            articleId = destination.articleId,
                            uid = profile.uid,
                            myUsername = profile.username,
                            onBack = { popDetail() },
                            onOpenPrivateChat = { conversationId, title, draft ->
                                // Replace article with chat so back returns to the feed.
                                detailStack = detailStack.dropLast(1) + DetailDestination.Chat(
                                    conversationId = conversationId,
                                    title = title,
                                    initialDraft = draft
                                )
                            }
                        )
                        is DetailDestination.Chat -> ChatScreen(
                            conversationId = destination.conversationId,
                            title = destination.title,
                            uid = profile.uid,
                            initialDraft = destination.initialDraft,
                            onOpenArticle = { pushDetail(DetailDestination.Article(it)) },
                            onBack = { popDetail() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    profile: UserProfile,
    onOpenArticle: (String) -> Unit,
    onOpenChat: (conversationId: String, title: String) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(MainTab.Home.ordinal) }
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { MainTab.entries.size }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            scope.launch { pagerState.scrollToPage(index) }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            beyondViewportPageCount = MainTab.entries.size,
            userScrollEnabled = false
        ) { page ->
            when (MainTab.entries[page]) {
                MainTab.Home -> HomeScreen(
                    profile = profile,
                    onOpenArticle = onOpenArticle
                )
                MainTab.Interests -> InterestsScreen(
                    profile = profile,
                    onOpenArticle = onOpenArticle
                )
                MainTab.Dms -> DmListScreen(
                    profile = profile,
                    onOpenChat = onOpenChat
                )
                MainTab.Settings -> SettingsScreen(
                    profile = profile,
                    onSignedOut = { }
                )
            }
        }
    }
}
