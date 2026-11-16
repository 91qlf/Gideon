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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.data.Category
import app.gideon.data.NewsItem
import app.gideon.ui.AmbientBackground
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassGroup
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.liquidGlass
import app.gideon.ui.glass.rememberPressScale
import app.gideon.ui.style
import app.gideon.ui.theme.GColors
import app.gideon.ui.theme.GType
import app.gideon.ui.timeAgo
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

@Composable
fun ExploreScreen(
    vm: MainViewModel,
    topInset: Dp,
    bottomSpace: Dp,
    onOpenCategory: (Category) -> Unit,
    onOpenStories: (List<NewsItem>, Int) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    var query by rememberSaveable { mutableStateOf("") }
    val latest = remember(vm.allItems) { vm.allItems.filter { !it.isVideo }.take(14) }
    val videos = remember(vm.allItems) { vm.allItems.filter { it.isVideo }.take(14) }
    val results = remember(query, vm.allItems) {
        val q = query.trim()
        if (q.length < 2) emptyList()
        else vm.allItems.filter { it.title.contains(q, true) || it.summary.contains(q, true) || it.sourceName.contains(q, true) }.take(80)
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.layerBackdrop(backdrop))
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 12.dp, bottom = bottomSpace + 24.dp),
            ) {
                item { ScreenHeader("Explorer", "Mis à jour ${timeAgo(vm.lastUpdated)}") }
                item { SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }

                if (query.trim().length >= 2) {
                    item {
                        SectionTitle(if (results.isEmpty()) "Aucun résultat" else "${results.size} résultat${if (results.size > 1) "s" else ""}")
                    }
                    if (results.isNotEmpty()) {
                        item {
                            GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                                results.forEachIndexed { i, item ->
                                    NewsRow(item, vm.imageFor(item), onClick = { onOpenStories(results, i) })
                                }
                            }
                        }
                    }
                } else {
                    item { SectionTitle("Catégories") }
                    item { CategoryGrid(vm.countsByCategory, onOpenCategory) }
                    if (latest.isNotEmpty()) {
                        item { SectionTitle("Les dernières") }
                        item { StoryCarousel(latest, vm::imageFor) { onOpenStories(latest, it) } }
                    }
                    if (videos.isNotEmpty()) {
                        item { SectionTitle("Vidéos du jour") }
                        item { StoryCarousel(videos, vm::imageFor) { onOpenStories(videos, it) } }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .liquidGlass(shape = Capsule(), surface = Color.White.copy(alpha = 0.10f), refractionHeight = 12.dp, refractionAmount = 24.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = GColors.Dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Rechercher une info, un média…", color = GColors.Faint, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(GColors.Cyan),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                "Effacer",
                tint = GColors.Dim,
                modifier = Modifier.size(22.dp).clickable(remember { MutableInteractionSource() }, indication = null) { onChange("") },
            )
        }
    }
}

@Composable
private fun CategoryGrid(counts: Map<Category, Int>, onOpen: (Category) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Category.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cat ->
                    CategoryTile(cat, counts[cat] ?: 0, { onOpen(cat) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(cat: Category, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, pressed = 1.06f)
    val style = cat.style()
    Row(
        modifier
            .height(78.dp)
            .liquidGlass(
                shape = RoundedRectangle(24.dp),
                surface = style.color.copy(alpha = 0.10f),
                blurRadius = 10.dp,
                refractionHeight = 14.dp,
                refractionAmount = 26.dp,
                scale = { scale },
            )
            .clickable(source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(Capsule()).background(style.color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) { Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(cat.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("$count infos", style = GType.Caption)
        }
    }
}

