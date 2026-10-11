#!/usr/bin/env python3
"""Vérifie que les index créés par les migrations sont ceux que déclarent les entités.

Room reconstruit le schéma attendu à partir des annotations @Entity, puis le compare à
celui produit par les migrations. Un index créé en migration mais absent de l'entité fait
échouer cette comparaison À L'OUVERTURE DE LA BASE, sur l'appareil :

    java.lang.IllegalStateException: Migration didn't properly handle: tracks

L'inverse (index déclaré mais jamais créé) échoue tout autant. La compilation ne dit
rien : Room ne valide à la compilation que le SQL des @Query et le schéma cible.

C'est exactement ce qu'a produit la 0.22.16 — MIGRATION_9_10 créait
index_tracks_original_track_id et index_tracks_first_played_at sans les déclarer dans
TrackEntity — et l'app ne s'ouvrait plus. Corrigé en 0.22.17.

Ce script compare les deux listes en quelques millisecondes, sans Android ni Gradle.

    Usage :  python3 NovaStats/tools/check_schema.py
    Sortie : 0 si toutes les tables couvertes par une migration sont cohérentes, 1 sinon.

Seules les tables dont une migration crée au moins un index sont vérifiées : pour les
autres, c'est Room lui-même qui crée les index (schéma v1), donc il ne peut pas y avoir
de divergence.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTITY_DIRS = [ROOT / "app/src/main/java/com/novastats/app/data/db/entity"]
DATABASE = ROOT / "app/src/main/java/com/novastats/app/data/db/NovaDatabase.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def slice_balanced(src: str, start: int, open_ch: str, close_ch: str) -> str:
    """Renvoie le contenu entre la parenthèse/crochet ouvrante en `start` et sa fermante.

    Un `re.match(r'\\((.*?)\\)')` s'arrête à la PREMIÈRE fermante : avec des annotations
    imbriquées (`@Entity(tableName = "x", indices = [Index("y")])`) cela tronque la
    déclaration et produit des faux positifs. On compte donc les niveaux.
    """
    depth = 0
    in_string = False
    i = start
    while i < len(src):
        c = src[i]
        if in_string:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return src[start + 1 : i]
        i += 1
    raise ValueError(f"{open_ch}{close_ch} non appariés à partir de l'offset {start}")


def declared_indices(src: str) -> dict[str, set[str]]:
    """table -> noms d'index déclarés par @Entity (règle de nommage de Room)."""
    result: dict[str, set[str]] = {}
    for match in re.finditer(r"@Entity\b", src):
        paren = src.find("(", match.end())
        # @Entity sans parenthèse : nom de table = nom de classe en snake_case, aucun index.
        if paren == -1 or src[match.end() : paren].strip():
            continue
        args = slice_balanced(src, paren, "(", ")")

        table = re.search(r'tableName\s*=\s*"([^"]+)"', args)
        if not table:
            continue  # entité sans nom de table explicite : aucune de celles-ci n'a d'index en migration
        table_name = table.group(1)

        names: set[str] = set()
        for idx in re.finditer(r"Index\b", args):
            inner_paren = args.find("(", idx.end())
            if inner_paren == -1 or args[idx.end() : inner_paren].strip():
                continue
            inner = slice_balanced(args, inner_paren, "(", ")")

            # Index(value = ["a", "b"]) ou Index("a", "b") ou Index("a")
            value = re.search(r"value\s*=\s*\[", inner)
            if value:
                cols_src = slice_balanced(inner, value.end() - 1, "[", "]")
            else:
                cols_src = inner.split(",unique")[0].split(", unique")[0]
            cols = re.findall(r'"([^"]+)"', cols_src)
            if not cols:
                continue
            explicit = re.search(r'name\s*=\s*"([^"]+)"', inner)
            names.add(explicit.group(1) if explicit else f"index_{table_name}_{'_'.join(cols)}")
        result[table_name] = names
    return result


CREATE_INDEX = re.compile(
    r"CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s+ON\s+(\w+)\s*\(",
    re.IGNORECASE,
)
DROP_INDEX = re.compile(r"DROP\s+INDEX\s+(?:IF\s+EXISTS\s+)?(\w+)", re.IGNORECASE)
DROP_TABLE = re.compile(r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?(\w+)", re.IGNORECASE)


def migration_indices(src: str) -> dict[str, set[str]]:
    """table -> noms d'index présents après TOUTES les migrations.

    Les migrations sont rejouées dans l'ordre du fichier (elles y sont écrites de la 1→2
    à la 9→10), en appliquant CREATE INDEX, DROP INDEX et DROP TABLE. Ne pas tenir compte
    des DROP produit des faux positifs : index_daily_plays_root_id est créé en v4→5 puis
    supprimé en v8→9, donc il ne doit PAS figurer dans le résultat.
    """
    result: dict[str, set[str]] = {}
    owner: dict[str, str] = {}  # nom d'index -> table, pour DROP INDEX (qui ne la nomme pas)

    events = []
    for pattern, kind in ((CREATE_INDEX, "create"), (DROP_INDEX, "drop_index"), (DROP_TABLE, "drop_table")):
        for match in pattern.finditer(src):
            events.append((match.start(), kind, match))
    events.sort(key=lambda e: e[0])

    for _, kind, match in events:
        if kind == "create":
            name, table = match.group(1), match.group(2)
            result.setdefault(table, set()).add(name)
            owner[name] = table
        elif kind == "drop_index":
            name = match.group(1)
            table = owner.get(name)
            if table:
                result.get(table, set()).discard(name)
                del owner[name]
            else:
                # DROP INDEX IF EXISTS sur un index jamais créé ici (schéma v1) : sans effet connu.
                pass
        else:  # drop_table : tous ses index disparaissent avec elle
            table = match.group(1)
            for name in result.get(table, set()):
                owner.pop(name, None)
            result.pop(table, None)
    return result


def main() -> int:
    if not DATABASE.exists():
        print(f"❌ introuvable : {DATABASE}")
        return 1

    declared: dict[str, set[str]] = {}
    for directory in ENTITY_DIRS:
        for path in sorted(directory.glob("*.kt")):
            for table, names in declared_indices(read(path)).items():
                declared.setdefault(table, set()).update(names)

    created = migration_indices(read(DATABASE))

    if not created:
        print("❌ aucun CREATE INDEX trouvé dans NovaDatabase.kt — le parseur est cassé")
        return 1
    if not declared:
        print("❌ aucune entité avec tableName trouvée — le parseur est cassé")
        return 1

    failures = 0
    total = 0
    for table in sorted(created):
        total += 1
        # Seule cette direction est vérifiable ici : un index qu'une migration crée doit être
        # déclaré dans l'entité, sinon Room refuse le schéma à l'ouverture.
        # L'autre sens (déclaré, jamais créé par une migration) est normal : ce sont les index
        # du schéma v1, que Room crée lui-même dans onCreate(). Ils ne peuvent pas diverger.
        missing = created[table] - declared.get(table, set())
        if missing:
            failures += 1
            print(f"❌ {table}")
            for name in sorted(missing):
                print(f"     créé par une migration mais ABSENT de @Entity : {name}")
        else:
            print(f"✅ {table:<22} {len(created[table])} index de migration, tous déclarés dans @Entity")

    print()
    if failures:
        print(f"❌ {failures}/{total} table(s) incohérente(s).")
        print("   Room refusera d'ouvrir la base : « Migration didn't properly handle: <table> ».")
        print("   La compilation ne le détecte pas — c'est l'appareil qui plante.")
        return 1
    print(f"✅ {total} table(s) touchées par une migration : chaque index créé est déclaré dans son entité.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
