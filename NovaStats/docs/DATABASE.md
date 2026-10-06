# 🗄️ Base de données NovaStats — 35 tables

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
| 35 | `artist_exceptions` | Référentiel | CoreEntities.kt |

## Migrations et index

- **Schéma v7 (`MIGRATION_6_7`)** : création non destructive de `index_notifications_feed_type_entity_id_created_at`
  sur `(type, entity_id, created_at)`. Il accélère la recherche des notifications déjà annoncées par catégorie et entité.
  Aucun enregistrement du fil ni aucune écoute n'est supprimé ou recalculé.
- Les index simples `created_at` et `is_read` restent dédiés au tri récent et au filtrage de lecture.

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

- `now_playing` a des colonnes supplémentaires `raw_title` / `raw_artist` / `raw_album` pour afficher immédiatement
  le titre avant sa résolution en base, et `position_ms` / `duration_ms` / `is_playing` (v3) pour la barre de
  progression de l'Accueil.
- **Featurings** : chaque artiste d'un titre (principal + featured, extraits du titre `feat./ft./with` puis du champ
  artiste du player) est lié dans `track_artists` et reçoit l'écoute à poids égal (Stats, Billboard, Panthéon).
  L'album n'est crédité qu'à l'artiste principal (`albums.artist_id`) ; les compilations ne créditent jamais d'album ;
  les éditions Deluxe / Japan / UK / Platinum… sont fusionnées ; un remix n'est un titre distinct (`is_remix`,
  `original_track_id`) que s'il porte un artiste featuring identifié.
- **Albums multi-artistes (v6, `MIGRATION_5_6`)** : `albums.artist_id` devient NULLable (table recréée). `NULL` = album
  partagé (BO, album d'événement, « Various Artists », marquage manuel) : un seul album par titre normalisé, sans
  propriétaire, étiquette d'affichage « Artistes variés » (jamais un artiste). Tous les titres s'y rattachent quel que
  soit leur artiste principal ; `play_count` = somme de tous les titres ; certification sur ce total. Exclu des
  certifications / records d'albums **par artiste** et du Panthéon (`artist_id IS NOT NULL`). Décision :
  `user_corrections` type `ALBUM_SHARED` (clé = titre normalisé, « 1 » / « 0 ») sinon `TitleNormalizer.isSharedAlbum`.
  `AlbumSharing.consolidate` fusionne / redécoupe les données existantes.
- **Liens & versions (v5, `MIGRATION_4_5`)** : `daily_plays.root_id` = `tracks.original_track_id` sinon `track_id` —
  toutes les agrégations par titre (Stats `topForPeriod`, Billboard `rankTracks`, rangs, séries, records) groupent sur
  `root_id` : un **remix featuring** ou une **version avec invité** (`is_remix = 1`, `original_track_id` = root) compte
  dans le total de l'original. `tracks.play_count` d'un root = somme du groupe (`recomputeAggregates`), celui d'une
  version = ses propres écoutes ; `topAllTime` / `rankAllTime` / candidats certification = roots seulement. Résolution
  (`LibraryRepository.resolve`) : même titre + même artiste principal mais jeu d'invités différent → version
  « Titre (with Invité) » (invité du champ artiste) ou « Titre (feat. Invité) » (invité du titre) liée au root ; si la
  version avec invité existait avant l'original, elle est renommée et l'original devient root ; remix dont l'artiste
  principal diffère → original cherché parmi les titres portant le même nom et partageant un artiste ; versions
  orphelines rattachées quand l'original apparaît. Table `artist_exceptions` (`name`, `name_key` UNIQUE) = noms
  jamais découpés par `splitArtists` (« HUNTR/X », « AC/DC », « Tyler, The Creator »…), éditables (Éditeur →
  🔒 Noms protégés), exportés dans le JSON (format 2.1, `artist_exceptions`). `RelinkJob` re-résout toutes les
  écoutes depuis `raw_title` / `raw_artist` / `raw_album` (une fois après la mise à jour, puis Réglages → Données).
- **Révision (v4, `MIGRATION_3_4`)** : `scrobbles.needs_review` / `review_reason` (ex. "Artiste manquant", "Titre
  inconnu", "Notification incomplète") et `raw_title` / `raw_artist` / `raw_album` (valeur brute du player). Le score
  `confidence_score` suit la source : MediaSession 100 · Mixte 80 · Notification complète 70 · Incomplet 50 ; titre ou
  artiste "Unknown/Inconnu" → 🔴 À corriger (score < 70). L'onglet ⚠️ À corriger de l'Éditeur regroupe ces écoutes par
  titre (🔴) et les titres acceptés avec réserve 70-89 (`tracks.needs_review`, 🟡). Valider déplace les écoutes cochées
  (score 100), journalise une entrée `edit_history` (type `REVIEW_FIX`, un seul Undo) et, si toutes les écoutes sont
  cochées, mémorise la règle dans `user_corrections` (appliquée dès la prochaine détection, avant tout appel API).
  "Ignorer" mémorise la valeur brute comme confirmée (`REVIEW_IGNORE`).
- Les index d'unicité (`scrobbles(track_id, started_at)`, `artists(name)`, `albums(title, artist_id)`) portent la
  règle "doublons ignorés à l'import" et la fusion des entités.

## Records 25-30 (extension 0.10.0) — `records_cache`

Aucun changement de schéma : nouvelles valeurs de `record_type` / `subcategory`, recalculées à chaque rebuild.

| record_type | period_type | category | subcategory | value | value_date | extra_data |
|---|---|---|---|---|---|---|
| `LONGEST_LIFESPAN` | D/W/M/Y | TRACK/ARTIST/ALBUM | `TOP10` `TOP20` `TOP50` `CHART` | périodes (inclusif, absences comprises, ≥ 2 apparitions) | dernière apparition dans la zone | « du … au … · n apparitions » |
| `MOST_REENTRIES` | D/W/M/Y | idem | idem | nb de retours (≥ 1 période manquée, sortie de la zone suffit) | dernier retour | plus longue absence |
| `LONGEST_ABSENCE_RETURN` | D/W/M/Y | idem | idem | plus longue pause (périodes) | date du retour | « #a → #b après n » |
| `LONGEST_LISTENING_STREAK` | NULL | idem | NULL | jours consécutifs (≥ 2) avec ≥ 1 écoute (`daily_plays`, artistes feat. compris) | fin de série | « du … au … (· en cours) » |
| `PODIUM_SWEEP` | D/W/M/Y | ARTIST/ALBUM | `TOP3_SOLO` `TOP3_STD` `TOP5_*` `TOP10_*` | nb de périodes où toutes les places de la zone (chart titres) lui appartiennent ; Solo = aucun featuring | dernier balayage | premier balayage |
| `MOST_RECORDS` | NULL | idem | NULL | nb de classements (record × période × catégorie × sous-section) où l'entité est #1 (ex æquo compris, `MOST_RECORDS` exclu) | — | répartition par famille |
| `FASTEST_RISE` (modifié) | D/W/M/Y | idem | `TOP1` `TOP3` `TOP5` `TOP10` `TOP20` | périodes calendaires entre l'entrée et la 1ʳᵉ atteinte de la zone, **≥ 1** (entrées directes exclues) | date d'atteinte | « entré #a → #b » |

Règles communes : un jour sans chart = une absence ; égalités départagées par la date la plus ancienne (`RecordsEngine.top`).
Requêtes dédiées : `RecordDao.heldNumberOnes(category, entityId)` (fiche Most Records) et `RecordDao.tiedAt(...)` (ex æquo), `DailyPlayDao.allEntityDays()` (séries d'écoute).
