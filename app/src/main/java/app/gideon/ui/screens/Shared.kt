package app.gideon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.NewsItem
import app.gideon.ui.ios.PublisherLogo
import app.gideon.ui.theme.Ios
import app.gideon.ui.theme.Palette
import app.gideon.ui.timeAgo
import coil3.compose.AsyncImage
import com.kyant.shapes.RoundedRectangle

/** Thumbnail with the outlet logo as fallback. */
@Composable
fun Thumb(item: NewsItem, imageUrl: String?, palette: Palette, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(RoundedRectangle(corner)).background(palette.fill), contentAlignment = Alignment.Center) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            PublisherLogo(item.sourceName, item.domain, 34.dp)
        }
        if (item.isVideo) {
            Box(
                Modifier.size(26.dp).clip(RoundedRectangle(13.dp)).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** iOS list row for a story: outlet + time, headline, thumbnail on the right (Apple News style). */
@Composable
fun StoryRow(
    item: NewsItem,
    imageUrl: String?,
    palette: Palette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(palette.background)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PublisherLogo(item.sourceName, item.domain, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(item.sourceName, style = Ios.footnote, fontWeight = FontWeight.SemiBold, color = palette.label, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                style = Ios.headline,
                color = palette.label,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(timeAgo(item.publishedAt), style = Ios.footnote, color = palette.secondaryLabel)
        }
        Spacer(Modifier.width(14.dp))
        Thumb(item, imageUrl, palette, Modifier.size(84.dp))
    }
}
