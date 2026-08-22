package com.prism.app.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.prism.app.data.FirebasePaths
import com.prism.app.data.model.ChatMessage
import com.prism.app.data.model.Conversation
import com.prism.app.data.model.FriendRequest
import com.prism.app.data.model.MessageType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    fun observeIncomingRequests(uid: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg = db.collection(FirebasePaths.FRIEND_REQUESTS)
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toFriendRequest() }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendFriendRequest(
        fromUid: String,
        fromUsername: String,
        toUsername: String
    ): Result<Unit> = runCatching {
        val targetKey = toUsername.trim().lowercase()
        require(targetKey.isNotEmpty()) { "Enter a username" }

        val targetSnap = db.collection(FirebasePaths.USERNAMES).document(targetKey).get().await()
        val toUid = targetSnap.getString("uid") ?: error("User not found")
        val resolvedUsername = targetSnap.getString("username") ?: toUsername.trim()
        require(toUid != fromUid) { "You can't add yourself" }

        val existingFriendship = findFriendshipId(fromUid, toUid)
        require(existingFriendship == null) { "You are already friends" }

        val pending = db.collection(FirebasePaths.FRIEND_REQUESTS)
            .whereEqualTo("fromUid", fromUid)
            .whereEqualTo("toUid", toUid)
            .whereEqualTo("status", "pending")
            .get()
            .await()
        require(pending.isEmpty) { "Request already pending" }

        val reverse = db.collection(FirebasePaths.FRIEND_REQUESTS)
            .whereEqualTo("fromUid", toUid)
            .whereEqualTo("toUid", fromUid)
            .whereEqualTo("status", "pending")
            .get()
            .await()
        if (!reverse.isEmpty) {
            acceptFriendRequest(reverse.documents.first().id, fromUid)
            return@runCatching
        }

        db.collection(FirebasePaths.FRIEND_REQUESTS).document().set(
            mapOf(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "fromUsername" to fromUsername,
                "toUsername" to resolvedUsername,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun acceptFriendRequest(requestId: String, currentUid: String): Result<Unit> =
        runCatching {
            val ref = db.collection(FirebasePaths.FRIEND_REQUESTS).document(requestId)
            val snap = ref.get().await()
            require(snap.exists()) { "Request not found" }
            require(snap.getString("toUid") == currentUid) { "Not your request" }

            val fromUid = snap.getString("fromUid") ?: error("Invalid request")
            val toUid = snap.getString("toUid") ?: error("Invalid request")
            var fromUsername = snap.getString("fromUsername").orEmpty()
            var toUsername = snap.getString("toUsername").orEmpty()

            if (fromUsername.isBlank() || toUsername.isBlank()) {
                val fromProfile = db.collection(FirebasePaths.USERS).document(fromUid).get().await()
                val toProfile = db.collection(FirebasePaths.USERS).document(toUid).get().await()
                if (fromUsername.isBlank()) {
                    fromUsername = fromProfile.getString("username").orEmpty()
                }
                if (toUsername.isBlank()) {
                    toUsername = toProfile.getString("username").orEmpty()
                }
            }

            ref.update("status", "accepted").await()

            val pair = sortedPair(fromUid, toUid)
            val friendshipId = "${pair.first}_${pair.second}"
            db.collection(FirebasePaths.FRIENDSHIPS).document(friendshipId).set(
                mapOf(
                    "userA" to pair.first,
                    "userB" to pair.second,
                    "createdAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            // Option A: friendship confirmed → open a DM thread for both users.
            ensureConversation(fromUid, toUid, fromUsername, toUsername)
        }

    suspend fun rejectFriendRequest(requestId: String, currentUid: String): Result<Unit> = runCatching {
        val ref = db.collection(FirebasePaths.FRIEND_REQUESTS).document(requestId)
        val snap = ref.get().await()
        require(snap.getString("toUid") == currentUid) { "Not your request" }
        ref.update("status", "rejected").await()
    }

    suspend fun ensureConversationsForFriends(uid: String, myUsername: String): Result<Int> =
        runCatching {
            val asA = db.collection(FirebasePaths.FRIENDSHIPS)
                .whereEqualTo("userA", uid)
                .get()
                .await()
                .documents
            val asB = db.collection(FirebasePaths.FRIENDSHIPS)
                .whereEqualTo("userB", uid)
                .get()
                .await()
                .documents

            val friendUids = (asA + asB).mapNotNull { doc ->
                val userA = doc.getString("userA") ?: return@mapNotNull null
                val userB = doc.getString("userB") ?: return@mapNotNull null
                when (uid) {
                    userA -> userB
                    userB -> userA
                    else -> null
                }
            }.distinct()

            var createdOrUpdated = 0
            for (friendUid in friendUids) {
                val friendProfile = db.collection(FirebasePaths.USERS).document(friendUid).get().await()
                val friendUsername = friendProfile.getString("username") ?: "User"
                ensureConversation(uid, friendUid, myUsername, friendUsername)
                createdOrUpdated++
            }
            createdOrUpdated
        }

    fun observeConversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        fun emitFromSnap(snap: com.google.firebase.firestore.QuerySnapshot?) {
            val list = snap?.documents
                ?.mapNotNull { it.toConversation() }
                .orEmpty()
                .sortedByDescending { it.updatedAt }
            android.util.Log.i(
                "SocialRepository",
                "observeConversations uid=$uid count=${list.size} ids=${list.map { it.id }}"
            )
            trySend(list)
        }

        val ordered = db.collection(FirebasePaths.CONVERSATIONS)
            .whereArrayContains("participantIds", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        var fallbackReg: com.google.firebase.firestore.ListenerRegistration? = null
        val orderedReg = ordered.addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e(
                    "SocialRepository",
                    "ordered observeConversations failed, falling back. ${error.message}",
                    error
                )
                if (fallbackReg == null) {
                    fallbackReg = db.collection(FirebasePaths.CONVERSATIONS)
                        .whereArrayContains("participantIds", uid)
                        .addSnapshotListener { fbSnap, fbError ->
                            if (fbError != null) {
                                android.util.Log.e(
                                    "SocialRepository",
                                    "fallback observeConversations failed",
                                    fbError
                                )
                                trySend(emptyList())
                                return@addSnapshotListener
                            }
                            emitFromSnap(fbSnap)
                        }
                }
                return@addSnapshotListener
            }
            emitFromSnap(snap)
        }
        awaitClose {
            orderedReg.remove()
            fallbackReg?.remove()
        }
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection(FirebasePaths.CONVERSATIONS)
            .document(conversationId)
            .collection(FirebasePaths.MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toChatMessage() }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendText(conversationId: String, senderId: String, text: String): Result<Unit> =
        runCatching {
            val clean = text.trim()
            require(clean.isNotEmpty()) { "Message is empty" }
            writeMessage(
                conversationId = conversationId,
                senderId = senderId,
                type = MessageType.TEXT,
                text = clean,
                preview = clean
            )
        }

    suspend fun sendImage(conversationId: String, senderId: String, uri: Uri): Result<Unit> =
        runCatching {
            val path = "chat/$conversationId/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(path)
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            writeMessage(
                conversationId = conversationId,
                senderId = senderId,
                type = MessageType.IMAGE,
                imageUrl = url,
                preview = "Photo"
            )
        }

    suspend fun sendArticle(
        conversationId: String,
        senderId: String,
        articleId: String,
        articleTitle: String
    ): Result<Unit> = runCatching {
        writeMessage(
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.ARTICLE,
            articleId = articleId,
            articleTitle = articleTitle,
            text = articleTitle,
            preview = "Article: $articleTitle"
        )
        ArticleRepository(db).recordShare(articleId).onFailure { err ->
            android.util.Log.w("SocialRepository", "shareCount bump failed for $articleId", err)
        }
    }

    suspend fun getOrCreateConversation(
        uid: String,
        username: String,
        otherUid: String,
        otherUsername: String
    ): String = ensureConversation(uid, otherUid, username, otherUsername)

    private suspend fun writeMessage(
        conversationId: String,
        senderId: String,
        type: MessageType,
        text: String = "",
        imageUrl: String = "",
        articleId: String = "",
        articleTitle: String = "",
        preview: String
    ) {
        val now = System.currentTimeMillis()
        val convo = db.collection(FirebasePaths.CONVERSATIONS).document(conversationId)
        convo.collection(FirebasePaths.MESSAGES).document().set(
            mapOf(
                "senderId" to senderId,
                "type" to type.name,
                "text" to text,
                "imageUrl" to imageUrl,
                "articleId" to articleId,
                "articleTitle" to articleTitle,
                "createdAt" to now
            )
        ).await()
        convo.update(
            mapOf(
                "lastMessage" to preview,
                "updatedAt" to now
            )
        ).await()
    }

    private suspend fun ensureConversation(
        uidA: String,
        uidB: String,
        usernameA: String,
        usernameB: String
    ): String {
        val pair = sortedPair(uidA, uidB)
        val id = "${pair.first}_${pair.second}"
        val ref = db.collection(FirebasePaths.CONVERSATIONS).document(id)
        val now = System.currentTimeMillis()
        val usernames = mapOf(
            uidA to usernameA.ifBlank { "User" },
            uidB to usernameB.ifBlank { "User" }
        )
        val existing = ref.get().await()
        if (!existing.exists()) {
            ref.set(
                mapOf(
                    "participantIds" to listOf(pair.first, pair.second),
                    "participantUsernames" to usernames,
                    "lastMessage" to "You're friends — say hello",
                    "updatedAt" to now
                )
            ).await()
        } else {
            ref.set(
                mapOf(
                    "participantUsernames" to usernames,
                    "updatedAt" to (existing.getLong("updatedAt") ?: now),
                    "lastMessage" to (
                        existing.getString("lastMessage")
                            ?.takeIf { it.isNotBlank() }
                            ?: "You're friends — say hello"
                        )
                ),
                SetOptions.merge()
            ).await()
        }
        return id
    }

    private suspend fun findFriendshipId(uidA: String, uidB: String): String? {
        val pair = sortedPair(uidA, uidB)
        val id = "${pair.first}_${pair.second}"
        val snap = db.collection(FirebasePaths.FRIENDSHIPS).document(id).get().await()
        return if (snap.exists()) id else null
    }

    private fun sortedPair(a: String, b: String): Pair<String, String> =
        if (a < b) a to b else b to a
}

private fun com.google.firebase.firestore.DocumentSnapshot.toFriendRequest(): FriendRequest? {
    if (!exists()) return null
    return FriendRequest(
        id = id,
        fromUid = getString("fromUid").orEmpty(),
        toUid = getString("toUid").orEmpty(),
        fromUsername = getString("fromUsername").orEmpty(),
        toUsername = getString("toUsername").orEmpty(),
        status = getString("status") ?: "pending",
        createdAt = getLong("createdAt") ?: 0L
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toConversation(): Conversation? {
    if (!exists()) return null
    @Suppress("UNCHECKED_CAST")
    val usernamesRaw = get("participantUsernames") as? Map<*, *>
    val usernames = usernamesRaw
        ?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = v as? String ?: return@mapNotNull null
            key to value
        }
        ?.toMap()
        .orEmpty()
    val participantIds = when (val raw = get("participantIds")) {
        is List<*> -> raw.mapNotNull { it as? String }
        else -> emptyList()
    }
    val updatedAt = when (val value = get("updatedAt")) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
    return Conversation(
        id = id,
        participantIds = participantIds,
        participantUsernames = usernames,
        lastMessage = getString("lastMessage").orEmpty(),
        updatedAt = updatedAt
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toChatMessage(): ChatMessage? {
    if (!exists()) return null
    return ChatMessage(
        id = id,
        senderId = getString("senderId").orEmpty(),
        type = MessageType.fromValue(getString("type") ?: "TEXT"),
        text = getString("text").orEmpty(),
        imageUrl = getString("imageUrl").orEmpty(),
        articleId = getString("articleId").orEmpty(),
        articleTitle = getString("articleTitle").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L
    )
}
