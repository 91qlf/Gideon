package app.gideon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.data.NewsItem
import app.gideon.ui.glass.rememberPressScale
import app.gideon.ui.initials
import app.gideon.ui.sourceColor
import app.gideon.ui.theme.GType
import app.gideon.ui.timeAgo
import coil3.compose.AsyncImage
import com.kyant.shapes.RoundedRectangle

@Composable
fun ScreenHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, style = GType.LargeTitle)
        if (subtitle != null) Text(subtitle, style = GType.Caption, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = GType.Section, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp))
}

/** Thumbnail with a source-coloured fallback when the feed has no picture. */
@Composable
fun Thumb(item: NewsItem, imageUrl: String?, modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    val color = sourceColor(item.sourceName)
    Box(
        modifier
            .clip(RoundedRectangle(corner))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.3f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(item.sourceName), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        if (item.isVideo) {
            Box(
                Modifier.size(30.dp).clip(RoundedRectangle(15.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Horizontal row of tall story cards. */
@Composable
fun StoryCarousel(items: List<NewsItem>, imageFor: (NewsItem) -> String?, onOpen: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            val source = remember { MutableInteractionSource() }
            val scale = rememberPressScale(source, pressed = 0.96f)
            Box(
                Modifier
                    .width(158.dp)
                    .height(236.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(RoundedRectangle(26.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedRectangle(26.dp))
                    .clickable(source, indication = null) { onOpen(index) }
            ) {
                Thumb(item, imageFor(item), Modifier.fillMaxSize(), corner = 26.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(item.sourceName, style = GType.Caption.copy(color = Color.White.copy(alpha = 0.8f)), maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun NewsRow(item: NewsItem, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item, imageUrl, Modifier.size(68.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${item.sourceName} · ${timeAgo(item.publishedAt)}", style = GType.Caption, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
