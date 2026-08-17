package com.prism.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.app.data.model.InterestCatalog
import com.prism.app.data.model.UserProfile
import com.prism.app.data.repository.AuthRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: UserProfile?,
    onSignedOut: () -> Unit,
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    var profileOpen by remember { mutableStateOf(true) }
    var interestsOpen by remember { mutableStateOf(true) }
    var selected by remember(profile?.interests) {
        mutableStateOf(profile?.interests?.toSet().orEmpty())
    }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        DropdownHeader("User info", profileOpen) { profileOpen = !profileOpen }
        AnimatedVisibility(profileOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Username: ${profile?.username ?: "—"}")
                Text("Email: ${profile?.email ?: "—"}")
                Text("Country: ${profile?.country ?: "—"}")
                Text(
                    "Joined: " + if (profile != null && profile.createdAt > 0) {
                        DateFormat.getDateInstance().format(Date(profile.createdAt))
                    } else "—"
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        DropdownHeader("Interests", interestsOpen) { interestsOpen = !interestsOpen }
        AnimatedVisibility(interestsOpen) {
            Column {
                Text(
                    "Check topics you want on your Interests feed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InterestCatalog.all.forEach { interest ->
                        FilterChip(
                            selected = interest in selected,
                            onClick = {
                                selected =
                                    if (interest in selected) selected - interest else selected + interest
                            },
                            label = { Text(interest) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val uid = profile?.uid ?: return@Button
                        scope.launch {
                            val result = authRepository.updateInterests(uid, selected.toList())
                            message = result.fold(
                                onSuccess = { "Interests saved" },
                                onFailure = { it.message ?: "Could not save" }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save interests")
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                authRepository.signOut()
                onSignedOut()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun DropdownHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Icon(
            imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null
        )
    }
}
