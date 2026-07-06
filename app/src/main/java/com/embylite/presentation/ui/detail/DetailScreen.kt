package com.embylite.presentation.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.embylite.data.local.TokenManager
import com.embylite.data.model.EmbyItem
import com.embylite.presentation.ui.common.BackScaffold
import com.embylite.presentation.ui.common.ErrorState
import com.embylite.presentation.ui.common.LoadingState
import com.embylite.utils.ImageUrlBuilder

@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onPlay: (String) -> Unit
) {
    val viewModel: DetailViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(itemId) { viewModel.loadItem(itemId) }

    BackScaffold(title = "详情", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is DetailState.Loading -> LoadingState()
                is DetailState.Error -> ErrorState(s.message)
                is DetailState.Success -> DetailContent(
                    item = s.item,
                    onTagClick = onTagClick,
                    onPersonClick = onPersonClick,
                    onPlay = { onPlay(s.item.Id ?: "") }
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    item: EmbyItem,
    onTagClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onPlay: () -> Unit
) {
    val server = TokenManager.getCachedServer() ?: ""
    val token = TokenManager.getCachedToken()
    val backdropUrl = ImageUrlBuilder.buildBackdropImageUrl(server, item.Id, item.ImageTags?.Backdrop, token)
    val primaryUrl = ImageUrlBuilder.buildImageUrl(server, item.Id, item.ImageTags?.Primary, token)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部海报区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            if (!backdropUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = item.Name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!primaryUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = primaryUrl,
                    contentDescription = item.Name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
            // 播放按钮
            Button(
                onClick = onPlay,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("播放")
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = item.Name ?: "",
                style = MaterialTheme.typography.headlineSmall
            )

            // 年份 / 评分
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item.ProductionYear?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item.CommunityRating?.let { Text("★ ${"%.1f".format(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                item.OfficialRating?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            // 简介
            if (!item.Overview.isNullOrEmpty()) {
                Text("简介", style = MaterialTheme.typography.titleMedium)
                Text(item.Overview, style = MaterialTheme.typography.bodyMedium)
            }

            // 标签
            if (!item.Tags.isNullOrEmpty()) {
                Text("标签", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.Tags.take(10).forEach { tag ->
                        AssistChip(onClick = { onTagClick(tag) }, label = { Text(tag) })
                    }
                }
            }

            // 演员
            val actors = item.People?.filter { it.Type == "Actor" || it.Role == "Actor" }
            if (!actors.isNullOrEmpty()) {
                Text("演员", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actors.take(15).forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val pid = person.Id ?: return@clickable
                                    onPersonClick(pid, person.Name ?: "")
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val personImg = ImageUrlBuilder.buildPersonImageUrl(server, person.Id, person.PrimaryImageTag, token)
                            if (!personImg.isNullOrEmpty()) {
                                AsyncImage(
                                    model = personImg,
                                    contentDescription = person.Name,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(person.Name ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                person.Role?.let { Text("饰 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
