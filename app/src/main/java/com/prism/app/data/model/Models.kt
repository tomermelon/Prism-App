package com.prism.app.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val country: String = "",
    val createdAt: Long = 0L,
    val interests: List<String> = emptyList(),
    val showLocal: Boolean = true,
    val showNational: Boolean = true,
    val showInternational: Boolean = true,
    val onboardingComplete: Boolean = false
)

enum class NewsScope(val label: String, val firestoreValue: String) {
    LOCAL("Local", "local"),
    NATIONAL("National", "national"),
    INTERNATIONAL("International", "international");

    companion object {
        fun fromValue(value: String): NewsScope =
            entries.firstOrNull { it.firestoreValue == value } ?: NATIONAL
    }
}

data class Article(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val body: String = "",
    val source: String = "",
    val topic: String = "",
    val bias: String = "Neutral",
    val scope: String = NewsScope.NATIONAL.firestoreValue,
    val interests: List<String> = emptyList(),
    val url: String = "",
    val createdAt: Long = 0L,
    val likeCount: Int = 0,
    val shareCount: Int = 0
)

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val fromUsername: String = "",
    val toUsername: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L
)

data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantUsernames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val updatedAt: Long = 0L
)

enum class MessageType {
    TEXT, IMAGE, ARTICLE;

    companion object {
        fun fromValue(value: String): MessageType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TEXT
    }
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val type: MessageType = MessageType.TEXT,
    val text: String = "",
    val imageUrl: String = "",
    val articleId: String = "",
    val articleTitle: String = "",
    val createdAt: Long = 0L
)

data class ArticleComment(
    val id: String = "",
    val articleId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val text: String = "",
    val parentId: String = "",
    val likeCount: Int = 0,
    val createdAt: Long = 0L
)

object InterestCatalog {
    val all: List<String> = listOf(
        "Local",
        "National",
        "International",
        "Politics",
        "Climate Change",
        "AI",
        "Technology",
        "Wars & Conflicts",
        "Economy",
        "Health",
        "Abortion",
        "Immigration",
        "Education",
        "Science"
    )
}
