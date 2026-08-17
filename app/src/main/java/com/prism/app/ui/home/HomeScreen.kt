package com.prism.app.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.app.data.model.Article
import com.prism.app.data.model.NewsScope
import com.prism.app.data.model.UserProfile
import com.prism.app.ui.components.ArticleCard
import com.prism.app.ui.components.SectionHeader

@Composable
fun HomeScreen(
    profile: UserProfile?,
    onOpenArticle: (String) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(key = profile?.uid ?: "signed_out")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile?.uid, profile?.showLocal, profile?.showNational, profile?.showInternational) {
        viewModel.bindProfile(profile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Today's biggest stories",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Text(
            text = "Toggle Local, National, and International. Bias labels help you read across viewpoints.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.showLocal,
                onClick = { viewModel.setSectionVisible(NewsScope.LOCAL, !state.showLocal) },
                label = { Text("Local") }
            )
            FilterChip(
                selected = state.showNational,
                onClick = { viewModel.setSectionVisible(NewsScope.NATIONAL, !state.showNational) },
                label = { Text("National") }
            )
            FilterChip(
                selected = state.showInternational,
                onClick = {
                    viewModel.setSectionVisible(NewsScope.INTERNATIONAL, !state.showInternational)
                },
                label = { Text("International") }
            )
        }

        if (state.showLocal) {
            ArticleRow("Local", state.local, onOpenArticle)
        }
        if (state.showNational) {
            ArticleRow("National", state.national, onOpenArticle)
        }
        if (state.showInternational) {
            ArticleRow("International", state.international, onOpenArticle)
        }
    }
}

@Composable
private fun ArticleRow(
    title: String,
    articles: List<Article>,
    onOpenArticle: (String) -> Unit
) {
    SectionHeader(title)
    if (articles.isEmpty()) {
        Text(
            text = "No articles yet — seed Firestore or check back soon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    } else {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            articles.forEach { article ->
                ArticleCard(
                    article = article,
                    onClick = { onOpenArticle(article.id) }
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
