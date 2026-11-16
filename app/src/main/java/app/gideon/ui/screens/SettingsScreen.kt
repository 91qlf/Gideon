package app.gideon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.BuildConfig
import app.gideon.data.SourceKind
import app.gideon.ui.GideonLogo
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassSwitch
import app.gideon.ui.ios.InlineTitleBar
import app.gideon.ui.ios.InsetSection
import app.gideon.ui.ios.IosRow
import app.gideon.ui.ios.LargeTitle
import app.gideon.ui.ios.PublisherLogo
import app.gideon.ui.ios.RowSeparator
import app.gideon.ui.ios.SectionFooter
import app.gideon.ui.ios.SectionHeader
import app.gideon.ui.ios.iosBounce
import app.gideon.ui.theme.Palette
import app.gideon.ui.timeAgo
import com.kyant.shapes.RoundedRectangle
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    palette: Palette,
    topInset: Dp,
    bottomSpace: Dp,
    onDailyBriefChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val textLabel = when {
        vm.textScale < 0.95f -> "Petit"
        vm.textScale < 1.05f -> "Normal"
        vm.textScale < 1.2f -> "Grand"
        else -> "Très grand"
    }
    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().iosBounce(),
            contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomSpace + 32.dp),
        ) {
            item { LargeTitle("Réglages", palette) }

            item {
                InsetSection(palette, Modifier) {
                    IosRow("Brief du jour", palette, icon = Icons.Rounded.Notifications, iconColor = palette.red, subtitle = "Chaque matin vers 8 h") {
                        GlassSwitch(vm.dailyBrief, onDailyBriefChange, onColor = palette.green, offColor = palette.fill)
                    }
                    RowSeparator(palette)
                    IosRow("Son des vidéos", palette, icon = Icons.AutoMirrored.Rounded.VolumeUp, iconColor = palette.blue) {
                        GlassSwitch(vm.soundOn, vm::setSound, onColor = palette.green, offColor = palette.fill)
                    }
                    RowSeparator(palette)
                    IosRow(
                        "Taille du texte",
                        palette,
                        icon = Icons.Rounded.TextFields,
                        iconColor = palette.gray,
                        value = textLabel,
                        chevron = true,
                        onClick = { vm.cycleTextScale() },
                    )
                    RowSeparator(palette)
                    IosRow(
                        "Actualiser",
                        palette,
                        icon = Icons.Rounded.Refresh,
                        iconColor = palette.green,
                        value = if (vm.isRefreshing) "…" else timeAgo(vm.lastUpdated),
                        onClick = { vm.refresh(manual = true) },
                    )
                }
            }

            item { SectionHeader("Journaux", palette) }
            item { PublisherSection(vm, palette, SourceKind.RSS) }
            item { SectionFooter("Désactive un journal pour ne plus voir ses articles dans ton fil.", palette) }

            item { SectionHeader("Chaînes vidéo", palette) }
            item { PublisherSection(vm, palette, SourceKind.YOUTUBE) }

            item { SectionHeader("À propos", palette) }
            item {
                InsetSection(palette) {
                    IosRow(
                        "Gideon",
                        palette,
                        leading = {
                            Box(Modifier.size(30.dp).clip(RoundedRectangle(8.dp)).background(androidx.compose.ui.graphics.Color(0xFF07070A))) {
                                GideonLogo(Modifier.size(30.dp))
                            }
                        },
                        value = "Version ${BuildConfig.VERSION_NAME}",
                    )
                }
            }
            item {
                SectionFooter(
                    "Gideon rassemble automatiquement les flux publics (RSS et YouTube) de ${vm.catalog.publishers.size} médias. " +
                        "Les articles et vidéos restent la propriété de leurs éditeurs.",
                    palette,
                )
            }
        }
        InlineTitleBar("Réglages", listState, palette, topInset)
    }
}

@Composable
private fun PublisherSection(vm: MainViewModel, palette: Palette, kind: String) {
    val publishers = vm.catalog.publishersOf(kind)
    InsetSection(palette) {
        publishers.forEachIndexed { i, pub ->
            if (i > 0) RowSeparator(palette)
            val feeds = vm.catalog.feeds.count { it.publisher == pub.id && it.kind == kind }
            IosRow(
                pub.name,
                palette,
                leading = { PublisherLogo(pub.name, pub.domain, 30.dp, shape = RoundedRectangle(8.dp)) },
                subtitle = if (kind == SourceKind.RSS) "$feeds rubrique${if (feeds > 1) "s" else ""}" else null,
            ) {
                GlassSwitch(
                    vm.isGroupEnabled(pub.id, kind),
                    { vm.setGroupEnabled(pub.id, kind, it) },
                    onColor = palette.green,
                    offColor = palette.fill,
                )
            }
        }
    }
}
