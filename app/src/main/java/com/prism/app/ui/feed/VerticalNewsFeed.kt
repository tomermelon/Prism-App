package com.prism.app.ui.feed

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.app.data.model.Article
import com.prism.app.data.model.Conversation
import com.prism.app.data.repository.ArticleRepository
import com.prism.app.data.repository.SocialRepository
import com.prism.app.ui.article.CommentsSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalNewsFeed(
    articles: List<Article>,
    uid: String,
    myUsername: String,
    loading: Boolean,
    emptyTitle: String,
    emptyBody: String,
    onOpenPrivateChat: (conversationId: String, title: String, draft: String) -> Unit,
    articleRepository: ArticleRepository = remember { ArticleRepository() },
    socialRepository: SocialRepository = remember { SocialRepository() }
) {
    var commentsArticle by remember { mutableStateOf<Article?>(null) }
    var shareArticle by remember { mutableStateOf<Article?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading && articles.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            articles.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(emptyTitle, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        emptyBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { articles.size })
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    val article = articles[page]
                    FeedStoryPage(
                        article = article,
                        uid = uid,
                        onComment = { commentsArticle = article },
                        onShare = { shareArticle = article },
                        articleRepository = articleRepository
                    )
                }
            }
        }
    }

    commentsArticle?.let { article ->
        CommentsSheet(
            articleId = article.id,
            articleTitle = article.title,
            uid = uid,
            myUsername = myUsername,
            onDismiss = { commentsArticle = null },
            onOpenPrivateChat = { conversationId, title, draft ->
                commentsArticle = null
                onOpenPrivateChat(conversationId, title, draft)
            }
        )
    }

    shareArticle?.let { article ->
        ShareToDmSheet(
            article = article,
            uid = uid,
            onDismiss = { shareArticle = null },
            socialRepository = socialRepository
        )
    }
}

@Composable
private fun FeedStoryPage(
    article: Article,
    uid: String,
    onComment: () -> Unit,
    onShare: () -> Unit,
    articleRepository: ArticleRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var liked by remember(article.id, uid) { mutableStateOf(false) }
    var likeCount by remember(article.id) { mutableIntStateOf(article.likeCount) }
    var likeInFlight by remember(article.id) { mutableStateOf(false) }
    var shareCount by remember(article.id) { mutableIntStateOf(article.shareCount) }

    LaunchedEffect(article.id, uid) {
        articleRepository.observeLiked(article.id, uid).collectLatest { serverLiked ->
            if (!likeInFlight) liked = serverLiked
        }
    }

    LaunchedEffect(article.likeCount, article.shareCount) {
        if (!likeInFlight) likeCount = article.likeCount
        shareCount = article.shareCount
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surface
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 88.dp, top = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = article.scope.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${article.source} · ${article.topic}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bias: ${article.bias}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = article.description.ifBlank { article.body },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val url = article.url.trim()
                    if (url.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } else {
                        Toast.makeText(context, "No publisher link", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = article.url.isNotBlank()
            ) {
                Text("Open publisher")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FeedActionButton(
                onClick = {
                    val wasLiked = liked
                    liked = !wasLiked
                    likeCount = (likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0)
                    likeInFlight = true
                    scope.launch {
                        val result = articleRepository.toggleLike(article.id, uid)
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
                    tint = if (liked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(likeCount.toString(), style = MaterialTheme.typography.labelLarge)

            FeedActionButton(onClick = onComment) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Comments"
                )
            }
            Text("Chat", style = MaterialTheme.typography.labelMedium)

            FeedActionButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share"
                )
            }
            Text(shareCount.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FeedActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 2.dp,
        modifier = Modifier.clip(CircleShape)
    ) {
        Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareToDmSheet(
    article: Article,
    uid: String,
    onDismiss: () -> Unit,
    socialRepository: SocialRepository
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        loading = true
        socialRepository.observeConversations(uid).collectLatest {
            conversations = it
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!sharing) onDismiss() },
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
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))

            when {
                loading && conversations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
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
                            val title = otherId?.let { convo.participantUsernames[it] } ?: "Chat"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !sharing) {
                                        sharing = true
                                        scope.launch {
                                            val result = socialRepository.sendArticle(
                                                conversationId = convo.id,
                                                senderId = uid,
                                                articleId = article.id,
                                                articleTitle = article.title
                                            )
                                            sharing = false
                                            result.onSuccess {
                                                onDismiss()
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
