package dev.plexlite.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.formatDuration
import dev.plexlite.tv.ui.theme.Background
import kotlinx.coroutines.delay

private const val HERO_DEBOUNCE_MS = 250L

@Composable
fun <T> rememberDebounced(value: T, delayMs: Long = HERO_DEBOUNCE_MS): T {
    var settled by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (settled != null) delay(delayMs)
        settled = value
    }
    return settled
}

fun Metadata.backdropPath(): String? = when (type) {
    "episode" -> grandparentArt ?: art ?: thumb
    "season" -> art ?: grandparentArt
    else -> art ?: thumb
}

@Composable
fun BoxScope.HeroBackdrop(item: Metadata?, alpha: Float = 1f, widthFraction: Float = 0.72f, heightFraction: Float = 0.72f) {
    val url = item?.let { AppGraph.server?.photoUrl(it.backdropPath(), 960, 540) }
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .fillMaxWidth(widthFraction)
            .fillMaxHeight(heightFraction),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(300).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Background, 0.45f to Background.copy(alpha = 0.55f), 1f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Background),
            ),
        )
    }
}

@Composable
fun HeroInfo(item: Metadata?, height: Dp) {
    Column(
        Modifier
            .padding(horizontal = 48.dp)
            .fillMaxWidth(0.55f)
            .height(height),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        if (item == null) return@Column
        val isEpisode = item.type == "episode"
        Text(
            if (isEpisode) item.grandparentTitle ?: item.title else if (item.type == "season") item.parentTitle ?: item.title else item.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isEpisode) {
            Text(
                listOfNotNull(item.episodeLabel, item.title).joinToString("  ·  "),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val meta = listOfNotNull(
            item.year?.toString(),
            item.duration?.takeIf { item.type == "movie" || isEpisode }?.let { formatDuration(it) },
            item.childCount?.takeIf { item.type == "show" }?.let { AppGraph.plural(R.plurals.detail_seasons, it) },
            item.genres.take(2).joinToString(", ") { it.tag }.ifEmpty { null },
            item.contentRating,
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DebouncedHero(source: State<Metadata?>, delayMs: Long = HERO_DEBOUNCE_MS, content: @Composable (Metadata?) -> Unit) {
    content(rememberDebounced(source.value, delayMs))
}
