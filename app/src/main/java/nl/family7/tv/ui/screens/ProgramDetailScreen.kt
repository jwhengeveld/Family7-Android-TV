package nl.family7.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.family7.tv.data.EpisodeItem
import nl.family7.tv.data.Family7VideoRepository
import nl.family7.tv.data.ProgramDetail
import nl.family7.tv.data.ProgramItem
import nl.family7.tv.ui.components.TVButton
import nl.family7.tv.ui.components.TVEpisodeCard
import nl.family7.tv.ui.theme.Family7BlueDark
import nl.family7.tv.ui.theme.Family7Orange
import nl.family7.tv.ui.theme.TextPrimary
import nl.family7.tv.ui.theme.TextSecondary

@Composable
fun ProgramDetailScreen(
    programItem: ProgramItem,
    videoRepo: Family7VideoRepository,
    onPlayEpisode: (EpisodeItem, ProgramDetail) -> Unit,
    onBack: () -> Unit
) {
    var programDetail by remember { mutableStateOf<ProgramDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(programItem.slug) {
        isLoading = true
        val res = videoRepo.getProgramDetail(programItem.slug)
        res.onSuccess {
            programDetail = it
            isLoading = false
        }.onFailure {
            errorMessage = it.message
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Family7BlueDark)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Family7Orange)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Fout bij laden programma: $errorMessage", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    TVButton(text = "TERUG", onClick = onBack)
                }
            }
        } else if (programDetail != null) {
            val detail = programDetail!!

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // Program Header Hero
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        AsyncImage(
                            model = detail.posterUrl.ifEmpty { programItem.thumbnailUrl },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dual Gradients
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Family7BlueDark.copy(alpha = 0.95f),
                                            Family7BlueDark.copy(alpha = 0.65f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Family7BlueDark),
                                        startY = 180f
                                    )
                                )
                        )

                        // Text & Buttons
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(horizontal = 48.dp)
                                .fillMaxWidth(0.7f)
                        ) {
                            TVButton(
                                text = "TERUG",
                                onClick = onBack,
                                isPrimary = false,
                                leadingIcon = {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = detail.title,
                                color = TextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (detail.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = detail.description,
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 3,
                                    lineHeight = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val firstEpisode = detail.seasons.firstOrNull()?.episodes?.firstOrNull()
                            if (firstEpisode != null) {
                                TVButton(
                                    text = "AFSPELEN (Afl. ${firstEpisode.episodeNumber})",
                                    onClick = { onPlayEpisode(firstEpisode, detail) },
                                    leadingIcon = {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                    }
                                )
                            }
                        }
                    }
                }

                // Episodes Rows per Season
                items(detail.seasons) { season ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = season.title,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 42.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(season.episodes) { episode ->
                                TVEpisodeCard(
                                    episode = episode,
                                    onClick = { onPlayEpisode(episode, detail) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
