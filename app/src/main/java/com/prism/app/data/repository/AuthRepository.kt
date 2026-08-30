package com.prism.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.prism.app.data.FirebasePaths
import com.prism.app.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUp(
        username: String,
        email: String,
        password: String,
        country: String
    ): Result<Unit> = runCatching {
        val cleanUsername = username.trim()
        require(cleanUsername.length in 3..24) { "Username must be 3–24 characters" }
        require(cleanUsername.matches(Regex("^[A-Za-z0-9_]+$"))) {
            "Username can only use letters, numbers, and underscores"
        }

        // Auth first — Firestore rules require a signed-in user for profile/username writes.
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Account created but user missing")
        val uid = user.uid

        val usernameKey = cleanUsername.lowercase()
        val usernameRef = db.collection(FirebasePaths.USERNAMES).document(usernameKey)
        val userRef = db.collection(FirebasePaths.USERS).document(uid)

        val profile = mapOf(
            "uid" to uid,
            "username" to cleanUsername,
            "email" to email.trim(),
            "country" to country.trim(),
            "createdAt" to System.currentTimeMillis(),
            "interests" to emptyList<String>(),
            "showLocal" to true,
            "showNational" to true,
            "showInternational" to true,
            "onboardingComplete" to false
        )

        try {
            db.runTransaction { tx ->
                val existing = tx.get(usernameRef)
                if (existing.exists()) error("Username is already taken")
                tx.set(usernameRef, mapOf("uid" to uid, "username" to cleanUsername))
                tx.set(userRef, profile)
                null
            }.await()
        } catch (e: Exception) {
            user.delete().await()
            throw e
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    fun signOut() = auth.signOut()

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        Log.i(TAG, "observeProfile: attaching uid=$uid")
        val reg = db.collection(FirebasePaths.USERS).document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "observeProfile failed uid=$uid", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                Log.i(TAG, "observeProfile: emit exists=${snap?.exists()}")
                trySend(snap?.toUserProfile())
            }
        awaitClose {
            Log.i(TAG, "observeProfile: removing uid=$uid")
            reg.remove()
        }
    }

    suspend fun getProfile(uid: String): UserProfile? =
        db.collection(FirebasePaths.USERS).document(uid).get().await().toUserProfile()

    suspend fun completeOnboarding(uid: String, interests: List<String>): Result<Unit> =
        runCatching {
            db.collection(FirebasePaths.USERS).document(uid).set(
                mapOf(
                    "interests" to interests,
                    "onboardingComplete" to true
                ),
                SetOptions.merge()
            ).await()
        }

    suspend fun updateInterests(uid: String, interests: List<String>): Result<Unit> =
        runCatching {
            db.collection(FirebasePaths.USERS).document(uid)
                .update("interests", interests).await()
        }

    suspend fun updateSectionToggles(
        uid: String,
        showLocal: Boolean,
        showNational: Boolean,
        showInternational: Boolean
    ): Result<Unit> = runCatching {
        db.collection(FirebasePaths.USERS).document(uid).update(
            mapOf(
                "showLocal" to showLocal,
                "showNational" to showNational,
                "showInternational" to showInternational
            )
        ).await()
    }

    suspend fun findUidByUsername(username: String): String? {
        val snap = db.collection(FirebasePaths.USERNAMES)
            .document(username.trim().lowercase())
            .get()
            .await()
        return snap.getString("uid")
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(): UserProfile? {
    if (!exists()) return null
    @Suppress("UNCHECKED_CAST")
    return UserProfile(
        uid = getString("uid") ?: id,
        username = getString("username").orEmpty(),
        email = getString("email").orEmpty(),
        country = getString("country").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        interests = (get("interests") as? List<String>).orEmpty(),
        showLocal = getBoolean("showLocal") ?: true,
        showNational = getBoolean("showNational") ?: true,
        showInternational = getBoolean("showInternational") ?: true,
        onboardingComplete = getBoolean("onboardingComplete") ?: false
    )
}
