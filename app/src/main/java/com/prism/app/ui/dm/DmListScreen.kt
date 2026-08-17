package com.prism.app.ui.dm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.app.data.model.Conversation
import com.prism.app.data.model.FriendRequest
import com.prism.app.data.model.UserProfile
import com.prism.app.data.repository.SocialRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun DmListScreen(
    profile: UserProfile,
    pendingShareTitle: String? = null,
    onClearPendingShare: () -> Unit = {},
    onOpenChat: (conversationId: String, title: String) -> Unit,
    socialRepository: SocialRepository = remember { SocialRepository() }
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var pending by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var showAddFriend by remember { mutableStateOf(false) }
    var showPending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile.uid) {
        socialRepository.ensureConversationsForFriends(
            uid = profile.uid,
            myUsername = profile.username
        )
        socialRepository.observeConversations(profile.uid).collectLatest { conversations = it }
    }
    LaunchedEffect(profile.uid) {
        socialRepository.observeIncomingRequests(profile.uid).collectLatest { pending = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium)
        if (pendingShareTitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick a chat to share: “$pendingShareTitle”",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onClearPendingShare) { Text("Cancel share") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAddFriend = true }, modifier = Modifier.weight(1f)) {
                Text("Add friend")
            }
            OutlinedButton(onClick = { showPending = true }, modifier = Modifier.weight(1f)) {
                Text("Pending (${pending.size})")
            }
        }
        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Recent", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (conversations.isEmpty()) {
            Text(
                "No conversations yet. Add a friend to start messaging.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(conversations, key = { it.id }) { convo ->
                    val otherId = convo.participantIds.firstOrNull { it != profile.uid }
                    val title = otherId?.let { convo.participantUsernames[it] } ?: "Chat"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(convo.id, title) }
                            .padding(vertical = 12.dp)
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

    if (showAddFriend) {
        var username by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFriend = false },
            title = { Text("Add friend") },
            text = {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = socialRepository.sendFriendRequest(
                                fromUid = profile.uid,
                                fromUsername = profile.username,
                                toUsername = username
                            )
                            status = result.fold(
                                onSuccess = { "Friend request sent" },
                                onFailure = { it.message ?: "Could not send request" }
                            )
                            showAddFriend = false
                        }
                    }
                ) { Text("Send request") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriend = false }) { Text("Cancel") }
            }
        )
    }

    if (showPending) {
        AlertDialog(
            onDismissRequest = { showPending = false },
            title = { Text("Pending requests") },
            text = {
                if (pending.isEmpty()) {
                    Text("No pending requests.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pending.forEach { req ->
                            Column {
                                Text(req.fromUsername, style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val result = socialRepository.acceptFriendRequest(
                                                    req.id,
                                                    profile.uid
                                                )
                                                status = result.fold(
                                                    onSuccess = {
                                                        "Friends now — conversation created"
                                                    },
                                                    onFailure = {
                                                        it.message ?: "Could not accept"
                                                    }
                                                )
                                                showPending = false
                                            }
                                        }
                                    ) { Text("Accept") }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val result = socialRepository.rejectFriendRequest(
                                                    req.id,
                                                    profile.uid
                                                )
                                                status = result.fold(
                                                    onSuccess = { "Request rejected" },
                                                    onFailure = {
                                                        it.message ?: "Could not reject"
                                                    }
                                                )
                                            }
                                        }
                                    ) { Text("Reject") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPending = false }) { Text("Close") }
            }
        )
    }
}
