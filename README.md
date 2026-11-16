# Gideon

L'actu en scroll vertical, façon TikTok, avec une interface *liquid glass*.

- Fil plein écran « Pour toi / Vidéos / Articles », double-tap pour aimer, « Garder », partager.
- **Articles lus dans l'app** : le texte complet est extrait de la page (lecteur façon iOS, taille réglable),
  avec repli sur la page d'origine intégrée quand l'article est réservé aux abonnés.
- Contenu 100 % automatique : 36 médias (Le Monde, franceinfo, France 24, Le Figaro, Libération, BFMTV, 20 Minutes,
  L'Express, L'Obs, Courrier international, RFI, Ouest-France, Numerama, Frandroid, Futura, L'Équipe, Télérama, Konbini…)
  et 8 chaînes vidéo YouTube, définis dans `app/src/main/assets/sources.json`.
- Interface iOS 26 : verre liquide (réfraction, reflets qui suivent l'inclinaison du téléphone), barre d'onglets
  flottante avec bouton de recherche séparé, feuilles iOS, défilement élastique, police Inter (proche de SF Pro).
- Rechercher (catégories, journaux, recherche plein texte), Gardés, Réglages par journal.

Le workflow `check-sources.yml` teste chaque flux et l'extraction d'articles en conditions réelles.

## Build

L'APK est construit par GitHub Actions (`.github/workflows/build-apk.yml`) à chaque push
et publié dans l'onglet **Releases** du dépôt.

En local : `./gradlew assembleRelease` (JDK 17 + Android SDK 36).

> La clé de signature `app/gideon-release.jks` est commitée pour que les mises à jour s'installent
> par-dessus la version précédente. Pour une publication sur le Play Store, remplace-la par une clé privée.
