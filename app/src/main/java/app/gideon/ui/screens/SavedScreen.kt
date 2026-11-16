package app.gideon.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.NewsItem
import app.gideon.ui.MainViewModel
import app.gideon.ui.ios.InlineTitleBar
import app.gideon.ui.ios.LargeTitle
import app.gideon.ui.ios.RowSeparator
import app.gideon.ui.ios.iosBounce
import app.gideon.ui.theme.Ios
import app.gideon.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    vm: MainViewModel,
    palette: Palette,
    topInset: Dp,
    bottomSpace: Dp,
    onOpen: (List<NewsItem>, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val saved = vm.saved
    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().iosBounce(),
            contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomSpace + 24.dp),
        ) {
            item {
                LargeTitle(
                    "Gardés",
                    palette,
                    subtitle = if (saved.isEmpty()) null else "${saved.size} article${if (saved.size > 1) "s" else ""} · glisse vers la gauche pour retirer",
                )
            }
            if (saved.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 90.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Bookmark, null, tint = palette.tertiaryLabel, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Aucun article gardé", style = Ios.title3, color = palette.label)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Touche « Garder » sur une info du fil pour la retrouver ici, même hors connexion.",
                            style = Ios.subheadline,
                            color = palette.secondaryLabel,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                itemsIndexed(saved, key = { _, it -> it.id }) { i, item ->
                    val state = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                vm.toggleSave(item)
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = state,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(palette.red).padding(end = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Delete, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                        },
                    ) {
                        StoryRow(item, item.imageUrl ?: vm.heroFor(item), palette, onClick = { onOpen(saved, i) })
                    }
                    if (i < saved.lastIndex) RowSeparator(palette, inset = 20.dp)
                }
            }
        }
        InlineTitleBar("Gardés", listState, palette, topInset)
    }
}
