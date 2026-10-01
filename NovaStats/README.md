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
| ⚙️ Paramètres | ✅ 8 sous-pages : Détection · Apparence · Notifications (11) · Données (export JSON v2 compatible v1, import, sauvegarde auto, suppression) · Éditeur · Service & diagnostic · APIs · À propos |
| 🌐 APIs (cascade 9 sources) | ✅ iTunes · Spotify · Last.fm · MusicBrainz/CAA · TheAudioDB · Deezer · Discogs · Fanart.tv · Google ; score de confiance, consensus, cache 6 mois/1 mois/négatif 7 j, fiabilité dynamique, worker en arrière-plan |
| ✏️ Éditeur de données | ✅ Renommer / fusionner (suggestions auto) / changer artiste-album / supprimer une écoute / marquer correct / historique 50 + Undo |

## Sécurité

`local.properties` est ignoré par Git. **Ne commite jamais de clé API.** Si une clé a fuité, régénère‑la.
