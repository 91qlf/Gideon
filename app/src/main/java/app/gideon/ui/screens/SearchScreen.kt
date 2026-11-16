package app.gideon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.Category
import app.gideon.data.NewsItem
import app.gideon.data.Publisher
import app.gideon.data.SourceKind
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.rememberGlassInteraction
import app.gideon.ui.glass.glassTouch
import app.gideon.ui.ios.InlineTitleBar
import app.gideon.ui.ios.LargeTitle
import app.gideon.ui.ios.PublisherLogo
import app.gideon.ui.ios.RowSeparator
import app.gideon.ui.ios.iosBounce
import app.gideon.ui.style
import app.gideon.ui.theme.Ios
import app.gideon.ui.theme.Palette
import com.kyant.shapes.RoundedRectangle

@Composable
fun SearchScreen(
    vm: MainViewModel,
    query: String,
    palette: Palette,
    topInset: Dp,
    bottomSpace: Dp,
    onOpen: (List<NewsItem>, Int) -> Unit,
    onCategory: (Category) -> Unit,
    onPublisher: (Publisher) -> Unit,
) {
    val listState = rememberLazyListState()
    val q = query.trim()
    val results = remember(q, vm.allItems) {
        if (q.length < 2) emptyList()
        else vm.allItems.filter {
            it.title.contains(q, true) || it.summary.contains(q, true) || it.sourceName.contains(q, true)
        }.take(80)
    }
    val latest = remember(vm.allItems) { vm.allItems.filter { !it.isVideo }.take(12) }
    val publishers = remember(vm.catalog) { vm.catalog.publishersOf(SourceKind.RSS) }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().iosBounce(),
            contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomSpace + 24.dp),
        ) {
            item { LargeTitle("Rechercher", palette) }
            if (q.length >= 2) {
                item {
                    Text(
                        if (results.isEmpty()) "Aucun résultat pour « $q »" else "${results.size} résultat${if (results.size > 1) "s" else ""}",
                        style = Ios.footnote,
                        color = palette.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(results, key = { _, it -> it.id }) { i, item ->
                    StoryRow(item, vm.heroFor(item), palette, onClick = { onOpen(results, i) })
                    RowSeparator(palette, inset = 20.dp)
                }
            } else {
                item { SectionTitle("Catégories", palette) }
                item { CategoryGrid(vm.countsByCategory, onCategory) }
                item { SectionTitle("Journaux", palette) }
                item { PublisherStrip(publishers, palette, onPublisher) }
                if (latest.isNotEmpty()) {
                    item { SectionTitle("Les plus récentes", palette) }
                    itemsIndexed(latest, key = { _, it -> it.id }) { i, item ->
                        StoryRow(item, vm.heroFor(item), palette, onClick = { onOpen(latest, i) })
                        if (i < latest.lastIndex) RowSeparator(palette, inset = 20.dp)
                    }
                }
            }
        }
        InlineTitleBar("Rechercher", listState, palette, topInset)
    }
}

@Composable
private fun SectionTitle(text: String, palette: Palette) {
    Text(
        text,
        style = Ios.title2,
        color = palette.label,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun CategoryGrid(counts: Map<Category, Int>, onOpen: (Category) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Category.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cat -> CategoryTile(cat, counts[cat] ?: 0, { onOpen(cat) }, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryTile(cat: Category, count: Int, onClick: () -> Unit, modifier: Modifier) {
    val interaction = rememberGlassInteraction(0.96f)
    val style = cat.style()
    Box(
        modifier
            .height(92.dp)
            .graphicsLayer {
                val s = 1f - 0.04f * interaction.progress
                scaleX = s
                scaleY = s
            }
            .clip(RoundedRectangle(18.dp))
            .background(Brush.linearGradient(listOf(style.color, style.color.copy(alpha = 0.62f))))
            .glassTouch(interaction, onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(26.dp).align(Alignment.TopStart))
        Text(
            "$count",
            style = Ios.footnote,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Text(cat.label, style = Ios.headline, color = Color.White, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun PublisherStrip(publishers: List<Publisher>, palette: Palette, onOpen: (Publisher) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(publishers, key = { it.id }) { pub ->
            Column(
                Modifier
                    .width(74.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onOpen(pub) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PublisherLogo(pub.name, pub.domain, 60.dp, shape = RoundedRectangle(16.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    pub.name,
                    style = Ios.caption1,
                    color = palette.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
