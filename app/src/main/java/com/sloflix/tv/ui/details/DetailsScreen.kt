package com.sloflix.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.sloflix.tv.domain.model.EpisodeSummary
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.components.PosterCard
import com.sloflix.tv.ui.components.PosterCardSlotHeight
import com.sloflix.tv.ui.components.SloflixLogo
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.i18n.LocalStrings

private val Background = Color(0xFF090C12)
private val Accent = Color(0xFFE50913)
private val SecondaryText = Color(0xFFC5CBD6)
private val ChipIdle = Color(0xFF1B2230)

@Composable
fun DetailsScreen(
    state: UiState<DetailsContent>,
    onRetry: () -> Unit,
    onPlay: (titleId: String, startPositionMs: Long?) -> Unit,
    onSeasonSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        when (state) {
            UiState.Loading -> DetailsLoading()
            is UiState.Error -> DetailsError(state.message, onRetry)
            is UiState.Ready -> DetailsReady(
                content = state.value,
                onPlay = onPlay,
                onSeasonSelected = onSeasonSelected,
            )
        }
    }
}

@Composable
private fun DetailsReady(
    content: DetailsContent,
    onPlay: (titleId: String, startPositionMs: Long?) -> Unit,
    onSeasonSelected: (Int) -> Unit,
) {
    val primaryFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val firstEpisodeId = content.episodes.firstOrNull()?.id

    LaunchedEffect(
        content.requestId,
        content.isSeries,
        content.episodesLoading,
        firstEpisodeId,
        content.title.seasons.firstOrNull(),
    ) {
        val target = when {
            !content.isSeries -> primaryFocus
            firstEpisodeId != null -> episodeFocus
            content.title.seasons.isNotEmpty() -> primaryFocus
            else -> return@LaunchedEffect
        }
        // Lazy rows may not have attached the requester on the first frame.
        kotlinx.coroutines.android.awaitFrame()
        runCatching { target.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().testTag(TestTags.DetailsRoot)) {
        RemoteImage(
            url = content.title.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Background,
                        0.58f to Background.copy(alpha = 0.88f),
                        1f to Background.copy(alpha = 0.34f),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Background,
                    ),
                ),
        )

        if (content.isSeries) {
            SeriesDetails(
                content = content,
                seasonFocus = primaryFocus,
                episodeFocus = episodeFocus,
                onPlay = onPlay,
                onSeasonSelected = onSeasonSelected,
            )
        } else {
            MovieDetails(
                content = content,
                primaryFocus = primaryFocus,
                onPlay = onPlay,
            )
        }
    }
}

@Composable
private fun MovieDetails(
    content: DetailsContent,
    primaryFocus: FocusRequester,
    onPlay: (titleId: String, startPositionMs: Long?) -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(
            url = content.title.posterUrl,
            title = content.title.name,
        )
        Spacer(Modifier.width(42.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            TitleHeader(content)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = {
                        onPlay(
                            content.title.id,
                            content.resumePositionMs.takeIf { content.canResume },
                        )
                    },
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .testTag(TestTags.DetailsPlay),
                ) {
                    Text(if (content.canResume) strings.resume else strings.play)
                }
                if (content.canResume) {
                    Button(onClick = { onPlay(content.title.id, null) }) {
                        Text(strings.playFromBeginning)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesDetails(
    content: DetailsContent,
    seasonFocus: FocusRequester,
    episodeFocus: FocusRequester,
    onPlay: (titleId: String, startPositionMs: Long?) -> Unit,
    onSeasonSelected: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 40.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.padding(horizontal = 56.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Poster(
                    url = content.title.posterUrl,
                    title = content.title.name,
                    compact = true,
                )
                Spacer(Modifier.width(36.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TitleHeader(content, descriptionMaxLines = 3)
                }
            }
        }

        if (content.title.seasons.isNotEmpty()) {
            item {
                Text(
                    text = strings.seasons,
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText,
                    modifier = Modifier.padding(horizontal = 56.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag(TestTags.DetailsSeasonChip),
                ) {
                    items(content.title.seasons, key = { it }) { season ->
                        val isFirst = season == content.title.seasons.first()
                        SeasonChip(
                            label = "${strings.season} $season",
                            selected = content.selectedSeason == season,
                            onClick = { onSeasonSelected(season) },
                            modifier = if (isFirst) {
                                Modifier.focusRequester(seasonFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = content.selectedSeason?.let { "${strings.episodes} · ${strings.season} $it" }
                    ?: strings.episodes,
                style = MaterialTheme.typography.titleMedium,
                color = SecondaryText,
                modifier = Modifier.padding(horizontal = 56.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                content.episodesLoading -> {
                    Text(
                        text = strings.loadingEpisodes,
                        color = SecondaryText,
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                }
                content.episodes.isEmpty() -> {
                    Text(
                        text = strings.noEpisodesInSeason,
                        color = SecondaryText,
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                }
                else -> {
                    LazyRow(
                        modifier = Modifier
                            .height(PosterCardSlotHeight)
                            .focusGroup()
                            .testTag(TestTags.DetailsEpisodes),
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(
                            items = content.episodes,
                            key = { _, episode -> episode.id },
                        ) { index, episode ->
                            PosterCard(
                                title = episode.toTitleSummary(),
                                onClick = { onPlay(episode.id, null) },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(episodeFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleHeader(
    content: DetailsContent,
    descriptionMaxLines: Int = 5,
) {
    val strings = LocalStrings.current
    SloflixLogo(markSize = 22.dp, textSize = 18.sp, showMark = false)
    Spacer(Modifier.height(10.dp))
    Text(
        text = content.title.name,
        style = MaterialTheme.typography.displayLarge,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(12.dp))
    val metadata = buildList {
        content.title.year?.let { add(it.toString()) }
        if (content.isSeries) add(strings.seriesLabel)
        content.title.duration?.let { add(it) }
        content.title.ratingLabel?.let { add("★ $it") }
        addAll(content.title.genres)
    }.joinToString("  •  ")
    if (metadata.isNotEmpty()) {
        Text(
            text = metadata,
            style = MaterialTheme.typography.titleMedium,
            color = SecondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
    }
    Text(
        text = content.title.description,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        lineHeight = 25.sp,
        maxLines = descriptionMaxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(0.92f),
    )
}

@Composable
private fun SeasonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent else ChipIdle,
            focusedContainerColor = if (selected) Accent else Color(0xFF2A3344),
            pressedContainerColor = Accent.copy(alpha = 0.85f),
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private fun EpisodeSummary.toTitleSummary() = TitleSummary(
    id = id,
    name = "$episodeIndex. $name",
    posterUrl = posterUrl,
)

@Composable
private fun Poster(
    url: String?,
    title: String,
    compact: Boolean = false,
) {
    val width = if (compact) 160.dp else 220.dp
    val height = if (compact) 238.dp else 326.dp
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF252C38)),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = url,
            contentDescription = "$title poster",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (url == null) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFF737D8E),
            )
        }
    }
}

@Composable
private fun DetailsLoading() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SloflixLogo(markSize = 28.dp, textSize = 24.sp, showMark = false)
        Spacer(Modifier.height(12.dp))
        Text(
            text = strings.loadingTitleDetails,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun DetailsError(
    message: String,
    onRetry: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.titleCouldNotLoad,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = SecondaryText,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(strings.retry)
        }
    }
}

@Composable
private fun RemoteImage(
    url: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    if (url == null) return
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
