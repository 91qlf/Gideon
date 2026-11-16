package app.gideon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.BuildConfig
import app.gideon.data.SourceKind
import app.gideon.ui.AmbientBackground
import app.gideon.ui.GideonLogo
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassGroup
import app.gideon.ui.glass.GlassSwitch
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.initials
import app.gideon.ui.sourceColor
import app.gideon.ui.theme.GColors
import app.gideon.ui.theme.GType
import app.gideon.ui.timeAgo
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    topInset: Dp,
    bottomSpace: Dp,
    onDailyBriefChange: (Boolean) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.layerBackdrop(backdrop))
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 12.dp, bottom = bottomSpace + 24.dp),
            ) {
                item { ScreenHeader("Réglages", null) }

                item { GroupLabel("Ton brief") }
                item {
                    GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                        SettingRow(Icons.Rounded.Notifications, GColors.Coral, "Brief du jour", "Une notification chaque matin vers 8 h") {
                            GlassSwitch(vm.dailyBrief, onDailyBriefChange)
                        }
                        Divider()
                        SettingRow(Icons.AutoMirrored.Rounded.VolumeUp, Color(0xFF4D7CFF), "Son des vidéos", "Lecture avec le son dès l'ouverture") {
                            GlassSwitch(vm.soundOn, vm::setSound)
                        }
                        Divider()
                        SettingRow(
                            Icons.Rounded.Refresh,
                            GColors.Green,
                            "Actualiser maintenant",
                            "Dernière mise à jour ${timeAgo(vm.lastUpdated)}",
                            onClick = { vm.refresh(manual = true) },
                        ) {}
                    }
                }

                item { GroupLabel("Sources — articles") }
                item { SourceGroup(vm, SourceKind.RSS) }
                item { GroupLabel("Sources — vidéos") }
                item { SourceGroup(vm, SourceKind.YOUTUBE) }

                item { GroupLabel("À propos") }
                item {
                    GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(54.dp).clip(RoundedRectangle(15.dp)).background(GColors.Bg),
                                contentAlignment = Alignment.Center,
                            ) { GideonLogo(Modifier.size(54.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Gideon", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Version ${BuildConfig.VERSION_NAME}", style = GType.Caption)
                            }
                        }
                        Divider()
                        SettingRow(
                            Icons.Rounded.Info,
                            Color(0xFF8E8E93),
                            "Contenus",
                            "Gideon rassemble automatiquement des flux publics (RSS et YouTube). Les articles et vidéos restent la propriété de leurs éditeurs.",
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceGroup(vm: MainViewModel, kind: String) {
    val groups = vm.catalog.publishersOf(kind)
    GlassGroup(Modifier.padding(horizontal = 16.dp)) {
        groups.forEachIndexed { i, pub ->
            val publisher = pub.id
            val k = kind
            if (i > 0) Divider()
            val feeds = vm.catalog.feeds.count { it.publisher == publisher && it.kind == k }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedRectangle(10.dp)).background(sourceColor(pub.name)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kind == SourceKind.YOUTUBE) Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    else Text(initials(pub.name), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pub.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (kind == SourceKind.YOUTUBE) "Chaîne vidéo" else "$feeds rubrique${if (feeds > 1) "s" else ""}",
                        style = GType.Caption,
                    )
                }
                GlassSwitch(vm.isGroupEnabled(publisher, k), { vm.setGroupEnabled(publisher, k, it) })
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = GType.Caption.copy(letterSpacing = 0.6.sp),
        modifier = Modifier.padding(start = 34.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f)))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedRectangle(10.dp)).background(color),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = GType.Caption)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
