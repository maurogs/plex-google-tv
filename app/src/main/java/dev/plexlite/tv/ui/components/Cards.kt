package dev.plexlite.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.theme.Accent

enum class CardStyle { POSTER, WIDE }

fun Metadata.cardImage(style: CardStyle): String? = when (style) {
    CardStyle.POSTER -> when (type) {
        "episode" -> grandparentThumb ?: parentThumb ?: thumb
        "season" -> thumb ?: parentThumb
        else -> thumb
    }
    CardStyle.WIDE -> when (type) {
        "episode" -> thumb ?: art
        else -> art ?: thumb
    }
}

fun Metadata.cardTitle(): String = when (type) {
    "episode" -> grandparentTitle ?: title
    "season" -> parentTitle ?: title
    else -> title
}

fun Metadata.cardSubtitle(): String? = when (type) {
    "episode" -> listOfNotNull(episodeLabel, title).joinToString(" · ")
    "season" -> title
    "show" -> year?.toString()
    else -> year?.toString()
}

@Composable
fun MediaCard(
    item: Metadata,
    style: CardStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = if (style == CardStyle.POSTER) 140.dp else 260.dp,
    title: String = item.cardTitle(),
    subtitle: String? = item.cardSubtitle(),
    onFocused: () -> Unit = {},
) {
    val server = AppGraph.server
    val ratio = if (style == CardStyle.POSTER) 2f / 3f else 16f / 9f
    val imageUrl = if (style == CardStyle.POSTER) {
        server?.photoUrl(item.cardImage(style), 300, 450)
    } else {
        server?.photoUrl(item.cardImage(style), 520, 293)
    }
    Column(modifier = modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .onFocusChanged { if (it.isFocused) onFocused() },
            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                    shape = RoundedCornerShape(8.dp),
                ),
            ),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item.progress?.let { progress ->
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(Accent),
                        )
                    }
                }
                val unwatched = item.unwatchedLeaves
                when {
                    item.isWatched && item.progress == null -> Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    unwatched != null -> Text(
                        unwatched.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<Metadata>,
    style: CardStyle,
    onClick: (Metadata) -> Unit,
    modifier: Modifier = Modifier,
    cardTitle: (Metadata) -> String = { it.cardTitle() },
    cardSubtitle: (Metadata) -> String? = { it.cardSubtitle() },
    itemModifier: (Metadata) -> Modifier = { Modifier },
    onFocused: (Metadata) -> Unit = {},
    cardWidth: Dp? = null,
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items.distinctBy { it.ratingKey }, key = { it.ratingKey }) { item ->
                MediaCard(
                    item = item,
                    style = style,
                    onClick = { onClick(item) },
                    width = cardWidth ?: if (style == CardStyle.POSTER) 140.dp else 260.dp,
                    title = cardTitle(item),
                    subtitle = cardSubtitle(item),
                    modifier = itemModifier(item),
                    onFocused = { onFocused(item) },
                )
            }
        }
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "$minutes min"
}

fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
