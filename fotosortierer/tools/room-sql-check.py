#!/usr/bin/env python3
"""
Prüft die Room-@Query-Texte der DAOs gegen eine echte SQLite-Datenbank – ersatzweise für die Compile-Zeit-
Prüfung von Room (KSP), die ohne Android-SDK/Google Maven nicht laufen kann.

Geprüft wird je @Query: (1) der SQL-Text ist gültig für das Schema der Entities, (2) die Ergebnisspalten
decken die Konstruktorparameter der Ergebnisklasse ab (Room verlangt das), (3) jeder benannte Parameter
(:name) existiert als Funktionsparameter und umgekehrt.

Aufruf:  python3 tools/room-sql-check.py
"""
import os
import re
import sqlite3
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
APP = os.path.join(ROOT, "app/src/main/kotlin/de/ingvoss/fotosortierer/data/db")
CORE = os.path.join(ROOT, "core/src/main/kotlin/de/ingvoss/fotosortierer/core/MediaSql.kt")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


# ---- Kotlin-Konstanten aus MediaSql.kt auswerten ------------------------------------------------
def eval_consts(src):
    consts = {}
    for m in re.finditer(r'const val ([A-Z_0-9]+) =\s*((?:"""[\s\S]*?"""|"(?:[^"\\]|\\.)*"|\s*\+\s*|\s)+?)(?=\n\s*(?:const val|/\*\*|private|fun|object|data|enum|\}|//)|\Z)', src):
        name, expr = m.group(1), m.group(2)
        parts = re.findall(r'"""([\s\S]*?)"""|"((?:[^"\\]|\\.)*)"', expr)
        text = ""
        for triple, plain in parts:
            piece = triple if triple else plain
            piece = re.sub(r'\$\{?([A-Z_0-9]+)\}?', lambda mm: consts.get(mm.group(1), "$" + mm.group(1)), piece)
            text += piece
        consts[name] = text
    return consts


CONSTS = eval_consts(read(CORE))


def resolve_query(expr):
    out = ""
    for token in re.findall(r'"((?:[^"\\]|\\.)*)"|MediaSql\.([A-Z_0-9]+)', expr):
        literal, const = token
        if const:
            out += CONSTS[const]
        else:
            out += re.sub(r'\$\{?([A-Z_0-9]+)\}?', lambda mm: CONSTS.get(mm.group(1), mm.group(0)), literal)
    return out


# ---- Schema ------------------------------------------------------------------------------------
SCHEMA = CONSTS["CREATE_SQL"] + """;
CREATE TABLE place (clusterId INTEGER PRIMARY KEY NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, name TEXT, nameState INTEGER NOT NULL);
CREATE TABLE geocode_cache (cellKey TEXT PRIMARY KEY NOT NULL, name TEXT, resolvedAt INTEGER NOT NULL);
"""

TABLE_COLUMNS = {}


def classes_with_fields():
    """Konstruktorparameter aller data classes in Rows.kt und Entities.kt."""
    result = {}
    for fname in ("Rows.kt", "Entities.kt"):
        src = read(os.path.join(APP, fname))
        for m in re.finditer(r'data class (\w+)\(([\s\S]*?)\)(?: \{|\n)', src):
            fields = re.findall(r'(?:@PrimaryKey\([^)]*\)\s*)?val (\w+):', m.group(2))
            result[m.group(1)] = fields
    return result


FIELDS = classes_with_fields()
ENTITY_TABLE = {"MediaStateEntity": "media_state", "PlaceEntity": "place", "GeocodeCacheEntity": "geocode_cache"}

PRIMITIVES = {"Int", "Long", "String", "Boolean", "Double", "Float"}


def result_class(return_type):
    t = return_type.strip()
    for _ in range(3):
        m = re.match(r'(?:Flow|List|PagingSource)<(.*)>$', t)
        if not m:
            break
        t = m.group(1)
        if "," in t:  # PagingSource<Int, X>
            t = t.split(",")[-1].strip()
    return t.rstrip("?").strip()


def main():
    db = sqlite3.connect(":memory:")
    db.executescript(SCHEMA)
    for table in ("media_state", "place", "geocode_cache"):
        TABLE_COLUMNS[table] = [r[1] for r in db.execute(f"PRAGMA table_info({table})")]

    problems = 0
    checked = 0
    for fname in sorted(os.listdir(APP)):
        if not fname.endswith("Dao.kt"):
            continue
        src = read(os.path.join(APP, fname))
        pattern = re.compile(
            r'@Query\(\s*((?:"(?:[^"\\]|\\.)*"|MediaSql\.[A-Z_0-9]+|\s|\+)+)\)\s*'
            r'(?:suspend\s+)?fun\s+(\w+)\(([^)]*)\)(?:\s*:\s*([^\n{]+))?'
        )
        for m in pattern.finditer(src):
            expr, name, params, ret = m.group(1), m.group(2), m.group(3), (m.group(4) or "Unit").strip()
            sql = resolve_query(expr)
            checked += 1
            where = f"{fname}:{name}"
            named = re.findall(r'(?<![:\w]):(\w+)', sql)
            declared = [p.split(":")[0].strip() for p in params.split(",") if p.strip()]
            if set(named) != set(declared):
                print(f"FEHLER {where}: Parameter im SQL {sorted(set(named))} ≠ Funktionsparameter {sorted(declared)}")
                problems += 1
            # Platzhalter ersetzen; Listen (IN (:x)) bekommen einen Wert.
            prepared = re.sub(r'(?<![:\w]):(\w+)', '?', sql)
            args = [1] * prepared.count("?")
            try:
                cur = db.execute(prepared, args)
                columns = [d[0] for d in cur.description] if cur.description else []
            except sqlite3.Error as e:
                print(f"FEHLER {where}: SQL ungültig: {e}\n    {prepared}")
                problems += 1
                continue
            cls = result_class(ret)
            if cls in PRIMITIVES or cls == "Unit" or not columns:
                continue
            if cls in ENTITY_TABLE:
                needed = FIELDS.get(cls, [])
            else:
                needed = FIELDS.get(cls)
                if needed is None:
                    print(f"WARNUNG {where}: Ergebnisklasse {cls} nicht gefunden")
                    continue
            missing = [f for f in needed if f not in columns]
            if missing:
                print(f"FEHLER {where}: Spalten fehlen für {cls}: {missing} (vorhanden: {columns})")
                problems += 1
    print(f"{checked} @Query geprüft, {problems} Problem(e)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
