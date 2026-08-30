package com.prism.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.app.data.model.UserProfile
import com.prism.app.ui.feed.VerticalNewsFeed

@Composable
fun HomeScreen(
    profile: UserProfile,
    onOpenPrivateChat: (conversationId: String, title: String, draft: String) -> Unit
) {
    // Unique key so a stuck old HomeViewModel cannot survive across rebuilds.
    val viewModel: HomeViewModel = viewModel(key = "home_${profile.uid}")
    val state by viewModel.state.collectAsStateWithLifecycle()

    VerticalNewsFeed(
        articles = state.articles,
        uid = profile.uid,
        myUsername = profile.username,
        loading = state.loading,
        emptyTitle = when {
            state.error != null -> "Couldn't load Home"
            else -> "No stories yet"
        },
        emptyBody = state.error
            ?: "Run the news ingest script or check back soon.",
        onOpenPrivateChat = onOpenPrivateChat
    )
}
