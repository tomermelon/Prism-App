package com.prism.app.ui.article

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.app.data.model.Article
import com.prism.app.data.model.Conversation
import com.prism.app.data.repository.ArticleRepository
import com.prism.app.data.repository.SocialRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    articleId: String,
    uid: String,
    myUsername: String,
    onBack: () -> Unit,
    onOpenPrivateChat: (conversationId: String, title: String, draft: String) -> Unit = { _, _, _ -> },
    articleRepository: ArticleRepository = remember { ArticleRepository() },
    socialRepository: SocialRepository = remember { SocialRepository() }
) {
    var article by remember { mutableStateOf<Article?>(null) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(0) }
    var shareCount by remember { mutableIntStateOf(0) }
    var likeInFlight by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var conversationsLoading by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(articleId) {
        loading = true
        articleRepository.observeArticle(articleId).collectLatest { latest ->
            article = latest
            loading = false
            if (latest == null) {
                error = "Article not found"
            } else {
                error = null
                if (!likeInFlight) likeCount = latest.likeCount
                shareCount = latest.shareCount
            }
        }
    }

    LaunchedEffect(articleId, uid) {
        articleRepository.observeLiked(articleId, uid).collectLatest { serverLiked ->
            if (!likeInFlight) liked = serverLiked
        }
    }

    LaunchedEffect(showShareSheet, uid) {
        if (!showShareSheet) return@LaunchedEffect
        conversationsLoading = true
        socialRepository.observeConversations(uid).collectLatest {
            conversations = it
            conversationsLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        IconButton(
                            enabled = !likeInFlight && article != null,
                            onClick = {
                                val wasLiked = liked
                                liked = !wasLiked
                                likeCount = (likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0)
                                likeInFlight = true
                                scope.launch {
                                    val result = articleRepository.toggleLike(articleId, uid)
                                    likeInFlight = false
                                    result.onFailure {
                                        liked = wasLiked
                                        likeCount = (likeCount + if (wasLiked) 1 else -1).coerceAtLeast(0)
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Could not update like",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (liked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = likeCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            article == null -> {
                Text(
                    text = error ?: "Missing article",
                    modifier = Modifier.padding(padding).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                val item = article!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(item.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${item.source} · ${item.topic} · Bias: ${item.bias}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (liked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = " $likeCount likes",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = " $shareCount shares",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        item.body.ifBlank { item.description },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (item.url.isNotBlank()) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    )
                                }
                            },
                            enabled = item.url.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open publisher")
                        }
                        OutlinedButton(
                            onClick = { showShareSheet = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Share in DM")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showCommentsSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Comments")
                    }
                }
            }
        }
    }

    if (showCommentsSheet) {
        CommentsSheet(
            articleId = articleId,
            articleTitle = article?.title.orEmpty(),
            uid = uid,
            myUsername = myUsername,
            onDismiss = { showCommentsSheet = false },
            onOpenPrivateChat = { conversationId, title, draft ->
                showCommentsSheet = false
                onOpenPrivateChat(conversationId, title, draft)
            }
        )
    }

    if (showShareSheet) {
        val item = article
        ModalBottomSheet(
            onDismissRequest = { if (!sharing) showShareSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Share to chat", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))

                when {
                    conversationsLoading && conversations.isEmpty() -> {
                        BoxCentered { CircularProgressIndicator() }
                    }
                    conversations.isEmpty() -> {
                        Text(
                            "No conversations yet. Add a friend and accept a request first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(conversations, key = { it.id }) { convo ->
                                val otherId = convo.participantIds.firstOrNull { it != uid }
                                val title = otherId?.let { convo.participantUsernames[it] }
                                    ?: "Chat"
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !sharing && item != null) {
                                            val articleToShare = item ?: return@clickable
                                            sharing = true
                                            scope.launch {
                                                val result = socialRepository.sendArticle(
                                                    conversationId = convo.id,
                                                    senderId = uid,
                                                    articleId = articleToShare.id,
                                                    articleTitle = articleToShare.title
                                                )
                                                sharing = false
                                                result.onSuccess {
                                                    showShareSheet = false
                                                    Toast.makeText(
                                                        context,
                                                        "Shared with $title",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }.onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        it.message ?: "Could not share",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                        .padding(vertical = 14.dp)
                                ) {
                                    Text(title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = convo.lastMessage.ifBlank { "Say hello" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (sharing) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun BoxCentered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
