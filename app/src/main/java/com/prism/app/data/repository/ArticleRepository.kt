package com.prism.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.prism.app.data.FirebasePaths
import com.prism.app.data.model.Article
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ArticleRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeByScope(scope: String): Flow<List<Article>> = callbackFlow {
        val reg = db.collection(FirebasePaths.ARTICLES)
            .whereEqualTo("scope", scope)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "observeByScope($scope) failed — create composite index if prompted", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toArticle() }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    fun observeByInterest(interest: String): Flow<List<Article>> = callbackFlow {
        val reg = db.collection(FirebasePaths.ARTICLES)
            .whereArrayContains("interests", interest)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "observeByInterest($interest) failed — create composite index if prompted", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toArticle() }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun getArticle(id: String): Article? =
        db.collection(FirebasePaths.ARTICLES).document(id).get().await().toArticle()

    fun observeLiked(articleId: String, uid: String): Flow<Boolean> = callbackFlow {
        val likeId = "${articleId}_$uid"
        val reg = db.collection(FirebasePaths.LIKES).document(likeId)
            .addSnapshotListener { snap, _ -> trySend(snap?.exists() == true) }
        awaitClose { reg.remove() }
    }

    suspend fun toggleLike(articleId: String, uid: String): Result<Boolean> = runCatching {
        val likeId = "${articleId}_$uid"
        val ref = db.collection(FirebasePaths.LIKES).document(likeId)
        val existing = ref.get().await()
        if (existing.exists()) {
            ref.delete().await()
            false
        } else {
            ref.set(
                mapOf(
                    "articleId" to articleId,
                    "uid" to uid,
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
            true
        }
    }

    suspend fun searchArticles(query: String, limit: Int = 20): List<Article> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val all = db.collection(FirebasePaths.ARTICLES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)
            .get()
            .await()
            .documents
            .mapNotNull { it.toArticle() }
        return all.filter {
            it.title.lowercase().contains(q) ||
                it.topic.lowercase().contains(q) ||
                it.source.lowercase().contains(q)
        }.take(limit)
    }

    companion object {
        private const val TAG = "ArticleRepository"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toArticle(): Article? {
    if (!exists()) return null
    @Suppress("UNCHECKED_CAST")
    val interestsRaw = get("interests")
    val interests = when (interestsRaw) {
        is List<*> -> interestsRaw.mapNotNull { it as? String }
        else -> emptyList()
    }
    val createdAt = when (val value = get("createdAt")) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
    return Article(
        id = id,
        title = getString("title").orEmpty(),
        description = getString("description").orEmpty(),
        body = getString("body").orEmpty(),
        source = getString("source").orEmpty(),
        topic = getString("topic").orEmpty(),
        bias = getString("bias") ?: "Neutral",
        scope = getString("scope")?.trim()?.lowercase() ?: "national",
        interests = interests,
        url = getString("url").orEmpty(),
        createdAt = createdAt
    )
}
