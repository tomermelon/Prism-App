package com.prism.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.prism.app.data.FirebasePaths
import com.prism.app.data.model.ArticleComment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun commentsRef(articleId: String) =
        db.collection(FirebasePaths.ARTICLES)
            .document(articleId)
            .collection(FirebasePaths.COMMENTS)

    fun observeComments(articleId: String): Flow<List<ArticleComment>> = callbackFlow {
        val reg = commentsRef(articleId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "observeComments failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents
                        ?.mapNotNull { it.toArticleComment(articleId) }
                        .orEmpty()
                )
            }
        awaitClose { reg.remove() }
    }

    fun observeLiked(commentId: String, uid: String): Flow<Boolean> = callbackFlow {
        val likeId = "${commentId}_$uid"
        val reg = db.collection(FirebasePaths.COMMENT_LIKES).document(likeId)
            .addSnapshotListener { snap, _ -> trySend(snap?.exists() == true) }
        awaitClose { reg.remove() }
    }

    suspend fun postComment(
        articleId: String,
        authorId: String,
        authorUsername: String,
        text: String,
        parentId: String = ""
    ): Result<String> = runCatching {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Comment is empty" }
        require(authorUsername.isNotBlank()) { "Missing username" }

        val ref = commentsRef(articleId).document()
        ref.set(
            mapOf(
                "authorId" to authorId,
                "authorUsername" to authorUsername,
                "text" to clean,
                "parentId" to parentId,
                "likeCount" to 0,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        ref.id
    }

    suspend fun toggleLike(
        articleId: String,
        commentId: String,
        uid: String
    ): Result<Boolean> = runCatching {
        val likeId = "${commentId}_$uid"
        val likeRef = db.collection(FirebasePaths.COMMENT_LIKES).document(likeId)
        val commentRef = commentsRef(articleId).document(commentId)

        db.runTransaction { tx ->
            val likeSnap = tx.get(likeRef)
            val commentSnap = tx.get(commentRef)
            require(commentSnap.exists()) { "Comment not found" }

            if (likeSnap.exists()) {
                tx.delete(likeRef)
                tx.update(commentRef, "likeCount", FieldValue.increment(-1))
                false
            } else {
                tx.set(
                    likeRef,
                    mapOf(
                        "commentId" to commentId,
                        "articleId" to articleId,
                        "uid" to uid,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                tx.update(commentRef, "likeCount", FieldValue.increment(1))
                true
            }
        }.await()
    }

    companion object {
        private const val TAG = "CommentRepository"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toArticleComment(
    articleId: String
): ArticleComment? {
    if (!exists()) return null
    val likeCount = when (val value = get("likeCount")) {
        is Long -> value.toInt()
        is Number -> value.toInt()
        else -> 0
    }.coerceAtLeast(0)
    return ArticleComment(
        id = id,
        articleId = articleId,
        authorId = getString("authorId").orEmpty(),
        authorUsername = getString("authorUsername").orEmpty(),
        text = getString("text").orEmpty(),
        parentId = getString("parentId").orEmpty(),
        likeCount = likeCount,
        createdAt = getLong("createdAt") ?: 0L
    )
}
