package app.gideon.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.NewsItem
import app.gideon.ui.AmbientBackground
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassGroup
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.theme.GColors
import app.gideon.ui.theme.GType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun SavedScreen(
    vm: MainViewModel,
    topInset: Dp,
    bottomSpace: Dp,
    onOpenStories: (List<NewsItem>, Int) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val saved = vm.saved
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.layerBackdrop(backdrop))
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 12.dp, bottom = bottomSpace + 24.dp),
            ) {
                item {
                    ScreenHeader(
                        "Enregistrés",
                        if (saved.isEmpty()) "Ta bibliothèque d'infos" else "${saved.size} info${if (saved.size > 1) "s" else ""} gardée${if (saved.size > 1) "s" else ""}",
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                if (saved.isEmpty()) {
                    item {
                        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                            Column(
                                Modifier.fillMaxWidth().padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Rounded.BookmarkBorder, null, tint = GColors.Gold, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Rien pour l'instant", style = GType.Section)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Appuie sur « Garder » dans le fil pour retrouver une info ici.",
                                    style = GType.Body.copy(color = GColors.Dim),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                            saved.forEachIndexed { i, item ->
                                NewsRow(
                                    item,
                                    vm.imageFor(item),
                                    onClick = { onOpenStories(saved, i) },
                                    trailing = {
                                        GlassIconButton(
                                            Icons.Rounded.DeleteOutline,
                                            "Retirer",
                                            onClick = { vm.toggleSave(item) },
                                            size = 38.dp,
                                            tint = Color.White.copy(alpha = 0.85f),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
