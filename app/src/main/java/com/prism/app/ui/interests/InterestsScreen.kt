package com.prism.app.ui.interests

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.app.data.model.Article
import com.prism.app.data.model.UserProfile
import com.prism.app.data.repository.ArticleRepository
import com.prism.app.ui.components.ArticleCard
import com.prism.app.ui.components.SectionHeader
import kotlinx.coroutines.flow.collectLatest

@Composable
fun InterestsScreen(
    profile: UserProfile?,
    onOpenArticle: (String) -> Unit,
    articleRepository: ArticleRepository = remember { ArticleRepository() }
) {
    val interests = profile?.interests.orEmpty()
    val articlesByInterest = remember { mutableStateMapOf<String, List<Article>>() }

    interests.forEach { interest ->
        LaunchedEffect(interest) {
            articleRepository.observeByInterest(interest).collectLatest { list ->
                articlesByInterest[interest] = list
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "For your interests",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Text(
            text = "Each row mixes coverage around topics you chose. Edit topics in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (interests.isEmpty()) {
            Text(
                text = "No interests yet. Add some in Settings.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            interests.forEach { interest ->
                SectionHeader(interest)
                val articles = articlesByInterest[interest].orEmpty()
                if (articles.isEmpty()) {
                    Text(
                        text = "No articles tagged “$interest” yet.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        articles.forEach { article ->
                            ArticleCard(article = article, onClick = { onOpenArticle(article.id) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
