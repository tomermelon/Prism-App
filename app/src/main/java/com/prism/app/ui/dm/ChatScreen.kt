package com.prism.app.ui.dm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prism.app.data.model.Article
import com.prism.app.data.model.ChatMessage
import com.prism.app.data.model.MessageType
import com.prism.app.data.repository.ArticleRepository
import com.prism.app.data.repository.SocialRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Set true after Firebase Storage is enabled (often requires Blaze). */
private const val EnableChatImages = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    title: String,
    uid: String,
    pendingArticleShare: Article? = null,
    onArticleShared: () -> Unit = {},
    onOpenArticle: (String) -> Unit,
    onBack: () -> Unit,
    socialRepository: SocialRepository = remember { SocialRepository() },
    articleRepository: ArticleRepository = remember { ArticleRepository() }
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var showArticlePicker by remember { mutableStateOf(false) }
    var articleQuery by remember { mutableStateOf("") }
    var articleResults by remember { mutableStateOf<List<Article>>(emptyList()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                socialRepository.sendImage(conversationId, uid, uri)
            }
        }
    }

    LaunchedEffect(conversationId) {
        socialRepository.observeMessages(conversationId).collectLatest {
            messages = it
            if (it.isNotEmpty()) {
                listState.animateScrollToItem(it.lastIndex)
            }
        }
    }

    LaunchedEffect(pendingArticleShare) {
        val article = pendingArticleShare ?: return@LaunchedEffect
        socialRepository.sendArticle(
            conversationId = conversationId,
            senderId = uid,
            articleId = article.id,
            articleTitle = article.title
        )
        onArticleShared()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (EnableChatImages) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Send image")
                    }
                }
                IconButton(onClick = { showArticlePicker = true }) {
                    Icon(Icons.Default.Newspaper, contentDescription = "Share article")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 4
                )
                IconButton(
                    onClick = {
                        val text = draft
                        draft = ""
                        scope.launch {
                            socialRepository.sendText(conversationId, uid, text)
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == uid
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(12.dp)
                    ) {
                        when (message.type) {
                            MessageType.TEXT -> Text(message.text)
                            MessageType.IMAGE -> AsyncImage(
                                model = message.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                contentScale = ContentScale.Crop
                            )
                            MessageType.ARTICLE -> Text(
                                text = "Article: ${message.articleTitle.ifBlank { message.text }}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    if (message.articleId.isNotBlank()) {
                                        onOpenArticle(message.articleId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showArticlePicker) {
        AlertDialog(
            onDismissRequest = { showArticlePicker = false },
            title = { Text("Share an article") },
            text = {
                Column {
                    OutlinedTextField(
                        value = articleQuery,
                        onValueChange = {
                            articleQuery = it
                            scope.launch {
                                articleResults = articleRepository.searchArticles(it)
                            }
                        },
                        label = { Text("Search title / topic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    articleResults.forEach { article ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    socialRepository.sendArticle(
                                        conversationId,
                                        uid,
                                        article.id,
                                        article.title
                                    )
                                    showArticlePicker = false
                                }
                            }
                        ) {
                            Text(article.title, maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showArticlePicker = false }) { Text("Close") }
            }
        )
    }
}
