package com.prism.app.ui.article

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prism.app.data.model.ArticleComment
import com.prism.app.data.repository.CommentRepository
import com.prism.app.data.repository.SocialRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    articleId: String,
    articleTitle: String,
    uid: String,
    myUsername: String,
    onDismiss: () -> Unit,
    onOpenPrivateChat: (conversationId: String, title: String, draft: String) -> Unit,
    commentRepository: CommentRepository = remember { CommentRepository() },
    socialRepository: SocialRepository = remember { SocialRepository() }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var comments by remember { mutableStateOf<List<ArticleComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<ArticleComment?>(null) }
    var authorTarget by remember { mutableStateOf<ArticleComment?>(null) }
    var authorBusy by remember { mutableStateOf(false) }

    LaunchedEffect(articleId) {
        loading = true
        commentRepository.observeComments(articleId).collectLatest {
            comments = it
            loading = false
        }
    }

    val roots = remember(comments) { comments.filter { it.parentId.isBlank() } }
    val repliesByParent = remember(comments) {
        comments.filter { it.parentId.isNotBlank() }.groupBy { it.parentId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Comments", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            when {
                loading && comments.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                comments.isEmpty() -> {
                    Text(
                        "No comments yet. Start the conversation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        roots.forEach { root ->
                            item(key = root.id) {
                                CommentRow(
                                    comment = root,
                                    articleId = articleId,
                                    uid = uid,
                                    indented = false,
                                    onAuthorClick = { authorTarget = root },
                                    onReply = { replyTo = root },
                                    commentRepository = commentRepository
                                )
                            }
                            repliesByParent[root.id].orEmpty().forEach { reply ->
                                item(key = reply.id) {
                                    CommentRow(
                                        comment = reply,
                                        articleId = articleId,
                                        uid = uid,
                                        indented = true,
                                        onAuthorClick = { authorTarget = reply },
                                        onReply = { replyTo = root },
                                        commentRepository = commentRepository
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            replyTo?.let { target ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to @${target.authorUsername}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { replyTo = null }) {
                        Text("Cancel")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment") },
                    maxLines = 4,
                    enabled = !posting
                )
                IconButton(
                    enabled = !posting && draft.isNotBlank(),
                    onClick = {
                        val text = draft
                        val parentId = replyTo?.id.orEmpty()
                        posting = true
                        scope.launch {
                            val result = commentRepository.postComment(
                                articleId = articleId,
                                authorId = uid,
                                authorUsername = myUsername,
                                text = text,
                                parentId = parentId
                            )
                            posting = false
                            result.onSuccess {
                                draft = ""
                                replyTo = null
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Could not post",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment")
                }
            }
        }
    }

    authorTarget?.let { target ->
        AuthorActionsDialog(
            comment = target,
            isSelf = target.authorId == uid,
            busy = authorBusy,
            onDismiss = { if (!authorBusy) authorTarget = null },
            onAddFriend = {
                authorBusy = true
                scope.launch {
                    val result = socialRepository.sendFriendRequest(
                        fromUid = uid,
                        fromUsername = myUsername,
                        toUsername = target.authorUsername
                    )
                    authorBusy = false
                    authorTarget = null
                    Toast.makeText(
                        context,
                        result.fold(
                            onSuccess = { "Friend request sent to @${target.authorUsername}" },
                            onFailure = { it.message ?: "Could not add friend" }
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onMessagePrivately = {
                authorBusy = true
                scope.launch {
                    runCatching {
                        socialRepository.getOrCreateConversation(
                            uid = uid,
                            username = myUsername,
                            otherUid = target.authorId,
                            otherUsername = target.authorUsername
                        )
                    }.onSuccess { conversationId ->
                        val draftText = buildPrivateDraft(articleTitle, target.text)
                        authorBusy = false
                        authorTarget = null
                        onDismiss()
                        onOpenPrivateChat(conversationId, target.authorUsername, draftText)
                    }.onFailure {
                        authorBusy = false
                        Toast.makeText(
                            context,
                            it.message ?: "Could not open chat",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun AuthorActionsDialog(
    comment: ArticleComment,
    isSelf: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAddFriend: () -> Unit,
    onMessagePrivately: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@${comment.authorUsername}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    isSelf -> Text("That's you.")
                    else -> {
                        TextButton(
                            onClick = onAddFriend,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add friend") }
                        TextButton(
                            onClick = onMessagePrivately,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Message privately") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun CommentRow(
    comment: ArticleComment,
    articleId: String,
    uid: String,
    indented: Boolean,
    onAuthorClick: () -> Unit,
    onReply: () -> Unit,
    commentRepository: CommentRepository
) {
    val scope = rememberCoroutineScope()
    var liked by remember(comment.id, uid) { mutableStateOf(false) }
    var likeCount by remember(comment.id) { mutableStateOf(comment.likeCount) }
    var likeInFlight by remember(comment.id) { mutableStateOf(false) }

    LaunchedEffect(comment.id, uid) {
        commentRepository.observeLiked(comment.id, uid).collectLatest { serverLiked ->
            if (!likeInFlight) liked = serverLiked
        }
    }

    LaunchedEffect(comment.likeCount) {
        if (!likeInFlight) likeCount = comment.likeCount
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 24.dp else 0.dp)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = comment.authorUsername.ifBlank { "User" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAuthorClick)
        )
        Spacer(Modifier.height(4.dp))
        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = !likeInFlight,
                onClick = {
                    val wasLiked = liked
                    liked = !wasLiked
                    likeCount = (likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0)
                    likeInFlight = true
                    scope.launch {
                        val result = commentRepository.toggleLike(articleId, comment.id, uid)
                        likeInFlight = false
                        result.onFailure {
                            liked = wasLiked
                            likeCount = (likeCount + if (wasLiked) 1 else -1).coerceAtLeast(0)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like comment",
                    tint = if (liked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = likeCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onReply) {
                Text("Reply")
            }
        }
    }
}

private fun buildPrivateDraft(articleTitle: String, commentText: String): String {
    val title = articleTitle.trim().ifBlank { "an article" }
    val snippet = commentText.trim().replace('\n', ' ').take(80)
    return "Re: \"$title\" — about your comment: \"$snippet\""
}
