# 🎵 NovaStats

Application Android de statistiques musicales personnelles — 100 % locale, sans compte, sans serveur.

> Squelette généré à partir du cahier des charges (`../DEBUT`). Toutes les règles métier et les 34 tables
> sont en place ; les onglets s'implémentent module par module.

## Stack

| Couche | Choix |
|---|---|
| Langage / UI | Kotlin · Jetpack Compose · Material 3 |
| Base de données | Room (SQLite) — **34 tables** |
| Architecture | MVVM léger + Repository (pas de Hilt pour l'instant) |
| Réseau | Retrofit + OkHttp + kotlinx.serialization (APIs métadonnées) |
| Détection | `NotificationListenerService` + `MediaSessionManager` |
| Préférences | DataStore |

## Ouvrir le projet

1. Ouvre le dossier `NovaStats/` dans **Android Studio** (Ladybug ou plus récent, JDK 17).
2. Copie `local.properties.example` → `local.properties` et renseigne `sdk.dir` (+ tes clés API si tu en as).
3. Sync Gradle → Run sur un appareil Android 8.0+ (minSdk 26).
4. Sur l'appareil : **Réglages → Service → Activer la détection** (accès aux notifications), puis désactive
   l'optimisation batterie pour NovaStats.
5. **Réglages → Données → Importer un backup JSON** pour charger `nova_backup_*.json` (format v1 :
   `songs` + `plays`). Les stats, certifications et statuts Panthéon sont recalculés automatiquement.

Tests unitaires (JVM, sans appareil) : `./gradlew :app:testDebugUnitTest`

> Le wrapper Gradle (`gradlew` + `gradle-wrapper.jar`) n'est pas versionné : Android Studio le génère à l'ouverture
> (ou lance `gradle wrapper --gradle-version 8.9` une fois).

## Arborescence

```
app/src/main/java/com/novastats/app/
├── NovaStatsApp.kt              # Application + conteneur de dépendances
├── MainActivity.kt
├── data/
│   ├── ApiKeys.kt               # Clés via BuildConfig (jamais en dur)
│   ├── db/
│   │   ├── NovaDatabase.kt      # Room — 34 tables
│   │   ├── entity/              # Entités groupées par module (Core, Playback, Billboard, Achievement, System)
│   │   └── dao/                 # DAOs (classements par période, agrégats, recalculs SQL)
│   ├── importer/
│   │   └── LegacyBackupImporter.kt   # Import du JSON v1 (songs/plays) + rapport
│   └── repository/
│       ├── LibraryRepository.kt      # Résolution titre/artiste/album (normalisation, feat., fusion versions)
│       ├── StatsRebuilder.kt         # Recalcul : agrégats → daily → streaks → sessions → certifs → Panthéon
│       └── SettingsRepository.kt     # DataStore (seuil, whitelist, thème…)
├── domain/
│   ├── Rules.kt                 # Certifications, Panthéon, normalisation, score de confiance
│   └── Periods.kt               # Périodes calendaires, streaks, limites Billboard
├── service/
│   ├── ScrobbleTracker.kt       # Machine à états d'une écoute (pause courte/longue, loop, seuil)
│   ├── NovaListenerService.kt   # MediaSession (principal) + notifications (fallback)
│   └── BootReceiver.kt
└── ui/
    ├── theme/                   # Les 15 thèmes + NovaStatsTheme
    ├── navigation/NovaApp.kt    # Barre d'onglets + NavHost
    └── screens/                 # Home, Stats, Settings (+ placeholders)
```

## État d'avancement

| Module | Statut |
|---|---|
| 🗄️ Base de données (34 tables) | ✅ Entités + DAOs |
| 📥 Import JSON v1 + recalcul complet | ✅ |
| 🎵 Détection v3.0 | ✅ Tracker + service MediaSession/Notification, whitelist apps, blacklist artistes/mots-clés, filtre > 10 min ; robustesse : plafond durée du morceau, anti-gel (trous d'horloge non comptés, clôture à la déconnexion), position réelle du lecteur comme garde-fou ; service premier plan compagnon (« NovaStats veille »), watchdog 15 min + ouverture (battement de cœur persisté, relance listener), tracker mono-thread, recalcul regroupé, bannières service endormi / optimisation batterie |
| 🏠 Accueil | ✅ 8 sections : lecture en cours (pochette, progression, source, statut), aujourd'hui, top du moment, actualités, prochaines certifs, records, récents, streak |
| 📊 Stats | ✅ Titres/Artistes/Albums × 5 périodes (Weekly = 7 jours glissants), recherche, bandeau, popups détaillées (appui long) |
| 💎 Certifications | ✅ Chansons / Albums, chips par palier avec compteurs, radar des 5 prochains paliers, tri niveau → écoutes, dates rétroactives + durée, recherche, fiche colorée par niveau (historique, progression, courbe, positions), sons distincts par palier |
| 👑 Panthéon | ✅ Liste par statut (Mythique en tête), recherche, progression vers le statut suivant, « Bientôt dans le Panthéon », parcours dans la fiche artiste |
| 🏆 Billboard | ✅ Hot 100 / Artist 50 / 75 Albums × 5 périodes, LIVE = dernière période close (la période en cours n'a jamais de snapshot), rattrapage automatique des snapshots manquants, navigation historique, fiche détaillée, recherche |
| 🏛️ Hall of Fame | ✅ Weekly / Monthly / Global × Chansons / Artistes / Albums, une carte par entité avec tous ses badges (Direct Debut, Long Run, Triple Debut, Legendary Run), tri par prestige puis durée de règne |
| 🏅 Records | ✅ 24 records (`domain/Records.kt` + `RecordsEngine`), recalculés après chaque écoute / import, popup 90 % avec Périodes · Sections · Sous-sections |
| 🏆 Nova Awards | ✅ 9 récompenses, déblocage à 2 mois, LIVE / FINAL par année, révélation une par une, cérémonie 31 déc., partage texte |
| 🎬 Onboarding | ✅ Bienvenue 7 phases (sons + vibrations), Étape 1 thèmes (preview live + 15 effets), Étape 2 permissions (3 orbes, gestion des refus), Étape 3 guide constructeur (Samsung / Xiaomi / Huawei / Oppo / Pixel, validation au retour), Étape 4 grand final (genèse, carte d'identité, première mission) ; accueil « premier contact » et notification de première écoute |
| 🎨 Système de thèmes (THEMES.md) | ✅ 15 thèmes × 8 rôles de couleur, polices Google Fonts par thème (titre + corps, repli système), icônes d'onglets stylisées (15 styles), transitions durée + easing par thème, 15 effets signature (scanlines, chrome, jump-cut, shimmer, bulles, rideau, flash N&B, nuage, pulsation, glitch, confettis, paillettes, flash bleu, motif, dualité), courbes thématisées (lissage, glow, dégradé, décor, arc-en-ciel) |
| 🪟 Pop-ups (POPUPS.md) | ✅ Base commune `NovaPopupCard` (overlay 85 %, 92 %, 16 dp, scroll interne, FERMER + clic extérieur, fade, bordure colorée → dégradé vers le fond), courbe unique `NovaCurveChart` ; fiches Chanson (primary) · Artiste (glowSecondary, bannière floue + cercle 90 dp + statut) · Album (secondary) · Billboard (or fixe, courbe + ⭐ peak, 4 badges) · Certifications (bordure par niveau + glow Diamant) · Panthéon (bordure par statut, Mythique holographique + particules) · Hall of Fame (historique complet + courbe de toutes les positions) ; Records en composant dédié 90 % (onglets internes, barres proportionnelles, 🥇) |
| 🅽 Icône de l'app (cahier des charges Icône) | ✅ N stylisé + onde sonore (zone sûre 66/108 dp), adaptive icon en vecteurs (foreground / background / monochrome, aucune PNG dans l'APK), **icône dynamique** = 15 `activity-alias` (un par thème, bascule `IconSwitcher`), icône de notification N blanc, splash Android 12+ (N scale-overshoot + pulsation de l'onde, `core-splashscreen`), PNG 512 Play Store × 15 dans `design/icon/` (générateur `generate_icons.py`) |
| 🧭 Navigation | ✅ Barre d'onglets déplacée **en haut** de l'écran (sous la barre d'état, défilement horizontal, onglet actif recentré) — 0.8.1 |
| 📊🏆 Stats & Billboard 0.8.2 | ✅ En-tête (périodes + bandeau) qui défile avec la liste, sous-onglets Titres / Artistes / Albums et Hot 100 / Artist 50 / Albums 75 **collés en haut** pendant le scroll ; **Top 25 + « Voir plus » (+20)** ; popups titre / artiste / album calés sur la **période choisie** (écoutes, temps, jours actifs, top chansons, albums, parts d'écoute — le cumul all time reste indiqué) |
| 🏅 Records 2.0 — 0.9.0 | ✅ Onglet Records réorganisé en **6 familles** (puces horizontales façon niveaux de certification) : ⏳ Durée dans le chart, 📈 Mouvements de position, 🚀 Débuts, ⚡ Vitesse, 🧮 Cumuls, 🎼 Domination ; une carte par record ; 🔍 au fond à droite (recherche d'un titre / artiste / album dans tous les records) ; le popup 90 % devient une **page** (sections · périodes · sous-sections · Top 10) ; appui sur une ligne → **page « Pourquoi il est là »** spécifique à chaque record (`RecordExplainer` : récit en français, faits chiffrés, frise des périodes avec surbrillance, éléments impliqués — titres d'un artiste, certifications, intronisations, bloqueurs du #1…), bouton « Voir la fiche complète » ; **appui sur une photo / pochette dans un popup → plein écran** (pincer pour zoomer) |
| 🖼️ Images à la main — 0.8.9 | ✅ Barre image dans les popups **artiste et album** : 🖼️ galerie (sélecteur système, sans permission) · 🌐 web (recherche Google Images ouverte dans le navigateur ; au retour dans l'app, la dernière image téléchargée est appliquée automatiquement — accès aux photos demandé — ou « Partager l'image » → NovaStats) · 💡 Autres photos / pochettes (propositions de toutes les sources + verdict bibliothèque) · 🔑 mots-clés par entité (ajoutés à la recherche web et aux requêtes APIs, +10 si présents dans le candidat). Images copiées dans le stockage privé (`files/images`), source 👤 USER, popups rechargés automatiquement |
| 🖼️ Contexte bibliothèque 2 — 0.8.8 | ✅ Comparaison **tolérante** des titres (égalité, inclusion « diamonds » ⊂ « diamonds official video », similarité ≥ 0,85) ; titres connus d'un artiste = tous ceux où il est crédité (principal **et** featuring) + fiches homonymes (« X feat. Rihanna ») ; seuils abaissés (artiste ≥ 2 connus / liste ≥ 3, piste 3 / 3) ; journal explicite « (K connus / L dans la liste) » ; popup artiste → **🖼️ Autres photos** : propositions de toutes les sources (Deezer, Fanart.tv, Wikidata, TheAudioDB, Last.fm…) avec score et verdict bibliothèque, un appui = photo 👤 (annulable dans l'éditeur) |
| 📚 Contexte bibliothèque 0.8.7 | ✅ Les pochettes et photos sont vérifiées avec **tes** titres : album candidat → liste de pistes (iTunes, Deezer, MusicBrainz, Last.fm, Discogs) qui doit contenir le titre (−30 sinon) et recouper d'autres titres connus de l'artiste / de l'album (+20 ; −25 si aucun alors que tu en as ≥ 5) ; photos d'artistes : les titres connus du candidat doivent recouper les tiens (−40 sinon → homonymes éliminés) ; sources sans liste de pistes (Genius, YouTube…) plafonnées à 89 → 🟡 À vérifier ; un titre dont l'album est connu passe d'abord par l'album (liste vérifiée, pochette partagée, aucun appel si l'album est déjà résolu) ; le popup de correction affiche le verdict (✅ N titres en commun / ⚠️ aucun) ; journal enrichi |
| 🔄 Ré-enrichissement & popups 0.8.6 | ✅ Réglages → APIs : « 🔄 Tout ré-enrichir » (confirmation, artistes → albums → titres, images choisies à la main conservées, service de premier plan WorkManager, bouton ⏹️ Arrêter, progression N/total) et « 🔄 Ré-enrichir… » (page dédiée : sections Artistes / Albums / Titres, 🔍 recherche, cases à cocher, Tout cocher, Lancer (N)) ; un élément ré-enrichi ignore son cache et ne perd son image que si une meilleure est trouvée · Tous les popups (titre, artiste, album, Panthéon, Hall of Fame, certification, historique Billboard, correction) ont pour arrière-plan la pochette / photo de l'élément floutée à 55 % · Billboard : plus de places vides « — » |
| 🌐 APIs gratuites 0.8.5 | ✅ Retrait de Spotify et Google Images ; ajout de Wikidata/Wikimedia Commons (photos d'artistes, sans clé), Genius (token gratuit) et YouTube Data API v3 (dernier recours, 🟡 À vérifier) ; Discogs n'est appelé qu'avec un token (sinon 401) ; TheAudioDB clé publique `123` et 2,1 s entre appels (quota 30/min) ; garde-fous de score : pochette refusée si l'artiste n'est pas confirmé (max 69), durée ±3 s +20 / écart > 10 s −15, consensus exigeant le même artiste ; dernière erreur HTTP par source affichée dans Réglages → APIs + remise à zéro des compteurs ; secrets GitHub `DISCOGS_TOKEN`, `GENIUS_ACCESS_TOKEN`, `YOUTUBE_API_KEY` |
| ⚠️ À corriger 0.8.4 | ✅ Onglet dédié de l'Éditeur : segments 🔴 À corriger (score < 70, Inconnu, notification incomplète, APIs épuisées) / 🟡 À vérifier (70-89, avec proposition) ; popup plein écran (bandeau info, champs pré-remplis avec suggestions, pochette galerie / web, propositions de l'app, écoutes concernées cochables, Valider / Annuler, menu ⋮ C'est correct · Ignorer · 🗑️ Supprimer) ; règle mémorisée dans `user_corrections` et appliquée avant les APIs ; un seul Undo ; notification discrète 🟡 ; raccourci Paramètres → Détection → Section à corriger |
| 🛠️ Éditeur de données 0.8.3 | ✅ Compteur de doublons sous chaque sous-onglet (Titres / Artistes / Albums) → popup de choix : une carte par paire (disparaît ⬇ conservé, écoutes), case à cocher par paire, ⇄ inverser le sens, 🚫 ignorer (mémorisé), fusion de la sélection en une seule transaction + un seul recalcul ; détection sans plafond (tout doublon → le plus écouté du groupe) |
| ⚙️ Paramètres | ✅ 8 sous-pages : Détection · Apparence · Notifications (11) · Données (export JSON v2 compatible v1, import, sauvegarde auto, suppression) · Éditeur · Service & diagnostic · APIs · À propos |
| 🌐 APIs (cascade 10 sources gratuites) | ✅ Titres/albums : iTunes → Deezer → MusicBrainz/CAA → Last.fm → Discogs → Genius → YouTube (dernier recours) ; artistes : Deezer → Fanart.tv → Wikidata/Commons → TheAudioDB → Last.fm → Genius → YouTube ; score de confiance, consensus, cache 6 mois/1 mois/négatif 7 j, fiabilité dynamique, worker en arrière-plan. Spotify (Premium obligatoire depuis mars 2026) et Google Images (API fermée, arrêt 01/01/2027) retirés en 0.8.5 |
| ✏️ Éditeur de données | ✅ Renommer / fusionner (suggestions auto) / changer artiste-album / supprimer une écoute / marquer correct / historique 50 + Undo |

## Sécurité

`local.properties` est ignoré par Git. **Ne commite jamais de clé API.** Si une clé a fuité, régénère‑la.
