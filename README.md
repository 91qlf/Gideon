# Gideon

L'actu en scroll vertical, façon TikTok, avec une interface *liquid glass*.

- Fil plein écran « Pour toi / Vidéos / Articles », double-tap pour aimer, « Garder », partager.
- Contenu 100 % automatique : flux RSS publics (Le Monde, franceinfo, France 24, 20 Minutes, Numerama, L'Équipe)
  et flux vidéo publics YouTube (franceinfo, France 24, HugoDécrypte, Brut, Le Monde, BFMTV), rafraîchis à l'ouverture
  et chaque matin (brief du jour en notification).
- Explorer par catégorie, recherche, infos gardées, réglages des sources.
- Verre liquide : réfraction, flou, vibrance et reflets via [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop`).

## Build

L'APK est construit par GitHub Actions (`.github/workflows/build-apk.yml`) à chaque push
et publié dans l'onglet **Releases** du dépôt.

En local : `./gradlew assembleRelease` (JDK 17 + Android SDK 36).

> La clé de signature `app/gideon-release.jks` est commitée pour que les mises à jour s'installent
> par-dessus la version précédente. Pour une publication sur le Play Store, remplace-la par une clé privée.
