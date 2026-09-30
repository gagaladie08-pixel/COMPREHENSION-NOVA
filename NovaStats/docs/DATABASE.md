# 🗄️ Base de données NovaStats — 34 tables

Conventions Room :

| Type spec | Type Kotlin / SQLite | Remarque |
|---|---|---|
| `DATETIME` | `Long` (epoch millis UTC) | `started_at`, `certified_at`, `created_at`… |
| `DATE` | `String` ISO `yyyy-MM-dd` (jour local) | `date`, `peak_date`, `debut_date`… |
| `BOOLEAN` | `Boolean` (INTEGER 0/1) | |
| `REAL` | `Double` | |

Fichier source : `app/src/main/java/com/novastats/app/data/db/entity/*.kt`

| # | Table | Module | Fichier |
|---|---|---|---|
| 1 | `tracks` | Entités | CoreEntities.kt |
| 2 | `artists` | Entités | CoreEntities.kt |
| 3 | `albums` | Entités | CoreEntities.kt |
| 4 | `track_artists` | Entités | CoreEntities.kt |
| 5 | `track_albums` | Entités | CoreEntities.kt |
| 6 | `scrobbles` | Écoutes | PlaybackEntities.kt |
| 7 | `sessions` | Écoutes | PlaybackEntities.kt |
| 8 | `pending_queue` | Écoutes | PlaybackEntities.kt |
| 9 | `daily_plays` | Écoutes | PlaybackEntities.kt |
| 10 | `daily_streaks` | Écoutes | PlaybackEntities.kt |
| 11 | `snapshots` | Snapshots | PlaybackEntities.kt |
| 12 | `snapshot_tracks` | Snapshots | PlaybackEntities.kt |
| 13 | `snapshot_artists` | Snapshots | PlaybackEntities.kt |
| 14 | `snapshot_albums` | Snapshots | PlaybackEntities.kt |
| 15 | `billboard_history_tracks` | Billboard | BillboardEntities.kt |
| 16 | `billboard_history_artists` | Billboard | BillboardEntities.kt |
| 17 | `billboard_history_albums` | Billboard | BillboardEntities.kt |
| 18 | `certifications` | Certifications | AchievementEntities.kt |
| 19 | `certification_history` | Certifications | AchievementEntities.kt |
| 20 | `hall_of_fame` | Hall of Fame | AchievementEntities.kt |
| 21 | `hall_of_fame_badges` | Hall of Fame | AchievementEntities.kt |
| 22 | `pantheon_status` | Panthéon | AchievementEntities.kt |
| 23 | `pantheon_history` | Panthéon | AchievementEntities.kt |
| 24 | `records_cache` | Records | AchievementEntities.kt |
| 25 | `nova_awards` | Nova Awards | AchievementEntities.kt |
| 26 | `nova_awards_history` | Nova Awards | AchievementEntities.kt |
| 27 | `now_playing` | Accueil | SystemEntities.kt |
| 28 | `daily_stats` | Accueil | SystemEntities.kt |
| 29 | `notifications_feed` | Accueil | SystemEntities.kt |
| 30 | `api_cache` | APIs | SystemEntities.kt |
| 31 | `api_reliability` | APIs | SystemEntities.kt |
| 32 | `edit_history` | Éditeur | SystemEntities.kt |
| 33 | `user_corrections` | Éditeur | SystemEntities.kt |
| 34 | `migration_log` | Import/Export | SystemEntities.kt |

## Flux de données

```
Service / Import ──► scrobbles (CONFIRMED)
                        │
                        ▼  StatsRebuilder.rebuildAll()
        tracks.play_count / artists / albums (agrégats)
        daily_plays  ─► Stats par période (Daily/Weekly/Monthly/Yearly) ; Global = agrégats
        daily_stats  ─► Accueil "Aujourd'hui"
        daily_streaks, sessions
        certifications (+ history, dates rétroactives = date de la N-ième écoute)
        pantheon_status (+ history)
                        │
                        ▼  BillboardEngine.rebuildAll() (import) / refreshCurrent() (à chaque écoute)
        snapshots (+ snapshot_tracks / artists / albums) → hall_of_fame (+ badges, notifications_feed)
                        ▼  (à venir)
        billboard_history → records_cache → nova_awards
```

## Billboard (snapshots) — v2 du schéma

- Une ligne `snapshots` par **(type, date d'ancrage)** : `DAILY` = le jour, `WEEKLY` et `GLOBAL` = lundi ISO,
  `MONTHLY` = 1er du mois, `YEARLY` = 1er janvier. Global = total all-time arrêté chaque fin de semaine.
- Les périodes passées sont **figées** ; la période courante (LIVE) est recalculée à chaque écoute.
- Chaque snapshot ne dépend que des snapshots strictement antérieurs → recalcul idempotent.
- `days_in_chart` sert de **compteur générique de périodes** dans le chart (jours / semaines / mois / années selon
  le type du snapshot) ; `weeks_in_chart` et `months_in_chart` reçoivent la même valeur.
- `is_plays_peak` (ajouté par `MIGRATION_1_2`) : nouveau record personnel d'écoutes sur une période → badge PEAK.
- Égalités départagées par le temps d'écoute cumulé. Limites : Hot 100 → 75 en Daily ; Artist 50 → 25 ; Albums 75 → 50.
- Hall of Fame évalué uniquement sur les périodes closes : `DIRECT_DEBUT` (entrée directe #1 hebdo/mensuel),
  `LONG_RUN` (3 semaines / 2 mois consécutifs #1), `TRIPLE_DEBUT` (#1 simultané Daily + Weekly + Monthly),
  `LEGENDARY_RUN` (10 semaines #1 au total).

## Écarts volontaires par rapport à la spec

- `now_playing` a deux colonnes supplémentaires `raw_title` / `raw_artist` pour afficher immédiatement le titre
  avant sa résolution en base.
- Les index d'unicité (`scrobbles(track_id, started_at)`, `artists(name)`, `albums(title, artist_id)`) portent la
  règle "doublons ignorés à l'import" et la fusion des entités.
