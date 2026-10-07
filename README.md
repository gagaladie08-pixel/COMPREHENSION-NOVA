# COMPREHENSION-NOVA

Espace de travail du projet **NovaStats** — application Android de statistiques musicales personnelles.

| Élément | Description |
|---|---|
| `DEBUT` | Export brut de la conversation de conception (cahier des charges complet : détection, onglets, 24 records, 34 tables, 15 thèmes…) |
| `nova_backup_20260707_1500.json` | Backup réel v1 (949 titres, 9 697 écoutes) — importable dans l'app |
| `NovaStats/` | **Projet Android** (Kotlin · Compose · Room) — voir `NovaStats/README.md` |

> ✅ **Vérifié le 2026-10-07** : `DEBUT` ne contient **aucune valeur de clé API**. Les mentions de
> `SPOTIFY_CLIENT_ID=`, `SPOTIFY_CLIENT_SECRET=`, `GOOGLE_API_KEY=`, `LASTFM_API_KEY=` et
> `FANART_API_KEY=` y sont des **modèles vides** (rien après le `=`). Le fichier cite même la
> recommandation de ne jamais publier de clé — c'est la consigne, pas une fuite. Aucune recherche
> de format réel (`AIza…` pour Google, 32 hexadécimales pour Spotify/Last.fm) ne remonte de
> résultat. Le seul élément sensible restant est un chemin Windows local (`C:\Users\LENOVO\…`),
> sans portée. Les vraies clés vivent dans `local.properties`, qui n'est **pas** versionné.
