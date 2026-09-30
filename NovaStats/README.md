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
| 🎵 Détection v3.0 | 🟡 Tracker + service MediaSession/Notification (watchdog WorkManager, guide constructeur, filtres podcast à brancher) |
| 🏠 Accueil | 🟡 Sections 1, 2, 3, 7, 8 (actualités, prochaines certifs, records à venir) |
| 📊 Stats | 🟡 Classements Top 300 × 5 périodes + bandeau (popups détaillées à venir) |
| 💎 Certifications | 🟡 Règles + calcul + dates rétroactives (écran à venir) |
| 👑 Panthéon | 🟡 Règles + calcul (écran à venir) |
| 🏆 Billboard | ✅ Hot 100 / Artist 50 / 75 Albums × 5 périodes, snapshots figés, navigation historique, fiche détaillée, recherche |
| 🏛️ Hall of Fame | 🟡 Alimenté par le Billboard (Direct Debut, Long Run, Triple Debut, Legendary Run) — écran à venir |
| 🏅 Records · 🏆 Nova Awards | ⬜ Tables prêtes |
| 🎨 15 thèmes | ✅ Palettes + sélecteur |
| ⚙️ Paramètres | 🟡 Détection, apparence, données, service |
| 🌐 APIs (cascade 9 sources) | ⬜ Clés câblées, clients à écrire |
| ✏️ Éditeur de données | ⬜ Tables prêtes |

## Sécurité

`local.properties` est ignoré par Git. **Ne commite jamais de clé API.** Si une clé a fuité, régénère‑la.
