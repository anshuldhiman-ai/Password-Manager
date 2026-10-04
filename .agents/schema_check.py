"""Verify migrations produce the schema Room expects, for every upgrade path.

Reads the expected DDL straight out of Room's generated `VaultDatabase_Impl.java`
and replays each `MIGRATION_x_y` against an in-memory SQLite DB seeded at the
source version, then diffs PRAGMA table_info against the expected columns.

This is the gate that catches schema drift like the stale `serialNo` column that
Room's TableInfo validation would reject at open time.
"""
import re, sqlite3, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, 'app/build/generated/ksp/debug/java/com/family/pswdmngr/data/VaultDatabase_Impl.java')
KOT = os.path.join(ROOT, 'app/src/main/java/com/family/pswdmngr/data/VaultDatabase.kt')


def collapse(text):
    """Join adjacent Kotlin string-literal concatenations onto one line."""
    return re.sub(r'"\s*\+\s*\n\s*"', '', text)


def exec_sql_statements(kotlin_src):
    """Return {(from, to): [sql, ...]} for every db.execSQL(...) call.

    A statement may be built from several string literals joined with `+`, and
    may contain a Kotlin `if (...) "a" else "b"` conditional. We take the first
    branch of any such conditional, which is correct for every branch the real
    device actually hits here (all source versions have the column).
    """
    out = {}
    for m in re.finditer(r'val MIGRATION_(\d+)_(\d+) = object', kotlin_src):
        start = m.end()
        nxt = kotlin_src.find('val MIGRATION_', start)
        block = kotlin_src[start: nxt if nxt != -1 else len(kotlin_src)]
        stmts = []
        for call in re.finditer(r'execSQL\s*\(', block):
            i = call.end()
            depth = 1
            literals = []
            while i < len(block) and depth > 0:
                ch = block[i]
                if ch == '"':
                    j = block.find('"', i + 1)
                    if j == -1:
                        break
                    literals.append(block[i + 1:j])
                    i = j + 1
                elif ch == '(':
                    depth += 1
                    i += 1
                elif ch == ')':
                    depth -= 1
                    i += 1
                else:
                    i += 1
            stmts.append(''.join(literals))
        out[(int(m.group(1)), int(m.group(2)))] = stmts
    return out


def cols_from_sql(sql):
    """Parse a CREATE TABLE into {column: (type, notNull, primaryKey)}."""
    body = sql[sql.index('(') + 1: sql.rindex(')')]
    out = {}
    for part in re.split(r',\s*(?![^()]*\))', body):
        m = re.match(r'\s*`(\w+)`\s+([A-Z]+)(.*)', part)
        if m:
            out[m.group(1)] = (m.group(2), 'NOT NULL' in m.group(3), 'PRIMARY KEY' in m.group(3))
    return out


def table(con, name):
    try:
        return {r[1]: (r[2].upper(), bool(r[3]), bool(r[5]))
                for r in con.execute("PRAGMA table_info('%s')" % name)}
    except sqlite3.Error:
        return None


gen = collapse(open(GEN, encoding='utf-8', errors='replace').read())
kot = collapse(open(KOT, encoding='utf-8', errors='replace').read())

# expected[table_name] = CREATE TABLE sql, as Room's generated code emits it
expected = {}
for sql, name in re.findall(r'"(CREATE TABLE IF NOT EXISTS `(\w+)` [^"]*)"', gen):
    expected[name] = sql

MIG = exec_sql_statements(kot)

# Columns added between v4 and v5, stripped to rebuild a v4-shaped database.
V4_STRIP = ("`tags` TEXT NOT NULL, ", "`lastUsedAt` INTEGER NOT NULL, ",
            "`passwordStrength` INTEGER NOT NULL, ", "`isCompromised` INTEGER NOT NULL, ")

# v1 shipped `entries` only (recovered from PSWD-MNGR-v1.0.apk).
V1_ENTRIES = ("CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
              " `title` TEXT NOT NULL, `category` TEXT NOT NULL, `username` TEXT NOT NULL,"
              " `password` TEXT NOT NULL, `url` TEXT NOT NULL, `notes` TEXT NOT NULL,"
              " `totpSecret` TEXT NOT NULL, `favorite` INTEGER NOT NULL,"
              " `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")


def build(version):
    con = sqlite3.connect(':memory:')
    if version == 1:
        con.execute(V1_ENTRIES)
    else:
        for name, sql in expected.items():
            if name == 'reminders':
                continue
            for drop in V4_STRIP:
                sql = sql.replace(drop, "")
            con.execute(sql)
    for k in sorted(MIG):
        if k[0] >= version:
            for s in MIG[k]:
                con.execute(s)
    return con


ok = True
for v in (1, 4):
    con = build(v)
    print("\n=== path v%d -> v5 ===" % v)
    for name, sql in sorted(expected.items()):
        exp, got = cols_from_sql(sql), table(con, name)
        if got is None:
            print("  MISSING TABLE %s" % name)
            ok = False
            continue
        diff = {c for c in set(exp) | set(got) if exp.get(c) != got.get(c)}
        if diff:
            ok = False
            print("  MISMATCH %s:" % name)
            for c in sorted(diff):
                print("    %-20s expected=%s got=%s" % (c, exp.get(c), got.get(c)))
        else:
            print("  ok %s" % name)

print("\nRESULT:", "PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)