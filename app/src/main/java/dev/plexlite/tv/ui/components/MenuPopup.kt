package dev.plexlite.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor

data class PopupRow(val label: String, val checked: Boolean = false, val onClick: () -> Unit)

@Composable
fun MenuPopup(
    rows: List<PopupRow>,
    onDismiss: () -> Unit,
    offsetY: Dp,
    alignment: Alignment = Alignment.TopStart,
    width: Dp = 420.dp,
) {
    val focus = remember { FocusRequester() }
    val focusIndex = rows.indexOfFirst { it.checked }.coerceAtLeast(0)
    Popup(
        alignment = alignment,
        offset = IntOffset(0, with(LocalDensity.current) { offsetY.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(width)
                .heightIn(max = 400.dp)
                .background(SurfaceColor, RoundedCornerShape(16.dp))
                .padding(vertical = 10.dp),
        ) {
            itemsIndexed(rows, key = { index, row -> "$index:${row.label}" }) { index, row ->
                ListItem(
                    selected = false,
                    onClick = row.onClick,
                    headlineContent = { Text(row.label) },
                    trailingContent = if (row.checked) {
                        { Text("✓", color = Accent, fontWeight = FontWeight.Bold) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .then(if (index == focusIndex) Modifier.focusRequester(focus) else Modifier),
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

@Composable
fun rememberPopupState(): PopupState = remember { PopupState() }

class PopupState {
    var open by mutableStateOf(false)
    val anchorFocus = FocusRequester()
    private var wasOpened = false

    fun show() {
        open = true
        wasOpened = true
    }

    fun dismiss() {
        open = false
    }

    @Composable
    fun RestoreFocus() {
        LaunchedEffect(open) { if (!open && wasOpened) runCatching { anchorFocus.requestFocus() } }
    }
}
