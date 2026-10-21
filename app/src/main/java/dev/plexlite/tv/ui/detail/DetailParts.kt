package dev.plexlite.tv.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.data.Part
import dev.plexlite.tv.data.Role
import dev.plexlite.tv.data.Stream
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.MediaCard
import dev.plexlite.tv.ui.components.MenuPopup
import dev.plexlite.tv.ui.components.PopupRow
import dev.plexlite.tv.ui.components.rememberPopupState
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor
import androidx.compose.foundation.BorderStroke

private data class RatingBadge(val label: String, val value: String, val background: Color, val content: Color)

@Composable
fun RatingsRow(item: Metadata) {
    val badges = item.ratings.mapNotNull { rating ->
        val image = rating.image ?: return@mapNotNull null
        val value = rating.value ?: return@mapNotNull null
        when {
            image.startsWith("imdb://") -> RatingBadge("IMDb", "%.1f".format(value), Color(0xFFF5C518), Color.Black)
            image.startsWith("rottentomatoes://") && rating.type == "critic" ->
                RatingBadge("🍅", "${(value * 10).toInt()}%", Color(0xFFFA320A), Color.White)
            image.startsWith("rottentomatoes://") -> RatingBadge("🍿", "${(value * 10).toInt()}%", Color(0xFFFA320A), Color.White)
            image.startsWith("themoviedb://") -> RatingBadge("TMDB", "%.1f".format(value), Color(0xFF01B4E4), Color.Black)
            else -> null
        }
    }.distinctBy { it.label }
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        badges.forEach { badge ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    badge.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = badge.content,
                    modifier = Modifier
                        .background(badge.background, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(badge.value, style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
        }
    }
}

@Composable
fun StreamChips(item: Metadata, onSelectAudio: (Part, Stream) -> Unit, onSelectSubtitle: (Part, Stream?) -> Unit) {
    val media = item.media.firstOrNull() ?: return
    val part = media.parts.firstOrNull() ?: return
    val audioStreams = part.streams.filter { it.streamType == Stream.AUDIO }
    val subtitleStreams = part.streams.filter { it.streamType == Stream.SUBTITLE }
    val audio = audioStreams.firstOrNull { it.selected } ?: audioStreams.firstOrNull()
    val subtitle = subtitleStreams.firstOrNull { it.selected }
    val none = stringResource(R.string.none)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val video = listOfNotNull(
            media.videoResolution?.let { if (it.all(Char::isDigit)) "${it}p" else it.uppercase() },
            media.videoCodec?.uppercase()?.let { "($it)" },
        ).joinToString(" ")
        if (video.isNotEmpty()) {
            Text(
                video,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(SurfaceColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (audioStreams.isNotEmpty()) {
            StreamChip(
                icon = R.drawable.ic_audio,
                label = audio?.displayTitle ?: audio?.language.orEmpty(),
                rows = audioStreams.map { stream ->
                    PopupRow(streamLabel(stream), checked = stream.id == audio?.id) { onSelectAudio(part, stream) }
                },
            )
        }
        StreamChip(
            icon = R.drawable.ic_subtitles,
            label = subtitle?.displayTitle ?: none,
            rows = listOf(PopupRow(none, checked = subtitle == null) { onSelectSubtitle(part, null) }) +
                subtitleStreams.map { stream ->
                    PopupRow(streamLabel(stream), checked = stream.id == subtitle?.id) { onSelectSubtitle(part, stream) }
                },
        )
    }
}

private fun streamLabel(stream: Stream): String =
    listOfNotNull(stream.title?.takeIf { it.isNotBlank() }, stream.displayTitle).distinct().joinToString(" · ")

@Composable
private fun StreamChip(icon: Int, label: String, rows: List<PopupRow>) {
    val popup = rememberPopupState()
    val shape = RoundedCornerShape(8.dp)
    Box {
        Surface(
            onClick = popup::show,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = SurfaceColor,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
            modifier = Modifier.focusRequester(popup.anchorFocus),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (popup.open) {
            MenuPopup(
                rows = rows.map { row ->
                    row.copy(onClick = {
                        popup.dismiss()
                        row.onClick()
                    })
                },
                onDismiss = popup::dismiss,
                offsetY = 48.dp,
                width = 460.dp,
            )
        }
    }
    popup.RestoreFocus()
}

@Composable
fun CastRow(roles: List<Role>) {
    if (roles.isEmpty()) return
    Column {
        SectionTitle(stringResource(R.string.detail_cast))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(roles.take(30), key = { "${it.tag}|${it.role}" }) { role -> CastCard(role) }
        }
    }
}

@Composable
private fun CastCard(role: Role) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceColor, focusedContainerColor = SurfaceColor),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(role.tag.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Accent)
                AsyncImage(
                    model = AppGraph.server?.photoUrl(role.thumb, 144, 144),
                    contentDescription = role.tag,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(role.tag, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        role.role?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ExtrasRow(extras: List<Metadata>, onPlay: (Metadata) -> Unit) {
    if (extras.isEmpty()) return
    val trailer = stringResource(R.string.detail_trailer)
    val extra = stringResource(R.string.detail_extra)
    Column {
        SectionTitle(stringResource(R.string.detail_extras))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(extras, key = { it.ratingKey }) { item ->
                MediaCard(
                    item = item,
                    style = CardStyle.WIDE,
                    onClick = { onPlay(item) },
                    width = 280.dp,
                    title = item.title,
                    subtitle = if (item.subtype == "trailer") trailer else extra,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
    )
}

@Composable
fun ActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    iconTint: Color = Color.Unspecified,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.12f),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = if (primary) Accent else Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = if (primary) 20.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = if (iconTint != Color.Unspecified && !focused) iconTint else LocalContentColor.current,
                modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(
                visible = primary || focused,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp),
                )
            }
        }
    }
}
