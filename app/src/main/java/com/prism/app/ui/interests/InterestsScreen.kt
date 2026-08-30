package com.prism.app.ui.interests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.app.data.model.UserProfile
import com.prism.app.ui.feed.VerticalNewsFeed

@Composable
fun InterestsScreen(
    profile: UserProfile,
    onOpenPrivateChat: (conversationId: String, title: String, draft: String) -> Unit
) {
    val viewModel: InterestsViewModel = viewModel(key = profile.uid)
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(profile.uid, profile.interests) {
        viewModel.bindInterests(profile.interests)
    }

    VerticalNewsFeed(
        articles = state.articles,
        uid = profile.uid,
        myUsername = profile.username,
        loading = state.loading,
        emptyTitle = if (!state.hasInterests) "No interests yet" else "No matching stories",
        emptyBody = if (!state.hasInterests) {
            "Add topics in Settings to build this feed."
        } else {
            "No articles match your interests yet. Run ingest or pick more topics."
        },
        onOpenPrivateChat = onOpenPrivateChat
    )
}
